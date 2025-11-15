import admin from "../db/firebaseAdmin.js";
import { getFirestore } from "firebase-admin/firestore";

/**
 * Middleware: Verifies Firebase ID token from `Authorization: Bearer <token>`.
 * Fetches user's role (from Firestore or token claims) and attaches it to `req.user`.
 * Supports lowercase roles like "admin", "student", "lecturer".
 */
export async function checkAuth(req, res, next) {
  try {
    // --- 1 Extract and verify Bearer token ---
    const h = req.headers.authorization || "";
    const m = h.match(/^Bearer (.+)$/i);
    if (!m) {
      return res.status(401).json({ message: "Missing Bearer token" });
    }

    const idToken = m[1];
    const decoded = await admin.auth().verifyIdToken(idToken, true);

    // --- 2️ Validate that token belongs to this Firebase project ---
    const projectId = process.env.FIREBASE_PROJECT_ID;
    if (projectId) {
      const expectedIss = `https://securetoken.google.com/${projectId}`;
      if (decoded.iss !== expectedIss || decoded.aud !== projectId) {
        console.error("[AUTH] Issuer/Audience mismatch", {
          iss: decoded.iss,
          aud: decoded.aud,
          expectedIss,
          expectedAud: projectId,
        });
        return res.status(401).json({ message: "Token not for this Firebase project" });
      }
    }

    // --- 3️ Try to load the user's role from Firestore ---
    const db = getFirestore();
    let role = null;

    try {
      const userDoc = await db.collection("users").doc(decoded.uid).get();
      if (userDoc.exists) {
        role = userDoc.data().role || null;
      }
    } catch (err) {
      console.warn("[AUTH] Could not fetch role from Firestore:", err.message);
    }

    // --- 4️ Fallback to custom claim if Firestore role not found ---
    if (!role && decoded.role) {
      role = decoded.role;
    }

    // --- 5️ Normalize role ---
    if (role) role = String(role).toLowerCase();

    // Default role for safety
    if (!role) role = "student";

    // --- 6️ Attach to request for route access checks ---
    req.user = {
      uid: decoded.uid,
      email: decoded.email || null,
      claims: decoded,
      role,
    };

    console.log(`[AUTH] ✅ ${req.user.uid} authenticated as "${req.user.role}"`);
    return next();
  } catch (err) {
    console.error("[AUTH] ❌ Token verification failed:", {
      code: err.code,
      message: err.message,
      name: err.name,
    });
    return res.status(401).json({ message: "Unauthorized" });
  }
}
