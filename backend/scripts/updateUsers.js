// scripts/updateUsers.mjs
// Usage:
//   node scripts/updateUsers.mjs
// Optional dry run:
//   DRY_RUN=1 node scripts/updateUsers.mjs

import { db, admin } from "../db/firebaseAdmin.js";

const DRY_RUN = process.env.DRY_RUN === "1";
const usersRef = admin.database().ref("users");

async function main() {
  console.log(`Starting user maintenance ${DRY_RUN ? "(DRY RUN)" : ""} ...`);

  const snap = await usersRef.once("value");
  if (!snap.exists()) {
    console.log("No users found at /users.");
    return;
  }

  const updates = {};
  const deletions = [];

  snap.forEach(child => {
    const uid = child.key;
    const user = child.val() ?? {};
    const role = String(user.role || "").toLowerCase();

    if (role === "consultant") {
      // Ensure the field exists and defaults to false (don’t flip true → false)
      if (user.isApproved === undefined) {
        updates[`/users/${uid}/isApproved`] = false;
      }
      // else: leave existing value (true/false) alone
    } else if (role === "admin" || role === "student") {
      // Keep as-is
    } else {
      // Any other role (or missing role) → delete
      deletions.push(uid);
    }
  });

  // Apply updates first
  if (Object.keys(updates).length) {
    console.log(`Will set isApproved=false for ${Object.keys(updates).length} consultant(s).`);
    if (!DRY_RUN) {
      await admin.database().ref().update(updates);
      console.log("Consultant updates applied.");
    }
  } else {
    console.log("No consultant updates needed.");
  }

  // Then deletions
  if (deletions.length) {
    console.log(`Will delete ${deletions.length} user(s):`, deletions);
    if (!DRY_RUN) {
      for (const uid of deletions) {
        await usersRef.child(uid).remove();
        console.log(`Deleted user ${uid}`);
      }
    }
  } else {
    console.log("No deletions needed.");
  }

  console.log(`Done ${DRY_RUN ? "(dry run, no writes performed)" : ""}.`);
}

main().catch(err => {
  console.error("Fatal error:", err);
  process.exitCode = 1;
});
