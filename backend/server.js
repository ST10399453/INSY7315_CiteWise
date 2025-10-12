import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { body, param, query, validationResult } from 'express-validator';
import { checkAuth } from './auth/checkAuth.js';
import {
  createRequest,
  getRequests,
  getRequestById,
  transitionAssign,
} from './db/dbManager.js';
import {
  transitionAssign,
  transitionStartReview,
  transitionSubmitReview,
  transitionResubmit,
  transitionCancel,
} from './db/dbManager.js';

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

// Health
app.get('/', (_req, res) => res.send('CiteWise API is running (Firebase-only).'));

// Validation helper
function bailIfInvalid(req, res) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ errors: errors.array() });
}

// 1) POST /requests — Create (Submitted)
app.post(
  '/requests',
  checkAuth,
  body('documentId').isString().notEmpty(),
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
  body('deadline').optional(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const payload = {
        userId: req.user.uid,
        documentId: req.body.documentId,
        consultantId: null,
        serviceType: req.body.serviceType,
        description: req.body.description,
        priority: req.body.priority,
        deadline: req.body.deadline ?? null,
      };
      const out = await createRequest(payload);
      res.status(201).json(out);
    } catch (err) {
      console.error(err);
      res.status(500).json({ message: err.message });
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
app.get('/requests/:id', checkAuth, param('id').isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
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
  '/requests/:id/assign',
  checkAuth,
  param('id').isString(),
  body('consultantId').isString().notEmpty(),
  body('deadline').optional(),
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

// 5) PUT /requests/:id/assign — Admin only (update existing assignment)
app.put(
  '/requests/:id/assign',
  checkAuth,
  param('id').isString(),
  body('consultantId').optional().isString(),
  body('deadline').optional(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id,
        consultantId: req.body.consultantId ?? null,
        deadline: req.body.deadline ?? null,
        allowUpdate: true, // indicates it's an update
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
app.post(
  '/requests/:id/start-review',
  checkAuth,
  param('id').isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionStartReview({
        id: req.params.id,
        actor: req.user,
      });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 7) POST /requests/:id/review — Consultant outcome
app.post(
  '/requests/:id/review',
  checkAuth,
  param('id').isString(),
  body('outcome').isString().isIn(['approve', 'reject', 'fail']),
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

// 8) POST /requests/:id/resubmit — Student
app.post(
  '/requests/:id/resubmit',
  checkAuth,
  param('id').isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionResubmit({
        id: req.params.id,
        actor: req.user,
      });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

// 9) POST /requests/:id/cancel — cancel anytime
app.post(
  '/requests/:id/cancel',
  checkAuth,
  param('id').isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionCancel({
        id: req.params.id,
        actor: req.user,
      });
      res.json(out);
    } catch (err) {
      console.error(err);
      res.status(400).json({ message: err.message });
    }
  }
);

