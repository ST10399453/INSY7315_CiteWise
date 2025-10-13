// backend/utils/chats.js
import admin from "../db/firebaseAdmin.js";
import { notify } from "./notify.js";

/** RTDB handle */
export function rtdb() {
  return admin.database();
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

/**
 * List user chats from /userChats/{uid}. Optional pagination via startAfter (chatId).
 * Returns: { chats, nextPageToken }
 */
export async function getUserChats(uid, { limit = 50, startAfter } = {}) {
  const n = Math.max(1, Math.min(Number(limit) || 50, 200));

  let q = rtdb().ref(`userChats/${uid}`).orderByKey();
  if (startAfter) q = q.startAfter(String(startAfter));
  q = q.limitToFirst(n);

  const snap = await q.get();
  const obj = snap.val() || {};
  const rows = Object.entries(obj).map(([chatId, meta]) => ({
    chatId,
    ...(meta || {}),
  }));

  const nextPageToken = rows.length ? rows[rows.length - 1].chatId : null;
  return { chats: rows, nextPageToken };
}

/**
 * Page messages from /chatMessages/{chatId} ordered by key (push IDs).
 * Returns: { messages, nextPageToken }
 */
export async function getChatMessagesPage(chatId, { limit = 100, startAfter } = {}) {
  const n = Math.max(1, Math.min(Number(limit) || 100, 500));
  let q = rtdb().ref(`chatMessages/${chatId}`).orderByKey();
  if (startAfter) q = q.startAfter(String(startAfter));
  q = q.limitToFirst(n);

  const snap = await q.get();
  const obj = snap.val() || {};
  const messages = Object.entries(obj).map(([id, m]) => ({ id, ...(m || {}) }));
  const nextPageToken = messages.length ? messages[messages.length - 1].id : null;

  return { messages, nextPageToken };
}

/**
 * Send a chat message (writes to RTDB and bumps mirrors), then notifies recipient.
 * Message shape:
 *  id (push key), chatId, fromUid, toUid, body, createdAt, updatedAt, status: 'sent'
 */
export async function sendChatMessage({ fromUid, toUid, body }) {
  const f = String(fromUid || "").trim();
  const t = String(toUid || "").trim();
  const text = String(body || "").trim();
  if (!f || !t || !text) throw new Error("sendChatMessage: fromUid, toUid, body required");

  const chatId = await ensureChat(f, t);
  const now = Date.now();

  const msgRef = rtdb().ref(`chatMessages/${chatId}`).push();
  const msg = {
    chatId,
    fromUid: f,
    toUid: t,
    body: text,
    createdAt: now,
    updatedAt: now,
    status: "sent",
  };
  await msgRef.set(msg);

  // update chat metadata + user mirror entries
  const last = { ...msg, id: msgRef.key };
  const updates = {};
  updates[`chats/${chatId}/lastMessage`] = last;
  updates[`chats/${chatId}/updatedAt`] = now;
  updates[`userChats/${f}/${chatId}/lastMessage`] = last;
  updates[`userChats/${f}/${chatId}/updatedAt`] = now;
  updates[`userChats/${t}/${chatId}/lastMessage`] = last;
  updates[`userChats/${t}/${chatId}/updatedAt`] = now;
  await rtdb().ref().update(updates);

  // Firestore + FCM notify (best-effort; errors are logged but do not fail send)
  try {
    const { displayName, username } = await notify.getUserProfile(f);
    const title = displayName || username || "New message";
    const preview = text.length > 120 ? text.slice(0, 117) + "..." : text;

    await notify.createFirestoreNotification(t, {
      type: "chat_message",
      fromUid: f,
      fromName: displayName,
      fromUsername: username,
      message: preview,
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

  return { id: msgRef.key, ...msg };
}

/**
 * Soft-delete a message (optional helper). Keeps placeholder but clears text.
 */
export async function softDeleteMessage(chatId, messageId, actorUid) {
  const ref = rtdb().ref(`chatMessages/${chatId}/${messageId}`);
  const snap = await ref.get();
  if (!snap.exists()) return { ok: false, reason: "not_found" };

  const cur = snap.val() || {};
  if (String(cur.fromUid) !== String(actorUid)) {
    return { ok: false, reason: "not_owner" };
  }

  const now = Date.now();
  await ref.update({ status: "deleted", body: "", updatedAt: now });
  await rtdb().ref(`chats/${chatId}/updatedAt`).set(now);
  return { ok: true };
}
