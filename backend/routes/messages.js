import { Router } from "express"; // (GeeksforGeeks, 2022a)
import { body, param, query } from "express-validator"; // (express-validator, 2019)
import { checkAuth } from "../auth/checkAuth.js"; // (Balaji, 2023)
import { bailIfInvalid } from "../utils/expressHelpers.js"; // (express-validator, 2019)
import { sendChatMessage, getChatMessagesChrono, isParticipant } from "../utils/chats.js"; // (Firebase, 2019a)

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
  checkAuth, // Require authentication (Balaji, 2023)
  param("chatId").isString().notEmpty(), // Validate chatId parameter (express-validator, 2019)
  query("limit").optional().isInt({ min: 1, max: 500 }), // Optional: number of messages to fetch (express-validator, 2019)
  query("after").optional().isInt(), // Optional: pagination cursor (express-validator, 2019)
  async (req, res) => {
    // Validate request params/query
    const v = bailIfInvalid(req, res); // Short-circuits with 400 on validation errors (express-validator, 2019)
    if (v) return v;

    try {
      // Extract and normalize query parameters
      const { chatId } = req.params;
      const limit = req.query.limit ? Number(req.query.limit) : 100;
      const after = req.query.after != null ? Number(req.query.after) : undefined;

      // Check if the user is allowed to access this chat (Manico & Detlefsen, 2015)
      const allowed = await isParticipant(chatId, req.user.uid);
      if (!allowed) return res.status(403).json({ success: false, message: "Forbidden" });

      // Fetch messages in ascending order (oldest → newest) (Firebase, 2019a)
      const { messages, nextAfter } = await getChatMessagesChrono(chatId, { limit, after });

      // Respond with messages and pagination info
      return res.json({
        success: true,
        chatId,
        messages,
        nextAfter, // Cursor for next page (Firebase, 2019a)
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
  checkAuth, // Require authentication (Balaji, 2023)
  body("toUid").isString().notEmpty(), // Validate recipient (express-validator, 2019)
  body("body").isString().notEmpty(), // Validate message content (express-validator, 2019)
  async (req, res) => {
    // Validate request body
    const v = bailIfInvalid(req, res); // (express-validator, 2019)
    if (v) return v;

    try {
      const fromUid = req.user.uid;
      const { toUid, body: text } = req.body;

      // Prevent users from messaging themselves (Manico & Detlefsen, 2015)
      if (String(toUid) === String(fromUid)) {
        return res.status(400).json({ success: false, message: "Cannot message yourself" });
      }

      // Send message (Firebase, 2019a)
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

/*
REFERENCES

Android Knowledge. 2023. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
YouTube. August 2023 <https://www.youtube.com/watch?v=oGyQMBKPuNY> [accessed September 2025].

Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
Medium. January 2024 <https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494> [accessed September 2025].

Axios. 2023. “Getting Started | Axios Docs”.
Axios-Http.com. 2023 <https://axios-http.com/docs/intro> [accessed September 2025].

Balaji, Dev. 2023. “JWT Authentication in Node.js: A Practical Guide”.
Medium. September 2023 <https://dvmhn07.medium.com/jwt-authentication-in-node-js-a-practical-guide-c8ab1b432a49> [accessed October 2025].

Cloudflare. 2024. “Cloudflare R2 · Cloudflare R2 Docs”.
Cloudflare Docs. April 5, 2024 <https://developers.cloudflare.com/r2/> [accessed 12 October 2025].

express-validator. 2019. “Getting Started · Express-Validator”.
Github.io. 2019 <https://express-validator.github.io/docs/> [accessed October 2025].

Firebase. 2019a. “Cloud Firestore | Firebase”.
Firebase. 2019 <https://firebase.google.com/docs/firestore> [accessed September 2025].

Firebase. 2019b. “Firebase Authentication | Firebase”.
Firebase. Google. 2019 <https://firebase.google.com/docs/auth> [accessed September 2025].

Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
Firebase. 2019 <https://firebase.google.com/docs/cloud-messaging> [accessed September 2025].

Firebase. 2019d. “Firebase Realtime Database”.
Firebase. 2019 <https://firebase.google.com/docs/database> [accessed September 2025].

GeeksforGeeks. 2022a. “Use of CORS in Node.js”.
GeeksforGeeks. March 2022 <https://www.geeksforgeeks.org/node-js/use-of-cors-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2022b. “What Is Expressratelimit in Node.js ?”.
GeeksforGeeks. April 2022 <https://www.geeksforgeeks.org/node-js/what-is-express-rate-limit-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2024. “NPM Dotenv”.
GeeksforGeeks. May 2024 <https://www.geeksforgeeks.org/node-js/npm-dotenv/> [accessed October 2025].

Manico, Jim and August Detlefsen. 2015. *Iron-Clad Java: Building Secure Web Applications*.
McGraw-Hill Education.

Nakazawa Tech. 2018. “Delightful JavaScript Testing with Jest”.
YouTube. May 30, 2018 <https://www.youtube.com/watch?v=cAKYQpTC7MA> [accessed 2 November 2025].

NextJS. 2025. “Documentation | NestJS - a Progressive Node.js Framework”.
Documentation | NestJS - a Progressive Node.js Framework. 2025 <https://docs.nestjs.com/security/helmet> [accessed October 2025].

Patel, Ravi. 2024. “A Beginner’s Guide to the Node.js”.
Medium. December 2024 <https://medium.com/@ravipatel.it/a-beginners-guide-to-the-node-js-469f7458bbb2> [accessed October 2025].

React Native. 2025. “React Fundamentals · React Native”.
Reactnative.dev. 2025 <https://reactnative.dev/docs/intro-react> [accessed September 2025].

Samson Omojola. 2024. “Password Hashing in Node.js with Bcrypt”.
Honeybadger Developer Blog. Honeybadger. January 2024 <https://www.honeybadger.io/blog/node-password-hashing/> [accessed September 2025].

Tony. 2023. “Guide to Node’s Crypto Module for Encryption/Decryption”.
Medium. May 5, 2023 <https://medium.com/@tony.infisical/guide-to-nodes-crypto-module-for-encryption-decryption-65c077176980> [accessed 2 November 2025].
*/
