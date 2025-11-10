// scripts/setAdminRole.js
import admin from '../db/firebaseAdmin.js'; // adjust path if needed

async function setAdminRole() {
  try {
    const users = [
      {
        uid: '9qVsPYF0jEc7CvS7fFa9GiojaoE2',
        email: 'ethan.huntley@gmail.com',
      },
      {
        uid: 'Uh0OqlSATvVwDopz7fG06n585m62',
        email: 'akhiparshotam@gmail.com',
      },
      {
        uid: 'KddBcEcH7FQYWEkeoc5hZV4C0es2',
        email: 'greg@gmail.com',
      },
    ];

    for (const user of users) {
      const userRef = admin.firestore().collection('users').doc(user.uid);
      await userRef.set(
        {
          role: 'admin',
          email: user.email,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
      console.log(`✅ Set role=admin for uid=${user.uid} (${user.email})`);
    }

    process.exit(0);
  } catch (err) {
    console.error('❌ Failed to set admin roles:', err);
    process.exit(1);
  }
}

setAdminRole();
