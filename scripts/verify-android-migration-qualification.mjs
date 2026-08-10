#!/usr/bin/env node

import { resolve } from "node:path";
import {
  AndroidMigrationQualificationError,
  lintAndroidMigrationQualificationTemplates,
  verifyAndroidMigrationQualificationV7,
} from "./lib/android-migration-qualification-v7.mjs";

const root = resolve(new URL("..", import.meta.url).pathname);

try {
  if (process.argv.length === 3 && process.argv[2] === "--lint-templates") {
    lintAndroidMigrationQualificationTemplates({ root });
  } else if (process.argv.length === 3 && process.argv[2] === "--verify-qualified") {
    const result = verifyAndroidMigrationQualificationV7({ root });
    process.stdout.write(`receiptSha256=${result.receiptSha256}\n`);
  } else {
    throw new AndroidMigrationQualificationError(
      "usage: verify-android-migration-qualification.mjs --lint-templates|--verify-qualified",
    );
  }
} catch (error) {
  process.stderr.write(`error: ${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
}
