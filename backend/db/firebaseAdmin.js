import admin from 'firebase-admin';
import dotenv from 'dotenv';

dotenv.config();

function parseServiceAccountFromEnv() {
  let raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    throw new Error(
      'FIREBASE_SERVICE_ACCOUNT_JSON is not set. Set it to the FULL service account JSON (single line).'
    );
  }

  // Some platforms wrap the JSON in quotes; strip once if present.
  if (
    (raw.startsWith('"') && raw.endsWith('"')) ||
    (raw.startsWith("'") && raw.endsWith("'"))
  ) {
    raw = raw.slice(1, -1);
  }

  let svc;
  try {
    svc = JSON.parse(raw);
  } catch {
    throw new Error(
      'Failed to JSON.parse(FIREBASE_SERVICE_ACCOUNT_JSON). Ensure it is valid JSON (no trailing commas, properly escaped quotes).'
    );
  }

  if (!svc.private_key || typeof svc.private_key !== 'string') {
    throw new Error('private_key missing from FIREBASE_SERVICE_ACCOUNT_JSON');
  }
  if (!svc.client_email) {
    throw new Error('client_email missing from FIREBASE_SERVICE_ACCOUNT_JSON');
  }
  if (!svc.project_id) {
    throw new Error('project_id missing from FIREBASE_SERVICE_ACCOUNT_JSON');
  }

  // Convert escaped newlines into real newlines for PEM correctness
  svc.private_key = svc.private_key.replace(/\\n/g, '\n');
  return svc;
}

let credential;
let resolvedProjectId;

if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
  const serviceAccount = parseServiceAccountFromEnv();
  credential = admin.credential.cert({
    projectId: serviceAccount.project_id,
    clientEmail: serviceAccount.client_email,
    privateKey: serviceAccount.private_key,
  });
  resolvedProjectId = serviceAccount.project_id;
} else {
  // Fallback to ADC if running on GCP with a bound service account
  credential = admin.credential.applicationDefault();
  resolvedProjectId = process.env.FIREBASE_PROJECT_ID || undefined;
}

const configuredProjectId = process.env.FIREBASE_PROJECT_ID || resolvedProjectId;
if (!configuredProjectId) {
  throw new Error(
    'FIREBASE_PROJECT_ID is not set, and could not be inferred from the service account JSON.'
  );
}

if (!admin.apps.length) {
  admin.initializeApp({
    credential,
    projectId: configuredProjectId,
    databaseURL: process.env.FIREBASE_RTDB_URL,
  });
}

// Firestore instance & settings
const db = admin.firestore();
db.settings({ ignoreUndefinedProperties: true });

// Helpers
const auth = admin.auth();

export { admin, auth, db, configuredProjectId as FIREBASE_PROJECT_ID };

export default admin;
