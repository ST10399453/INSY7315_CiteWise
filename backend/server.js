// ─────────────────────────────────────────────────────────────────────────────
// File: server.js
// ─────────────────────────────────────────────────────────────────────────────
import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import multer from 'multer';
import { body, param, query, validationResult } from 'express-validator';

import { checkAuth } from './auth/checkAuth.js';
import {
  createRequest,
  getRequests,
  getRequestById,
  transitionAssign,
  transitionStartReview,
  transitionSubmitReview,
  transitionResubmit,
  transitionCancel,
} from './db/dbManager.js';

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
} from './blobs/storage.js';

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
  const isAdmin = Boolean(user.claims?.role === 'admin' || user.claims?.admin === true);
  return isOwner || isConsultant || isAdmin;
}


// ─────────────────────────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────────────────────────

// Health
app.get('/', (_req, res) =>
  res.send('CiteWise API is running (uploads: Cloudflare R2 + Azure Blob).')
);

// 1) POST /requests — Create (Submitted) via MULTIPART: file + fields
//    Android sends: file, documentName, serviceType, description, priority, deadline?
app.post(
  '/requests',
  checkAuth,
  upload.single('file'),
  body('documentName').isString().notEmpty(),
  body('serviceType').isString().isIn([
    'PROOFREADING_EDITING',
    'FORMATTING_REFERENCING',
    'DATA_ANALYSIS_SUPPORT',
    'RESEARCH_METHODOLOGY_COACHING',
    'TRANSLATION',
    'OTHER',
  ]),
  body('description').isString().notEmpty(),
  body('priority').isString().isIn(['LOW', 'MEDIUM', 'HIGH']),
  body('deadline').optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      if (!req.file) return res.status(400).json({ message: 'file is required' });

      const {
        documentName,
        serviceType,
        description,
        priority,
        deadline = null,
      } = req.body;

      const mime = req.file.mimetype || 'application/octet-stream';
      const size = req.file.size || (req.file.buffer?.length ?? 0);
      const fileId = newFileId();
      const cleanName = safeName(documentName);
      const objectPath = `uploads/${fileId}/${cleanName}`;

      // Upload to both clouds in parallel
      const [r2Meta, azureMeta] = await Promise.all([
        uploadToR2({ key: objectPath, body: req.file.buffer, contentType: mime }),
        uploadToAzure({ blobPath: objectPath, body: req.file.buffer, contentType: mime }),
      ]);

      // Assemble Firestore payload
      const payload = {
        userId: req.user.uid,
        consultantId: null,
        serviceType,
        description,
        priority,
        deadline: deadline || null,
        status: 'Submitted',
        file: {
          fileId,
          originalName: documentName,
          mimeType: mime,
          size,
        },
        storage: {
          cloudflare: r2Meta, // { bucket, key, url? }
          azure: azureMeta,   // { container, blob, url? }
        },
      };

      // Persist via DB manager
      const created = await createRequest(payload);

      // Return DTO
      return res.status(201).json({
        ...created,
        documentId: fileId, // logical document id (shared across providers)
        storage: payload.storage,
      });
    } catch (err) {
      console.error(err);
      return res.status(500).json({ message: err.message });
    }
  }
);

// 2) GET /requests — list (filter by status, userId, consultantId)
app.get(
  '/requests',
  checkAuth,
  query('status').optional().isString(),
  query('userId').optional().isString(),
  query('consultantId').optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { status, userId, consultantId } = req.query;
      const out = await getRequests({ status, userId, consultantId });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(500).json({ message: err.message });
    }
  }
);

// 3) GET /requests/:id — details
app.get(
  '/requests/:id',
  checkAuth,
  param('id').isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await getRequestById(req.params.id);
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(404).json({ message: err.message });
    }
  }
);

