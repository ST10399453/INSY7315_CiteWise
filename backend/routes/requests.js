import { Router } from "express"; // (GeeksforGeeks, 2022a)
import multer from "multer";
import { body, param } from "express-validator"; // (express-validator, 2019)
import { checkAuth } from "../auth/checkAuth.js"; // (Balaji, 2023)
import {
  createRequest, getRequests, getRequestById, transitionAssign,
  transitionStartReview, transitionSubmitReview, transitionResubmit,
  transitionCancel
} from "../db/dbManager.js"; // Firestore-backed ops (Firebase, 2019a)
import { newFileId, safeName, uploadToR2 } from "../blobs/storage.js"; // R2 storage helpers (Cloudflare, 2024)
import { bailIfInvalid } from "../utils/expressHelpers.js"; // Validation bail-out (express-validator, 2019)

const router = Router();
const upload = multer({ storage: multer.memoryStorage() }); // In-memory multipart handling

/**
 * ============================================================
 * 1) Create Request (multipart)
 * ------------------------------------------------------------
 * Security & roles enforced via middleware/transition (Manico & Detlefsen, 2015)
 * Uploads to Cloudflare R2, persists Firestore record (Cloudflare, 2024; Firebase, 2019a)
 * ============================================================
 */
router.post(
  "/",
  checkAuth, // Require auth (Balaji, 2023)
  upload.single("file"), // Expect a single file under field name "file"
  body("documentName").isString().notEmpty(), // (express-validator, 2019)
  body("serviceType").isString().isIn([
    "PROOFREADING_EDITING","FORMATTING_REFERENCING","DATA_ANALYSIS_SUPPORT",
    "RESEARCH_METHODOLOGY_COACHING","TRANSLATION","OTHER",
  ]), // (express-validator, 2019)
  body("description").isString().notEmpty(), // (express-validator, 2019)
  body("priority").isString().isIn(["LOW","MEDIUM","HIGH"]), // (express-validator, 2019)
  body("deadline").optional().isString(), // ISO string recommended
  async (req, res) => {
    // Validate inputs; bail early if invalid
    const v = bailIfInvalid(req, res); if (v) return v;

    try {
      // Ensure a file is present
      if (!req.file) return res.status(400).json({ message: "file is required" });

      // Extract form fields
      const { documentName, serviceType, description, priority, deadline = null, customName = null } = req.body;

      // Gather file metadata
      const mime = req.file.mimetype || "application/octet-stream";
      const size = req.file.size || req.file.buffer?.length || 0;

      // Generate a stable file ID and safe object key for storage
      const fileId = newFileId(); // (Tony, 2023)
      const cleanName = safeName(documentName); // input sanitization for keys (Manico & Detlefsen, 2015)
      const objectPath = `uploads/${fileId}/${cleanName}`;

      // Upload file bytes to Cloudflare R2
      const r2Meta = await uploadToR2({ key: objectPath, body: req.file.buffer, contentType: mime }); // (Cloudflare, 2024)

      // Assemble DB payload (Firestore document shape) (Firebase, 2019a)
      const payload = {
        userId: req.user.uid,           // Student submitting the request
        consultantId: null,  
        quotationId: null,           // Not assigned yet
        serviceType,
        description,
        priority,
        deadline: deadline || null,
        status: "Submitted",            // Initial state
        documentId: fileId,
        customName: customName || null,
        file: { fileId, originalName: documentName, mimeType: mime, size },
        storage: { cloudflare: r2Meta },// Store R2 location metadata (Cloudflare, 2024)
      };

      // Persist the new request
      const created = await createRequest(payload); // (Firebase, 2019a)

      // Return created entity (include storage meta for client to reference)
      return res.status(201).json({ ...created, storage: payload.storage });
    } catch (err) {
      console.error(err);
      res.status(500).json({ message: err.message });
    }
  }
);

/**
 * ============================================================
 * 2) List Requests
 * ------------------------------------------------------------
 * Uses query filtering & optional ordering (Firebase, 2019a)
 * Role-based visibility to be enforced (Manico & Detlefsen, 2015)
 * ============================================================
 */
