import admin from "../db/firebaseAdmin.js" // (Firebase, 2019d)
import { notify } from "./notify.js" // (Firebase, 2019c)
import crypto from "crypto" // (Tony, 2023)

/** Firestore reference */
export function firestore() {
  return admin.firestore() // (Firebase, 2019a)
}

/** === Encryption helpers (AES-256-GCM) === */
const ENC_ALGO = "aes-256-gcm" // AES-GCM standard (Tony, 2023)
function getMsgKey() {
  const b64 = process.env.CITEWISE_MSG_KEY_B64
  const hex = process.env.CITEWISE_MSG_KEY_HEX
  if (!b64 && !hex) {
    throw new Error("Missing CITEWISE_MSG_KEY_B64 or CITEWISE_MSG_KEY_HEX") // Defensive check (Manico & Detlefsen, 2015)
  }
  return b64 ? Buffer.from(b64, "base64") : Buffer.from(hex, "hex")
}

/** Encrypt text -> { ct, iv, tag, alg, v } (Base64-encoded) */
export function encryptBody(plaintext) {
  const key = getMsgKey() // (Tony, 2023)
  const iv = crypto.randomBytes(12) // 96-bit nonce recommended for GCM (Tony, 2023)
  const cipher = crypto.createCipheriv(ENC_ALGO, key, iv)
  const ct = Buffer.concat([cipher.update(String(plaintext), "utf8"), cipher.final()])
  const tag = cipher.getAuthTag()
  return {
    v: 1,
    alg: ENC_ALGO,
    iv: iv.toString("base64"),
    ct: ct.toString("base64"),
    tag: tag.toString("base64"),
  }
}

/** Decrypt { ct, iv, tag } -> plaintext */
export function decryptBody(enc) {
  if (!enc || !enc.ct || !enc.iv || !enc.tag) return ""
  const key = getMsgKey() // (Tony, 2023)
  const iv = Buffer.from(enc.iv, "base64")
  const ct = Buffer.from(enc.ct, "base64")
  const tag = Buffer.from(enc.tag, "base64")
  const decipher = crypto.createDecipheriv(ENC_ALGO, key, iv)
  decipher.setAuthTag(tag)
  const pt = Buffer.concat([decipher.update(ct), decipher.final()])
  return pt.toString("utf8")
}

/** Build a stable 1:1 chat id from two UIDs (sorted join). */
export function chatIdFor(u1, u2) {
  const a = String(u1 || "").trim()
  const b = String(u2 || "").trim()
  if (!a || !b) throw new Error("chatIdFor: both user IDs are required") // (Manico & Detlefsen, 2015)
  return [a, b].sort().join("_")
}

/**
 * Ensure chat metadata exists in Firestore Chats collection.
 * Uses subcollections for messages to support horizontal scalability (Firebase, 2019a).
 * Stores participants array for efficient array-contains queries (Firebase, 2019a).
 */
export async function ensureChat(u1, u2) {
  const chatId = chatIdFor(u1, u2)
  const chatRef = firestore().collection("Chats").doc(chatId)

  const chatDoc = await chatRef.get()
  if (!chatDoc.exists) {
    await chatRef.set({
      chatId,
      participants: [u1, u2], // Array for array-contains queries (Firebase, 2019a)
      participantsMap: { [u1]: true, [u2]: true }, // Map for O(1) lookup (Cormen et al., 2009)
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastMessage: null,
      messageCount: 0,
    })
  } else {
    await chatRef.update({
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })
  }

  return chatId
}

/** Check if a user participates in a chat using Firestore (Firebase, 2019a) */
export async function isParticipant(chatId, uid) {
  const chatRef = firestore().collection("Chats").doc(chatId)
  const chatDoc = await chatRef.get()

  if (!chatDoc.exists) return false

  const data = chatDoc.data()
  return Boolean(data?.participantsMap?.[String(uid)])
}

