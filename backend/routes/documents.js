import { Router } from "express"; // (GeeksforGeeks, 2022a)
import { checkAuth } from "../auth/checkAuth.js"; // (Balaji, 2023)
import { getRequestByDocumentId } from "../db/dbManager.js"; // (Firebase, 2019a)
import { r2SignedUrl, streamFromR2 } from "../blobs/storage.js"; // (Cloudflare, 2024)
import { canAccessRequest } from "../utils/expressHelpers.js"; // (Manico & Detlefsen, 2015)

const router = Router();

/**
 * ========================================
 *  ROUTE: Generate Signed URL for Download
 *  ----------------------------------------
 *  Endpoint: GET /:documentId/download
 *  Purpose: Returns a short-lived signed URL for securely downloading
 *           or viewing a document stored in Cloudflare R2.
 *  Used by: Web App & Mobile App
 * ========================================
 */
// Uses authentication middleware (Balaji, 2023)
//Implemented in app
router.get("/:documentId/download", checkAuth, async (req, res) => {
  try {
    // Retrieve document metadata (Firebase, 2019a)
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });

    // Permission enforcement (Manico & Detlefsen, 2015)
    if (!canAccessRequest(reqDoc, req.user))
      return res.status(403).json({ message: "Forbidden" });

    // Signed URL configuration (Cloudflare, 2024)
    const disposition = (req.query.disposition || "inline").toLowerCase();
    const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10)));
    const filename = reqDoc?.file?.originalName || "document";

    // Generate secure presigned URL for R2 (Cloudflare, 2024)
    const url = await r2SignedUrl({
      bucket: reqDoc.storage.cloudflare.bucket,
      key: reqDoc.storage.cloudflare.key,
      expiresSeconds: expires,
      disposition,
      filename,
    });

    res.json({ url, expiresInSeconds: expires, provider: "r2" });
  } catch (e) {
    const msg = e?.message || "";
    if (msg.includes("The specified key does not exist")) {
      return res.status(404).json({ message: "Document not found" });
    }
    res.status(400).json({ message: msg });
  }
});

/**
 * ========================================
 *  ROUTE: Direct File Streaming
 *  ----------------------------------------
 *  Endpoint: GET /:documentId/file
 *  Purpose: Streams the document file directly from Cloudflare R2
 *           to the client without a signed URL. Ideal for apps
 *           that need inline document preview or secure streaming.
 *  Used by: Web App & Mobile App
 * ========================================
 */
// Implements secure direct streaming (Cloudflare, 2024)
//Implemented in app
router.get("/:documentId/file", checkAuth, async (req, res) => {
  try {
    // Fetch document metadata (Firebase, 2019a)
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });

    // Access validation (Manico & Detlefsen, 2015)
    if (!canAccessRequest(reqDoc, req.user))
      return res.status(403).json({ message: "Forbidden" });

    // Define output settings (Tony, 2023)
    const disposition = (req.query.disposition || "inline").toLowerCase();
    const filename = reqDoc?.file?.originalName || "document";

    // Retrieve file stream from R2 (Cloudflare, 2024)
    const meta = await streamFromR2({
      bucket: reqDoc.storage.cloudflare.bucket,
      key: reqDoc.storage.cloudflare.key,
    });

    // Set content headers (GeeksforGeeks, 2022a)
    res.setHeader("Content-Type", meta.contentType || "application/octet-stream");
    if (meta.contentLength)
      res.setHeader("Content-Length", String(meta.contentLength));
    res.setHeader(
      "Content-Disposition",
      `${disposition}; filename="${encodeURIComponent(filename)}"`
    );

    // Securely stream to client (Manico & Detlefsen, 2015)
    meta.stream.pipe(res);
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
  }
});

export default router;

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
