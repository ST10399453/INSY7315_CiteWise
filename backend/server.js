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