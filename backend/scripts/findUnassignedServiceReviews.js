/**
 * Find ServiceReviews with no assigned consultant.
 * Conditions:
 *   - consultantId === null
 *   - consultantId === ""
 *   - consultantId field is missing
 *
 * Usage:
 *   node scripts/findUnassignedServiceReviews.js
 *   node scripts/findUnassignedServiceReviews.js --csv=unassigned.csv
 */

import { db } from "../db/firebaseAdmin.js";
import fs from "node:fs";

const COLLECTION = "ServiceReviews";

// Simple CSV escaper
function csvEscape(val) {
  if (val === null || val === undefined) return "";
  const s = String(val);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

async function main() {
  const csvArg = process.argv.find(a => a.startsWith("--csv="));
  const csvPath = csvArg ? csvArg.split("=")[1] : null;

  console.log("🔎 Searching Firestore for unassigned ServiceReviews...");

  const col = db.collection(COLLECTION);

  // 1) consultantId === null
  const snapNull = await col.where("consultantId", "==", null).get();

  // 2) consultantId === "" (empty string)
  const snapEmpty = await col.where("consultantId", "==", "").get();

  // 3) consultantId missing → scan all docs and filter
  //    (Firestore can’t query for “field missing” directly)
  const streamAll = col.stream();

  // Use a Map to de-duplicate across queries
  const unassigned = new Map();

  // Add null matches
  for (const d of snapNull.docs) {
    unassigned.set(d.id, d.data());
  }

  // Add empty string matches
  for (const d of snapEmpty.docs) {
    unassigned.set(d.id, d.data());
  }

  // Scan for missing field
  let scanned = 0;
  for await (const doc of streamAll) {
    scanned++;
    const data = doc.data() || {};
    if (!Object.prototype.hasOwnProperty.call(data, "consultantId")) {
      unassigned.set(doc.id, data);
    }
  }

  console.log(`🧮 Scanned ${scanned} docs. Found ${unassigned.size} unassigned.`);

  // Pretty print a short summary
  for (const [id, data] of unassigned) {
    const createdAt = data.createdAt ?? "—";
    const serviceType = data.serviceType ?? "—";
    const status = data.status ?? "—";
    console.log("────────────────────────────────────────");
    console.log(`📄 ${id}`);
    console.log(`   serviceType: ${serviceType}`);
    console.log(`   status:      ${status}`);
    console.log(`   createdAt:   ${createdAt}`);
  }

  // Optional CSV export
  if (csvPath) {
    const rows = [
      ["id", "serviceType", "status", "createdAt", "consultantId"].map(csvEscape).join(","),
      ...[...unassigned.entries()].map(([id, d]) =>
        [
          id,
          d.serviceType ?? "",
          d.status ?? "",
          d.createdAt ?? "",
          d.consultantId ?? ""
        ].map(csvEscape).join(",")
      ),
    ];
    fs.writeFileSync(csvPath, rows.join("\n"));
    console.log(`💾 Wrote CSV: ${csvPath}`);
  }

  console.log("✅ Done.");
}

main().catch(err => {
  console.error("❌ Error:", err);
  process.exitCode = 1;
});
