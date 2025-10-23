import admin from "../db/firebaseAdmin.js";

/** Small wrapper class around notification helpers (Firestore + FCM). */
class NotifyService {
  constructor() {
    this.fs = admin.firestore();
    this.auth = admin.auth();
    this.messaging = admin.messaging();
  }

  /** Firestore DB handle (convenience) */
  fsdb() {
    return this.fs;
  }

  /**
   * Create Notifications/{userId}/items/{autoId}
   * NOTE: We only use this for message events.
   */
  async createFirestoreNotification(
    userId,
    { type, fromUid, message, fromName, fromUsername, chatId }
  ) {
    const uid = String(userId || "").trim();
    if (!uid) throw new Error("createFirestoreNotification: userId required");

    const ref = this.fs
      .collection("Notifications")
      .doc(uid)
      .collection("items")
      .doc();

    const payload = {
      type: String(type || "notification"), // e.g. "chat_message"
      fromUid: String(fromUid || ""),
      message: String(message || ""),
      ...(chatId ? { chatId: String(chatId) } : {}),
      ...(fromName ? { fromName: String(fromName) } : {}),
      ...(fromUsername ? { fromUsername: String(fromUsername) } : {}),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      read: false,
    };

    await ref.set(payload);
    return { id: ref.id, ...payload };
  }

  /**
   * Get all FCM device tokens stored under users/{uid}/fcmTokens/{tokenId}
   */
  async getUserDeviceTokens(userId) {
    const uid = String(userId || "").trim();
    if (!uid) return [];
    const col = this.fs.collection("users").doc(uid).collection("fcmTokens");
    const docs = await col.listDocuments();
    return docs.map((d) => d.id);
  }

  /**
   * Send a push to all tokens; prunes invalid tokens automatically.
   */
  async sendPushToUser(userId, { title, body, data = {} }) {
    const uid = String(userId || "").trim();
    if (!uid) return { sent: 0, pruned: 0 };

    const tokensCol = this.fs.collection("users").doc(uid).collection("fcmTokens");
    const tokens = await this.getUserDeviceTokens(uid);
    if (!tokens.length) return { sent: 0, pruned: 0 };

    const message = {
      tokens,
      notification: {
        title: title ?? "Notification",
        body: body ?? "",
      },
      data: Object.fromEntries(
        Object.entries({
          title: title ?? "Notification",
          body: body ?? "",
          ...data,
        }).map(([k, v]) => [k, v == null ? "" : String(v)])
      ),
      android: {
        priority: "high",
        ttl: 60 * 60 * 1000, // 1 hour
        notification: {
          sound: "default",
          channelId: "citewise-general", // must exist in the Android app
        },
      },
    };

    const res = await this.messaging.sendEachForMulticast(message);

    // prune invalid tokens
    const toDelete = [];
    res.responses.forEach((r, i) => {
      if (!r.success) toDelete.push(tokens[i]);
    });
    await Promise.all(toDelete.map((t) => tokensCol.doc(t).delete().catch(() => {})));

    return { sent: res.successCount, pruned: toDelete.length };
  }

  /**
   * Look up a user's display name and username from Firebase Auth.
   */
  async getUserProfile(uid) {
    try {
      const rec = await this.auth.getUser(String(uid));
      const displayName = rec.displayName || rec.customClaims?.displayName || "";
      const username =
        rec.customClaims?.username ||
        (rec.email ? rec.email.split("@")[0] : "") ||
        rec.phoneNumber ||
        String(uid);

      return {
        displayName: displayName || username || String(uid),
        username: username || String(uid),
      };
    } catch {
      return { displayName: String(uid), username: String(uid) };
    }
  }
}

// export a ready-to-use singleton and the class
export const notify = new NotifyService();
export default NotifyService;
