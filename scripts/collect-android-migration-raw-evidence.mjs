#!/usr/bin/env node

import { resolve } from "node:path";

import {
  AndroidMigrationRawEvidenceError,
  collectAndroidMigrationRawEvidenceV1,
} from "./lib/android-migration-raw-evidence-v1.mjs";

const usage =
  "usage: collect-android-migration-raw-evidence.mjs --raw-run-root /absolute/protected/run/root";

try {
  if (
    process.argv.length !== 4 ||
    process.argv[2] !== "--raw-run-root" ||
    !process.argv[3].startsWith("/") ||
    resolve(process.argv[3]) !== process.argv[3]
  ) {
    throw new AndroidMigrationRawEvidenceError(usage);
  }
  const result = collectAndroidMigrationRawEvidenceV1({
    root: process.argv[3],
  });
  process.stdout.write(result.bytes);
} catch (error) {
  process.stderr.write(
    `error: ${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
}
