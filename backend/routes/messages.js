import { Router } from "express" // (GeeksforGeeks, 2022a)
import { body, param, query } from "express-validator" // (express-validator, 2019)
import { checkAuth } from "../auth/checkAuth.js" // (Balaji, 2023)
import { bailIfInvalid } from "../utils/expressHelpers.js" // (express-validator, 2019)
import {
  sendChatMessage,
  getChatMessagesChrono,
  isParticipant,
  chatIdFor,
  firestore,
  decryptBody,
} from "../utils/chats.js" // (Firebase, 2022)

const router = Router()

/**
 * =======================================================
 *  ROUTE: Get Messages Since Timestamp
 *  -------------------------------------------------------
 *  Endpoint: GET /since?since=xxx
 *  Purpose: Get all messages for the authenticated user since a timestamp
 *
 *  Used by: Mobile App for polling
 *  Access: Authenticated users only.
 *  NOTE: Placed before "/:chatId" so it isn't captured as chatId="since".
 * =======================================================
 */
//Implemented in app
router.get(
  "/since",
  checkAuth,
  query("since").isNumeric(),
  async (req, res) => {
    const v = bailIfInvalid(req, res)
    if (v) return v

    try {
      const myUid = req.user.uid
      const timestamp = req.query.since ? Number(req.query.since) : 0

      console.log(`/messages/since?since=${timestamp} called by user: ${myUid}`)

      const chatsSnapshot = await firestore()
        .collection("Chats")
        .where("participants", "array-contains", myUid)
        .get()

      const allMessages = []

      for (const chatDoc of chatsSnapshot.docs) {
        const chatId = chatDoc.id
        const messagesSnapshot = await firestore()
          .collection("Chats")
          .doc(chatId)
          .collection("Messages")
          .where("createdAt", ">", timestamp)
          .orderBy("createdAt", "asc")
          .limit(100)
          .get()

        for (const msgDoc of messagesSnapshot.docs) {
          const m = msgDoc.data()
          const base = { id: msgDoc.id, ...m }

          // Decrypt if encrypted
          if (base.bodyEnc && base.body == null) {
            try {
              base.body = decryptBody(base.bodyEnc)
            } catch {
              base.body = ""
            }
          }

          const message = {
            id: base.id,
            fromUid: base.fromUid,
            toUid: base.toUid,
            body: base.body || "",
            createdAt: base.createdAt,
            updatedAt: base.updatedAt,
            status: base.status,
          }

          console.log(
            `Message ${message.id}: fromUid=${message.fromUid}, toUid=${message.toUid}, requesting user=${myUid}`,
          )

          allMessages.push(message)
        }
      }

      // Sort by timestamp
      allMessages.sort((a, b) => a.createdAt - b.createdAt)

      return res.json(allMessages)
    } catch (e) {
      console.error("GET /messages/since error:", e)
      return res.status(500).json({ success: false, message: "Failed to fetch messages" })
    }
  },
)

/**
 * =======================================================
 *  ROUTE: Get Chat Messages (Chronological Order)
 *  -------------------------------------------------------
 *  Endpoint: GET /:chatId
 *  Purpose: Retrieve messages from Firestore subcollection,
 *           ordered chronologically with cursor-based pagination.
 *
 *  Used by: Web App & Mobile App
 *  Access: Only participants of the chat can access it.
 * =======================================================
 */
//Implemented in app
router.get(
  "/:chatId",
  checkAuth,
  param("chatId").isString().notEmpty(),
  query("limit").optional().isInt({ min: 1, max: 500 }),
  query("after").optional().isInt(),
  async (req, res) => {
    const v = bailIfInvalid(req, res)
    if (v) return v

    try {
      const { chatId } = req.params
      const limit = req.query.limit ? Number(req.query.limit) : 100
      const after = req.query.after != null ? Number(req.query.after) : undefined

      const allowed = await isParticipant(chatId, req.user.uid)
      if (!allowed) return res.status(403).json({ success: false, message: "Forbidden" })

      const { messages, nextAfter } = await getChatMessagesChrono(chatId, { limit, after })

      return res.json({
        success: true,
        chatId,
        messages,
        nextAfter,
        meta: { order: "asc", limit },
      })
    } catch (e) {
      console.error("GET /messages/:chatId error:", e)
      return res.status(500).json({ success: false, message: "Failed to fetch messages" })
    }
  },
)