router.get("/", 
 checkAuth,  // (Balaji, 2023)
  async (req, res) => {
  try {
    // Default behavior: students see their own requests; admins/consultants can override with query
    const status = req.query.status ?? null;
    const userId = (req.query.userId ?? req.user?.uid) || null;
    const consultantId = req.query.consultantId ?? null;
    const sort = req.query.sort ?? undefined;
    const dir = req.query.dir ?? undefined;

    // NOTE: Ensure getRequests enforces role-based filtering using req.user (Manico & Detlefsen, 2015)
    const out = await getRequests({ status, userId, consultantId, sort, dir }); // (Firebase, 2019a)
    res.json(out);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 3) Request Details
 * ------------------------------------------------------------
 * Single-document fetch; downstream should verify visibility (Firebase, 2019a)
 * ============================================================
 */
router.get("/:id", 
  checkAuth, // (Balaji, 2023)
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v; // (express-validator, 2019)
  try {
    // Fetch a single request by ID; authorization should be verified downstream
    res.json(await getRequestById(req.params.id)); // (Firebase, 2019a)
  } catch (err) {
    console.error(err);
    res.status(404).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 4) Assign Consultant (Create assignment)
 * ------------------------------------------------------------
 * Transactional transition with RBAC checks (Firebase, 2019a; Manico & Detlefsen, 2015)
 * ============================================================
 */
router.post("/:id/assign",
  checkAuth, // (Balaji, 2023)
  param("id").isString(),
  body("consultantId").isString().notEmpty(), // (express-validator, 2019)
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v; // (express-validator, 2019)
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.body.consultantId,
        deadline: req.body.deadline ?? null,
        allowUpdate: false,     // This call creates a new assignment
        actor: req.user,        // Used for authorization inside transition
      }); // (Firebase, 2019a)
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

/**
 * ============================================================
 * 5) Update Assignment (Change consultant/deadline)
 * ------------------------------------------------------------
 * Admin-only adjustment path, enforced in transition (Manico & Detlefsen, 2015)
 * ============================================================
 */
router.put("/:id/assign",
  checkAuth, // (Balaji, 2023)
  param("id").isString(),
  body("consultantId").optional().isString(), // (express-validator, 2019)
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.body.consultantId ?? null, // null to unassign
        deadline: req.body.deadline ?? null,
        allowUpdate: true,       // This call updates an existing assignment
        actor: req.user,
      }); // (Firebase, 2019a)
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

/**
 * ============================================================
 * 6) Start Review
 * ------------------------------------------------------------
 * Only assigned consultant/admin may start (RBAC) (Manico & Detlefsen, 2015)
 * ============================================================
 */
router.post("/:id/start-review", 
  checkAuth, // (Balaji, 2023)
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    res.json(await transitionStartReview({ id: req.params.id, actor: req.user })); // (Firebase, 2019a)
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 7) Submit Review Outcome
 * ------------------------------------------------------------
 * Validates outcome; enforces reviewable state & permissions (express-validator, 2019; Manico & Detlefsen, 2015)
 * ============================================================
 */
router.post("/:id/review",
  checkAuth, // (Balaji, 2023)
  param("id").isString(),
  body("outcome").isIn(["approve","reject","fail"]), // (express-validator, 2019)
  body("feedback").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionSubmitReview({
        id: req.params.id,
        outcome: req.body.outcome,
        feedback: req.body.feedback ?? null,
        actor: req.user,
      }); // (Firebase, 2019a)
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

/**
 * ============================================================
 * 8) Resubmit (Student action)
 * ------------------------------------------------------------
 * Ownership checks apply (Manico & Detlefsen, 2015); transactional update (Firebase, 2019a)
 * ============================================================
 */
router.post("/:id/resubmit", 
  checkAuth, // (Balaji, 2023)
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    res.json(await transitionResubmit({ id: req.params.id, actor: req.user })); // (Firebase, 2019a)
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 9) Cancel Request
 * ------------------------------------------------------------
 * Admin/owner/assigned consultant only (RBAC) (Manico & Detlefsen, 2015)
 * ============================================================
 */
router.post("/:id/cancel", 
  checkAuth, // (Balaji, 2023)
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    res.json(await transitionCancel({ id: req.params.id, actor: req.user })); // (Firebase, 2019a)
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 10) Self-Assign (Consultant claims a request)
 * ------------------------------------------------------------
 * Self-claim flow; transition enforces claimable state & role (Manico & Detlefsen, 2015)
 * ============================================================
 */
router.post("/:id/self-assign",
  checkAuth, // (Balaji, 2023)
  param("id").isString(),
  body("deadline").optional().isString(), // (express-validator, 2019)
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.user.uid,        // self-assign to the actor
        deadline: req.body.deadline ?? null,
        allowUpdate: false,                // create assignment (not update)
        actor: req.user,                   // used by transition for authz
      }); // (Firebase, 2019a)
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);


