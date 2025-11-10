// scripts/setAdminRole.js
// Run: node scripts/setAdminRole.js
//      DRY_RUN=1 node scripts/setAdminRole.js   (no writes)

import * as maybe from '../db/firebaseAdmin.js';

// Handle either named or default exports from firebaseAdmin.js
const admin = maybe.admin || maybe.default || maybe;
const db = maybe.db || (admin.firestore ? admin.firestore() : null);

const DRY_RUN = process.env.DRY_RUN === '1';

const USERS = [
  { uid: '9qVsPYF0jEc7CvS7fFa9GiojaoE2', email: 'ethan.huntley@gmail.com' },
  { uid: 'Uh0OqlSATvVwDopz7fG06n585m62', email: 'akhiparshotam@gmail.com' },
  { uid: '1MlQoXXtInXbAG9LZediMEszCaA2', email: 'sedi@gmail.com' },
];

async function ensureAuthUser(uid, email) {
  try {
    const u = await admin.auth().getUser(uid);
    // If email differs, update (optional)
    if (email && u.email !== email) {
      if (!DRY_RUN) await admin.auth().updateUser(uid, { email, emailVerified: true });
      console.log(`🔧 Auth updated email for ${uid} -> ${email}`);
    }
  } catch (e) {
    if (e.code === 'auth/user-not-found') {
      console.log(`🆕 Creating Auth user ${uid} (${email})`);
      if (!DRY_RUN) {
        await admin.auth().createUser({
          uid,
          email,
          emailVerified: true,
          // temp password (optional)
          password: Math.random().toString(36).slice(-12),
          disabled: false,
        });
      }
    } else {
      throw e;
    }
  }
}

async function setAdminForUser({ uid, email }) {
  console.log(`\n👤 Setting admin for uid=${uid} (${email})`);

  // 1) Ensure the user exists in Auth (so we can set claims)
  await ensureAuthUser(uid, email);

  // 2) Set custom claims
  console.log('🔐 Setting custom claims { role: "admin", isApproved: true }');
  if (!DRY_RUN) {
    await admin.auth().setCustomUserClaims(uid, { role: 'admin', isApproved: true });
  }

  // 3) Realtime Database mirror (this is what your Android code reads)
  const rtdbPayload = {
    email,
    role: 'admin',
    isApproved: true,
    updatedAt: Date.now(),
  };
  console.log(`💾 RTDB /users/${uid} :=`, rtdbPayload);
  if (!DRY_RUN) {
    await admin.database().ref(`users/${uid}`).update(rtdbPayload);
  }

  // 4) (Optional) Firestore mirror for convenience
  if (db) {
    console.log('📁 Firestore users/{uid} mirror (optional)');
    if (!DRY_RUN) {
      await db.collection('users').doc(uid).set(
        {
          email,
          role: 'admin',
          isApproved: true,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
  }

  console.log(`✅ Admin set for ${uid}`);
}

async function main() {
  try {
    console.log(`START setAdminRole (DRY_RUN=${DRY_RUN})`);
    for (const user of USERS) {
      await setAdminForUser(user);
    }
    console.log('\n🎉 Done');
    process.exit(0);
  } catch (err) {
    console.error('❌ Failed to set admin roles:', err);
    process.exit(1);
  }
}

main();
