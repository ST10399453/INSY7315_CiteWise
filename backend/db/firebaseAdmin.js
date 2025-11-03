import admin from 'firebase-admin'; // (Firebase, 2019a)
import dotenv from 'dotenv'; // (GeeksforGeeks, 2024)

dotenv.config(); // Loads environment variables (GeeksforGeeks, 2024)

// Parses and validates service account JSON from environment (Firebase, 2019b)
function parseServiceAccountFromEnv() {
  let raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    throw new Error(
      'FIREBASE_SERVICE_ACCOUNT_JSON is not set. Set it to the FULL service account JSON (single line).'
    );
  }

  // Strip wrapping quotes for some deployment environments (Manico & Detlefsen, 2015)
  if (
    (raw.startsWith('"') && raw.endsWith('"')) ||
    (raw.startsWith("'") && raw.endsWith("'"))
  ) {
    raw = raw.slice(1, -1);
  }

  let svc;
  try {
    svc = JSON.parse(raw); // (Firebase, 2019b)
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

  // Convert escaped newlines for PEM formatting (Firebase, 2019b)
  svc.private_key = svc.private_key.replace(/\\n/g, '\n');
  return svc;
}

let credential;
let resolvedProjectId;

// Initialize Firebase Admin using service account credentials (Firebase, 2019b)
if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
  const serviceAccount = parseServiceAccountFromEnv();
  credential = admin.credential.cert({
    projectId: serviceAccount.project_id,
    clientEmail: serviceAccount.client_email,
    privateKey: serviceAccount.private_key,
  });
  resolvedProjectId = serviceAccount.project_id;
} else {
  // Fallback to Application Default Credentials (Firebase, 2019b)
  credential = admin.credential.applicationDefault();
  resolvedProjectId = process.env.FIREBASE_PROJECT_ID || undefined;
}

const configuredProjectId = process.env.FIREBASE_PROJECT_ID || resolvedProjectId;
if (!configuredProjectId) {
  throw new Error(
    'FIREBASE_PROJECT_ID is not set, and could not be inferred from the service account JSON.'
  );
}

// Initialize Firebase App if not already done (Firebase, 2019b)
if (!admin.apps.length) {
  admin.initializeApp({
    credential,
    projectId: configuredProjectId,
    databaseURL: process.env.FIREBASE_RTDB_URL,
  });
}

// Firestore instance configuration (Firebase, 2019a)
const db = admin.firestore();
db.settings({ ignoreUndefinedProperties: true });

// Authentication helper (Firebase, 2019b)
const auth = admin.auth();

export { admin, auth, db, configuredProjectId as FIREBASE_PROJECT_ID };

export default admin;

/*
REFERENCES

Android Knowledge. 2023. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
YouTube. August 2023 <https://www.youtube.com/watch?v=oGyQMBKPuNY> [accessed September 2025].

Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
Medium. January 2024 <https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494> [accessed September 2025].

Axios. 2023. “Getting Started | Axios Docs”.
Axios-Http.com. 2023 <https://axios-http.com/docs/intro> [accessed September 2025].

Balaji, Dev. 2023. “JWT Authentication in Node.js: A Practical Guide”.
Medium. September 2023 <https://dvmhn07.medium.com/jwt-authentication-in-node-js-a-practical-guide-c8ab1b432a49> [accessed October 2025].

Cloudflare. 2024. “Cloudflare R2 · Cloudflare R2 Docs”.
Cloudflare Docs. April 5, 2024 <https://developers.cloudflare.com/r2/> [accessed 12 October 2025].

express-validator. 2019. “Getting Started · Express-Validator”.
Github.io. 2019 <https://express-validator.github.io/docs/> [accessed October 2025].

Firebase. 2019a. “Cloud Firestore | Firebase”.
Firebase. 2019 <https://firebase.google.com/docs/firestore> [accessed September 2025].

Firebase. 2019b. “Firebase Authentication | Firebase”.
Firebase. Google. 2019 <https://firebase.google.com/docs/auth> [accessed September 2025].

Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
Firebase. 2019 <https://firebase.google.com/docs/cloud-messaging> [accessed September 2025].

Firebase. 2019d. “Firebase Realtime Database”.
Firebase. 2019 <https://firebase.google.com/docs/database> [accessed September 2025].

GeeksforGeeks. 2022a. “Use of CORS in Node.js”.
GeeksforGeeks. March 2022 <https://www.geeksforgeeks.org/node-js/use-of-cors-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2022b. “What Is Expressratelimit in Node.js ?”.
GeeksforGeeks. April 2022 <https://www.geeksforgeeks.org/node-js/what-is-express-rate-limit-in-node-js/> [accessed October 2025].

GeeksforGeeks. 2024. “NPM Dotenv”.
GeeksforGeeks. May 2024 <https://www.geeksforgeeks.org/node-js/npm-dotenv/> [accessed October 2025].

Manico, Jim and August Detlefsen. 2015. *Iron-Clad Java: Building Secure Web Applications*.
McGraw-Hill Education.

Nakazawa Tech. 2018. “Delightful JavaScript Testing with Jest”.
YouTube. May 30, 2018 <https://www.youtube.com/watch?v=cAKYQpTC7MA> [accessed 2 November 2025].

NextJS. 2025. “Documentation | NestJS - a Progressive Node.js Framework”.
Documentation | NestJS - a Progressive Node.js Framework. 2025 <https://docs.nestjs.com/security/helmet> [accessed October 2025].

Patel, Ravi. 2024. “A Beginner’s Guide to the Node.js”.
Medium. December 2024 <https://medium.com/@ravipatel.it/a-beginners-guide-to-the-node-js-469f7458bbb2> [accessed October 2025].

React Native. 2025. “React Fundamentals · React Native”.
Reactnative.dev. 2025 <https://reactnative.dev/docs/intro-react> [accessed September 2025].

Samson Omojola. 2024. “Password Hashing in Node.js with Bcrypt”.
Honeybadger Developer Blog. Honeybadger. January 2024 <https://www.honeybadger.io/blog/node-password-hashing/> [accessed September 2025].

Tony. 2023. “Guide to Node’s Crypto Module for Encryption/Decryption”.
Medium. May 5, 2023 <https://medium.com/@tony.infisical/guide-to-nodes-crypto-module-for-encryption-decryption-65c077176980> [accessed 2 November 2025].
*/
