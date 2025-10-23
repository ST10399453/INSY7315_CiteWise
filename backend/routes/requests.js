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

// 1) Create request (multipart)
router.post(
  "/",
  checkAuth,
  upload.single("file"),
  body("documentName").isString().notEmpty(),
  body("serviceType").isString().isIn([
    "PROOFREADING_EDITING","FORMATTING_REFERENCING","DATA_ANALYSIS_SUPPORT",
    "RESEARCH_METHODOLOGY_COACHING","TRANSLATION","OTHER",
  ]),
  body("description").isString().notEmpty(),
  body("priority").isString().isIn(["LOW","MEDIUM","HIGH"]),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      if (!req.file) return res.status(400).json({ message: "file is required" });

      const { documentName, serviceType, description, priority, deadline = null } = req.body;
      const mime = req.file.mimetype || "application/octet-stream";
      const size = req.file.size || req.file.buffer?.length || 0;
      const fileId = newFileId();
      const cleanName = safeName(documentName);
      const objectPath = `uploads/${fileId}/${cleanName}`;

      // Upload to Cloudflare R2
      const r2Meta = await uploadToR2({ key: objectPath, body: req.file.buffer, contentType: mime });

      const payload = {
        userId: req.user.uid, consultantId: null,
        serviceType, description, priority, deadline: deadline || null,
        status: "Submitted",
        documentId: fileId,
        file: { fileId, originalName: documentName, mimeType: mime, size },
        storage: { cloudflare: r2Meta },
      };

      const created = await createRequest(payload);
      return res.status(201).json({ ...created, storage: payload.storage });
    } catch (err) {
      console.error(err);
      res.status(500).json({ message: err.message });
    }
  }
);

// 2) List, 3) Details, 4–9) Transitions (unchanged handlers)
router.get("/", checkAuth, async (req, res) => {
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

router.get("/:id", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try { res.json(await getRequestById(req.params.id)); }
  catch (err) { console.error(err); res.status(404).json({ message: err.message }); }
});

router.post("/:id/assign",
  checkAuth, param("id").isString(),
  body("consultantId").isString().notEmpty(),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id, consultantId: req.body.consultantId,
        deadline: req.body.deadline ?? null, allowUpdate: false, actor: req.user,
      });
      res.json(out);
    } catch (err) { console.error(err); res.status(400).json({ message: err.message }); }
  }
);

router.put("/:id/assign",
  checkAuth, param("id").isString(),
  body("consultantId").optional().isString(),
  body("deadline").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionAssign({
        id: req.params.id, consultantId: req.body.consultantId ?? null,
        deadline: req.body.deadline ?? null, allowUpdate: true, actor: req.user,
      });
      res.json(out);
    } catch (err) { console.error(err); res.status(400).json({ message: err.message }); }
  }
);

router.post("/:id/start-review", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try { res.json(await transitionStartReview({ id: req.params.id, actor: req.user })); }
  catch (err) { console.error(err); res.status(400).json({ message: err.message }); }
});

router.post("/:id/review",
  checkAuth, param("id").isString(),
  body("outcome").isIn(["approve","reject","fail"]),
  body("feedback").optional().isString(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const out = await transitionSubmitReview({
        id: req.params.id, outcome: req.body.outcome,
        feedback: req.body.feedback ?? null, actor: req.user,
      });
      res.json(out);
    } catch (err) { console.error(err); res.status(400).json({ message: err.message }); }
  }
);

router.post("/:id/resubmit", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try { res.json(await transitionResubmit({ id: req.params.id, actor: req.user })); }
  catch (err) { console.error(err); res.status(400).json({ message: err.message }); }
});

router.post("/:id/cancel", checkAuth, param("id").isString(), async (req, res) => {
  const v = bailIfInvalid(req, res); if (v) return v;
  try { res.json(await transitionCancel({ id: req.params.id, actor: req.user })); }
  catch (err) { console.error(err); res.status(400).json({ message: err.message }); }
});

export default router;
