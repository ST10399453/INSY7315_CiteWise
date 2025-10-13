// ─────────────────────────────────────────────────────────────────────────────
// File: server.js
// ─────────────────────────────────────────────────────────────────────────────
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
  transitionAssign,
  transitionStartReview,
  transitionSubmitReview,
  transitionResubmit,
  transitionCancel,
  // optional helper you may add in your dbManager to find by documentId:
  // getRequestByDocumentId,
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

// Multer memory storage for multipart/form-data
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

/** Find the ServiceReviews doc that contains this documentId (file.fileId) */
async function findRequestByDocumentId(documentId) {
  // If you have getRequestByDocumentId in dbManager, call that instead.
  const snap = await fsdb
    .collection("ServiceReviews")
    .where("file.fileId", "==", String(documentId))
    .limit(1)
    .get();
  if (snap.empty) return null;
  const d = snap.docs[0];
  return { id: d.id, ...d.data() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────────────────────────

// Health
app.get("/", (_req, res) =>
  res.send("CiteWise API is running (uploads: Cloudflare R2 + Azure Blob).")
);

// 1) POST /requests — Create (Submitted) via MULTIPART: file + fields
//    Android sends: file, documentName, serviceType, description, priority, deadline?
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
        documentId: fileId,
        storage: payload.storage,
      });
    } catch (err) {
      console.error(err);
      return res.status(500).json({ message: err.message });
    }
  }
);

// 2) GET /requests — list (filter by status, userId, consultantId)
app.get("/requests", checkAuth, async (req, res) => {
  try {
    const status = req.query.status ?? null;
    const userId = (req.query.userId ?? req.user?.uid) || null;
    const consultantId = req.query.consultantId ?? null;
    const out = await getRequests({ status, userId, consultantId });
    res.json(out);
  } catch (err) {
    console.error(err);
    res.status(500).json({ message: err.message });
  }
});

// 3) GET /requests/:id — details
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

// 4) POST /requests/:id/assign — Admin only
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

// 5) PUT /requests/:id/assign — Admin only (update while Assigned)
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

