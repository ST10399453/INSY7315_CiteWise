import { Router } from "express"; // (GeeksforGeeks, 2022a)
import multer from "multer";
import { body, param, query } from "express-validator"; // (express-validator, 2019)
import { checkAuth } from "../auth/checkAuth.js"; // (Balaji, 2023)
import { db as fsdb } from "../db/firebaseAdmin.js"; // (Firebase, 2019a)
import { attachRole } from "../middleware/attachRole.js";
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

const router = Router();
const upload = multer({ storage: multer.memoryStorage() }); // In-memory multipart handling

/**
 * ============================================================
 * Create Resource (Admins only)
 * ============================================================
 */
//Implemented in App
router.post(
  "/",
  checkAuth,
  attachRole,
  upload.single("file"),
  body("name").isString().notEmpty(),
  body("faculty").isString().notEmpty(),
  body("category").isString().isIn(["WRITING_GUIDE", "TEMPLATE", "AI_USAGE"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;

    try {
      console.log("----- INCOMING /resources -----");
      console.log("Auth header present:", Boolean(req.headers.authorization));
      console.log("User parsed from token:", req.user); // { uid, email, claims, role? }
      console.log("Body:", req.body);
      console.log(
        "File:",
        req.file
          ? {
              originalname: req.file.originalname,
              mimetype: req.file.mimetype,
              size: req.file.size,
            }
          : "NO FILE"
      );
      console.log("-------------------------------");

      if (!isAdmin(req.user)) {
        console.log(
          "❌ Forbidden: not admin. Role seen:",
          req.user?.role || req.user?.claims?.role
        );
        return res.status(403).json({ message: "Forbidden" });
      }

      if (!req.file) {
        return res.status(400).json({ message: "file is required" });
      }

      await ensureStorageReady();

      const rawName = String(req.body.name || "");
      const rawFaculty = String(req.body.faculty || "");
      const rawCategory = String(req.body.category || "");

      const name = safeName(rawName);
      const faculty = rawFaculty.trim().toUpperCase();   // <-- normalize faculty
      const category = rawCategory.trim().toUpperCase(); // already validated

      const id = newFileId();
      const mime = req.file.mimetype || "application/pdf";
      const size = req.file.size || (req.file.buffer ? req.file.buffer.length : 0);
      const objectKey = resourcesKey({ id, fileName: name, mime });

      const r2Meta = await uploadToR2({
        key: objectKey,
        body: req.file.buffer,
        contentType: mime,
      });

      const now = Date.now();
      const doc = {
        id,
        name,
        faculty,
        category,
        mimeType: mime,
        size,
        visibility: "students",
        storage: { cloudflare: r2Meta },
        createdBy: req.user.uid,
        createdAt: now,
        updatedAt: now,
      };

      console.log("✅ Writing Firestore doc:", {
        id: doc.id,
        createdBy: doc.createdBy,
        role: req.user.role,
      });

      await fsdb.collection("resources").doc(id).set(doc);

      res.status(201).json({
        id: doc.id,
        name: doc.name,
        faculty: doc.faculty,
        category: doc.category,
        mimeType: doc.mimeType,
        size: doc.size,
        updatedAt: doc.updatedAt,
        createdBy: doc.createdBy,
      });

      console.log("✅ Done /resources:", id);
    } catch (e) {
      console.error("❌ Error /resources:", e);
      res.status(400).json({ message: e.message });
    }
  }
);

/**
 * ============================================================
 * List Resources
 * ============================================================
 */
//Implemented in App
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
      // Everything from req.query is plain JS; normalize manually
      let faculty = req.query.faculty;
      let visibility = req.query.visibility;
      let q = req.query.q;
      let sort = req.query.sort || "date";
      let dir = req.query.dir || "desc";

      let ref = fsdb.collection("resources");

      if (faculty) {
        const facNorm = String(faculty).trim().toUpperCase(); // <-- normalize filter
        ref = ref.where("faculty", "==", facNorm);
      }

      if (visibility && visibility !== "all") {
        ref = ref.where("visibility", "==", String(visibility));
      }

      const snap = await ref.get();
      let items = snap.docs.map((d) => d.data());

      if (q) {
        const needle = String(q).toLowerCase();
        items = items.filter((x) =>
          String(x.name || "").toLowerCase().includes(needle)
        );
      }

      items.sort((a, b) => {
        if (sort === "alpha") {
          const A = String(a.name || "").toLowerCase();
          const B = String(b.name || "").toLowerCase();
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
 * ============================================================
 */
//Implemented in App
router.get(
  "/:id/download",
  checkAuth,
  param("id").isString(),
  query("disposition").optional().isString().isIn(["inline", "attachment"]),
  query("expires").optional().isInt({ min: 60, max: 7200 }),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const id = req.params.id;
      const disposition = String(req.query.disposition || "inline").toLowerCase();
      const expiresRaw = req.query.expires || "900";
      const expires = Math.max(
        60,
        Math.min(7200, parseInt(String(expiresRaw), 10))
      );

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
 * ============================================================
 */
//Implemented in App
router.get(
  "/:id/file",
  checkAuth,
  param("id").isString(),
  query("disposition").optional().isString().isIn(["inline", "attachment"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const id = req.params.id;
      const disposition = String(req.query.disposition || "inline").toLowerCase();

      const docSnap = await fsdb.collection("resources").doc(id).get();
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      const filename = doc.name || "resource";
      const meta = await streamFromR2({
        bucket: doc.storage.cloudflare.bucket,
        key: doc.storage.cloudflare.key,
      });

      res.setHeader(
        "Content-Type",
        meta.contentType || doc.mimeType || "application/octet-stream"
      );
      if (meta.contentLength) {
        res.setHeader("Content-Length", String(meta.contentLength));
      }
      res.setHeader(
        "Content-Disposition",
        `${disposition}; filename="${encodeURIComponent(filename)}"`
      );
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
 * ============================================================
 */
//Implemented in App
router.delete(
  "/:id",
  checkAuth,
  attachRole,
  param("id").isString().trim().notEmpty(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;

    const startedAt = Date.now();
    const reqId = `${startedAt}-${Math.random().toString(36).slice(2, 8)}`;
    const actor = (req.user && req.user.uid) || "unknown";
    const role = String(
      (req.user && (req.user.role || (req.user.claims && req.user.claims.role))) || ""
    ).toLowerCase();
    const id = req.params.id;

    console.log("----- INCOMING DELETE /resources/%s [%s] -----", id, reqId);
    console.log("Auth header present:", Boolean(req.headers.authorization));
    console.log("User:", { uid: actor, role });
    console.log("Params:", req.params);

    try {
      if (role !== "admin") {
        console.warn(
          "❌ [%s] Forbidden: not admin (role=%s, uid=%s)",
          reqId,
          role,
          actor
        );
        return res.status(403).json({ message: "Forbidden" });
      }

      const ref = fsdb.collection("resources").doc(id);
      const snap = await ref.get();
      if (!snap.exists) {
        console.warn("⚠️  [%s] Resource not found: %s", reqId, id);
        return res.status(404).json({ message: "Not found" });
      }

      const doc = snap.data() || {};
      const bucket = doc &&
        doc.storage &&
        doc.storage.cloudflare &&
        doc.storage.cloudflare.bucket;
      const key =
        doc &&
        doc.storage &&
        doc.storage.cloudflare &&
        doc.storage.cloudflare.key;

      if (bucket && key) {
        try {
          console.log("→ [%s] Deleting R2 object", reqId, { bucket, key });
          await deleteFromR2({ bucket, key });
          console.log("✓ [%s] R2 object deleted", reqId);
        } catch (err) {
          console.error(
            "⚠️  [%s] R2 delete failed (continuing): %s",
            reqId,
            err && err.message
          );
        }
      } else {
        console.log("ℹ️  [%s] No R2 info on resource (bucket/key missing)", reqId);
      }

      await ref.delete();
      console.log("✓ [%s] Firestore doc deleted: %s", reqId, id);

      try {
        await fsdb.collection("audit").add({
          type: "resource.delete",
          resourceId: id,
          actor,
          role,
          ts: Date.now(),
        });
        console.log("ℹ️  [%s] Audit record written", reqId);
      } catch (err) {
        console.error(
          "⚠️  [%s] Audit write failed: %s",
          reqId,
          err && err.message
        );
      }

      console.log(
        "✅ [%s] Done DELETE /resources/%s in %dms",
        reqId,
        id,
        Date.now() - startedAt
      );
      return res.json({ ok: true, id });
    } catch (e) {
      console.error(
        "💥 [%s] Error DELETE /resources/%s: %s",
        reqId,
        id,
        e && e.message
      );
      return res.status(400).json({ message: (e && e.message) || "Delete failed" });
    }
  }
);

export default router;
