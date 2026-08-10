#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  realpathSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import {
  AndroidMigrationQualificationError,
  lintAndroidMigrationQualificationTemplates,
} from "./lib/android-migration-qualification-v7.mjs";

const sourceRoot = realpathSync(
  new URL("..", import.meta.url).pathname.replace(/\/$/, ""),
);
const fixturePaths = [
  "android-migration-matrix.blocked.json",
  "android-migration-evidence.blocked.json",
  "android-migration-trust.blocked.json",
];

const createRoot = () => {
  const root = realpathSync(
    mkdtempSync(join(tmpdir(), "sora-android-migration-v7-contract-")),
  );
  const destination = join(root, "docs/modernization/qualification");
  mkdirSync(destination, { recursive: true });
  for (const name of fixturePaths) {
    writeFileSync(
      join(destination, name),
      readFileSync(
        join(sourceRoot, "docs/modernization/qualification", name),
      ),
      { flag: "wx", mode: 0o600 },
    );
  }
  return { root, destination };
};

const mutateJson = (path, mutate) => {
  const value = JSON.parse(readFileSync(path, "utf8"));
  mutate(value);
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
};

const expectRejected = (file, mutate, message) => {
  const fixture = createRoot();
  try {
    mutateJson(join(fixture.destination, file), mutate);
    assert.throws(
      () => lintAndroidMigrationQualificationTemplates({ root: fixture.root }),
      (error) =>
        error instanceof AndroidMigrationQualificationError &&
        error.message.includes(message),
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
};

const positive = createRoot();
try {
  lintAndroidMigrationQualificationTemplates({ root: positive.root });
} finally {
  rmSync(positive.root, { recursive: true, force: true });
}

expectRejected(
  "android-migration-matrix.blocked.json",
  (receipt) => {
    receipt.schemaVersion = 6;
    receipt.contractId = "sora-android-wallet-migration-qualification-v6";
  },
  "stale v6 or older",
);

expectRejected(
  "android-migration-evidence.blocked.json",
  (evidence) => {
    evidence.schemaVersion = 1;
    evidence.contractId = "sora-android-wallet-migration-evidence-v1";
  },
  "stale v1 or older",
);

expectRejected(
  "android-migration-evidence.blocked.json",
  (evidence) => {
    delete evidence.artifacts.rawExecutionEvidence;
  },
  "missing or unreviewed fields",
);

expectRejected(
  "android-migration-evidence.blocked.json",
  (evidence) => {
    evidence.artifacts.rawExecutionEvidence.sha256 = "1".repeat(64);
  },
  "fabricated qualification state",
);

expectRejected(
  "android-migration-matrix.blocked.json",
  (receipt) => {
    receipt.rawResultFileCount = 1;
  },
  "fabricated qualification state",
);

expectRejected(
  "android-migration-matrix.blocked.json",
  (receipt) => {
    receipt.qualificationChecks.rawExecutionEvidenceReviewed = true;
  },
  "fabricated qualification state",
);

process.stdout.write(
  "Android migration qualification v7/v2: exact templates and 6 fail-closed stale/fabricated mutations passed\n",
);