// 4) POST /requests/:id/assign — Admin only (Submitted|Pending → Assigned)
app.post(
  '/requests/:id/assign',
  checkAuth,
  param('id').isString(),
  body('consultantId').isString().notEmpty(),
  body('deadline').optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
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
  '/requests/:id/assign',
  checkAuth,
  param('id').isString(),
  body('consultantId').optional().isString(),
  body('deadline').optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
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

// 6) POST /requests/:id/start-review — Consultant (Assigned → In Review)
app.post(
  '/requests/:id/start-review',
  checkAuth,
  param('id').isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionStartReview({ id: req.params.id, actor: req.user });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 7) POST /requests/:id/review — Consultant outcome (approve→Feedback | reject→Pending | fail→Failed)
app.post(
  '/requests/:id/review',
  checkAuth,
  param('id').isString(),
  body('outcome').isIn(['approve', 'reject', 'fail']),
  body('feedback').optional().isString(),
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

// 8) POST /requests/:id/resubmit — Student (Pending → Assigned)
app.post(
  '/requests/:id/resubmit',
  checkAuth,
  param('id').isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionResubmit({ id: req.params.id, actor: req.user });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 9) POST /requests/:id/cancel — cancel anytime (admin, owner, or assigned consultant)
app.post(
  '/requests/:id/cancel',
  checkAuth,
  param('id').isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionCancel({ id: req.params.id, actor: req.user });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// GET /requests/:id/download?provider=r2|azure&disposition=inline|attachment&expires=900
app.get('/requests/:id/download', checkAuth, async (req, res) => {
  try {
    const reqDoc = await getRequestById(req.params.id);
    if (!reqDoc) return res.status(404).json({ message: 'Request not found' });
    if (!canAccessRequest(reqDoc, req.user)) return res.status(403).json({ message: 'Forbidden' });

    const provider = (req.query.provider || 'r2').toLowerCase();
    const disposition = (req.query.disposition || 'inline').toLowerCase(); // or 'attachment'
    const expires = Math.max(60, Math.min(3600 * 2, parseInt(req.query.expires || '900', 10))); // clamp 1m-2h
    const filename = reqDoc?.file?.originalName || 'document';

    let url, ttl;
    if (provider === 'azure') {
      url = await azureSasUrl({
        container: reqDoc.storage.azure.container,
        blob: reqDoc.storage.azure.blob,
        expiresMinutes: Math.ceil(expires / 60),
      });
      // (Azure SAS doesn't encode disposition by default; preview works via content-type)
      ttl = Math.ceil(expires / 60) * 60;
    } else {
      // R2/S3 can embed content-disposition in the presign
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


// GET /requests/:id/file?provider=r2|azure&disposition=inline|attachment
app.get('/requests/:id/file', checkAuth, async (req, res) => {
  try {
    const reqDoc = await getRequestById(req.params.id);
    if (!reqDoc) return res.status(404).json({ message: 'Request not found' });
    if (!canAccessRequest(reqDoc, req.user)) return res.status(403).json({ message: 'Forbidden' });

    const provider = (req.query.provider || 'r2').toLowerCase();
    const disposition = (req.query.disposition || 'inline').toLowerCase();
    const filename = reqDoc?.file?.originalName || 'document';

    let meta;
    if (provider === 'azure') {
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

    res.setHeader('Content-Type', meta.contentType);
    if (meta.contentLength) res.setHeader('Content-Length', String(meta.contentLength));
    res.setHeader('Content-Disposition', `${disposition}; filename="${encodeURIComponent(filename)}"`);

    meta.stream.pipe(res);
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
  }
});


// ─────────────────────────────────────────────────────────────────────────────
// Startup: ensure storage, then boot server
// ─────────────────────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 8081;

(async function boot() {
  try {
        if (process.env.SKIP_STORAGE_INIT === '1') {
      console.log('[BOOT] SKIP_STORAGE_INIT=1 → skipping R2/Azure container checks');
    } else {
      await ensureR2Bucket();
      await ensureAzureContainer();
    }
    app.listen(PORT, () => console.log(`Server listening on :${PORT}`));
  } catch (err) {
    console.error('Startup failed:', err);
    process.exit(1);
  }
})();