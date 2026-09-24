import { spawnSync } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const verifier = join(root, "scripts/verify-production-modernization.mjs");
const result = spawnSync(process.execPath, [verifier, "--dependency-preflight"], {
  cwd: root,
  encoding: "utf8",
  timeout: 120_000,
});
if (result.error) throw result.error;

let report;
try {
  report = JSON.parse(result.stdout);
} catch {
  throw new Error(`Dependency preflight did not emit JSON: ${result.stderr}`);
}

if (
  report.mode !== "dependency-preflight" ||
  report.inventoryStatus !== "STABLE" ||
  !Array.isArray(report.failures) ||
  report.failures.length !== 0 ||
  !Array.isArray(report.releaseBlockers) ||
  (result.status !== 0 && !(result.status === 1 && report.releaseBlockers.length > 0))
) {
  throw new Error(`Dependency preflight structure failed: ${JSON.stringify(report)}`);
}

console.log(`Dependency preflight structure: STABLE (${report.releaseBlockers.length} release blockers remain)`);