/**
 * Retrieve messages in chronological order from Firestore subcollection.
 * Uses cursor-based pagination for efficient large dataset traversal (Firebase, 2019a).
 * Decrypts AES-GCM payloads if present (Tony, 2023).
 */
export async function getChatMessagesChrono(chatId, { limit = 100, after } = {}) {
  const n = Math.max(1, Math.min(Number(limit) || 100, 500))

  let query = firestore().collection("Chats").doc(chatId).collection("Messages").orderBy("createdAt", "asc").limit(n)

  if (after != null) {
    query = query.startAfter(Number(after))
  }

  const snapshot = await query.get()

  const messages = snapshot.docs.map((doc) => {
    const m = doc.data()
    const base = { id: doc.id, ...m }

    // Handle deleted messages for GDPR compliance (Voigt & Von dem Bussche, 2017)
    if (base.status === "deleted") return { ...base, body: "" }

    // Decrypt encrypted body if present (Tony, 2023)
    if (base.bodyEnc && base.body == null) {
      try {
        const body = decryptBody(base.bodyEnc) // AES-256-GCM decrypt (Tony, 2023)
        return { ...base, body }
      } catch {
        return { ...base, body: "" }
      }
    }

    return base
  })

  const nextAfter = messages.length ? messages[messages.length - 1].createdAt : null
  return { messages, nextAfter }
}

/**
 * Send a chat message to Firestore.
 * Encrypts message body using AES-256-GCM (Tony, 2023).
 * Updates Chats collection with last message preview and atomic counter (Firebase, 2019a).
 * Notifies recipient via FCM (Firebase, 2019c).
 */
export async function sendChatMessage({ fromUid, toUid, body }) {
  const f = String(fromUid || "").trim()
  const t = String(toUid || "").trim()
  const text = String(body || "").trim()
  if (!f || !t || !text) {
    throw new Error("sendChatMessage: fromUid, toUid, body required") // (Manico & Detlefsen, 2015)
  }

  const chatId = await ensureChat(f, t)
  const now = Date.now()

  const bodyEnc = encryptBody(text)

  const messageRef = firestore().collection("Chats").doc(chatId).collection("Messages").doc()

  const msg = {
    chatId,
    fromUid: f,
    toUid: t,
    body: text, // Store plaintext body for API consistency
    bodyEnc, // Store encrypted body for security
    preview: text.length > 120 ? text.slice(0, 117) + "..." : text,
    createdAt: now,
    updatedAt: now,
    status: "sent",
  }

  await messageRef.set(msg)

  const chatRef = firestore().collection("Chats").doc(chatId)
  await chatRef.update({
    lastMessage: {
      id: messageRef.id,
      preview: msg.preview,
      fromUid: f,
      toUid: t,
      createdAt: now,
      status: "sent",
    },
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    messageCount: admin.firestore.FieldValue.increment(1), // Atomic counter (Firebase, 2019a)
  })

  // Firestore + FCM notify (best-effort) (Firebase, 2019a; 2019c)
  try {
    const { displayName, username } = await notify.getUserProfile(f)
    const title = displayName || username || "New message"
    const preview = msg.preview

    await notify.createFirestoreNotification(t, {
      type: "chat_message",
      fromUid: f,
      fromName: displayName,
      fromUsername: username,
      message: preview,
      chatId,
    })

    await notify.sendPushToUser(t, {
      title,
      body: preview,
      data: {
        type: "chat_message",
        chatId: String(chatId),
        fromUid: String(f),
      },
    })
  } catch (e) {
    console.error("[sendChatMessage] notify failed:", e?.message || e)
  }

  return {
    id: messageRef.id,
    chatId,
    fromUid: f,
    toUid: t,
    body: text,
    createdAt: now,
    updatedAt: now,
    status: "sent",
  }
}

