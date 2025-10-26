import { Router } from "express";
import multer from "multer";
import { body, param, query } from "express-validator";
import { checkAuth } from "../auth/checkAuth.js";
import { db as fsdb } from "../db/firebaseAdmin.js";
import { isAdmin, bailIfInvalid } from "../utils/expressHelpers.js";
import {
  newFileId,
  safeName,
  uploadToR2,
  r2SignedUrl,
  streamFromR2,
} from "../blobs/storage.js";

const router = Router();
const upload = multer({ storage: multer.memoryStorage() });

/**
 * ============================================================
 * Create Resource (Admins only)
 * ------------------------------------------------------------
 * Endpoint: POST /
 * Roles:
 *   - Admins: can upload resource files (e.g., guides, templates).
 *   - Students/Consultants: cannot create resources.
 *
 * Request (multipart/form-data):
 *   - file: binary (required)
 *   - name: string (required) — resource display name; is sanitized for storage
 *   - faculty: string (required)
 *   - category: enum ["WRITING_GUIDE","TEMPLATE","AI_USAGE"] (required)
 *
 * Behavior:
 *   - Validates input.
 *   - Uploads to Cloudflare R2.
 *   - Persists metadata in Firestore (collection: "resources").
 *   - Sets visibility default to "students" (adjust as policy requires).
 *
 * Security:
 *   - Auth required.
 *   - Admin check via isAdmin(req.user).
 * ============================================================
 */
router.post(
  "/",
  checkAuth,
  upload.single("file"),
  body("name").isString().notEmpty(),
  body("faculty").isString().notEmpty(),
  body("category").isString().isIn(["WRITING_GUIDE", "TEMPLATE", "AI_USAGE"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      // Only admins may create resources
      if (!isAdmin(req.user)) return res.status(403).json({ message: "Forbidden" });

      // File is mandatory
      if (!req.file) return res.status(400).json({ message: "file is required" });

      // Normalize inputs
      const { name, faculty, category } = req.body;
      const id = newFileId();                 // Unique ID for the resource
      const clean = safeName(name);           // Safe filename for storage path
      const mime = req.file.mimetype || "application/pdf";
      const size = req.file.size || req.file.buffer?.length || 0;

      // Object key layout for R2 (namespaced by resource id)
      const objectKey = `resources/${id}/${clean}`;

      // Upload bytes to Cloudflare R2 (single source of truth for file storage)
      const r2Meta = await uploadToR2({ key: objectKey, body: req.file.buffer, contentType: mime });

      // Persist metadata to Firestore
      const doc = {
        id,
        name: clean,
        faculty,
        category,
        mimeType: mime,
        size,
        visibility: "students",           // Default visibility; tune as needed
        storage: { cloudflare: r2Meta },  // Store R2 location/keys
        createdBy: req.user.uid,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      await fsdb.collection("resources").doc(id).set(doc);

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
 * Endpoint: GET /
 * Roles:
 *   - Students: can list resources (typically those with visibility "students").
 *   - Consultants: can list resources; adjust visibility filters as needed.
 *   - Admins: can list and filter by visibility.
 *
 * Query:
 *   - faculty?: string — exact match filter
 *   - visibility?: "all" | "students" | "admins" — filter by visibility (default server-side logic)
 *   - q?: string — client-side substring match on name (post-fetch)
 *   - sort?: "alpha" | "date" (default "date")
 *   - dir?: "asc" | "desc" (default "desc")
 *
 * Behavior:
 *   - Firestore query for basic filters (faculty, visibility).
 *   - Optional client-side filter for q (name contains).
 *   - Sorting client-side by alpha or updatedAt.
 *
 * NOTE:
 *   - If dataset grows, consider moving search/sort to Firestore indexes
 *     or a search service to avoid full scans and large payloads.
 * ============================================================
 */
router.get(
  "/",
  checkAuth,
  query("faculty").optional().isString(),
  query("visibility").optional().isString().isIn(["all", "students", "admins"]),
  query("q").optional().isString(),
  query("sort").optional().isString().isIn(["alpha", "date"]),
  query("dir").optional().isString().isIn(["asc", "desc"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { faculty, visibility, q, sort = "date", dir = "desc" } = req.query;

      // Base collection
      let ref = fsdb.collection("resources");

      // Server-side Firestore filters
      if (faculty) ref = ref.where("faculty", "==", String(faculty));
      if (visibility && visibility !== "all") ref = ref.where("visibility", "==", String(visibility));

      // Fetch and map raw docs
      const snap = await ref.get();
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
 * Endpoint: GET /:id/download
 * Roles:
 *   - Students/Consultants/Admins: may download if allowed by visibility policy.
 *
 * Query:
 *   - disposition?: "inline" | "attachment" (default "inline")
 *   - expires?: number seconds (min 60, max 7200; default 900)
 *
 * Behavior:
 *   - Loads resource doc.
 *   - Returns a short-lived signed URL from Cloudflare R2 for secure access.
 * ============================================================
 */
router.get(
  "/:id/download",
  checkAuth,
  param("id").isString(),
  query("disposition").optional().isString().isIn(["inline", "attachment"]),
  query("expires").optional().isInt({ min: 60, max: 7200 }),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { id } = req.params;
      const disposition = String(req.query.disposition || "inline").toLowerCase();
      const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10)));

      // Load resource from Firestore
      const docSnap = await fsdb.collection("resources").doc(id).get();
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      // (Optional) Enforce visibility / ownership here if not handled elsewhere

      // Create signed URL on R2
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
 * Endpoint: GET /:id/file
 * Roles:
 *   - Students/Consultants/Admins: may stream if allowed by visibility policy.
 *
 * Query:
 *   - disposition?: "inline" | "attachment" (default "inline")
 *
 * Behavior:
 *   - Streams file bytes directly from R2 to the client.
 *   - Sets appropriate Content-Type/Length and Content-Disposition headers.
 *   - Useful for web/mobile inline previews (PDF, images).
 * ============================================================
 */
router.get(
  "/:id/file",
  checkAuth,
  param("id").isString(),
  query("disposition").optional().isString().isIn(["inline", "attachment"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { id } = req.params;
      const disposition = String(req.query.disposition || "inline").toLowerCase();

      // Load resource from Firestore
      const docSnap = await fsdb.collection("resources").doc(id).get();
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      // (Optional) Enforce visibility / ownership here if not handled elsewhere

      // Retrieve stream + metadata from R2
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
