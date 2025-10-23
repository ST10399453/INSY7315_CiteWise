import crypto from 'crypto';

// ---------- Cloudflare R2 (S3-compatible) ----------
import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  HeadBucketCommand,
  CreateBucketCommand,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';

// ---------- Azure Blob ----------
import {
  BlobServiceClient,
  StorageSharedKeyCredential,
  generateBlobSASQueryParameters,
  BlobSASPermissions,
  SASProtocol,
} from '@azure/storage-blob';

// ---------- Env & clients ----------
export const R2_ENDPOINT = process.env.R2_ENDPOINT;                 // e.g. https://<accountid>.r2.cloudflarestorage.com
export const R2_BUCKET   = process.env.R2_BUCKET;                   // e.g. citewise
export const AZURE_CONN  = process.env.AZURE_STORAGE_CONNECTION_STRING;
export const AZURE_CONTAINER = process.env.AZURE_BLOB_CONTAINER;    // e.g. citewise

function assertEnv() {
  const missing = [];
  if (!R2_ENDPOINT)     missing.push('R2_ENDPOINT');
  if (!R2_BUCKET)       missing.push('R2_BUCKET');
  if (!process.env.R2_ACCESS_KEY_ID)     missing.push('R2_ACCESS_KEY_ID');
  if (!process.env.R2_SECRET_ACCESS_KEY) missing.push('R2_SECRET_ACCESS_KEY');
  if (!AZURE_CONN)      missing.push('AZURE_STORAGE_CONNECTION_STRING');
  if (!AZURE_CONTAINER) missing.push('AZURE_BLOB_CONTAINER');
  if (missing.length) throw new Error(`Missing env: ${missing.join(', ')}`);
}

export const r2 = new S3Client({
  region: 'auto',
  endpoint: R2_ENDPOINT,
  credentials: {
    accessKeyId: process.env.R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.R2_SECRET_ACCESS_KEY,
  },
});

export const azure = BlobServiceClient.fromConnectionString(AZURE_CONN);

// ---------- Utilities ----------
export function newFileId() {
  return crypto.randomUUID();
}

export function safeName(name = 'file') {
  return name.replace(/[^\w.\- ]+/g, '_').trim().slice(0, 180) || 'file';
}

function extFromMime(mime = '') {
  const m = String(mime).toLowerCase();
  if (m === 'application/pdf') return '.pdf';
  if (m.startsWith('image/')) return `.${m.split('/')[1] || 'img'}`;
  if (m === 'text/plain') return '.txt';
  return ''; // let caller include original extension if desired
}

export function uploadsKey({ id, fileName, mime }) {
  const clean = safeName(fileName);
  const ext = extFromMime(mime);
  return `uploads/${id}/${clean}${ext && !clean.toLowerCase().endsWith(ext) ? ext : ''}`;
}

export function resourcesKey({ id, fileName, mime }) {
  const clean = safeName(fileName);
  const ext = extFromMime(mime);
  return `resources/${id}/${clean}${ext && !clean.toLowerCase().endsWith(ext) ? ext : ''}`;
}

// (Optional) called at boot when not skipped
export async function ensureR2Bucket() {
  if (!R2_BUCKET) throw new Error('R2_BUCKET not set');
  try {
    await r2.send(new HeadBucketCommand({ Bucket: R2_BUCKET }));
  } catch {
    await r2.send(new CreateBucketCommand({ Bucket: R2_BUCKET }));
  }
}

export async function ensureAzureContainer() {
  if (!AZURE_CONTAINER) throw new Error('AZURE_BLOB_CONTAINER not set');
  const containerClient = azure.getContainerClient(AZURE_CONTAINER);
  await containerClient.createIfNotExists();
}

// ---------- Uploads (generic) ----------
export async function uploadToR2({ key, body, contentType }) {
  await r2.send(new PutObjectCommand({
    Bucket: R2_BUCKET,
    Key: key,
    Body: body,
    ContentType: contentType,
  }));
  return { bucket: R2_BUCKET, key };
}

export async function uploadToAzure({ blobPath, body, contentType }) {
  const containerClient = azure.getContainerClient(AZURE_CONTAINER);
  await containerClient.createIfNotExists();
  const blockBlob = containerClient.getBlockBlobClient(blobPath);
  await blockBlob.uploadData(body, { blobHTTPHeaders: { blobContentType: contentType } });
  return { container: AZURE_CONTAINER, blob: blobPath };
}

