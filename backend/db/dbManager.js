import { db, admin } from './firebaseAdmin.js';

const COLLECTION = 'ServiceReviews';
const TS = () => admin.firestore.FieldValue.serverTimestamp();

// Allowed status flow:
// Submitted -> Assigned -> In Review -> (Feedback | Pending | Failed)
// Pending -> Assigned (via resubmit)
// Cancel: from any state (by admin, consultant on own assigned item, or owner)

export async function createRequest(data) {
  const doc = await db.collection(COLLECTION).add({
    ...data,
    status: 'Pending',
    createdAt: TS(),
    updatedAt: TS(),
  });
  // Return what we actually stored
  return { id: doc.id, status: 'Pending' };
}

export async function getRequests({ status, userId, consultantId, sort, dir }) {
  let q = db.collection(COLLECTION);

  if (userId)       q = q.where('userId', '==', userId);
  if (consultantId) q = q.where('consultantId', '==', consultantId);
  if (status)       q = q.where('status', '==', status);

  // Optional ordering (may require composite index depending on filters)
  if (sort === 'updatedAt' || sort === 'createdAt') {
    const direction =
      String(dir || 'desc').toLowerCase() === 'asc' ? 'asc' : 'desc';
    try {
      q = q.orderBy(sort, direction);
    } catch (e) {
      // If missing index, return without ordering rather than hard-failing
      console.warn(
        '[getRequests] orderBy failed (likely missing index). Returning unordered.',
        e.message
      );
    }
  }

  const snap = await q.get();
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export async function getRequestById(id) {
  const ref = db.collection(COLLECTION).doc(id);
  const doc = await ref.get();
  if (!doc.exists) throw new Error('Request not found');
  return { id: doc.id, ...doc.data() };
}

// ── Transitions ──────────────────────────────────────────────────────────────
export async function transitionAssign({
  id,
  consultantId,
  deadline = null,
  allowUpdate = false,
  actor,
}) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error('Request not found');
    const r = snap.data();

    if (allowUpdate) {
      if (r.status !== 'Assigned')
        throw new Error('Can only update when status is Assigned');
      const updates = {
        ...(consultantId ? { consultantId } : {}),
        ...(deadline !== undefined ? { deadline } : {}),
        updatedAt: TS(),
      };
      tx.update(ref, updates);
      return { id, ...r, ...updates };
    }

    // initial assign: Submitted/Pending -> Assigned
    if (r.status !== 'Submitted' && r.status !== 'Pending') {
      throw new Error('Only Submitted or Pending requests can be assigned');
    }
    if (!consultantId) throw new Error('consultantId is required');

    const updates = {
      consultantId,
      deadline: deadline ?? null,
      status: 'Assigned',
      updatedAt: TS(),
    };
    tx.update(ref, updates);
    return { id, ...r, ...updates };
  });
}

export async function transitionStartReview({ id, actor }) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error('Request not found');
    const r = snap.data();

    if (r.status !== 'Assigned')
      throw new Error('Only Assigned requests can move to In Review');

    const actorId = actor.uid;
    const role = actor.role;
    if (!(role === 'admin' || r.consultantId === actorId)) {
      throw new Error('Only assigned consultant or admin can start review');
    }

    const updates = { status: 'In Review', updatedAt: TS() };
    tx.update(ref, updates);
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
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error('Request not found');
    const r = snap.data();

    if (r.status !== 'In Review')
      throw new Error('Only In Review requests can be completed');
    const actorId = actor.uid;
    const role = actor.role;
    if (!(role === 'admin' || r.consultantId === actorId)) {
      throw new Error('Only assigned consultant or admin can submit review');
    }

    let nextStatus;
    if (outcome === 'approve') nextStatus = 'Feedback';
    else if (outcome === 'reject') nextStatus = 'Pending';
    else if (outcome === 'fail') nextStatus = 'Failed';
    else throw new Error('Invalid outcome');

    const updates = {
      status: nextStatus,
      feedback: feedback ?? null,
      updatedAt: TS(),
    };
    tx.update(ref, updates);
    return { id, ...r, ...updates };
  });
}

export async function transitionResubmit({ id, actor }) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error('Request not found');
    const r = snap.data();

    if (r.status !== 'Pending')
      throw new Error('Only Pending requests can be resubmitted');
    if (!(actor.role === 'admin' || actor.uid === r.userId)) {
      throw new Error('Only owner (student) or admin can resubmit');
    }

    const updates = { status: 'Assigned', updatedAt: TS() };
    tx.update(ref, updates);
    return { id, ...r, ...updates };
  });
}

export async function transitionCancel({ id, actor }) {
  const ref = db.collection(COLLECTION).doc(id);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new Error('Request not found');
    const r = snap.data();

    const actorId = actor.uid;
    const role = actor.role;

    const isOwner = actorId === r.userId;
    const isAssignedConsultant = r.consultantId && r.consultantId === actorId;

    if (!(role === 'admin' || isOwner || isAssignedConsultant)) {
      throw new Error('Only admin, owner, or assigned consultant can cancel');
    }

    const updates = { status: 'Cancelled', updatedAt: TS() };
    tx.update(ref, updates);
    return { id, ...r, ...updates };
  });
}
