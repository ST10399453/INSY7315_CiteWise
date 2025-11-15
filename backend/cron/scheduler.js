import cron from "node-cron";
import { cleanupOldCompletedServiceReviews } from "../jobs/cleanupServiceReviews.js";

/**
 * CRON FORMAT: "m h dom mon dow"
 *  - m (minutes): 0-59
 *  - h (hours):   0-23
 *  - dom:         day of month (1-31)
 *  - mon:         month (1-12)
 *  - dow:         day of week (0-6, 0 = Sunday)
 *
 * Here: "0 3 * * *" = every day at 03:00 (3 AM) server time.
 */
cron.schedule("0 3 * * *", async () => {
  console.log("[scheduler] Running daily ServiceReview cleanup...");

  try {
    const result = await cleanupOldCompletedServiceReviews();
    console.log("[scheduler] Cleanup result:", result);
  } catch (err) {
    console.error("[scheduler] Cleanup error:", err);
  }
});

/**
 * NPM. 2025. “Node-Cron”.
 * <https://www.npmjs.com/package/node-cron>
 *  [accessed 25 October 2025].
 */

