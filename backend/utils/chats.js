import admin from "../db/firebaseAdmin.js"; // (Firebase, 2019d)
import { notify } from "./notify.js"; // (Firebase, 2019c)
import crypto from "crypto"; // (Tony, 2023)

/** RTDB */
export function rtdb() {
  return admin.database(); // Firebase Realtime Database access (Firebase, 2019d)
}

/** === Encryption helpers (AES-256-GCM) === */
const ENC_ALGO = "aes-256-gcm"; // AES-GCM standard (Tony, 2023)
function getMsgKey() {
  const b64 = process.env.CITEWISE_MSG_KEY_B64;
  const hex = process.env.CITEWISE_MSG_KEY_HEX;
  if (!b64 && !hex) {
    throw new Error("Missing CITEWISE_MSG_KEY_B64 or CITEWISE_MSG_KEY_HEX"); // Defensive check (Manico & Detlefsen, 2015)
  }
  return b64 ? Buffer.from(b64, "base64") : Buffer.from(hex, "hex");
}

/** Encrypt text -> { ct, iv, tag, alg, v } (Base64-encoded) */
export function encryptBody(plaintext) {
  const key = getMsgKey(); // (Tony, 2023)
  const iv = crypto.randomBytes(12); // 96-bit nonce recommended for GCM (Tony, 2023)
  const cipher = crypto.createCipheriv(ENC_ALGO, key, iv);
  const ct = Buffer.concat([cipher.update(String(plaintext), "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return {
    v: 1,
    alg: ENC_ALGO,
    iv: iv.toString("base64"),
    ct: ct.toString("base64"),
    tag: tag.toString("base64"),
  };
}

/** Decrypt { ct, iv, tag } -> plaintext */
export function decryptBody(enc) {
  if (!enc || !enc.ct || !enc.iv || !enc.tag) return "";
  const key = getMsgKey(); // (Tony, 2023)
  const iv = Buffer.from(enc.iv, "base64");
  const ct = Buffer.from(enc.ct, "base64");
  const tag = Buffer.from(enc.tag, "base64");
  const decipher = crypto.createDecipheriv(ENC_ALGO, key, iv);
  decipher.setAuthTag(tag);
  const pt = Buffer.concat([decipher.update(ct), decipher.final()]);
  return pt.toString("utf8");
}

/** Build a stable 1:1 chat id from two UIDs (sorted join). */
export function chatIdFor(u1, u2) {
  const a = String(u1 || "").trim();
  const b = String(u2 || "").trim();
  if (!a || !b) throw new Error("chatIdFor: both user IDs are required"); // (Manico & Detlefsen, 2015)
  return [a, b].sort().join("_");
}

/**
 * Ensure chat metadata exists.
 * Uses Firebase RTDB mirror structure for listing efficiency (Firebase, 2019d).
 */
export async function ensureChat(u1, u2) {
  const chatId = chatIdFor(u1, u2);
  const now = Date.now();
  const ref = rtdb().ref(`chats/${chatId}`);

  const snap = await ref.get();
  if (!snap.exists()) {
    await ref.set({
      id: chatId,
      users: { [u1]: true, [u2]: true },
      createdAt: now,
      updatedAt: now,
    });
  } else {
    await ref.child("updatedAt").set(now);
  }

  // mirror entries for listing (Firebase, 2019d)
  const updates = {};
  updates[`userChats/${u1}/${chatId}`] = { chatId, otherUid: u2, updatedAt: now };
  updates[`userChats/${u2}/${chatId}`] = { chatId, otherUid: u1, updatedAt: now };
  await rtdb().ref().update(updates);

  return chatId;
}

/** Check if a user participates in a chat (Firebase, 2019d) */
export async function isParticipant(chatId, uid) {
  const usersSnap = await rtdb().ref(`chats/${chatId}/users`).get();
  const users = usersSnap.val() || {};
  return Boolean(users && users[String(uid)]);
}

/**
 * Retrieve messages in chronological order.
 * Decrypts AES-GCM payloads if present (Tony, 2023).
 */
export async function getChatMessagesChrono(chatId, { limit = 100, after } = {}) {
  const n = Math.max(1, Math.min(Number(limit) || 100, 500));

  let q = rtdb().ref(`chatMessages/${chatId}`).orderByChild("createdAt");
  if (after != null) q = q.startAt(Number(after) + 1);
  q = q.limitToFirst(n);

  const snap = await q.get();
  const obj = snap.val() || {};

  const messages = Object.entries(obj)
    .map(([id, m]) => {
      const base = { id, ...(m || {}) };
      if (base.status === "deleted") return { ...base, body: "" };
      if (base.bodyEnc && base.body == null) {
        try {
          const body = decryptBody(base.bodyEnc); // AES-256-GCM decrypt (Tony, 2023)
          return { ...base, body };
        } catch {
          return { ...base, body: "" };
        }
      }
      return base;
    })
    .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));

  const nextAfter = messages.length ? messages[messages.length - 1].createdAt : null;
  return { messages, nextAfter };
}

/**
 * Send a chat message.
 * Encrypts message body using AES-256-GCM (Tony, 2023) and updates mirrors (Firebase, 2019d).
 * Notifies recipient via FCM (Firebase, 2019c).
 */
export async function sendChatMessage({ fromUid, toUid, body }) {
  const f = String(fromUid || "").trim();
  const t = String(toUid || "").trim();
  const text = String(body || "").trim();
  if (!f || !t || !text) throw new Error("sendChatMessage: fromUid, toUid, body required"); // (Manico & Detlefsen, 2015)

  const chatId = await ensureChat(f, t);
  const now = Date.now();

  // Encrypt plaintext body (Tony, 2023)
  const bodyEnc = encryptBody(text);

  const msgRef = rtdb().ref(`chatMessages/${chatId}`).push();
  const msg = {
    chatId,
    fromUid: f,
    toUid: t,
    bodyEnc,
    preview: text.length > 120 ? text.slice(0, 117) + "..." : text,
    createdAt: now,
    updatedAt: now,
    status: "sent",
  };
  await msgRef.set(msg);

  // update chat metadata + user mirrors (Firebase, 2019d)
  const last = { ...msg, id: msgRef.key };
  const updates = {};
  updates[`chats/${chatId}/lastMessage`] = last;
  updates[`chats/${chatId}/updatedAt`] = now;
  updates[`userChats/${f}/${chatId}/lastMessage`] = last;
  updates[`userChats/${f}/${chatId}/updatedAt`] = now;
  updates[`userChats/${t}/${chatId}/lastMessage`] = last;
  updates[`userChats/${t}/${chatId}/updatedAt`] = now;
  await rtdb().ref().update(updates);

  // Firestore + FCM notify (best-effort) (Firebase, 2019a; 2019c)
  try {
    const { displayName, username } = await notify.getUserProfile(f);
    const title = displayName || username || "New message";
    const preview = msg.preview;

    await notify.createFirestoreNotification(t, {
      type: "chat_message",
      fromUid: f,
      fromName: displayName,
      fromUsername: username,
      message: preview,
      chatId,
    });

    await notify.sendPushToUser(t, {
      title,
      body: preview,
      data: {
        type: "chat_message",
        chatId: String(chatId),
        fromUid: String(f),
      },
    });
  } catch (e) {
    console.error("[sendChatMessage] notify failed:", e?.message || e);
  }

  return { id: msgRef.key, ...msg, body: text };
}

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

