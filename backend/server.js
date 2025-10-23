// server.js — HTTP version (no HTTPS/TLS)

import express from "express";
import cors from "cors";
import "dotenv/config";

import { ensureR2Bucket } from "./blobs/storage.js";

import requestsRouter from "./routes/requests.js";
import documentsRouter from "./routes/documents.js";
import resourcesRouter from "./routes/resources.js";
import messagesRouter from "./routes/messages.js";

const app = express();
app.use(cors());
app.use(express.json());

// Root
app.get("/", (_req, res) =>
  res.send("CiteWise API is running over HTTP (uploads: Cloudflare R2).")
);

// Mount grouped routes
app.use("/requests", requestsRouter);
app.use("/documents", documentsRouter);
app.use("/resources", resourcesRouter);
app.use("/messages", messagesRouter);

// HTTP startup
const PORT = process.env.PORT || 8080;

(async function boot() {
  try {
    if (process.env.SKIP_STORAGE_INIT === "1") {
      console.log("[BOOT] SKIP_STORAGE_INIT=1 → skipping R2 container checks");
    } else {
      await ensureR2Bucket();
    }

    app.listen(PORT, () => {
      console.log(`✅ HTTP server running at http://localhost:${PORT}`);
    });
  } catch (err) {
    console.error("Startup failed:", err);
    process.exit(1);
  }
})();
