// backend/blobs/storage.js
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

const R2_ENDPOINT = process.env.R2_ENDPOINT;           // e.g. https://<accountid>.r2.cloudflarestorage.com
const R2_BUCKET   = process.env.R2_BUCKET;              // e.g. citewise
const r2 = new S3Client({
  region: 'auto',
  endpoint: R2_ENDPOINT,
  credentials: {
    accessKeyId: process.env.R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.R2_SECRET_ACCESS_KEY,
  },
});

const AZURE_CONN = process.env.AZURE_STORAGE_CONNECTION_STRING;
const AZURE_CONTAINER = process.env.AZURE_BLOB_CONTAINER; // e.g. citewise
const azure = BlobServiceClient.fromConnectionString(AZURE_CONN);

// ---------- Utilities ----------
export function newFileId() {
  return crypto.randomUUID();
}

export function safeName(name = 'file') {
  return name.replace(/[^\w.\- ]+/g, '_').trim().slice(0, 180) || 'file';
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

// ---------- Uploads ----------
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

// ---------- Signed URLs (Download/Preview) ----------
export async function r2SignedUrl({ bucket, key, expiresSeconds = 900, disposition, filename }) { //Temporary connection
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

export async function azureSasUrl({ container, blob, expiresMinutes = 15 }) { //Creates temporary connection
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
