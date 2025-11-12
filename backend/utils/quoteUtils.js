import * as pdf from "pdf-parse";
import mammoth from "mammoth";

/** Tokenizer good enough for quotes */
export function countWordsFromText(text = "") {
  if (!text || typeof text !== "string") return 0;
  return text
    .replace(/\s+/g, " ")
    .trim()
    .split(/[^\p{L}\p{N}'-]+/u)
    .filter(Boolean).length;
}

/** buffer + mime/extension → word count */
export async function wordCountFromBuffer({ buffer, mime, originalName }) {
  try {
    if (!buffer) return 0;
    const name = (originalName || "").toLowerCase();

    if (mime === "application/pdf" || name.endsWith(".pdf")) {
      const res = await pdf(buffer); 
      return countWordsFromText(res.text || "");
    }
    if (
      mime === "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
      name.endsWith(".docx")
    ) {
      const { value } = await mammoth.extractRawText({ buffer });
      return countWordsFromText(value || "");
    }
    if ((mime && mime.startsWith("text/")) || name.endsWith(".txt") || name.endsWith(".md")) {
      return countWordsFromText(buffer.toString("utf8"));
    }

    // fallback
    return countWordsFromText(buffer.toString("utf8"));
  } catch (e) {
    console.error("wordCountFromBuffer failed:", e);
    return 0;
  }
}

// ---- Pricing table (tweak these for your business) ----
const RATE_TABLE = {
  PROOFREADING_EDITING: 0.02,
  FORMATTING_REFERENCING: 0.015,
  DATA_ANALYSIS_SUPPORT: 0.03,
  RESEARCH_METHODOLOGY_COACHING: 0.025,
  TRANSLATION: 0.035,
  OTHER: 0.02,
};
const URGENCY = { LOW: 1.0, MEDIUM: 1.15, HIGH: 1.3 };

export function computeQuote({ serviceType = "OTHER", priority = "LOW", words = 0, currency = "USD" }) {
  const base = RATE_TABLE[serviceType] ?? RATE_TABLE.OTHER;
  const mult = URGENCY[priority] ?? 1.0;
  const amount = Math.round(words * base * mult * 100) / 100;
  return { currency, ratePerWord: base, urgencyMultiplier: mult, amount };
}

/** If your R2 adapter returns publicUrl, pass it through. Otherwise return null. */
export async function publicUrlFromR2Meta(r2Meta) {
  if (r2Meta?.publicUrl) return r2Meta.publicUrl;
  return null;
}
