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

// ─────────────────────────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────────────────────────
app.get("/", (_req, res) =>
  res.send("CiteWise API is running (uploads: Cloudflare R2 + Azure Blob).")
);

// ─────────────────────────────────────────────────────────────────────────────
// 1. Create request (POST /requests)
// ─────────────────────────────────────────────────────────────────────────────
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

      const [r2Meta, azureMeta] = await Promise.all([
        uploadToR2({ key: objectPath, body: req.file.buffer, contentType: mime }),
        uploadToAzure({ blobPath: objectPath, body: req.file.buffer, contentType: mime }),
      ]);

      const payload = {
        userId: req.user.uid,
        consultantId: null,
        serviceType,
        description,
        priority,
        deadline: deadline || null,
        status: "Submitted",
        documentId: fileId, // ✅ root-level field
        file: {
          fileId,
          originalName: documentName,
          mimeType: mime,
          size,
        },
        storage: {
          cloudflare: r2Meta,
          azure: azureMeta,
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

// ─────────────────────────────────────────────────────────────────────────────
// Downloads
// ─────────────────────────────────────────────────────────────────────────────
app.get("/documents/:documentId/download", checkAuth, async (req, res) => {
  try {
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });
    if (!canAccessRequest(reqDoc, req.user)) return res.status(403).json({ message: "Forbidden" });

    const provider = (req.query.provider || "r2").toLowerCase();
    const disposition = (req.query.disposition || "inline").toLowerCase();
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

// ─────────────────────────────────────────────────────────────────────────────
// Startup
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
    app.listen(PORT, () => console.log(`Server listening on :${PORT}`));
  } catch (err) {
    console.error("Startup failed:", err);
    process.exit(1);
  }
})();
