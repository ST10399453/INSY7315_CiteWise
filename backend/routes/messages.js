import { Router } from "express";
import { body, param, query } from "express-validator";
import { checkAuth } from "../auth/checkAuth.js";
import { bailIfInvalid } from "../utils/expressHelpers.js";
import { sendChatMessage, getChatMessagesChrono, isParticipant } from "../utils/chats.js";

const router = Router();

/**
 * =======================================================
 *  ROUTE: Get Chat Messages (Chronological Order)
 *  -------------------------------------------------------
 *  Endpoint: GET /:chatId
 *  Purpose: Retrieve messages in a chat thread, ordered chronologically.
 *  Supports pagination via `after` cursor and limit per request.
 * 
 *  Used by: Web App & Mobile App
 *  Access: Only participants of the chat can access it.
 * =======================================================
 */
router.get(
  "/:chatId",
  checkAuth, // Require authentication
  param("chatId").isString().notEmpty(), // Validate chatId parameter
  query("limit").optional().isInt({ min: 1, max: 500 }), // Optional: number of messages to fetch
  query("after").optional().isInt(), // Optional: pagination cursor
  async (req, res) => {
    // Validate request params/query
    const v = bailIfInvalid(req, res);
    if (v) return v;

    try {
      // Extract and normalize query parameters
      const { chatId } = req.params;
      const limit = req.query.limit ? Number(req.query.limit) : 100;
      const after = req.query.after != null ? Number(req.query.after) : undefined;

      // Check if the user is allowed to access this chat
      const allowed = await isParticipant(chatId, req.user.uid);
      if (!allowed) return res.status(403).json({ success: false, message: "Forbidden" });

      // Fetch messages in ascending order (oldest → newest)
      const { messages, nextAfter } = await getChatMessagesChrono(chatId, { limit, after });

      // Respond with messages and pagination info
      return res.json({
        success: true,
        chatId,
        messages,
        nextAfter, // Cursor for next page
        meta: { order: "asc", limit },
      });
    } catch (e) {
      console.error("GET /messages/:chatId error:", e);
      return res.status(500).json({ success: false, message: "Failed to fetch messages" });
    }
  }
);

/**
 * =======================================================
 *  ROUTE: Send Chat Message
 *  -------------------------------------------------------
 *  Endpoint: POST /
 *  Purpose: Send a new message from the authenticated user
 *           to another user, creating a chat if needed.
 * 
 *  Used by: Web App & Mobile App
 *  Access: Authenticated users only.
 * =======================================================
 */
router.post(
  "/",
  checkAuth, // Require authentication
  body("toUid").isString().notEmpty(), // Validate recipient
  body("body").isString().notEmpty(), // Validate message content
  async (req, res) => {
    // Validate request body
    const v = bailIfInvalid(req, res);
    if (v) return v;

    try {
      const fromUid = req.user.uid;
      const { toUid, body: text } = req.body;

      // Prevent users from messaging themselves
      if (String(toUid) === String(fromUid)) {
        return res.status(400).json({ success: false, message: "Cannot message yourself" });
      }

      // Send message
      const saved = await sendChatMessage({ fromUid, toUid, body: text });

      // Return created message
      return res.status(201).json({ success: true, message: saved });
    } catch (e) {
      console.error("POST /messages error:", e);
      return res.status(500).json({ success: false, message: "Failed to send message" });
    }
  }
);

export default router;
