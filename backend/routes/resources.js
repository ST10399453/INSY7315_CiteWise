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
  resourcesKey,
  ensureStorageReady,
  deleteFromR2,
} from "../blobs/storage.js"; // Cloudflare R2 integration (Cloudflare, 2024)
import { DeleteObjectCommand } from "@aws-sdk/client-s3";

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
      if (!isAdmin(req.user)) return res.status(403).json({ message: "Forbidden" });
      if (!req.file) return res.status(400).json({ message: "file is required" });

      await ensureStorageReady();

      const { name, faculty, category } = req.body;
      const id = newFileId();
      const clean = safeName(name);
      const mime = req.file.mimetype || "application/pdf";
      const size = req.file.size || req.file.buffer?.length || 0;
      const objectKey = resourcesKey({ id, fileName: clean, mime });

      const r2Meta = await uploadToR2({
        key: objectKey,
        body: req.file.buffer,
        contentType: mime,
      });

      const doc = {
        id,
        name: clean,
        faculty,
        category,
        mimeType: mime,
        size,
        visibility: "students",
        storage: { cloudflare: r2Meta },
        createdBy: req.user.uid,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      await fsdb.collection("resources").doc(id).set(doc);

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
      let ref = fsdb.collection("resources");
      if (faculty) ref = ref.where("faculty", "==", String(faculty));
      if (visibility && visibility !== "all") ref = ref.where("visibility", "==", String(visibility));
      const snap = await ref.get();
      let items = snap.docs.map((d) => d.data());

      if (q) {
        const needle = String(q).toLowerCase();
        items = items.filter((x) => (x.name || "").toLowerCase().includes(needle));
      }

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

      res.json(
        items.map((x) => ({
          id: x.id,
          name: x.name,
          faculty: x.faculty,
          category: x.category,
          mimeType: x.mimeType,
          size: x.size,
          updatedAt: x.updatedAt,
        }))
      );
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

      const docSnap = await fsdb.collection("resources").doc(id).get();
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

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
  checkAuth,
  param("id").isString(),
  query("disposition").optional().isString().isIn(["inline", "attachment"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { id } = req.params;
      const disposition = String(req.query.disposition || "inline").toLowerCase();

      const docSnap = await fsdb.collection("resources").doc(id).get();
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      const filename = doc.name || "resource";
      const meta = await streamFromR2({
        bucket: doc.storage.cloudflare.bucket,
        key: doc.storage.cloudflare.key,
      });

      res.setHeader("Content-Type", meta.contentType || doc.mimeType || "application/octet-stream");
      if (meta.contentLength) res.setHeader("Content-Length", String(meta.contentLength));
      res.setHeader("Content-Disposition", `${disposition}; filename="${encodeURIComponent(filename)}"`);
      meta.stream.pipe(res);
    } catch (e) {
      console.error(e);
      res.status(400).json({ message: e.message });
    }
  }
);

/**
 * ============================================================
 * Delete Resource (Admins only)
 * ------------------------------------------------------------
 * Deletes both Firestore record and R2 object (Cloudflare, 2024)
 * ============================================================
 */
router.delete("/:id", 
  checkAuth, 
  param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try {
    if (!isAdmin(req.user)) return res.status(403).json({ message: "Forbidden" });

    const { id } = req.params;
    const docSnap = await fsdb.collection("resources").doc(id).get();
    if (!docSnap.exists) return res.status(404).json({ message: "Not found" });

    const doc = docSnap.data();
    const { bucket, key } = (doc.storage && doc.storage.cloudflare) || {};

    if (bucket && key) {
      await deleteFromR2({ bucket, key });
    }

    await fsdb.collection("resources").doc(id).delete();
    res.json({ ok: true, id });
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
  }
});

export default router;
