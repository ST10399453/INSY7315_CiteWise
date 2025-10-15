// server.js
import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import multer from "multer";
import { body, param, query, validationResult } from "express-validator";

import { checkAuth } from "./auth/checkAuth.js";
import {
  createRequest,
  getRequests,
  getRequestById,
  getRequestByDocumentId,
  transitionAssign,
  transitionStartReview,
  transitionSubmitReview,
  transitionResubmit,
  transitionCancel,
} from "./db/dbManager.js";

import admin, { db as fsdb } from "./db/firebaseAdmin.js";
import { notify } from "./utils/notify.js";

import {
  ensureR2Bucket,
  ensureAzureContainer,
  uploadToR2,
  uploadToAzure,
  newFileId,
  safeName,
  r2SignedUrl,
  azureSasUrl,
  streamFromR2,
  streamFromAzure,
} from "./blobs/storage.js";

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

const upload = multer({ storage: multer.memoryStorage() });

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
function bailIfInvalid(req, res) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ errors: errors.array() });
}

function canAccessRequest(doc, user) {
  if (!doc || !user) return false;
  const isOwner = doc.userId === user.uid;
  const isConsultant = doc.consultantId && doc.consultantId === user.uid;
  const isAdmin = Boolean(user.claims?.role === "admin" || user.claims?.admin === true);
  return isOwner || isConsultant || isAdmin;
}

function isAdmin(user) {
  return Boolean(user?.claims?.role === "admin" || user?.claims?.admin === true);
}

// ─────────────────────────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────────────────────────
app.get("/", (_req, res) =>
  res.send("CiteWise API is running (uploads: Cloudflare R2 + Azure Blob).")
);

// 1) Create request (multipart)
app.post(
  "/requests",
  checkAuth,
  upload.single("file"),
  body("documentName").isString().notEmpty(),
  body("serviceType")
    .isString()
    .isIn([
      "PROOFREADING_EDITING",
      "FORMATTING_REFERENCING",
      "DATA_ANALYSIS_SUPPORT",
      "RESEARCH_METHODOLOGY_COACHING",
      "TRANSLATION",
      "OTHER",
    ]),
  body("description").isString().notEmpty(),
  body("priority").isString().isIn(["LOW", "MEDIUM", "HIGH"]),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;

    try {
      if (!req.file) return res.status(400).json({ message: "file is required" });

      const { documentName, serviceType, description, priority, deadline = null } = req.body;

      const mime = req.file.mimetype || "application/octet-stream";
      const size = req.file.size || req.file.buffer?.length || 0;
      const fileId = newFileId();
      const cleanName = safeName(documentName);
      const objectPath = `uploads/${fileId}/${cleanName}`;

      // Upload to both clouds in parallel
      const [r2Meta, azureMeta] = await Promise.all([
        uploadToR2({ key: objectPath, body: req.file.buffer, contentType: mime }),
        uploadToAzure({ blobPath: objectPath, body: req.file.buffer, contentType: mime }),
      ]);

      // Firestore payload
      const payload = {
        userId: req.user.uid,
        consultantId: null,
        serviceType,
        description,
        priority,
        deadline: deadline || null,
        status: "Submitted",
        documentId: fileId,
        file: {
          fileId,
          originalName: documentName,
          mimeType: mime,
          size,
        },
        storage: {
          cloudflare: r2Meta, // { bucket, key }
          azure: azureMeta, // { container, blob }
        },
      };

      const created = await createRequest(payload);

      return res.status(201).json({
        ...created,
        storage: payload.storage,
      });
    } catch (err) {
      console.error(err);
      res.status(500).json({ message: err.message });
    }
  }
);

// 2) List requests
app.get("/requests", checkAuth, async (req, res) => {
  try {
    const status = req.query.status ?? null;
    const userId = (req.query.userId ?? req.user?.uid) || null;
    const consultantId = req.query.consultantId ?? null;
    const sort = req.query.sort ?? undefined;
    const dir = req.query.dir ?? undefined;

    const out = await getRequests({ status, userId, consultantId, sort, dir });
    res.json(out);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

// 3) Request details
app.get("/requests/:id", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res);
  if (v) return v;
  try {
    const out = await getRequestById(req.params.id);
    res.json(out);
  } catch (err) {
    console.error(err);
    res.status(404).json({ message: err.message });
  }
});

// 4) Assign
app.post(
  "/requests/:id/assign",
  checkAuth,
  param("id").isString(),
  body("consultantId").isString().notEmpty(),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.body.consultantId,
        deadline: req.body.deadline ?? null,
        allowUpdate: false,
        actor: req.user,
      });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 5) Update assignment
