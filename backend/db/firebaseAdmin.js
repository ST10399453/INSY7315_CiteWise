// src/db/firebaseAdmin.js
import admin from 'firebase-admin';
import dotenv from 'dotenv';

dotenv.config();

function parseServiceAccountFromEnv() {
  let raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON is not set');
  }

  // Some shells wrap the JSON in quotes; strip a single leading/trailing quote if present.
  if ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'"))) {
    raw = raw.slice(1, -1);
  }

  const svc = JSON.parse(raw);

  // Convert escaped newlines into real newlines for PEM correctness
  if (svc.private_key && typeof svc.private_key === 'string') {
    svc.private_key = svc.private_key.replace(/\\n/g, '\n');
  } else {
    throw new Error('private_key missing from FIREBASE_SERVICE_ACCOUNT_JSON');
  }

  return svc;
}

let credential;

if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
  const serviceAccount = parseServiceAccountFromEnv();
  // Use the object directly — no file path, no temp files
  credential = admin.credential.cert({
    projectId: serviceAccount.project_id,
    clientEmail: serviceAccount.client_email,
    privateKey: serviceAccount.private_key,
  });
} else {
  // Fallback (won’t be used in your case)
  credential = admin.credential.applicationDefault();
}

if (!admin.apps.length) {
  admin.initializeApp({
    credential,
    projectId: process.env.FIREBASE_PROJECT_ID,
    databaseURL: process.env.FIREBASE_RTDB_URL,
  });
}

const db = admin.firestore();
export { admin, db };
