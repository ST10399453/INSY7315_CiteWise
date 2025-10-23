// server.js — HTTPS only (self-signed certs for localhost)

import express from "express";
import cors from "cors";
import "dotenv/config";
import fs from "fs";
import https from "https";

import { ensureR2Bucket } from "./blobs/storage.js";

import requestsRouter from "./routes/requests.js";
import documentsRouter from "./routes/documents.js";
import resourcesRouter from "./routes/resources.js";
import messagesRouter from "./routes/messages.js";


// ─────────────────────────────────────────────────────────────────────────────
// TLS certificates — directly read from ./keys directory
//   - ./keys/localhost+2-key.pem  (private key)
//   - ./keys/localhost+2.pem      (certificate)
// ─────────────────────────────────────────────────────────────────────────────
const KEY_PATH = process.env.TLS_KEY_PATH || "./keys/localhost+2-key.pem";
const CERT_PATH = process.env.TLS_CERT_PATH || "./keys/localhost+2.pem";

// Load HTTPS key and certificate
let httpsOptions;
try {
  httpsOptions = {
    key: fs.readFileSync(KEY_PATH),
    cert: fs.readFileSync(CERT_PATH),
  };
} catch (err) {
  console.error("[TLS] Failed to read key/cert files:");
  console.error(`  Key:  ${KEY_PATH}`);
  console.error(`  Cert: ${CERT_PATH}`);
  console.error(`  Error: ${err.message}`);
  process.exit(1);
}

const app = express();
app.use(cors());
app.use(express.json());

// Root
app.get("/", (_req, res) =>
  res.send("CiteWise API is running over HTTPS (uploads: Cloudflare R2).")
);

// Mount grouped routes
app.use("/requests", requestsRouter);
app.use("/documents", documentsRouter);
app.use("/resources", resourcesRouter);
app.use("/messages", messagesRouter);

// HTTPS startup
const PORT = process.env.PORT || 8443;

(async function boot() {
  try {
    if (process.env.SKIP_STORAGE_INIT === "1") {
      console.log("[BOOT] SKIP_STORAGE_INIT=1 → skipping R2 container checks");
    } else {
      await ensureR2Bucket();
    }

    https.createServer(httpsOptions, app).listen(PORT, () => {
      console.log(`✅ HTTPS server running at https://localhost:${PORT}`);
    });
  } catch (err) {
    console.error("Startup failed:", err);
    process.exit(1);
  }
})();
