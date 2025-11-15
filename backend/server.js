import express from "express"; // Express app setup (Patel, 2024)
import cors from "cors"; // Enable CORS middleware (GeeksforGeeks, 2022a)
import "dotenv/config"; // Load environment variables from .env (GeeksforGeeks, 2024)

import { ensureR2Bucket } from "./blobs/storage.js"; // Cloudflare R2 storage initialization (Cloudflare, 2024)

import requestsRouter from "./routes/requests.js"; // Modular route organization (Manico & Detlefsen, 2015)
import documentsRouter from "./routes/documents.js";
import resourcesRouter from "./routes/resources.js";
import messagesRouter from "./routes/messages.js";
import "./cron/scheduler.js"; //call scheduler

const app = express();
app.use(cors()); // Middleware for CORS (GeeksforGeeks, 2022a)
app.use(express.json()); // Parse JSON request bodies (Patel, 2024)

// Health check endpoint for deployment monitoring (Manico & Detlefsen, 2015)
app.get("/", (_req, res) =>
  res.send("CiteWise API is running over HTTP (uploads: Cloudflare R2).")
);

// Primary API routes (Manico & Detlefsen, 2015)
app.use("/requests", requestsRouter);
app.use("/documents", documentsRouter);
app.use("/resources", resourcesRouter);
app.use("/messages", messagesRouter);

// HTTP startup configuration (Patel, 2024)
const PORT = process.env.PORT || 8080;

(async function boot() {
  try {
    if (process.env.SKIP_STORAGE_INIT === "1") {
      console.log("[BOOT] SKIP_STORAGE_INIT=1 → skipping R2 container checks"); // Config flag (Cloudflare, 2024)
    } else {
      await ensureR2Bucket(); // Initialize R2 bucket (Cloudflare, 2024)
    }

    app.listen(PORT, () => {
      console.log(`✅ HTTP server running at http://localhost:${PORT}`); // Startup log (Nakazawa Tech, 2018)
    });
  } catch (err) {
    console.error("Startup failed:", err); // Error handling (Manico & Detlefsen, 2015)
    process.exit(1);
  }
})();


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