export default router;

/*
REFERENCES

Android Knowledge. 2023. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
YouTube. August 2023 <https://www.youtube.com/watch?v=oGyQMBKPuNY> [accessed September 2025].

Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
Medium. January 2024 <https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494> [accessed September 2025].

Axios. 2023. “Getting Started | Axios Docs”.
Axios-Http.com. 2023 <https://axios-http.com/docs/intro> [accessed September 2025].

Balaji, Dev. 2023. “JWT Authentication in Node.js: A Practical Guide”.
Medium. September 2023 <https://dvmhn07.medium.com/jwt-authentication-in-node-js-a-practical-guide-c8ab1b432a49> [accessed October 2025].

Cloudflare. 2024. “Cloudflare R2 · Cloudflare R2 Docs”.
Cloudflare Docs. April 5, 2024 <https://developers.cloudflare.com/r2/> [accessed 12 October 2025].

express-validator. 2019. “Getting Started · Express-Validator”.
Github.io. 2019 <https://express-validator.github.io/docs/> [accessed October 2025].

Firebase. 2019a. “Cloud Firestore | Firebase”.
Firebase. 2019 <https://firebase.google.com/docs/firestore> [accessed September 2025].

Firebase. 2019b. “Firebase Authentication | Firebase”.
Firebase. Google. 2019 <https://firebase.google.com/docs/auth> [accessed September 2025].

Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
Firebase. 2019 <https://firebase.google.com/docs/cloud-messaging> [accessed September 2025].

Firebase. 2019d. “Firebase Realtime Database”.
Firebase. 2019 <https://firebase.google.com/docs/database> [accessed September 2025].

GeeksforGeeks. 2022a. “Use of CORS in Node.js”.
GeeksforGeeks. March 2022 <https://www.geeksforgeeks.org/node-js/use-of-cors-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2022b. “What Is Expressratelimit in Node.js ?”.
GeeksforGeeks. April 2022 <https://www.geeksforgeeks.org/node-js/what-is-express-rate-limit-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2024. “NPM Dotenv”.
GeeksforGeeks. May 2024 <https://www.geeksforgeeks.org/node-js/npm-dotenv/> [accessed October 2025].

Manico, Jim and August Detlefsen. 2015. *Iron-Clad Java: Building Secure Web Applications*.
McGraw-Hill Education.

Nakazawa Tech. 2018. “Delightful JavaScript Testing with Jest”.
YouTube. May 30, 2018 <https://www.youtube.com/watch?v=cAKYQpTC7MA> [accessed 2 November 2025].

NextJS. 2025. “Documentation | NestJS - a Progressive Node.js Framework”.
Documentation | NestJS - a Progressive Node.js Framework. 2025 <https://docs.nestjs.com/security/helmet> [accessed October 2025].

Patel, Ravi. 2024. “A Beginner’s Guide to the Node.js”.
Medium. December 2024 <https://medium.com/@ravipatel.it/a-beginners-guide-to-the-node-js-469f7458bbb2> [accessed October 2025].

React Native. 2025. “React Fundamentals · React Native”.
Reactnative.dev. 2025 <https://reactnative.dev/docs/intro-react> [accessed September 2025].

Samson Omojola. 2024. “Password Hashing in Node.js with Bcrypt”.
Honeybadger Developer Blog. Honeybadger. January 2024 <https://www.honeybadger.io/blog/node-password-hashing/> [accessed September 2025].

Tony. 2023. “Guide to Node’s Crypto Module for Encryption/Decryption”.
Medium. May 5, 2023 <https://medium.com/@tony.infisical/guide-to-nodes-crypto-module-for-encryption-decryption-65c077176980> [accessed 2 November 2025].
*/
