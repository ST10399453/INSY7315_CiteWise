import { db as fsdb } from "../db/firebaseAdmin.js";

export async function attachRole(req, _res, next) {
  try {
    const uid = req.user?.uid;
    if (!uid) return next();
    const snap = await fsdb.collection("users").doc(uid).get();
    const role = String(snap.data()?.role || "").toLowerCase();
    req.user.role = role;
    console.log("attachRole:", { uid, role });
  } catch (e) {
    console.error("attachRole error:", e);
  }
  next();
}