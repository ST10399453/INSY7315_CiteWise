import { db, admin } from "./firebaseAdmin.js";

const COLLECTION = "ServiceReviews";
// Using Firestore server-side timestamp helper (Firebase, 2019a)
const TS = () => admin.firestore.FieldValue.serverTimestamp();

// Allowed status flow:
// Submitted -> Assigned -> In Review -> (Feedback | Pending | Failed)
// Pending -> Assigned (via resubmit)
// Cancel: from any state (by admin, consultant on own assigned item, or owner)
// State-machine style transitions & role checks for integrity (Manico & Detlefsen, 2015)

// ─────────────────────────────────────────────────────────────────────────────
// Create new request with root-level documentId
// ─────────────────────────────────────────────────────────────────────────────
// Firestore collection writes (Firebase, 2019a)
export async function createRequest(data) {
  const documentId = data.documentId || data.file?.fileId || null;

  const payload = {
    ...data,
    documentId,
    status: data.status || "Pending",
    createdAt: TS(), // serverTimestamp for write time (Firebase, 2019a)
    updatedAt: TS(),
  };

  const doc = await db.collection(COLLECTION).add(payload);
  return { id: doc.id, documentId, status: payload.status };
}

// ─────────────────────────────────────────────────────────────────────────────
// Firestore query with conditional filters & ordering (Firebase, 2019a)
// ─────────────────────────────────────────────────────────────────────────────
export async function getRequests({ status, userId, consultantId, sort, dir }) {
  let q = db.collection(COLLECTION);

  // if (userId) q = q.where("userId", "==", userId);            // (Firebase, 2019a)
  // if (consultantId) q = q.where("consultantId", "==", consultantId);// (Firebase, 2019a)
  if(consultantId){
    q=q.where("consultantId", "==", consultantId);
  }else if(userId){
    q=q.where("userId", "==", userId)
  }
  if (status) q = q.where("status", "==", status);            // (Firebase, 2019a)

  if (sort === "updatedAt" || sort === "createdAt") {
    const direction =
      String(dir || "desc").toLowerCase() === "asc" ? "asc" : "desc";
    try {
      q = q.orderBy(sort, direction);                         // (Firebase, 2019a)
    } catch (e) {
      console.warn(
        "[getRequests] orderBy failed, returning unordered:",
        e.message
      );
    }
  }

  const snap = await q.get();                                 // (Firebase, 2019a)
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

// Single document read (Firebase, 2019a)
export async function getRequestById(id) {
  const ref = db.collection(COLLECTION).doc(id);
  const doc = await ref.get();
  if (!doc.exists) throw new Error("Request not found");
  return { id: doc.id, ...doc.data() };
}

/**
 * Find a request by root-level `documentId`. Falls back to legacy `file.fileId`
 * so older documents still work.
 */
// Indexed equality queries with limit (Firebase, 2019a)
export async function getRequestByDocumentId(documentId) {
  const id = String(documentId);

  // Preferred: root-level documentId
  let snap = await db
    .collection(COLLECTION)
    .where("documentId", "==", id) // (Firebase, 2019a)
    .limit(1)
    .get();

  if (!snap.empty) {
    const d = snap.docs[0];
    return { id: d.id, ...d.data() };
  }

  // Legacy fallback
  snap = await db
    .collection(COLLECTION)
    .where("file.fileId", "==", id)  // (Firebase, 2019a)
    .limit(1)
    .get();

  if (!snap.empty) {
    const d = snap.docs[0];
    return { id: d.id, ...d.data() };
  }

  return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Transitions
// ─────────────────────────────────────────────────────────────────────────────
// ACID-like updates via Firestore transactions (Firebase, 2019a)
// Authorization & business-rule enforcement at write time (Manico & Detlefsen, 2015)
export async function transitionAssign({
  id,
  consultantId,
  deadline = null,
  allowUpdate = false,
  actor,
}) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {                 // (Firebase, 2019a)
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error("Request not found");
    const r = snap.data();

    if (allowUpdate) {
      if (r.status !== "Assigned")
        throw new Error("Can only update when status is Assigned"); // invariant guard (Manico & Detlefsen, 2015)
      const updates = {
        ...(consultantId ? { consultantId } : {}),
        ...(deadline !== undefined ? { deadline } : {}),
        updatedAt: TS(),
      };
      tx.update(ref, updates);                             // (Firebase, 2019a)
      return { id, ...r, ...updates };
    }

    if (r.status !== "Submitted" && r.status !== "Pending") {
      throw new Error("Only Submitted or Pending requests can be assigned");
    }
    if (!consultantId) throw new Error("consultantId is required");

    const updates = {
      consultantId,
      deadline: deadline ?? null,
      status: "Assigned",
      updatedAt: TS(),
    };
    tx.update(ref, updates);                               // (Firebase, 2019a)
    return { id, ...r, ...updates };
  });
}

