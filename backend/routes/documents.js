import { Router } from "express";
import { checkAuth } from "../auth/checkAuth.js";
import { getRequestByDocumentId } from "../db/dbManager.js";
import { r2SignedUrl, streamFromR2 } from "../blobs/storage.js";
import { canAccessRequest } from "../utils/expressHelpers.js";

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
router.get("/:documentId/download", checkAuth, async (req, res) => {
  try {
    // Fetch document metadata by ID from DB
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });

    // Validate that the current user has permission to access it
    if (!canAccessRequest(reqDoc, req.user))
      return res.status(403).json({ message: "Forbidden" });

    // Parse query parameters for optional settings
    // disposition: "inline" (view in browser) or "attachment" (download)
    const disposition = (req.query.disposition || "inline").toLowerCase();

    // expires: validity of the signed URL in seconds (min 60s, max 2h)
    const expires = Math.max(60, Math.min(7200, parseInt(req.query.expires || "900", 10)));

    // Optional file name for the download prompt
    const filename = reqDoc?.file?.originalName || "document";

    // Generate a signed URL for Cloudflare R2
    const url = await r2SignedUrl({
      bucket: reqDoc.storage.cloudflare.bucket,
      key: reqDoc.storage.cloudflare.key,
      expiresSeconds: expires,
      disposition,
      filename,
    });

    // Return the signed URL and related metadata
    res.json({ url, expiresInSeconds: expires, provider: "r2" });
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
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
router.get("/:documentId/file", checkAuth, async (req, res) => {
  try {
    // Fetch document metadata by ID from DB
    const reqDoc = await getRequestByDocumentId(req.params.documentId);
    if (!reqDoc) return res.status(404).json({ message: "Document not found" });

    // Validate that the current user has permission to access it
    if (!canAccessRequest(reqDoc, req.user))
      return res.status(403).json({ message: "Forbidden" });

    // Prepare file stream options
    const disposition = (req.query.disposition || "inline").toLowerCase();
    const filename = reqDoc?.file?.originalName || "document";

    // Fetch file stream and metadata from Cloudflare R2
    const meta = await streamFromR2({
      bucket: reqDoc.storage.cloudflare.bucket,
      key: reqDoc.storage.cloudflare.key,
    });

    // Set response headers for browser/client handling
    res.setHeader("Content-Type", meta.contentType || "application/octet-stream");
    if (meta.contentLength)
      res.setHeader("Content-Length", String(meta.contentLength));
    res.setHeader(
      "Content-Disposition",
      `${disposition}; filename="${encodeURIComponent(filename)}"`
    );

    // Pipe the file stream directly to the client
    meta.stream.pipe(res);
  } catch (e) {
    console.error(e);
    res.status(400).json({ message: e.message });
  }
});

export default router;
