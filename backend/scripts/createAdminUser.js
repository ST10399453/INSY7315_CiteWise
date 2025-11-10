/**
 * createAdminUser.js (force-create capable)
 *
 * Force-(re)creates a Firebase Auth user with a specific UID + email as an admin.
 * - If a user with the same UID exists and --force is set, deletes then recreates.
 * - If a different user already uses the target email and --force is set, deletes that user.
 * - Sets custom claims { role: 'admin', isApproved: true }.
 * - Upserts RTDB: /users/{uid} with role, isApproved, createdAt.
 *
 * Usage:
 *   node scripts/createAdminUser.js --force
 *   FORCE=1 node scripts/createAdminUser.js
 *   DRY_RUN=1 node scripts/createAdminUser.js --force
 */

import { db, admin } from "../db/firebaseAdmin.js";

const userData = {
  uid: "UKddBcEcH7FQYWEkeoc5hZV4C0es2",
  email: "greg@gmail.com",
  role: "admin",
  isApproved: true,
};

const args = process.argv.slice(2);
const FORCE = process.env.FORCE === "1" || args.includes("--force");
const DRY_RUN = process.env.DRY_RUN === "1" || args.includes("--dry-run");
// Optional explicit password (otherwise randomized)
const EXPLICIT_PASSWORD = process.env.PASSWORD || null;

function randPassword() {
  // 12 chars, includes letters & digits
  return Math.random().toString(36).slice(-12) + Math.floor(Math.random() * 10);
}

async function ensureDeleted(uid, reason) {
  console.log(`🗑️  Deleting user uid=${uid} (${reason})`);
  if (DRY_RUN) return;
  try {
    await admin.auth().deleteUser(uid);
  } catch (e) {
    if (e.code === "auth/user-not-found") return;
    throw e;
  }
}

async function main() {
  console.log(`👤 Force-create admin: uid=${userData.uid}, email=${userData.email}`);
  console.log(`   FORCE=${FORCE ? "true" : "false"} DRY_RUN=${DRY_RUN ? "true" : "false"}`);

  // 1) Look up current users by UID and by email
  let byUid = null;
  let byEmail = null;

  try {
    byUid = await admin.auth().getUser(userData.uid);
  } catch (e) {
    if (e.code !== "auth/user-not-found") throw e;
  }

  try {
    byEmail = await admin.auth().getUserByEmail(userData.email);
  } catch (e) {
    if (e.code !== "auth/user-not-found") throw e;
  }

  // 2) Resolve conflicts
  // If an existing account has the target email but a different UID
  if (byEmail && byEmail.uid !== userData.uid) {
    const msg = `Email ${userData.email} is used by uid=${byEmail.uid}`;
    if (!FORCE) {
      console.error(`❌ Conflict: ${msg}. Re-run with --force to delete the conflicting user.`);
      process.exit(1);
    }
    await ensureDeleted(byEmail.uid, `email conflict with ${userData.uid}`);
  }

  // If an account with the target UID already exists
  if (byUid) {
    if (FORCE) {
      await ensureDeleted(userData.uid, `recreate requested`);
      byUid = null;
    } else {
      console.log("ℹ️  UID exists and --force is not set; will update it in-place.");
    }
  }

  // 3) Create or update the target user
  let userRecord;
  if (!byUid) {
    const password = EXPLICIT_PASSWORD || randPassword();
    console.log(`🆕 Creating user uid=${userData.uid} (emailVerified=true)`);
    if (!DRY_RUN) {
      userRecord = await admin.auth().createUser({
        uid: userData.uid,
        email: userData.email,
        emailVerified: true,
        password,
        disabled: false,
      });
    }
  } else {
    console.log(`🔧 Updating existing user uid=${userData.uid}`);
    if (!DRY_RUN) {
      userRecord = await admin.auth().updateUser(userData.uid, {
        email: userData.email,
        emailVerified: true,
        disabled: false,
      });
    }
  }

  // 4) Set custom claims
  console.log("🔐 Setting custom claims: { role: 'admin', isApproved: true }");
  if (!DRY_RUN) {
    await admin.auth().setCustomUserClaims(userData.uid, {
      role: "admin",
      isApproved: true,
    });
  }

  // 5) Upsert Realtime Database record
  const usersRef = admin.database().ref("users");
  const rtdbPayload = {
    email: userData.email,
    role: userData.role,
    isApproved: userData.isApproved,
    createdAt: Date.now(),
  };
  console.log(`💾 RTDB /users/${userData.uid} :=`, rtdbPayload);
  if (!DRY_RUN) {
    await usersRef.child(userData.uid).set(rtdbPayload);
  }

  console.log("✅ Admin user ensured.");
  if (DRY_RUN) {
    console.log("🧪 DRY_RUN: no changes were actually made.");
  }
}

main()
  .catch((err) => {
    console.error("❌ Error:", err);
    process.exit(1);
  })
  .finally(() => process.exit());
