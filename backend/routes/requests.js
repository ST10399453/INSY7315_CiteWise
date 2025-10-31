import { Router } from "express";
import multer from "multer";
import { body, param } from "express-validator";
import { checkAuth } from "../auth/checkAuth.js";
import {
  createRequest, getRequests, getRequestById, transitionAssign,
  transitionStartReview, transitionSubmitReview, transitionResubmit,
  transitionCancel
} from "../db/dbManager.js";
import { newFileId, safeName, uploadToR2 } from "../blobs/storage.js";
import { bailIfInvalid } from "../utils/expressHelpers.js";

const router = Router();
const upload = multer({ storage: multer.memoryStorage() });

/**
 * ============================================================
 * 1) Create Request (multipart)
 * ------------------------------------------------------------
 * Endpoint: POST /
 * Roles & Expectations:
 *   - Students: create new service requests, must attach a file.
 *   - Consultants: typically should not create requests (enforce via policy/middleware if needed).
 *   - Admins: may create on behalf of students (if business rules allow).
 *
 * Behavior:
 *   - Accepts a file (multipart/form-data) and request metadata.
 *   - Uploads file to Cloudflare R2.
 *   - Persists DB record with "Submitted" status.
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - Additional role checks should happen in checkAuth, a role guard,
 *     or within createRequest(), depending on your architecture.
 * ============================================================
 */
router.post(
  "/",
  checkAuth,
  upload.single("file"), // Expect a single file under field name "file"
  body("documentName").isString().notEmpty(),
  body("serviceType").isString().isIn([
    "PROOFREADING_EDITING","FORMATTING_REFERENCING","DATA_ANALYSIS_SUPPORT",
    "RESEARCH_METHODOLOGY_COACHING","TRANSLATION","OTHER",
  ]),
  body("description").isString().notEmpty(),
  body("priority").isString().isIn(["LOW","MEDIUM","HIGH"]),
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
      const fileId = newFileId();
      const cleanName = safeName(documentName);
      const objectPath = `uploads/${fileId}/${cleanName}`;

      // Upload file bytes to Cloudflare R2
      const r2Meta = await uploadToR2({ key: objectPath, body: req.file.buffer, contentType: mime });

      // Assemble DB payload
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
        storage: { cloudflare: r2Meta },// Store R2 location metadata
      };

      // Persist the new request
      const created = await createRequest(payload);

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
 * Endpoint: GET /
 * Roles & Expectations:
 *   - Students: list their own requests by default.
 *   - Consultants: can view all requests (or those assigned to them,
 *     depending on business rules). Use query filters as needed.
 *   - Admins: can view all and filter by userId/consultantId/status.
 *
 * Query params:
 *   - status?: string
 *   - userId?: string (defaults to current user for students)
 *   - consultantId?: string
 *   - sort?: string  (e.g., "createdAt")
 *   - dir?: string   ("asc" | "desc")
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - IMPORTANT: Authorization to view "all" vs. "own" should be enforced
 *     either here (role checks) or inside getRequests() with the actor context.
 * ============================================================
 */
