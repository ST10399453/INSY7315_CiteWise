import crypto from "crypto";
import { S3Client, PutObjectCommand, GetObjectCommand, HeadBucketCommand, CreateBucketCommand } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import "dotenv/config";

function env() {
  const { R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY } = process.env;
  return { R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY };
}

let r2Client;
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

export function newFileId() { return crypto.randomUUID(); }
export function safeName(name = "file") { return name.replace(/[^\w.\- ]+/g, "_").trim().slice(0, 180) || "file"; }
function extFromMime(mime = "") { const m = String(mime).toLowerCase(); if (m === "application/pdf") return ".pdf"; if (m.startsWith("image/")) return `.${m.split("/")[1] || "img"}`; if (m === "text/plain") return ".txt"; return ""; }
export function uploadsKey({ id, fileName, mime }) { const clean = safeName(fileName); const ext = extFromMime(mime); return `uploads/${id}/${clean}${ext && !clean.toLowerCase().endsWith(ext) ? ext : ""}`; }
export function resourcesKey({ id, fileName, mime }) { const clean = safeName(fileName); const ext = extFromMime(mime); return `resources/${id}/${clean}${ext && !clean.toLowerCase().endsWith(ext) ? ext : ""}`; }

export async function ensureR2Bucket() {
  const { R2_BUCKET } = env();
  if (!R2_BUCKET) throw new Error("R2_BUCKET not set");
  try {
    await r2().send(new HeadBucketCommand({ Bucket: R2_BUCKET }));
  } catch {
    await r2().send(new CreateBucketCommand({ Bucket: R2_BUCKET }));
  }
}

export async function uploadToR2({ key, body, contentType }) {
  const { R2_BUCKET } = env();
  await r2().send(new PutObjectCommand({ Bucket: R2_BUCKET, Key: key, Body: body, ContentType: contentType }));
  return { bucket: R2_BUCKET, key };
}

export async function r2SignedUrl({ bucket, key, expiresSeconds = 900, disposition, filename }) {
  const cmd = new GetObjectCommand({
    Bucket: bucket,
    Key: key,
    ...(disposition && filename ? { ResponseContentDisposition: `${disposition}; filename="${encodeURIComponent(filename)}"` } : {}),
  });
  return getSignedUrl(r2(), cmd, { expiresIn: expiresSeconds });
}

export async function streamFromR2({ bucket, key }) {
  const res = await r2().send(new GetObjectCommand({ Bucket: bucket, Key: key }));
  return { stream: res.Body, contentType: res.ContentType || "application/octet-stream", contentLength: res.ContentLength };
}

export async function ensureStorageReady() { assertEnv(); await ensureR2Bucket(); }