// 6) POST /requests/:id/start-review — Consultant
app.post("/requests/:id/start-review", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res);
  if (v) return v;
  try {
    const out = await transitionStartReview({ id: req.params.id, actor: req.user });
    res.json(out);
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

// 7) POST /requests/:id/review — Consultant outcome
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

// 8) POST /requests/:id/resubmit — Student (Pending → Assigned)
app.post("/requests/:id/resubmit", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res);
  if (v) return v;
  try {
    const out = await transitionResubmit({ id: req.params.id, actor: req.user });
    res.json(out);
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

// 9) POST /requests/:id/cancel — cancel anytime
app.post("/requests/:id/cancel", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res);
  if (v) return v;
  try {
    const out = await transitionCancel({ id: req.params.id, actor: req.user });
    res.json(out);
  } catch (err) {
    console.error(err);
    res.status(400).json({ message: err.message });
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// Document download/stream by documentId (file.fileId)
// ─────────────────────────────────────────────────────────────────────────────

// GET /documents/:documentId/download?provider=r2|azure&disposition=inline|attachment&expires=900
app.get("/documents/:documentId/download", checkAuth, async (req, res) => {
  try {
    const docId = req.params.documentId;
    const reqDoc = await findRequestByDocumentId(docId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });
    if (!canAccessRequest(reqDoc, req.user)) return res.status(403).json({ message: "Forbidden" });

    const provider = (req.query.provider || "r2").toLowerCase();
    const disposition = (req.query.disposition || "inline").toLowerCase(); // or 'attachment'
    const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10))); // 1m–2h
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

// GET /documents/:documentId/file?provider=r2|azure&disposition=inline|attachment
app.get("/documents/:documentId/file", checkAuth, async (req, res) => {
  try {
    const docId = req.params.documentId;
    const reqDoc = await findRequestByDocumentId(docId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });
    if (!canAccessRequest(reqDoc, req.user)) return res.status(403).json({ message: "Forbidden" });

    const provider = (req.query.provider || "r2").toLowerCase();
    const disposition = (req.query.disposition || "inline").toLowerCase();
    const filename = reqDoc?.file?.originalName || "document";

    let meta;
    if (provider === "azure") {
      meta = await streamFromAzure({
        container: reqDoc.storage.azure.container,
        blob: reqDoc.storage.azure.blob,
      });
    } else {
      meta = await streamFromR2({
        bucket: reqDoc.storage.cloudflare.bucket,
        key: reqDoc.storage.cloudflare.key,
      });
    }

    res.setHeader("Content-Type", meta.contentType);
    if (meta.contentLength) res.setHeader("Content-Length", String(meta.contentLength));
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
// Messages (REST) — used by Android worker
// ─────────────────────────────────────────────────────────────────────────────

// POST /messages — send a message (Firestore) + best-effort push & FS notification
app.post(
  "/messages",
  checkAuth,
  body("toUid").isString().notEmpty(),
  body("body").isString().notEmpty(),
  body("clientId").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
    try {
      const now = Date.now();
      const fromUid = req.user.uid;
      const toUid = String(req.body.toUid);
      const bodyText = String(req.body.body);
      const clientId = req.body.clientId ? String(req.body.clientId) : undefined;

      const data = {
        fromUid,
        toUid,
        body: bodyText,
        clientId: clientId || admin.firestore.FieldValue.delete(), // optional
        status: "sent",
        createdAt: now,
        updatedAt: now,
      };

      const ref = await fsdb.collection("messages").add(data);

      // Best-effort notification (won't fail the request)
      try {
        const { displayName, username } = await notify.getUserProfile(fromUid);
        const title = displayName || username || "New message";
        const preview = bodyText.length > 120 ? bodyText.slice(0, 117) + "..." : bodyText;

        await notify.createFirestoreNotification(toUid, {
          type: "chat_message",
          fromUid: fromUid,
          fromName: displayName,
          fromUsername: username,
          message: preview,
        });

        await notify.sendPushToUser(toUid, {
          title,
          body: preview,
          data: {
            type: "chat_message",
            fromUid: String(fromUid),
            messageId: String(ref.id),
          },
        });
      } catch (nerr) {
        console.error("[/messages] notify failed:", nerr?.message || nerr);
      }

      res.status(201).json({ id: ref.id, ...data });
    } catch (e) {
      console.error("POST /messages error:", e);
      res.status(500).json({ message: e.message || "send failed" });
    }
  }
);

// GET /messages/since?since=<ms>&limit=<n>
// returns merged list of messages where the user is either sender or recipient
app.get(
  "/messages/since",
  checkAuth,
  query("since").optional().isInt({ min: 0 }),
  query("limit").optional().isInt({ min: 1, max: 500 }),
  async (req, res) => {
    const v = bailIfInvalid(req, res);
    if (v) return v;
    try {
      const uid = req.user.uid;
      const since = req.query.since ? Number(req.query.since) : 0;
      const limit = req.query.limit ? Number(req.query.limit) : 200;

      // Two queries → merge → sort → dedupe
      const q1 = fsdb
        .collection("messages")
        .where("toUid", "==", uid)
        .where("createdAt", ">", since)
        .limit(limit);

      const q2 = fsdb
        .collection("messages")
        .where("fromUid", "==", uid)
        .where("createdAt", ">", since)
        .limit(limit);

      const [r1, r2] = await Promise.all([q1.get(), q2.get()]);
      const combined = [...r1.docs, ...r2.docs]
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));

      const seen = new Set();
      const deduped = [];
      for (const m of combined) {
        if (!seen.has(m.id)) {
          seen.add(m.id);
          deduped.push(m);
        }
      }

      res.json(deduped.slice(0, limit));
    } catch (e) {
      console.error("GET /messages/since error:", e);
      res.status(500).json({ message: e.message || "fetch failed" });
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// Startup: ensure storage, then boot server
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
