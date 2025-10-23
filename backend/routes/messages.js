import { Router } from "express";
import { body, param, query } from "express-validator";
import { checkAuth } from "../auth/checkAuth.js";
import { bailIfInvalid } from "../utils/expressHelpers.js";
import { sendChatMessage, getChatMessagesChrono, isParticipant } from "../utils/chats.js";

const router = Router();

router.get(
  "/:chatId",
  checkAuth,
  param("chatId").isString().notEmpty(),
  query("limit").optional().isInt({ min: 1, max: 500 }),
  query("after").optional().isInt(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const { chatId } = req.params;
      const limit = req.query.limit ? Number(req.query.limit) : 100;
      const after = req.query.after != null ? Number(req.query.after) : undefined;

      const allowed = await isParticipant(chatId, req.user.uid);
      if (!allowed) return res.status(403).json({ success: false, message: "Forbidden" });

      const { messages, nextAfter } = await getChatMessagesChrono(chatId, { limit, after });

      return res.json({
        success: true, chatId, messages, nextAfter, meta: { order: "asc", limit }
      });
    } catch (e) {
      console.error("GET /messages/:chatId error:", e);
      return res.status(500).json({ success: false, message: "Failed to fetch messages" });
    }
  }
);

router.post(
  "/",
  checkAuth,
  body("toUid").isString().notEmpty(),
  body("body").isString().notEmpty(),
  async (req, res) => {
    const v = bailIfInvalid(req, res); if (v) return v;
    try {
      const fromUid = req.user.uid;
      const { toUid, body: text } = req.body;

      if (String(toUid) === String(fromUid)) {
        return res.status(400).json({ success: false, message: "Cannot message yourself" });
      }

      const saved = await sendChatMessage({ fromUid, toUid, body: text });
      return res.status(201).json({ success: true, message: saved });
    } catch (e) {
      console.error("POST /messages error:", e);
      return res.status(500).json({ success: false, message: "Failed to send message" });
    }
  }
);

export default router;
