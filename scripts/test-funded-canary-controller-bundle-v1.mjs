#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  chmodSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import {
  extractFundedCanaryControllerBundleV1,
  FUNDED_CANARY_CONTROLLER_BUNDLE_FILES,
} from "./lib/funded-canary-controller-bundle-v1.mjs";

const BLOCK = 512;
const EXPECTED_NAMES = Object.keys(FUNDED_CANARY_CONTROLLER_BUNDLE_FILES).sort();

const octal = (value, length) =>
  `${value.toString(8).padStart(length - 1, "0")}\0`;

const tarHeader = ({ name, bytes, mode = 0o600, type = "0" }) => {
  const header = Buffer.alloc(BLOCK);
  header.write(name, 0, 100, "ascii");
  header.write(octal(mode, 8), 100, 8, "ascii");
  header.write(octal(0, 8), 108, 8, "ascii");
  header.write(octal(0, 8), 116, 8, "ascii");
  header.write(octal(bytes.length, 12), 124, 12, "ascii");
  header.write(octal(1, 12), 136, 12, "ascii");
  header.fill(0x20, 148, 156);
  header.write(type, 156, 1, "ascii");
  header.write("ustar\0", 257, 6, "binary");
  header.write("00", 263, 2, "ascii");
  const checksum = header.reduce((sum, value) => sum + value, 0);
  header.write(`${checksum.toString(8).padStart(6, "0")}\0 `, 148, 8, "ascii");
  return header;
};

const defaultEntries = () =>
  EXPECTED_NAMES.map((name) => ({
    name,
    bytes: name.endsWith(".json")
      ? Buffer.from('{"status":"synthetic-test"}\n')
      : Buffer.from(`synthetic ${name}\n`),
  }));

const buildTar = (entries) => {
  const blocks = [];
  for (const entry of entries) {
    blocks.push(tarHeader(entry));
    blocks.push(entry.bytes);
    const padding = (BLOCK - (entry.bytes.length % BLOCK)) % BLOCK;
    if (padding > 0) blocks.push(Buffer.alloc(padding));
  }
  blocks.push(Buffer.alloc(BLOCK * 2));
  return Buffer.concat(blocks);
};

const createFixture = (entries = defaultEntries()) => {
  const root = realpathSync(
    resolve(mkdtempSync(join(tmpdir(), "sora-canary-controller-bundle-"))),
  );
  const tarPath = join(root, "evidence.tar");
  const outputRoot = join(root, "evidence");
  writeFileSync(tarPath, buildTar(entries), { mode: 0o600 });
  chmodSync(tarPath, 0o600);
  return { root, tarPath, outputRoot };
};

const positive = createFixture();
try {
  const receipt = extractFundedCanaryControllerBundleV1({
    tarPath: positive.tarPath,
    outputRoot: positive.outputRoot,
  });
  assert.equal(receipt.status, "extracted-unreviewed");
  assert.equal(receipt.fileCount, EXPECTED_NAMES.length);
  assert.deepEqual(Object.keys(receipt.files), EXPECTED_NAMES);
  assert.equal(receipt.authorization.authorizesRelease, false);
  assert.equal(receipt.authorization.authorizesProductionMutation, false);
  assert.equal(
    readFileSync(join(positive.outputRoot, "taira-canary.json"), "utf8"),
    '{"status":"synthetic-test"}\n',
  );
} finally {
  rmSync(positive.root, { recursive: true, force: true });
}

const mutations = [
  [
    "missing-entry",
    "FUNDED_CANARY_BUNDLE_INVENTORY_INVALID",
    () => createFixture(defaultEntries().slice(0, -1)),
  ],
  [
    "unknown-entry",
    "FUNDED_CANARY_BUNDLE_TAR_ENTRY_INVALID",
    () =>
      createFixture([
        ...defaultEntries(),
        { name: "unknown.json", bytes: Buffer.from("{}\n") },
      ]),
  ],
  [
    "duplicate-entry",
    "FUNDED_CANARY_BUNDLE_TAR_DUPLICATE_ENTRY",
    () => {
      const entries = defaultEntries();
      return createFixture([...entries, structuredClone(entries[0])]);
    },
  ],
  [
    "symbolic-entry",
    "FUNDED_CANARY_BUNDLE_TAR_ENTRY_INVALID",
    () => {
      const entries = defaultEntries();
      entries[0].type = "2";
      return createFixture(entries);
    },
  ],
  [
    "world-readable-entry-mode",
    "FUNDED_CANARY_BUNDLE_TAR_ENTRY_BOUNDS_INVALID",
    () => {
      const entries = defaultEntries();
      entries[0].mode = 0o644;
      return createFixture(entries);
    },
  ],
  [
    "checksum-drift",
    "FUNDED_CANARY_BUNDLE_TAR_CHECKSUM_INVALID",
    () => {
      const fixture = createFixture();
      const bytes = readFileSync(fixture.tarPath);
      bytes[10] ^= 1;
      writeFileSync(fixture.tarPath, bytes, { mode: 0o600 });
      return fixture;
    },
  ],
  [
    "nonzero-padding",
    "FUNDED_CANARY_BUNDLE_TAR_PADDING_INVALID",
    () => {
      const fixture = createFixture();
      const bytes = readFileSync(fixture.tarPath);
      const firstSize = defaultEntries()[0].bytes.length;
      bytes[BLOCK + firstSize] = 1;
      writeFileSync(fixture.tarPath, bytes, { mode: 0o600 });
      return fixture;
    },
  ],
  [
    "duplicate-json-key",
    "FUNDED_CANARY_BUNDLE_JSON_INVALID",
    () => {
      const entries = defaultEntries();
      const json = entries.find((entry) => entry.name === "taira-canary.json");
      json.bytes = Buffer.from('{"status":"a","status":"b"}\n');
      return createFixture(entries);
    },
  ],
  [
    "unprotected-tar-mode",
    "FUNDED_CANARY_BUNDLE_PATH_INVALID",
    () => {
      const fixture = createFixture();
      chmodSync(fixture.tarPath, 0o644);
      return fixture;
    },
  ],
  [
    "preexisting-output",
    "FUNDED_CANARY_BUNDLE_OUTPUT_ROOT_ALREADY_EXISTS",
    () => {
      const fixture = createFixture();
      mkdirSync(fixture.outputRoot, { mode: 0o700 });
      return fixture;
    },
  ],
];

for (const [name, expectedCode, fixtureFactory] of mutations) {
  const fixture = fixtureFactory();
  try {
    assert.throws(
      () =>
        extractFundedCanaryControllerBundleV1({
          tarPath: fixture.tarPath,
          outputRoot: fixture.outputRoot,
        }),
      (error) => error instanceof Error && error.message === expectedCode,
      name,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
}

process.stdout.write(
  `Funded canary controller bundle v1: positive extraction and ${mutations.length} fail-closed mutations passed.\n`,
);