app.put(
  "/requests/:id/assign",
  checkAuth,
  param("id").isString(),
  body("consultantId").optional().isString(),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.body.consultantId ?? null,
        deadline: req.body.deadline ?? null,
        allowUpdate: true,
        actor: req.user,
      });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 6) Start review
app.post(
  "/requests/:id/start-review",
  checkAuth,
  param("id").isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
    try {
      const out = await transitionStartReview({ id: req.params.id, actor: req.user });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 7) Submit review
app.post(
  "/requests/:id/review",
  checkAuth,
  param("id").isString(),
  body("outcome").isIn(["approve", "reject", "fail"]),
  body("feedback").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
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

// 8) Resubmit (Pending → Assigned)
app.post(
  "/requests/:id/resubmit",
  checkAuth,
  param("id").isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
    try {
      const out = await transitionResubmit({ id: req.params.id, actor: req.user });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 9) Cancel
app.post(
  "/requests/:id/cancel",
  checkAuth,
  param("id").isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
    try {
      const out = await transitionCancel({ id: req.params.id, actor: req.user });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// Document download/stream by documentId (file.fileId)
// ─────────────────────────────────────────────────────────────────────────────

// Signed URL (good for external opening/downloading)
app.get("/documents/:documentId/download", checkAuth, async (req, res) => {
  try {
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });
    if (!canAccessRequest(reqDoc, req.user))
      return res.status(403).json({ message: "Forbidden" });

    const provider = (req.query.provider || "r2").toLowerCase(); // "r2" | "azure"
    const disposition = (req.query.disposition || "inline").toLowerCase(); // "inline" | "attachment"
    const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10)));
    const filename = reqDoc?.file?.originalName || "document";

    let url, ttl;
    if (provider === "azure") {
      url = await azureSasUrl({
        container: reqDoc.storage.azure.container,
        blob: reqDoc.storage.azure.blob,
        expiresMinutes: Math.ceil(expires / 60),
      });
      ttl = Math.ceil(expires / 60) * 60;
    } else {
      url = await r2SignedUrl({
        bucket: reqDoc.storage.cloudflare.bucket,
        key: reqDoc.storage.cloudflare.key,
        expiresSeconds: expires,
        disposition,
        filename,
      });
      ttl = expires;
    }

    res.json({ url, expiresInSeconds: ttl, provider });
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
  }
});

// Stream bytes (for in-app viewer)
app.get("/documents/:documentId/file", checkAuth, async (req, res) => {
  try {
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });
    if (!canAccessRequest(reqDoc, req.user))
      return res.status(403).json({ message: "Forbidden" });

    const provider = (req.query.provider || "r2").toLowerCase(); // "r2" | "azure"
    const disposition = (req.query.disposition || "inline").toLowerCase();
    const filename = reqDoc?.file?.originalName || "document";

    const meta =
      provider === "azure"
        ? await streamFromAzure({
            container: reqDoc.storage.azure.container,
            blob: reqDoc.storage.azure.blob,
          })
        : await streamFromR2({
            bucket: reqDoc.storage.cloudflare.bucket,
            key: reqDoc.storage.cloudflare.key,
          });

    res.setHeader("Content-Type", meta.contentType || "application/octet-stream");
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
});

// ─────────────────────────────────────────────────────────────────────────────
// RESOURCES — Create / List / Signed URL / Stream
// (bucket directory is "resources")
// ─────────────────────────────────────────────────────────────────────────────

