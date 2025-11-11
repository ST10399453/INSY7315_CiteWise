import admin from "../db/firebaseAdmin.js" // (Firebase, 2019a; 2019b; 2019c)

/** Small wrapper class around notification helpers (Firestore + FCM). */
class NotifyService {
  constructor() {
    this.fs = admin.firestore() // Firestore (Firebase, 2019a)
    this.auth = admin.auth() // Auth for user lookups (Firebase, 2019b)
    this.messaging = admin.messaging() // FCM server SDK (Firebase, 2019c)
  }

  /** Firestore DB handle (convenience) */
  fsdb() {
    return this.fs // (Firebase, 2019a)
  }

  /**
   * Create Notifications/{userId}/items/{autoId}
   * NOTE: We only use this for message events.
   */
  async createFirestoreNotification(userId, { type, fromUid, message, fromName, fromUsername, chatId }) {
    const uid = String(userId || "").trim()
    if (!uid) throw new Error("createFirestoreNotification: userId required") // defensive check (Manico & Detlefsen, 2015)

    const ref = this.fs.collection("Notifications").doc(uid).collection("items").doc() // auto-id (Firebase, 2019a)

    const payload = {
      type: String(type || "notification"), // e.g. "chat_message"
      fromUid: String(fromUid || ""),
      message: String(message || ""),
      ...(chatId ? { chatId: String(chatId) } : {}),
      ...(fromName ? { fromName: String(fromName) } : {}),
      ...(fromUsername ? { fromUsername: String(fromUsername) } : {}),
      createdAt: admin.firestore.FieldValue.serverTimestamp(), // server time (Firebase, 2019a)
      read: false,
    }

    await ref.set(payload) // write to subcollection (Firebase, 2019a)
    return { id: ref.id, ...payload }
  }

  /**
   * Get all FCM device tokens stored under users/{uid}/fcmTokens/{tokenId}
   */
  async getUserDeviceTokens(userId) {
    const uid = String(userId || "").trim()
    if (!uid) return []
    const col = this.fs.collection("users").doc(uid).collection("fcmTokens") // token storage pattern (Firebase, 2019c)
    const docs = await col.listDocuments()
    return docs.map((d) => d.id)
  }

  /**
   * Send a push to all tokens; prunes invalid tokens automatically.
   */
  async sendPushToUser(userId, { title, body, data = {} }) {
    const uid = String(userId || "").trim()
    if (!uid) return { sent: 0, pruned: 0 }

    const tokensCol = this.fs.collection("users").doc(uid).collection("fcmTokens") // (Firebase, 2019c)
    const tokens = await this.getUserDeviceTokens(uid)

    console.log(`[FCM] Sending to user ${uid}, tokens found: ${tokens.length}`)

    if (!tokens.length) return { sent: 0, pruned: 0 }

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
        }).map(([k, v]) => [k, v == null ? "" : String(v)]),
      ), // data payload normalization (Firebase, 2019c)
      android: {
        priority: "high",
        ttl: 60 * 60 * 1000, // 1 hour (Firebase, 2019c)
        notification: {
          sound: "default",
          channelId: "messages", // must match CHANNEL_MESSAGES in AppMessagingService
        },
      },
    }

    console.log(`[FCM] Sending message:`, JSON.stringify({ title, body, tokenCount: tokens.length, data }))

    const res = await this.messaging.sendEachForMulticast(message) // multicast send (Firebase, 2019c)

    console.log(`[FCM] Send result: success=${res.successCount}, failures=${res.failureCount}`)
    if (res.failureCount > 0) {
      res.responses.forEach((r, i) => {
        if (!r.success) {
          console.error(`[FCM] Failed to send to token ${i}:`, r.error?.message)
        }
      })
    }

    // prune invalid tokens
    const toDelete = []
    res.responses.forEach((r, i) => {
      if (!r.success) toDelete.push(tokens[i]) // handle invalid/unregistered tokens (Firebase, 2019c)
    })
    await Promise.all(
      toDelete.map((t) =>
        tokensCol
          .doc(t)
          .delete()
          .catch(() => {}),
      ),
    )

    return { sent: res.successCount, pruned: toDelete.length }
  }

  /**
   * Look up a user's display name and username from Firebase Auth.
   */
  async getUserProfile(uid) {
    try {
      const rec = await this.auth.getUser(String(uid)) // Admin SDK user fetch (Firebase, 2019b)
      const displayName = rec.displayName || rec.customClaims?.displayName || ""
      const username =
        rec.customClaims?.username || (rec.email ? rec.email.split("@")[0] : "") || rec.phoneNumber || String(uid)

      return {
        displayName: displayName || username || String(uid),
        username: username || String(uid),
      }
    } catch {
      return { displayName: String(uid), username: String(uid) } // safe fallback (Manico & Detlefsen, 2015)
    }
  }
}

// export a ready-to-use singleton and the class
export const notify = new NotifyService()
export default NotifyService

/*
REFERENCES

Android Knowledge. 2023. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
YouTube. August 2023 <https://www.youtube.com/watch?v=oGyQMBKPuNY> [accessed September 2025].

Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
Medium. January 2024 <https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494> [accessed September 2025].

Axios. 2023. “Getting Started | Axios Docs”.
Axios-Http.com. 2023 <https://axios-http.com/docs/intro> [accessed October 2025].

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
