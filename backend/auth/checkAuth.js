// auth/checkAuth.js
import admin from '../firebaseAdmin.js';

export async function checkAuth(req, res, next) {
  try {
    const h = req.headers.authorization || '';
    const m = h.match(/^Bearer (.+)$/i);
    if (!m) return res.status(401).json({ message: 'Missing Bearer token' });

    const idToken = m[1];
    // verify and get claims
    const decoded = await admin.auth().verifyIdToken(idToken, true);

    // Optional: lock to the expected project issuer
    const expectedIss = `https://securetoken.google.com/${process.env.FIREBASE_PROJECT_ID}`;
    if (decoded.iss !== expectedIss || decoded.aud !== process.env.FIREBASE_PROJECT_ID) {
      console.error('[AUTH] Issuer/Audience mismatch', { iss: decoded.iss, aud: decoded.aud, expectedIss, expectedAud: process.env.FIREBASE_PROJECT_ID });
      return res.status(401).json({ message: 'Token not for this Firebase project' });
    }

    req.user = { uid: decoded.uid, email: decoded.email || null, claims: decoded };
    return next();
  } catch (err) {
    console.error('[AUTH] verifyIdToken failed', {
      code: err.code,
      message: err.message,
      name: err.name,
    });
    return res.status(401).json({ message: 'Unauthorized' });
  }
}
