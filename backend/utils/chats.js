import admin from "../db/firebaseAdmin.js";
import { notify } from "./notify.js";
import crypto from "crypto";

/** RTDB handle */
export function rtdb() {
  return admin.database();
}

/** === Encryption helpers (AES-256-GCM) === */
const ENC_ALGO = "aes-256-gcm";
function getMsgKey() {
  const b64 = process.env.CITEWISE_MSG_KEY_B64;
  const hex = process.env.CITEWISE_MSG_KEY_HEX;
  if (!b64 && !hex) {
    throw new Error("Missing CITEWISE_MSG_KEY_B64 or CITEWISE_MSG_KEY_HEX");
  }
  return b64 ? Buffer.from(b64, "base64") : Buffer.from(hex, "hex");
}

/** Encrypt text -> { ct, iv, tag, alg, v } (Base64-encoded) */
export function encryptBody(plaintext) {
  const key = getMsgKey();
  const iv = crypto.randomBytes(12); // 96-bit nonce recommended for GCM
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
  const key = getMsgKey();
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
  if (!a || !b) throw new Error("chatIdFor: both user IDs are required");
  return [a, b].sort().join("_");
}

/**
 * Ensure chat metadata exists:
 *  - /chats/{chatId}: { id, users:{uid:true}, createdAt, updatedAt, lastMessage? }
 *  - /userChats/{uid}/{chatId}: { chatId, otherUid, updatedAt, lastMessage? }
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

  // mirror entries for listing
  const updates = {};
  updates[`userChats/${u1}/${chatId}`] = { chatId, otherUid: u2, updatedAt: now };
  updates[`userChats/${u2}/${chatId}`] = { chatId, otherUid: u1, updatedAt: now };
  await rtdb().ref().update(updates);

  return chatId;
}

/** Check if a user participates in a chat */
export async function isParticipant(chatId, uid) {
  const usersSnap = await rtdb().ref(`chats/${chatId}/users`).get();
  const users = usersSnap.val() || {};
  return Boolean(users && users[String(uid)]);
}

/**
 * Retrieve messages in chronological order (oldest → newest) by createdAt.
 * Pagination: pass `after` (ms) to start strictly after that timestamp.
 * Returns: { messages, nextAfter }
 * Decrypts `bodyEnc` if present; falls back to legacy `body`.
 */
export async function getChatMessagesChrono(chatId, { limit = 100, after } = {}) {
  const n = Math.max(1, Math.min(Number(limit) || 100, 500));

  let q = rtdb().ref(`chatMessages/${chatId}`).orderByChild("createdAt");
  if (after != null) q = q.startAt(Number(after) + 1); // strictly after
  q = q.limitToFirst(n);

  const snap = await q.get();
  const obj = snap.val() || {};

  const messages = Object.entries(obj)
    .map(([id, m]) => {
      const base = { id, ...(m || {}) };
      // If deleted, leave body empty
      if (base.status === "deleted") return { ...base, body: "" };
      // Prefer encrypted payload
      if (base.bodyEnc && base.body == null) {
        try {
          const body = decryptBody(base.bodyEnc);
          return { ...base, body };
        } catch {
          // On any decrypt error, return placeholder
          return { ...base, body: "" };
        }
      }
      // Legacy fallback (plain body existed before encryption rollout)
      return base;
    })
    .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));

  const nextAfter = messages.length ? messages[messages.length - 1].createdAt : null;
  return { messages, nextAfter };
}

/**
 * Send a chat message (writes to RTDB and bumps mirrors), then notifies recipient.
 * DB stores only encrypted body (bodyEnc). Plaintext is NOT stored.
 */
export async function sendChatMessage({ fromUid, toUid, body }) {
  const f = String(fromUid || "").trim();
  const t = String(toUid || "").trim();
  const text = String(body || "").trim();
  if (!f || !t || !text) throw new Error("sendChatMessage: fromUid, toUid, body required");

  const chatId = await ensureChat(f, t);
  const now = Date.now();

  // Encrypt plaintext body
  const bodyEnc = encryptBody(text);

  const msgRef = rtdb().ref(`chatMessages/${chatId}`).push();
  const msg = {
    chatId,
    fromUid: f,
    toUid: t,
    // body is intentionally omitted to avoid storing plaintext
    bodyEnc,                // { v, alg, iv, ct, tag } Base64 fields
    preview: text.length > 120 ? text.slice(0, 117) + "..." : text, // small UI hint
    createdAt: now,
    updatedAt: now,
    status: "sent",
  };
  await msgRef.set(msg);

  // update chat metadata + user mirror entries (use preview only)
  const last = { ...msg, id: msgRef.key };
  const updates = {};
  updates[`chats/${chatId}/lastMessage`] = last;
  updates[`chats/${chatId}/updatedAt`] = now;
  updates[`userChats/${f}/${chatId}/lastMessage`] = last;
  updates[`userChats/${f}/${chatId}/updatedAt`] = now;
  updates[`userChats/${t}/${chatId}/lastMessage`] = last;
  updates[`userChats/${t}/${chatId}/updatedAt`] = now;
  await rtdb().ref().update(updates);

  // Firestore + FCM notify (best-effort; errors do not fail send)
  try {
    const { displayName, username } = await notify.getUserProfile(f);
    const title = displayName || username || "New message";
    const preview = msg.preview;

    await notify.createFirestoreNotification(t, {
      type: "chat_message",       // ONLY message notifications
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