export async function transitionStartReview({ id, actor }) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {                 // (Firebase, 2019a)
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error("Request not found");
    const r = snap.data();

    if (r.status !== "Assigned")
      throw new Error("Only Assigned requests can move to In Review");

    const actorId = actor.uid;
    const role = actor.role;
    if (!(role === "admin" || r.consultantId === actorId)) {
      throw new Error("Only assigned consultant or admin can start review"); // RBAC guard (Manico & Detlefsen, 2015)
    }

    const updates = { status: "In Review", updatedAt: TS() };
    tx.update(ref, updates);                               // (Firebase, 2019a)
    return { id, ...r, ...updates };
  });
}

export async function transitionSubmitReview({
  id,
  outcome,
  feedback = null,
  actor,
}) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {                 // (Firebase, 2019a)
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error("Request not found");
    const r = snap.data();

    if (r.status !== "In Review")
      throw new Error("Only In Review requests can be completed");
    const actorId = actor.uid;
    const role = actor.role;
    if (!(role === "admin" || r.consultantId === actorId)) {
      throw new Error("Only assigned consultant or admin can submit review"); // RBAC guard (Manico & Detlefsen, 2015)
    }

    let nextStatus;
    if (outcome === "approve") nextStatus = "Feedback";
    else if (outcome === "reject") nextStatus = "Pending";
    else if (outcome === "fail") nextStatus = "Failed";
    else throw new Error("Invalid outcome");

    const updates = {
      status: nextStatus,
      feedback: feedback ?? null,
      updatedAt: TS(),
    };
    tx.update(ref, updates);                               // (Firebase, 2019a)
    return { id, ...r, ...updates };
  });
}

export async function transitionResubmit({ id, actor }) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {                 // (Firebase, 2019a)
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error("Request not found");
    const r = snap.data();

    if (r.status !== "Pending")
      throw new Error("Only Pending requests can be resubmitted");
    if (!(actor.role === "admin" || actor.uid === r.userId)) {
      throw new Error("Only owner (student) or admin can resubmit"); // ownership check (Manico & Detlefsen, 2015)
    }

    const updates = { status: "Assigned", updatedAt: TS() };
    tx.update(ref, updates);                               // (Firebase, 2019a)
    return { id, ...r, ...updates };
  });
}

export async function transitionCancel({ id, actor }) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {                 // (Firebase, 2019a)
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error("Request not found");
    const r = snap.data();

    const actorId = actor.uid;
    const role = actor.role;
    const isOwner = actorId === r.userId;
    const isAssignedConsultant = r.consultantId && r.consultantId === actorId;

    if (!(role === "admin" || isOwner || isAssignedConsultant)) {
      throw new Error("Only admin, owner, or assigned consultant can cancel"); // authorization guard (Manico & Detlefsen, 2015)
    }

    const updates = { status: "Cancelled", updatedAt: TS() };
    tx.update(ref, updates);                               // (Firebase, 2019a)
    return { id, ...r, ...updates };
  });
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
