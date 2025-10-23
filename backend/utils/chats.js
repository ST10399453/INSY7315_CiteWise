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
 */
export async function getChatMessagesChrono(chatId, { limit = 100, after } = {}) {
  const n = Math.max(1, Math.min(Number(limit) || 100, 500));

  let q = rtdb().ref(`chatMessages/${chatId}`).orderByChild("createdAt");
  if (after != null) q = q.startAt(Number(after) + 1); // strictly after
  q = q.limitToFirst(n);

  const snap = await q.get();
  const obj = snap.val() || {};

  // Ensure ascending sort; RTDB returns sorted but we enforce correctness
  const messages = Object.entries(obj)
    .map(([id, m]) => ({ id, ...(m || {}) }))
    .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));

  const nextAfter = messages.length ? messages[messages.length - 1].createdAt : null;
  return { messages, nextAfter };
}

/**
 * Send a chat message (writes to RTDB and bumps mirrors), then notifies recipient.
 * Message shape:
 *  id (push key), chatId, fromUid, toUid, body, createdAt, updatedAt, status: 'sent'
 * Side-effects:
 *  - Firestore notification: type "chat_message" to recipient only
 *  - FCM push to recipient tokens
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

  // Firestore + FCM notify (best-effort; errors do not fail send)
  try {
    const { displayName, username } = await notify.getUserProfile(f);
    const title = displayName || username || "New message";
    const preview = text.length > 120 ? text.slice(0, 117) + "..." : text;

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

  return { id: msgRef.key, ...msg };
}