// /**
//  * =======================================================
//  *  ROUTE: Send Chat Message
//  *  -------------------------------------------------------
//  *  Endpoint: POST /send
//  *  Purpose: Send a new message from the authenticated user
//  *           to another user. Stores in Firestore Messages subcollection.
//  *
//  *  Used by: Web App & Mobile App
//  *  Access: Authenticated users only.
//  * =======================================================
//  */
// router.post(
//   "/send",
//   checkAuth,
//   body("toUid").isString().notEmpty(),
//   body("text").isString().notEmpty(), // Android/web send "text" for this endpoint
//   async (req, res) => {
//     const v = bailIfInvalid(req, res)
//     if (v) return v

//     try {
//       const fromUid = req.user.uid
//       const { toUid, text } = req.body

//       // Prevent users from messaging themselves (Manico & Detlefsen, 2015)
//       if (String(toUid) === String(fromUid)) {
//         return res.status(400).json({ success: false, message: "Cannot message yourself" })
//       }

//       const saved = await sendChatMessage({ fromUid, toUid, body: text })

//       console.log(`Message sent: id=${saved.id}, fromUid=${saved.fromUid}, toUid=${saved.toUid}`)

//       return res.status(201).json({
//         id: saved.id,
//         chatId: saved.chatId,
//         fromUid: saved.fromUid,
//         toUid: saved.toUid,
//         body: saved.body,
//         createdAt: saved.createdAt,
//         updatedAt: saved.updatedAt,
//         status: saved.status,
//       })
//     } catch (e) {
//       console.error("POST /messages/send error:", e)
//       return res.status(500).json({ success: false, message: "Failed to send message" })
//     }
//   },
// )

// /**
//  * =======================================================
//  *  ROUTE: Get Messages with Specific Peer
//  *  -------------------------------------------------------
//  *  Endpoint: GET /with-peer/:peerUid
//  *  Purpose: Get all messages between authenticated user and peer
//  *
//  *  Used by: Mobile App
//  *  Access: Authenticated users only.
//  * =======================================================
//  */
// router.get(
//   "/with-peer/:peerUid",
//   checkAuth,
//   param("peerUid").isString().notEmpty(),
//   query("limit").optional().isInt({ min: 1, max: 500 }),
//   async (req, res) => {
//     const v = bailIfInvalid(req, res)
//     if (v) return v

//     try {
//       const myUid = req.user.uid
//       const { peerUid } = req.params
//       const limit = req.query.limit ? Number(req.query.limit) : 100

//       const chatId = chatIdFor(myUid, peerUid)
//       const messagesRef = firestore().collection("Chats").doc(chatId).collection("Messages")
//       const snapshot = await messagesRef.orderBy("createdAt", "asc").limit(limit).get()

//       const messages = snapshot.docs.map((doc) => {
//         const m = doc.data()
//         const base = { id: doc.id, ...m }

//         // Decrypt if encrypted
//         if (base.bodyEnc && base.body == null) {
//           try {
//             base.body = decryptBody(base.bodyEnc)
//           } catch {
//             base.body = ""
//           }
//         }

//         return {
//           id: base.id,
//           fromUid: base.fromUid,
//           toUid: base.toUid,
//           body: base.body || "",
//           createdAt: base.createdAt,
//           updatedAt: base.updatedAt,
//           status: base.status,
//         }
//       })

//       return res.json({ success: true, messages })
//     } catch (e) {
//       console.error("GET /messages/with-peer error:", e)
//       return res.status(500).json({ success: false, message: "Failed to fetch messages" })
//     }
//   },
// )

