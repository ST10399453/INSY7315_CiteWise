import { Router } from "express";
import { checkAuth } from "../auth/checkAuth.js";
import { getRequestByDocumentId } from "../db/dbManager.js";
import { r2SignedUrl, streamFromR2 } from "../blobs/storage.js";
import { canAccessRequest } from "../utils/expressHelpers.js";

const router = Router();

// Signed URL
router.get("/:documentId/download", checkAuth, async (req, res) => {
  try {
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });
    if (!canAccessRequest(reqDoc, req.user)) return res.status(403).json({ message: "Forbidden" });

    // Only R2 supported
    const disposition = (req.query.disposition || "inline").toLowerCase();
    const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10)));
    const filename = reqDoc?.file?.originalName || "document";

    const url = await r2SignedUrl({
      bucket: reqDoc.storage.cloudflare.bucket,
      key: reqDoc.storage.cloudflare.key,
      expiresSeconds: expires,
      disposition,
      filename,
    });

    res.json({ url, expiresInSeconds: expires, provider: "r2" });
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
  }
});

// Stream
router.get("/:documentId/file", checkAuth, async (req, res) => {
  try {
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });
    if (!canAccessRequest(reqDoc, req.user)) return res.status(403).json({ message: "Forbidden" });

    // Only R2 supported
    const disposition = (req.query.disposition || "inline").toLowerCase();
    const filename = reqDoc?.file?.originalName || "document";

    const meta = await streamFromR2({
      bucket: reqDoc.storage.cloudflare.bucket,
      key: reqDoc.storage.cloudflare.key,
    });

    res.setHeader("Content-Type", meta.contentType || "application/octet-stream");
    if (meta.contentLength) res.setHeader("Content-Length", String(meta.contentLength));
    res.setHeader(
      "Content-Disposition",
      `${disposition}; filename="${encodeURIComponent(filename)}"`
    );

    meta.stream.pipe(res);
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
  }
});

export default router;
