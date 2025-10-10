// auth/checkAuth.js
import admin from '../db/firebaseAdmin.js';

/**
 * Verifies Firebase ID token from `Authorization: Bearer <token>`.
 * Attaches { uid, email, claims } to req.user on success.
 * Logs precise failure reasons for debugging.
 */
export async function checkAuth(req, res, next) {
  try {
    const h = req.headers.authorization || '';
    const m = h.match(/^Bearer (.+)$/i);
    if (!m) {
      return res.status(401).json({ message: 'Missing Bearer token' });
    }

    const idToken = m[1];
    const decoded = await admin.auth().verifyIdToken(idToken, true);

    // Optional: strongly assert token belongs to THIS Firebase project
    const projectId = process.env.FIREBASE_PROJECT_ID;
    if (projectId) {
      const expectedIss = `https://securetoken.google.com/${projectId}`;
      if (decoded.iss !== expectedIss || decoded.aud !== projectId) {
        console.error('[AUTH] Issuer/Audience mismatch', {
          iss: decoded.iss, aud: decoded.aud, expectedIss, expectedAud: projectId
        });
        return res.status(401).json({ message: 'Token not for this Firebase project' });
      }
    }

    req.user = { uid: decoded.uid, email: decoded.email || null, claims: decoded };
    return next();
  } catch (err) {
    console.error('[AUTH] verifyIdToken failed', {
      code: err.code, message: err.message, name: err.name
    });
    return res.status(401).json({ message: 'Unauthorized' });
  }
}
