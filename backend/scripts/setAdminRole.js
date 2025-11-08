// scripts/setAdminRole.js
import admin from '../db/firebaseAdmin.js'; // adjust path if needed

async function setAdminRole() {
  try {
    const uid = '9qVsPYF0jEc7CvS7fFa9GiojaoE2'; // <-- your UID from the logs

    const userRef = admin.firestore().collection('users').doc(uid);
    await userRef.set(
      {
        role: 'admin',
        email: 'ethan.huntley@gmail.com',
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    console.log(`✅ Set role=admin for uid=${uid}`);
    process.exit(0);
  } catch (err) {
    console.error('❌ Failed to set admin role:', err);
    process.exit(1);
  }
}

setAdminRole();
