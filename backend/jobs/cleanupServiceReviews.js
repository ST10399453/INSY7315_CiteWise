
import { db } from "../db/firebaseAdmin.js";

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * Cleanup routine:
 * - Find ServiceReviews with:
 *    - status === "Completed"
 *    - updatedAt <= now - 7 days
 * - Check linked quotation:
 *    - quotation.status === "approved"
 * - Delete ONLY the ServiceReview document (keep the quotation for records)
 */
export async function cleanupOldCompletedServiceReviews() {
  const now = Date.now();
  const cutoff = now - SEVEN_DAYS_MS;

  console.log("[cleanup] now =", new Date(now).toISOString());
  console.log("[cleanup] cutoff (7 days ago) =", new Date(cutoff).toISOString());

  // 1) Find ServiceReviews that are Completed and older than 7 days
  const snapshot = await db
    .collection("ServiceReviews")
    .where("status", "==", "Completed")
    .where("updatedAt", "<=", cutoff) // updatedAt stored as Date.now() (number)
    .get();

  if (snapshot.empty) {
    console.log("[cleanup] No ServiceReviews eligible for deletion.");
    return {
      processedServiceReviews: 0,
      deletedServiceReviews: 0,
      skippedNoQuotationId: 0,
      skippedMissingQuotation: 0,
      skippedUnapprovedQuotation: 0,
    };
  }

  console.log("[cleanup] Found", snapshot.size, "ServiceReviews candidates");

  const BATCH_LIMIT = 500;
  let batch = db.batch();
  let batchCount = 0;

  let processedServiceReviews = 0;
  let deletedServiceReviews = 0;
  let skippedNoQuotationId = 0;
  let skippedMissingQuotation = 0;
  let skippedUnapprovedQuotation = 0;

  for (const doc of snapshot.docs) {
    processedServiceReviews++;
    const data = doc.data() || {};
    const quotationId = data.quotationId;

    // 2) Must have quotationId
    if (!quotationId) {
      skippedNoQuotationId++;
      console.log(
        `[cleanup] Skipping ServiceReview ${doc.id} – no quotationId on document`
      );
      continue;
    }

    const quoteRef = db.collection("Quotations").doc(String(quotationId));
    const quoteSnap = await quoteRef.get();

    if (!quoteSnap.exists) {
      skippedMissingQuotation++;
      console.log(
        `[cleanup] Skipping ServiceReview ${doc.id} – quotation ${quotationId} not found`
      );
      continue;
    }

    const quote = quoteSnap.data() || {};
    const quoteStatus = String(quote.status || "").toLowerCase();

    // 3) Only proceed if quotation is approved
    if (quoteStatus !== "approved") {
      skippedUnapprovedQuotation++;
      console.log(
        `[cleanup] Skipping ServiceReview ${doc.id} – quotation ${quotationId} status is "${quote.status}"`
      );
      continue;
    }

    // 4) Delete ONLY the ServiceReview (keep the quotation for record keeping)
    batch.delete(doc.ref);
    deletedServiceReviews++;
    batchCount++;

    // Commit every 500 operations
    if (batchCount === BATCH_LIMIT) {
      await batch.commit();
      console.log("[cleanup] Committed batch of", batchCount, "ServiceReview deletes");
      batch = db.batch();
      batchCount = 0;
    }
  }

  // Commit remaining deletes
  if (batchCount > 0) {
    await batch.commit();
    console.log("[cleanup] Committed final batch of", batchCount, "ServiceReview deletes");
  }

  console.log("[cleanup] Completed.");
  return {
    processedServiceReviews,
    deletedServiceReviews,
    skippedNoQuotationId,
    skippedMissingQuotation,
    skippedUnapprovedQuotation,
  };
}