// Convenience wrappers specifically for resources (keeps server.js tidy)
export async function putResourceToR2({ id, name, mime, buffer }) {
  const key = resourcesKey({ id, fileName: name, mime });
  const meta = await uploadToR2({ key, body: buffer, contentType: mime });
  return meta; // { bucket, key }
}

export async function putResourceToAzure({ id, name, mime, buffer }) {
  const blob = resourcesKey({ id, fileName: name, mime });
  const meta = await uploadToAzure({ blobPath: blob, body: buffer, contentType: mime });
  return meta; // { container, blob }
}

// ---------- Signed URLs (Download/Preview) ----------
export async function r2SignedUrl({ bucket, key, expiresSeconds = 900, disposition, filename }) {
  const cmd = new GetObjectCommand({
    Bucket: bucket,
    Key: key,
    ...(disposition && filename
      ? { ResponseContentDisposition: `${disposition}; filename="${encodeURIComponent(filename)}"` }
      : {}),
  });
  return getSignedUrl(r2, cmd, { expiresIn: expiresSeconds });
}

function parseAzureConn(cs) {
  // "DefaultEndpointsProtocol=https;AccountName=...;AccountKey=...;EndpointSuffix=core.windows.net"
  const entries = Object.fromEntries(
    cs.split(';').filter(Boolean).map(kv => kv.split('='))
  );
  return { accountName: entries.AccountName, accountKey: entries.AccountKey };
}

export async function azureSasUrl({ container, blob, expiresMinutes = 15 }) {
  const { accountName, accountKey } = parseAzureConn(AZURE_CONN);
  const cred = new StorageSharedKeyCredential(accountName, accountKey);
  const now = new Date();
  const exp = new Date(now.getTime() + expiresMinutes * 60 * 1000);

  const sas = generateBlobSASQueryParameters({
    containerName: container,
    blobName: blob,
    permissions: BlobSASPermissions.parse('r'), // read only
    startsOn: new Date(now.getTime() - 60 * 1000), // 1 min clock skew
    expiresOn: exp,
    protocol: SASProtocol.Https,
  }, cred).toString();

  const containerClient = azure.getContainerClient(container);
  const blobClient = containerClient.getBlobClient(blob);
  return `${blobClient.url}?${sas}`;
}

// Helper that mirrors your /resources/:id/download semantics
export async function signedResourceUrl({ provider = 'r2', r2Meta, azureMeta, filename, expiresSeconds = 900, disposition = 'inline' }) {
  if (String(provider).toLowerCase() === 'azure') {
    const url = await azureSasUrl({
      container: azureMeta.container,
      blob: azureMeta.blob,
      expiresMinutes: Math.ceil(expiresSeconds / 60),
    });
    return { url, expiresInSeconds: Math.ceil(expiresSeconds / 60) * 60, provider: 'azure' };
  }
  const url = await r2SignedUrl({
    bucket: r2Meta.bucket,
    key: r2Meta.key,
    expiresSeconds,
    disposition,
    filename,
  });
  return { url, expiresInSeconds: expiresSeconds, provider: 'r2' };
}

// ---------- Streaming (pipe through your API) ----------
export async function streamFromR2({ bucket, key }) {
  const res = await r2.send(new GetObjectCommand({ Bucket: bucket, Key: key }));
  // res.Body is a Node stream
  return {
    stream: res.Body,
    contentType: res.ContentType || 'application/octet-stream',
    contentLength: res.ContentLength,
  };
}

export async function streamFromAzure({ container, blob }) {
  const containerClient = azure.getContainerClient(container);
  const blobClient = containerClient.getBlockBlobClient(blob);
  const resp = await blobClient.download();
  return {
    stream: resp.readableStreamBody,
    contentType: resp.contentType || 'application/octet-stream',
    contentLength: resp.contentLength,
  };
}

// Convenience for resource streaming
export async function streamResource({ provider = 'r2', r2Meta, azureMeta }) {
  if (String(provider).toLowerCase() === 'azure') {
    return streamFromAzure({ container: azureMeta.container, blob: azureMeta.blob });
  }
  return streamFromR2({ bucket: r2Meta.bucket, key: r2Meta.key });
}

// ---------- Boot helper ----------
export async function ensureStorageReady() {
  assertEnv();
  await ensureR2Bucket();
  await ensureAzureContainer();
}