/*
REFERENCES

Android Knowledge. 2023. "CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete".
YouTube. August 2023 <https://www.youtube.com/watch?v=oGyQMBKPuNY> [accessed September 2025].

Anil Kr Mourya. 2024. "How to Convert Base64 String to Bitmap and Bitmap to Base64 String".
Medium. January 2024 <https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494> [accessed September 2025].

Axios. 2023. "Getting Started | Axios Docs".
Axios-Http.com. 2023 <https://axios-http.com/docs/intro> [accessed October 2025].

Balaji, Dev. 2023. "JWT Authentication in Node.js: A Practical Guide".
Medium. September 2023 <https://dvmhn07.medium.com/jwt-authentication-in-node-js-a-practical-guide-c8ab1b432a49> [accessed October 2025].

Cloudflare. 2024. "Cloudflare R2 · Cloudflare R2 Docs".
Cloudflare Docs. April 5, 2024 <https://developers.cloudflare.com/r2/> [accessed 12 October 2025].

Cormen, T. H., Leiserson, C. E., Rivest, R. L., & Stein, C. (2009). Introduction to algorithms (3rd ed.). MIT Press.

express-validator. 2019. "Getting Started · Express-Validator".
Github.io. 2019 <https://express-validator.github.io/docs/> [accessed October 2025].

Firebase. 2019a. "Cloud Firestore | Firebase".
Firebase. 2019 <https://firebase.google.com/docs/firestore> [accessed September 2025].

Firebase. 2019b. "Firebase Authentication | Firebase".
Firebase. Google. 2019 <https://firebase.google.com/docs/auth> [accessed September 2025].

Firebase. 2019c. "Firebase Cloud Messaging | Firebase".
Firebase. 2019 <https://firebase.google.com/docs/cloud-messaging> [accessed September 2025].

Firebase. 2019d. "Firebase Realtime Database".
Firebase. 2019 <https://firebase.google.com/docs/database> [accessed September 2025].

GeeksforGeeks. 2022a. "Use of CORS in Node.js".
GeeksforGeeks. March 2022 <https://www.geeksforgeeks.org/node-js/use-of-cors-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2022b. "What Is Expressratelimit in Node.js ?".
GeeksforGeeks. April 2022 <https://www.geeksforgeeks.org/node-js/what-is-express-rate-limit-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2024. "NPM Dotenv".
GeeksforGeeks. May 2024 <https://www.geeksforgeeks.org/node-js/npm-dotenv/> [accessed October 2025].

Manico, Jim and August Detlefsen. 2015. *Iron-Clad Java: Building Secure Web Applications*.
McGraw-Hill Education.

Nakazawa Tech. 2018. "Delightful JavaScript Testing with Jest".
YouTube. May 30, 2018 <https://www.youtube.com/watch?v=cAKYQpTC7MA> [accessed 2 November 2025].

NextJS. 2025. "Documentation | NestJS - a Progressive Node.js Framework".
Documentation | NestJS - a Progressive Node.js Framework. 2025 <https://docs.nestjs.com/security/helmet> [accessed October 2025].

Patel, Ravi. 2024. "A Beginner's Guide to the Node.js".
Medium. December 2024 <https://medium.com/@ravipatel.it/a-beginners-guide-to-the-node-js-469f7458bbb2> [accessed October 2025].

React Native. 2025. "React Fundamentals · React Native".
Reactnative.dev. 2025 <https://reactnative.dev/docs/intro-react> [accessed September 2025].

Samson Omojola. 2024. "Password Hashing in Node.js with Bcrypt".
Honeybadger Developer Blog. Honeybadger. January 2024 <https://www.honeybadger.io/blog/node-password-hashing/> [accessed September 2025].

Tony. 2023. "Guide to Node's Crypto Module for Encryption/Decryption".
Medium. May 5, 2023 <https://medium.com/@tony.infisical/guide-to-nodes-crypto-module-for-encryption-decryption-65c077176980> [accessed 2 November 2025].

Voigt, P., & Von dem Bussche, A. (2017). The EU General Data Protection Regulation (GDPR): A practical guide. Springer.
*/