// Create resource (admin only)
app.post(
  "/resources",
  checkAuth,
  upload.single("file"),
  body("name").isString().notEmpty(),
  body("faculty").isString().notEmpty(),
  body("category").isString().isIn(["WRITING_GUIDE", "TEMPLATE", "AI_USAGE"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;

    try {
      if (!isAdmin(req.user)) return res.status(403).json({ message: "Forbidden" });
      if (!req.file) return res.status(400).json({ message: "file is required" });

      const { name, faculty, category } = req.body;
      const id = newFileId();
      const clean = safeName(name);
      const mime = req.file.mimetype || "application/pdf";
      const size = req.file.size || req.file.buffer?.length || 0;

      const objectKey = `resources/${id}/${clean}`;

      const [r2Meta, azureMeta] = await Promise.all([
        uploadToR2({ key: objectKey, body: req.file.buffer, contentType: mime }),
        uploadToAzure({ blobPath: objectKey, body: req.file.buffer, contentType: mime }),
      ]);

      const doc = {
        id,
        name: clean,
        faculty,
        category,
        mimeType: mime,
        size,
        visibility: "students", // default
        storage: {
          cloudflare: r2Meta, // { bucket, key }
          azure: azureMeta,   // { container, blob }
        },
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

// List resources (filters + sort)
app.get(
  "/resources",
  checkAuth,
  query("faculty").optional().isString(),
  query("visibility").optional().isString().isIn(["all", "students", "admins"]),
  query("q").optional().isString(),
  query("sort").optional().isString().isIn(["alpha", "date"]),
  query("dir").optional().isString().isIn(["asc", "desc"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;

    try {
      const { faculty, visibility, q, sort = "date", dir = "desc" } = req.query;

      let ref = fsdb.collection("resources");
      if (faculty) ref = ref.where("faculty", "==", String(faculty));
      if (visibility && visibility !== "all") ref = ref.where("visibility", "==", String(visibility));

      const snap = await ref.get();
      let items = snap.docs.map(d => d.data());

      if (q) {
        const needle = String(q).toLowerCase();
        items = items.filter(x => (x.name || "").toLowerCase().includes(needle));
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
        items.map(x => ({
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

// Signed URL for a resource
app.get(
  "/resources/:id/download",
  checkAuth,
  param("id").isString(),
  query("provider").optional().isString().isIn(["r2", "azure"]),
  query("disposition").optional().isString().isIn(["inline", "attachment"]),
  query("expires").optional().isInt({ min: 60, max: 7200 }),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;

    try {
      const { id } = req.params;
      const provider = String(req.query.provider || "r2").toLowerCase();
      const disposition = String(req.query.disposition || "inline").toLowerCase();
      const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10)));

      const docSnap = await fsdb.collection("resources").doc(id).get();
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      let url, ttl;
      if (provider === "azure" && doc.storage?.azure) {
        url = await azureSasUrl({
          container: doc.storage.azure.container,
          blob: doc.storage.azure.blob,
          expiresMinutes: Math.ceil(expires / 60),
        });
        ttl = Math.ceil(expires / 60) * 60;
      } else {
        url = await r2SignedUrl({
          bucket: doc.storage.cloudflare.bucket,
          key: doc.storage.cloudflare.key,
          expiresSeconds: expires,
          disposition,
          filename: doc.name || "resource",
        });
        ttl = expires;
      }

      res.json({ url, expiresInSeconds: ttl, provider });
    } catch (e) {
      console.error(e);
      res.status(400).json({ message: e.message });
    }
  }
);

// Stream resource via API (for in-app viewer)
app.get(
  "/resources/:id/file",
  checkAuth,
  param("id").isString(),
  query("provider").optional().isString().isIn(["r2", "azure"]),
  query("disposition").optional().isString().isIn(["inline", "attachment"]),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;

    try {
      const { id } = req.params;
      const provider = String(req.query.provider || "r2").toLowerCase();
      const disposition = String(req.query.disposition || "inline").toLowerCase();

      const docSnap = await fsdb.collection("resources").doc(id).get();
      if (!docSnap.exists) return res.status(404).json({ message: "Not found" });
      const doc = docSnap.data();

      const filename = doc.name || "resource";
      const meta =
        provider === "azure"
          ? await streamFromAzure({
              container: doc.storage.azure.container,
              blob: doc.storage.azure.blob,
            })
          : await streamFromR2({
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

// ─────────────────────────────────────────────────────────────────────────────
/** Startup */
// ─────────────────────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 8081;

(async function boot() {
  try {
    if (process.env.SKIP_STORAGE_INIT === "1") {
      console.log("[BOOT] SKIP_STORAGE_INIT=1 → skipping R2/Azure container checks");
    } else {
      await ensureR2Bucket();
      await ensureAzureContainer();
    }
    // directory in bucket is called "resources"
    app.listen(PORT, () => console.log(`Server listening on :${PORT}`));
  } catch (err) {
    console.error("Startup failed:", err);
    process.exit(1);
  }
})();
