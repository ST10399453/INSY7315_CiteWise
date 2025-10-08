import { admin, db } from '../db/firebaseAdmin.js';

// Reads role from RTDB at /users/{uid}/role
export async function getUserRole(uid) {
  const snap = await rtdb.ref(`/users/${uid}/role`).get();
  return snap.exists() ? snap.val() : undefined;
}

export async function checkAuth(req, res, next) {
  try {
    const h = req.headers.authorization || '';
    if (!h.startsWith('Bearer ')) {
      return res.status(401).json({ message: 'Missing or invalid Authorization header' });
    }
    const token = h.split(' ')[1];
    const decoded = await admin.auth().verifyIdToken(token);
    const role = await getUserRole(decoded.uid);

    req.user = { ...decoded, role };
    next();
  } catch (e) {
    console.error('Auth error', e);
    return res.status(401).json({ message: 'Unauthorized' });
  }
}

// export async function checkAuth(req, res, next) {
//   // TEMPORARY AUTH BYPASS FOR TESTING ONLY
//   // Simulate a logged-in student user
//   req.user = {
//     uid: 'cgKio13iN0Rt4c9pd4GdNJTJtEj2', // example UID from your Realtime DB
//     role: 'student',
//     email: 'ethan.huntley@gmail.com'
//   };
//   return next();
// }

