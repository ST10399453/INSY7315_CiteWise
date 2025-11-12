import * as pdf from "pdf-parse";
import mammoth from "mammoth";
import Tesseract from "tesseract.js";

/** Tokenizer good enough for quotes */
export function countWordsFromText(text = "") {
  if (!text || typeof text !== "string") return 0;
  return text
    .replace(/\s+/g, " ")
    .trim()
    .split(/[^\p{L}\p{N}'-]+/u)
    .filter(Boolean).length;
}

/**
 * Basic OCR using Tesseract for image-like content.
 * NOTE: This is CPU-heavy; consider offloading if needed.
 */
async function ocrCountFromBuffer(buffer) {
  try {
    if (!buffer) return 0;

    const result = await Tesseract.recognize(buffer, "eng");
    const text = result?.data?.text || "";
    return countWordsFromText(text);
  } catch (e) {
    console.error("ocrCountFromBuffer failed:", e);
    return 0;
  }
}

/** buffer + mime/extension → word count (with OCR fallback) */
export async function wordCountFromBuffer({ buffer, mime, originalName }) {
  try {
    if (!buffer) return 0;
    const name = (originalName || "").toLowerCase();

    let words = 0;

    // --- 1) Structured formats first ---
    if (mime === "application/pdf" || name.endsWith(".pdf")) {
      const res = await pdf(buffer);
      words = countWordsFromText(res.text || "");
    } else if (
      mime === "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
      name.endsWith(".docx")
    ) {
      const { value } = await mammoth.extractRawText({ buffer });
      words = countWordsFromText(value || "");
    } else if (
      (mime && mime.startsWith("text/")) ||
      name.endsWith(".txt") ||
      name.endsWith(".md")
    ) {
      words = countWordsFromText(buffer.toString("utf8"));
    } else {
      // Generic fallback → treat as UTF-8 text
      words = countWordsFromText(buffer.toString("utf8"));
    }

    // --- 2) If zero words, try OCR for image-like content ---
    const looksLikeImage =
      (mime && mime.startsWith("image/")) ||
      name.endsWith(".png") ||
      name.endsWith(".jpg") ||
      name.endsWith(".jpeg") ||
      name.endsWith(".tif") ||
      name.endsWith(".tiff");

    // For **scanned PDFs**, pdf-parse will give text = "".
    // Proper OCR for PDFs would require converting pages to images first.
    const looksLikeScannedPdf =
      (mime === "application/pdf" || name.endsWith(".pdf")) && words === 0;

    if (words === 0 && (looksLikeImage || looksLikeScannedPdf)) {
      console.warn(
        "wordCountFromBuffer: no text extracted, attempting OCR (mime=%s, name=%s)",
        mime,
        originalName
      );
      const ocrWords = await ocrCountFromBuffer(buffer);
      if (ocrWords > 0) {
        words = ocrWords;
      }
    }

    return words;
  } catch (e) {
    console.error("wordCountFromBuffer failed:", e);
    return 0;
  }
}

// ---- Pricing table (ZAR/Rand-based) ----
// Adjust these rates to what you actually want to charge *per word* in Rands.
const RATE_TABLE = {
  PROOFREADING_EDITING: 0.20,          // R 0.20 per word
  FORMATTING_REFERENCING: 0.15,       // R 0.15 per word
  DATA_ANALYSIS_SUPPORT: 0.30,        // R 0.30 per word
  RESEARCH_METHODOLOGY_COACHING: 0.25,// R 0.25 per word
  TRANSLATION: 0.35,                  // R 0.35 per word
  OTHER: 0.20,
};

const URGENCY = { LOW: 1.0, MEDIUM: 1.15, HIGH: 1.3 };

/**
 * Compute quote in Rands by default.
 * - serviceType: one of the ServiceType enums
 * - priority: LOW | MEDIUM | HIGH
 * - words: integer word count
 * - currency: "R" by default, but you can override (e.g. "ZAR" / "USD")
 */
export function computeQuote({
  serviceType = "OTHER",
  priority = "LOW",
  words = 0,
  currency = "R",
}) {
  const base = RATE_TABLE[serviceType] ?? RATE_TABLE.OTHER;
  const mult = URGENCY[priority] ?? 1.0;

  // amount in currency (R)
  const amount = Math.round(words * base * mult * 100) / 100;

  return { currency, ratePerWord: base, urgencyMultiplier: mult, amount };
}

/** If your R2 adapter returns publicUrl, pass it through. Otherwise return null. */
export async function publicUrlFromR2Meta(r2Meta) {
  if (r2Meta?.publicUrl) return r2Meta.publicUrl;
  return null;
}