router.get("/", 
 checkAuth, 
  async (req, res) => {
  try {
    // Default behavior: students see their own requests; admins/consultants can override with query
    const status = req.query.status ?? null;
    const userId = (req.query.userId ?? req.user?.uid) || null;
    const consultantId = req.query.consultantId ?? null;
    const sort = req.query.sort ?? undefined;
    const dir = req.query.dir ?? undefined;

    // NOTE: Ensure getRequests enforces role-based filtering using req.user
    const out = await getRequests({ status, userId, consultantId, sort, dir });
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
 * Endpoint: GET /:id
 * Roles & Expectations:
 *   - Students: can view details of their own requests.
 *   - Consultants: can view details of all requests (or those they handle).
 *   - Admins: can view any request.
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - IMPORTANT: getRequestById should enforce visibility based on req.user.
 * ============================================================
 */
router.get("/:id", 
  checkAuth, 
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    // Fetch a single request by ID; authorization should be verified downstream
    res.json(await getRequestById(req.params.id));
  } catch (err) {
    console.error(err);
    res.status(404).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 4) Assign Consultant (Create assignment)
 * ------------------------------------------------------------
 * Endpoint: POST /:id/assign
 * Roles & Expectations:
 *   - Admins: can assign a consultant to a request.
 *   - Consultants/Students: should not assign (enforce via authz).
 *
 * Body:
 *   - consultantId: string (required)
 *   - deadline?: string (ISO)
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - transitionAssign should verify the actor is allowed (admin).
 * ============================================================
 */
router.post("/:id/assign",
  checkAuth,
  param("id").isString(),
  body("consultantId").isString().notEmpty(),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.body.consultantId,
        deadline: req.body.deadline ?? null,
        allowUpdate: false,     // This call creates a new assignment
        actor: req.user,        // Used for authorization inside transition
      });
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
 * Endpoint: PUT /:id/assign
 * Roles & Expectations:
 *   - Admins: can reassign or adjust deadlines.
 *
 * Body:
 *   - consultantId?: string (null → unassign)
 *   - deadline?: string (ISO)
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - transitionAssign should verify admin permissions when allowUpdate=true.
 * ============================================================
 */
router.put("/:id/assign",
  checkAuth,
  param("id").isString(),
  body("consultantId").optional().isString(),
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
      });
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
 * Endpoint: POST /:id/start-review
 * Roles & Expectations:
 *   - Consultants: typically start review when they begin working.
 *   - Admins: may also trigger depending on policy.
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - transitionStartReview must enforce that only assigned consultant
 *     (or admin) can start review.
 * ============================================================
 */
router.post("/:id/start-review", 
  checkAuth, 
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    res.json(await transitionStartReview({ id: req.params.id, actor: req.user }));
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 7) Submit Review Outcome
 * ------------------------------------------------------------
 * Endpoint: POST /:id/review
 * Roles & Expectations:
 *   - Consultants: submit an outcome with optional feedback.
 *   - Admins: may override depending on policy.
 *
 * Body:
 *   - outcome: "approve" | "reject" | "fail"
 *   - feedback?: string
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - transitionSubmitReview should confirm actor is permitted and
 *     that the request is in a reviewable state.
 * ============================================================
 */
router.post("/:id/review",
  checkAuth,
  param("id").isString(),
  body("outcome").isIn(["approve","reject","fail"]),
  body("feedback").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionSubmitReview({
        id: req.params.id,
        outcome: req.body.outcome,
        feedback: req.body.feedback ?? null,
        actor: req.user,
      });
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
 * Endpoint: POST /:id/resubmit
 * Roles & Expectations:
 *   - Students: can resubmit after making changes.
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - transitionResubmit should verify the request belongs to the student
 *     (or that policy allows resubmission by others).
 * ============================================================
 */
router.post("/:id/resubmit", 
  checkAuth, 
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    res.json(await transitionResubmit({ id: req.params.id, actor: req.user }));
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 9) Cancel Request
 * ------------------------------------------------------------
 * Endpoint: POST /:id/cancel
 * Roles & Expectations:
 *   - Students: can cancel their own requests (depending on status).
 *   - Admins: can cancel any request as per policy.
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - transitionCancel should enforce ownership or admin privileges.
 * ============================================================
 */
router.post("/:id/cancel", 
  checkAuth, 
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    res.json(await transitionCancel({ id: req.params.id, actor: req.user }));
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

/**
 * ============================================================
 * 10) Self-Assign (Consultant claims a request)
 * ------------------------------------------------------------
 * Endpoint: POST /:id/self-assign
 * Roles & Expectations:
 *   - Consultants: can claim (assign themselves to) an unassigned request.
 *   - Admins: could also use this if policy allows (enforced in transition).
 *
 * Body:
 *   - deadline?: string (ISO)  // optional target date for the assignment
 *
 * Security:
 *   - Requires authentication (checkAuth).
 *   - transitionAssign should verify the actor is allowed to self-assign
 *     (e.g., has "consultant" role) and that the request is in a claimable state.
 * ============================================================
 */
router.post("/:id/self-assign",
  checkAuth,
  param("id").isString(),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.user.uid,        // self-assign to the actor
        deadline: req.body.deadline ?? null,
        allowUpdate: false,                // create assignment (not update)
        actor: req.user,                   // used by transition for authz
      });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);


export default router;
