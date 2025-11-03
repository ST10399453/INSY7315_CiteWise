import { Router } from "express"; // (GeeksforGeeks, 2022a)
import multer from "multer";
import { body, param, query } from "express-validator"; // (express-validator, 2019)
import { checkAuth } from "../auth/checkAuth.js"; // (Balaji, 2023)
import { db as fsdb } from "../db/firebaseAdmin.js"; // (Firebase, 2019a)
import { isAdmin, bailIfInvalid } from "../utils/expressHelpers.js"; // (Manico & Detlefsen, 2015; express-validator, 2019)
import {
  newFileId,
  safeName,
  uploadToR2,
  r2SignedUrl,
  streamFromR2,
} from "../blobs/storage.js"; // Cloudflare R2 integration (Cloudflare, 2024)

const router = Router();
const upload = multer({ storage: multer.memoryStorage() }); // In-memory multipart handling

/**
 * ============================================================
 * Create Resource (Admins only)
 * ------------------------------------------------------------
 * Uses input validation + RBAC guard pattern (express-validator, 2019; Manico & Detlefsen, 2015)
 * Uploads to R2 and persists metadata in Firestore (Cloudflare, 2024; Firebase, 2019a)
 * ============================================================
 */
router.post(
  "/",
  checkAuth, // Auth required (Balaji, 2023)
  upload.single("file"),
  body("name").isString().notEmpty(), // (express-validator, 2019)
  body("faculty").isString().notEmpty(), // (express-validator, 2019)
  body("category").isString().isIn(["WRITING_GUIDE", "TEMPLATE", "AI_USAGE"]), // (express-validator, 2019)
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      // Only admins may create resources (Manico & Detlefsen, 2015)
      if (!isAdmin(req.user)) return res.status(403).json({ message: "Forbidden" });

      // File is mandatory
      if (!req.file) return res.status(400).json({ message: "file is required" });

      // Normalize inputs
      const { name, faculty, category } = req.body;
      const id = newFileId();                 // Unique ID for the resource (Tony, 2023)
      const clean = safeName(name);           // Safe filename for storage path (Manico & Detlefsen, 2015)
      const mime = req.file.mimetype || "application/pdf";
      const size = req.file.size || req.file.buffer?.length || 0;

      // Object key layout for R2 (namespaced by resource id)
      const objectKey = `resources/${id}/${clean}`;

      // Upload bytes to Cloudflare R2 (single source of truth for file storage) (Cloudflare, 2024)
      const r2Meta = await uploadToR2({ key: objectKey, body: req.file.buffer, contentType: mime });

      // Persist metadata to Firestore (Firebase, 2019a)
      const doc = {
        id,
        name: clean,
        faculty,
        category,
        mimeType: mime,
        size,
        visibility: "students",           // Default visibility; tune as needed
        storage: { cloudflare: r2Meta },  // Store R2 location/keys (Cloudflare, 2024)
        createdBy: req.user.uid,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      await fsdb.collection("resources").doc(id).set(doc); // (Firebase, 2019a)

      // Return a lean payload (omit storage internals)
      res.status(201).json({
        id: doc.id,
        name: doc.name,
        faculty: doc.faculty,
        category: doc.category,
        mimeType: doc.mimeType,
        size: doc.size,
        updatedAt: doc.updatedAt,
      });
    } catch (e) {
      console.error(e);
      res.status(400).json({ message: e.message });
    }
  }
);

/**
 * ============================================================
 * List Resources
 * ------------------------------------------------------------
 * Firestore filtering + client-side search/sort (Firebase, 2019a; express-validator, 2019)
 * ============================================================
 */