/**
 * =======================================================
 *  ROUTE: Get Messages with Query Params (Legacy Support)
 *  -------------------------------------------------------
 *  Endpoint: GET /?peerId=xxx
 *  Purpose: Legacy endpoint for mobile app compatibility
 *
 *  Used by: Mobile App (legacy)
 *  Access: Authenticated users only.
 * =======================================================
 */
//Implemented in app
router.get(
  "/",
  checkAuth,
  query("peerId").isString().notEmpty(),
  query("limit").optional().isInt({ min: 1, max: 500 }),
  async (req, res) => {
    const v = bailIfInvalid(req, res)
    if (v) return v

    try {
      const myUid = req.user.uid
      const { peerId: peerUid } = req.query
      const limit = req.query.limit ? Number(req.query.limit) : 100

      const chatId = chatIdFor(myUid, peerUid)
      const messagesRef = firestore().collection("Chats").doc(chatId).collection("Messages")
      const snapshot = await messagesRef.orderBy("createdAt", "asc").limit(limit).get()

      const messages = snapshot.docs.map((doc) => {
        const m = doc.data()
        const base = { id: doc.id, ...m }

        // Decrypt if encrypted
        if (base.bodyEnc && base.body == null) {
          try {
            base.body = decryptBody(base.bodyEnc)
          } catch {
            base.body = ""
          }
        }

        return {
          id: base.id,
          fromUid: base.fromUid,
          toUid: base.toUid,
          body: base.body || "",
          createdAt: base.createdAt,
          updatedAt: base.updatedAt,
          status: base.status,
        }
      })

      return res.json(messages)
    } catch (e) {
      console.error("GET /messages error:", e)
      return res.status(500).json({ success: false, message: "Failed to fetch messages" })
    }
  },
)

/**
 * =======================================================
 *  ROUTE: Send Chat Message (Root POST - Legacy Support)
 *  -------------------------------------------------------
 *  Endpoint: POST /
 *  Purpose: Legacy endpoint for mobile app - accepts "body" field
 *
 *  Used by: Mobile App (legacy)
 *  Access: Authenticated users only.
 * =======================================================
 */
//Implemented in app
router.post(
  "/",
  checkAuth,
  body("toUid").isString().notEmpty(),
  body("body").isString().notEmpty(), // Android legacy sends "body"
  async (req, res) => {
    const v = bailIfInvalid(req, res)
    if (v) return v

    try {
      const fromUid = req.user.uid
      const { toUid, body: text } = req.body

      if (String(toUid) === String(fromUid)) {
        return res.status(400).json({ success: false, message: "Cannot message yourself" })
      }

      const saved = await sendChatMessage({ fromUid, toUid, body: text })

      console.log(`Message sent (root POST): id=${saved.id}, fromUid=${saved.fromUid}, toUid=${saved.toUid}`)

      // Return message directly without wrapper for Android app
      return res.status(201).json({
        id: saved.id,
        chatId: saved.chatId,
        fromUid: saved.fromUid,
        toUid: saved.toUid,
        body: saved.body,
        createdAt: saved.createdAt,
        updatedAt: saved.updatedAt,
        status: saved.status,
      })
    } catch (e) {
      console.error("POST /messages error:", e)
      return res.status(500).json({ success: false, message: "Failed to send message" })
    }
  },
)

export default router

/*
REFERENCES

Balaji, S. (2023). Building secure REST APIs with Node.js and Express. Packt Publishing.

express-validator. (2019). express-validator documentation. https://express-validator.github.io/docs/

Firebase. (2022). Cloud Firestore. Google. https://firebase.google.com/docs/firestore

GeeksforGeeks. (2022a). Express.js Router. https://www.geeksforgeeks.org/express-js-router/

Manico, J., & Detlefsen, A. (2015). Iron-clad Java: Building secure web applications. Oracle Press.

Tony, E. (2023). Practical cryptography for developers. Leanpub.
*/
