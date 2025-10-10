// ─────────────────────────────────────────────────────────────────────────────
// File: storage.js
// Purpose: Storage helpers for Cloudflare R2 (S3-compatible) + Azure Blob
// ─────────────────────────────────────────────────────────────────────────────
import {
  S3Client,
  PutObjectCommand,
  HeadBucketCommand,
  CreateBucketCommand,
} from '@aws-sdk/client-s3';
import { BlobServiceClient } from '@azure/storage-blob';
import crypto from 'crypto';

// ---------- R2 (S3-compatible) client ----------
export const r2 = new S3Client({
  region: 'auto',
  endpoint: process.env.R2_ENDPOINT, // e.g. https://<accountid>.r2.cloudflarestorage.com
  credentials: {
    accessKeyId: process.env.R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.R2_SECRET_ACCESS_KEY,
  },
});

export const R2_BUCKET = process.env.R2_BUCKET;
export const R2_PUBLIC_BASE = process.env.R2_PUBLIC_BASE || null; // optional public domain (r2.dev / worker / CDN)

// ---------- Azure client ----------
export const azureBlob = BlobServiceClient.fromConnectionString(
  process.env.AZURE_STORAGE_CONNECTION_STRING
);
export const AZURE_CONTAINER = process.env.AZURE_BLOB_CONTAINER;
export const AZURE_PUBLIC_BASE = process.env.AZURE_PUBLIC_BASE || null; // optional public base URL

// ---------- Utils ----------
export function newFileId() {
  return crypto.randomUUID();
}

export function safeName(name) {
  return String(name || 'upload.bin').replace(/[^\w.\-]/g, '_');
}

// ---------- Startup ensure functions ----------
export async function ensureR2Bucket() {
  if (!R2_BUCKET) throw new Error('R2_BUCKET is not set');
  try {
    await r2.send(new HeadBucketCommand({ Bucket: R2_BUCKET }));
    console.log(`[R2] Bucket exists: ${R2_BUCKET}`);
  } catch (err) {
    const status = err?.$metadata?.httpStatusCode;
    if (status === 404) {
      try {
        await r2.send(new CreateBucketCommand({ Bucket: R2_BUCKET }));
        console.log(`[R2] Bucket created: ${R2_BUCKET}`);
      } catch (createErr) {
        console.error(`[R2] Failed to create bucket ${R2_BUCKET}:`, createErr);
        throw createErr;
      }
    } else if (status === 403) {
      console.warn(`[R2] HeadBucket 403 for ${R2_BUCKET}. It may exist, but your key lacks permission.`);
    } else {
      console.error(`[R2] HeadBucket error for ${R2_BUCKET}:`, err);
      throw err;
    }
  }
}

export async function ensureAzureContainer() {
  if (!AZURE_CONTAINER) throw new Error('AZURE_BLOB_CONTAINER is not set');
  const container = azureBlob.getContainerClient(AZURE_CONTAINER);
  const res = await container.createIfNotExists();
  if (res?.succeeded) {
    console.log(`[Azure] Container created: ${AZURE_CONTAINER}`);
  } else {
    console.log(`[Azure] Container exists: ${AZURE_CONTAINER}`);
  }
}

// ---------- Per-request upload helpers ----------
export async function uploadToR2({ key, body, contentType }) {
  await r2.send(
    new PutObjectCommand({
      Bucket: R2_BUCKET,
      Key: key,
      Body: body,
      ContentType: contentType,
    })
  );
  return {
    bucket: R2_BUCKET,
    key,
    url: R2_PUBLIC_BASE ? `${R2_PUBLIC_BASE}/${key}` : null,
  };
}

export async function uploadToAzure({ blobPath, body, contentType }) {
  const containerClient = azureBlob.getContainerClient(AZURE_CONTAINER);
  await containerClient.createIfNotExists();
  const blockBlob = containerClient.getBlockBlobClient(blobPath);
  await blockBlob.uploadData(body, {
    blobHTTPHeaders: { blobContentType: contentType },
  });
  return {
    container: AZURE_CONTAINER,
    blob: blobPath,
    url: AZURE_PUBLIC_BASE ? `${AZURE_PUBLIC_BASE}/${blobPath}` : null,
  };
}