router.get(
  "/",
  checkAuth, // (Balaji, 2023)
  query("faculty").optional().isString(), // (express-validator, 2019)
  query("visibility").optional().isString().isIn(["all", "students", "admins"]), // (express-validator, 2019)
  query("q").optional().isString(), // (express-validator, 2019)
  query("sort").optional().isString().isIn(["alpha", "date"]), // (express-validator, 2019)
  query("dir").optional().isString().isIn(["asc", "desc"]), // (express-validator, 2019)
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { faculty, visibility, q, sort = "date", dir = "desc" } = req.query;

      // Base collection
      let ref = fsdb.collection("resources"); // (Firebase, 2019a)

      // Server-side Firestore filters
      if (faculty) ref = ref.where("faculty", "==", String(faculty)); // (Firebase, 2019a)
      if (visibility && visibility !== "all") ref = ref.where("visibility", "==", String(visibility)); // (Firebase, 2019a)

      // Fetch and map raw docs
      const snap = await ref.get(); // (Firebase, 2019a)
      let items = snap.docs.map(d => d.data());

      // Client-side filter by name contains (q)
      if (q) {
        const needle = String(q).toLowerCase();
        items = items.filter(x => (x.name || "").toLowerCase().includes(needle));
      }

      // Sort results (alpha by name, or date by updatedAt)
      items.sort((a, b) => {
        if (sort === "alpha") {
          const A = (a.name || "").toLowerCase();
          const B = (b.name || "").toLowerCase();
          const cmp = A.localeCompare(B);
          return dir === "asc" ? cmp : -cmp;
        }
        const A = a.updatedAt || 0;
        const B = b.updatedAt || 0;
        return dir === "asc" ? A - B : B - A;
      });

      // Lean response for list view
      res.json(items.map(x => ({
        id: x.id,
        name: x.name,
        faculty: x.faculty,
        category: x.category,
        mimeType: x.mimeType,
        size: x.size,
        updatedAt: x.updatedAt,
      })));
    } catch (e) {
      console.error(e);
      res.status(400).json({ message: e.message });
    }
  }
);

/**
 * ============================================================
 * Generate Signed URL (R2 only)
 * ------------------------------------------------------------
 * Short-lived URL for secure access (Cloudflare, 2024)
 * ============================================================
 */
router.get(
  "/:id/download",
  checkAuth, // (Balaji, 2023)
  param("id").isString(), // (express-validator, 2019)
  query("disposition").optional().isString().isIn(["inline", "attachment"]), // (express-validator, 2019)
  query("expires").optional().isInt({ min: 60, max: 7200 }), // (express-validator, 2019)
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { id } = req.params;
      const disposition = String(req.query.disposition || "inline").toLowerCase();
      const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10)));

      // Load resource from Firestore
      const docSnap = await fsdb.collection("resources").doc(id).get(); // (Firebase, 2019a)
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      // (Optional) Enforce visibility / ownership here if not handled elsewhere (Manico & Detlefsen, 2015)

      // Create signed URL on R2 (Cloudflare, 2024)
      const url = await r2SignedUrl({
        bucket: doc.storage.cloudflare.bucket,
        key: doc.storage.cloudflare.key,
        expiresSeconds: expires,
        disposition,
        filename: doc.name || "resource",
      });

      res.json({ url, expiresInSeconds: expires, provider: "r2" });
    } catch (e) {
      console.error(e);
      res.status(400).json({ message: e.message });
    }
  }
);

/**
 * ============================================================
 * Stream File (R2 only)
 * ------------------------------------------------------------
 * Direct streaming with proper headers (Cloudflare, 2024)
 * ============================================================
 */
router.get(
  "/:id/file",
  checkAuth, // (Balaji, 2023)
  param("id").isString(), // (express-validator, 2019)
  query("disposition").optional().isString().isIn(["inline", "attachment"]), // (express-validator, 2019)
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { id } = req.params;
      const disposition = String(req.query.disposition || "inline").toLowerCase();

      // Load resource from Firestore
      const docSnap = await fsdb.collection("resources").doc(id).get(); // (Firebase, 2019a)
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      // (Optional) Enforce visibility / ownership here if not handled elsewhere (Manico & Detlefsen, 2015)

      // Retrieve stream + metadata from R2 (Cloudflare, 2024)
      const filename = doc.name || "resource";
      const meta = await streamFromR2({
        bucket: doc.storage.cloudflare.bucket,
        key: doc.storage.cloudflare.key,
      });

      // Send appropriate headers for client rendering or download
      res.setHeader("Content-Type", meta.contentType || doc.mimeType || "application/octet-stream");
      if (meta.contentLength) res.setHeader("Content-Length", String(meta.contentLength));
      res.setHeader("Content-Disposition", `${disposition}; filename="${encodeURIComponent(filename)}"`);

      // Pipe storage stream to response
      meta.stream.pipe(res);
    } catch (e) {
      console.error(e);
      res.status(400).json({ message: e.message });
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
