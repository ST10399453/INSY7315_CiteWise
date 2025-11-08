import { db as fsdb } from "../db/firebaseAdmin.js";

export async function attachRole(req, res, next) {
  try {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ message: "Unauthorized" });

    const snap = await fsdb.collection("users").doc(uid).get();
    const role = (snap.exists && (snap.data()?.role || "")) || "";
    req.user.role = String(role).toLowerCase(); // normalize to lowercase
    return next();
  } catch (e) {
    console.error("[attachRole] failed", e);
    return res.status(500).json({ message: "Role lookup failed" });
  }
}