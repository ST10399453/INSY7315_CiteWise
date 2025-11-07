import crypto from "crypto";
import { S3Client, PutObjectCommand, GetObjectCommand, HeadBucketCommand, CreateBucketCommand, DeleteObjectCommand,} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import "dotenv/config";

// (Cloudflare, 2024)
function env() {
  const { R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY } = process.env;
  return { R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY };
}

let r2Client;
// (Cloudflare, 2024)
function r2() {
  const { R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY } = env();
  if (!r2Client) {
    r2Client = new S3Client({
      region: "auto",
      endpoint: R2_ENDPOINT,
      credentials: { accessKeyId: R2_ACCESS_KEY_ID, secretAccessKey: R2_SECRET_ACCESS_KEY },
    });
  }
  return r2Client;
}

function assertEnv() {
  const { R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY } = env();
  const missing = [];
  if (!R2_ENDPOINT) missing.push("R2_ENDPOINT");
  if (!R2_BUCKET) missing.push("R2_BUCKET");
  if (!R2_ACCESS_KEY_ID) missing.push("R2_ACCESS_KEY_ID");
  if (!R2_SECRET_ACCESS_KEY) missing.push("R2_SECRET_ACCESS_KEY");
  if (missing.length) throw new Error(`Missing env: ${missing.join(", ")}`);
}

// (Tony, 2023)
export function newFileId() { return crypto.randomUUID(); }
// (Das, 2025)
export function safeName(name = "file") { return name.replace(/[^\w.\- ]+/g, "_").trim().slice(0, 180) || "file"; }
function extFromMime(mime = "") { const m = String(mime).toLowerCase(); if (m === "application/pdf") return ".pdf"; if (m.startsWith("image/")) return `.${m.split("/")[1] || "img"}`; if (m === "text/plain") return ".txt"; return ""; }
// (Cloudflare, 2024)
export function uploadsKey({ id, fileName, mime }) { const clean = safeName(fileName); const ext = extFromMime(mime); return `uploads/${id}/${clean}${ext && !clean.toLowerCase().endsWith(ext) ? ext : ""}`; }
// (Cloudflare, 2024)
export function resourcesKey({ id, fileName, mime }) { const clean = safeName(fileName); const ext = extFromMime(mime); return `resources/${id}/${clean}${ext && !clean.toLowerCase().endsWith(ext) ? ext : ""}`; }

// (Cloudflare, 2024)
export async function ensureR2Bucket() {
  const { R2_BUCKET } = env();
  if (!R2_BUCKET) throw new Error("R2_BUCKET not set");
  try {
    await r2().send(new HeadBucketCommand({ Bucket: R2_BUCKET }));
  } catch {
    await r2().send(new CreateBucketCommand({ Bucket: R2_BUCKET }));
  }
}

// (Cloudflare, 2024)
export async function uploadToR2({ key, body, contentType }) {
  const { R2_BUCKET } = env();
  await r2().send(new PutObjectCommand({ Bucket: R2_BUCKET, Key: key, Body: body, ContentType: contentType }));
  return { bucket: R2_BUCKET, key };
}

// (Cloudflare, 2024)
export async function r2SignedUrl({ bucket, key, expiresSeconds = 900, disposition, filename }) {
  const cmd = new GetObjectCommand({
    Bucket: bucket,
    Key: key,
    ...(disposition && filename ? { ResponseContentDisposition: `${disposition}; filename="${encodeURIComponent(filename)}"` } : {}),
  });
  return getSignedUrl(r2(), cmd, { expiresIn: expiresSeconds });
}

// (Cloudflare, 2024)
export async function streamFromR2({ bucket, key }) {
  const res = await r2().send(new GetObjectCommand({ Bucket: bucket, Key: key }));
  return { stream: res.Body, contentType: res.ContentType || "application/octet-stream", contentLength: res.ContentLength };
}

/** Delete a single object from R2.
 *  Note: S3 DeleteObject is idempotent — it succeeds even if the key doesn’t exist.
 */
export async function deleteFromR2({ bucket, key }) {
  await r2().send(new DeleteObjectCommand({ Bucket: bucket, Key: key }));
}

// (Cloudflare, 2024)
export async function ensureStorageReady() { assertEnv(); await ensureR2Bucket(); }

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
