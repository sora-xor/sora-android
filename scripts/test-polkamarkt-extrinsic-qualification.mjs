#!/usr/bin/env node

/**
 * Hermetic regression and mutation harness for the Polkamarkt receipt merger.
 *
 * All candidate receipts below are deliberately public synthetic data. The
 * harness checks only the merger's validation, review, and fail-closed
 * contract. It never fabricates a native cryptographic proof and therefore
 * must not produce a qualified fixture until reviewed platform proofs exist.
 */

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  createHash,
  createPrivateKey,
  generateKeyPairSync,
  sign as signEd25519,
} from "node:crypto";
import {
  chmodSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { readStrictJsonFile } from "./lib/strict-evidence.mjs";

const SCRIPT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const MERGER_PATH = resolve(
  SCRIPT_DIRECTORY,
  "qualify-polkamarkt-extrinsic-receipts.mjs",
);
const FIXTURE_PATH = resolve(
  SCRIPT_DIRECTORY,
  "../feature_polkaswap_impl/src/test/resources/polkamarkt_web_contract.json",
);

const CANDIDATE_SCHEMA =
  "sora-mobile-polkamarkt-platform-extrinsic-receipt-v1";
const REVIEW_SCHEMA = "sora-mobile-polkamarkt-extrinsic-review-v1";
const REVIEW_KEY_SCHEMA =
  "sora-mobile-polkamarkt-extrinsic-review-key-v1";
const REVIEW_SIGNATURE_SCHEMA =
  "sora-mobile-polkamarkt-extrinsic-review-signature-v1";
const QUALIFIED_SCHEMA =
  "sora-mobile-polkamarkt-full-extrinsic-receipt-v1";
const BLOCKER =
  "Generate independent reference, Android, and iOS receipts from the pinned web revision and exact runtime metadata; independently sign the composite receipt; then prove full call bytes, signing prehashes, signatures, signed extrinsics, and decoded projections before enabling mutations.";
const WEB_REVISION = "893783ba6a19c33043eb5dabe42d949c14d0f257";
const RUNTIME_REVISION = "411dcdb70c5c00b21482a44d02334840d5f338c6";
const METADATA_SHA256 =
  "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf";
const GENESIS_HASH =
  "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5";
const SPEC_VERSION = 130;
const TRANSACTION_VERSION = 130;
const PLATFORMS = ["reference", "android", "ios"];
const PRIVACY_FIELDS = [
  "privateKeysIncluded",
  "seedsIncluded",
  "mnemonicsIncluded",
  "walletAddressesIncluded",
  "productionAccountIdentifiersIncluded",
];
const MAXIMUM_INSTALLED_FIXTURE_BYTES = 16 * 1024 * 1024;

const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const clone = (value) => JSON.parse(JSON.stringify(value));
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
const replaceLastNibble = (value) =>
  `${value.slice(0, -1)}${value.endsWith("a") ? "b" : "a"}`;

const stableValue = (value) => {
  if (Array.isArray(value)) return value.map(stableValue);
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, stableValue(value[key])]),
    );
  }
  return value;
};
const sha256StableJson = (value) =>
  sha256(Buffer.from(JSON.stringify(stableValue(value)), "utf8"));

// Substrate uses BLAKE2b's 32-byte parameterization, not a truncation of
// BLAKE2b-512. Keep this dependency-free implementation aligned with the
// merger and pin it with public known-answer vectors.
const BLAKE2B_MASK = (1n << 64n) - 1n;
const BLAKE2B_IV = [
  0x6a09e667f3bcc908n,
  0xbb67ae8584caa73bn,
  0x3c6ef372fe94f82bn,
  0xa54ff53a5f1d36f1n,
  0x510e527fade682d1n,
  0x9b05688c2b3e6c1fn,
  0x1f83d9abfb41bd6bn,
  0x5be0cd19137e2179n,
];
const BLAKE2B_SIGMA = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
  [14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3],
  [11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4],
  [7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8],
  [9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13],
  [2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9],
  [12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11],
  [13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10],
  [6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5],
  [10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0],
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
  [14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3],
];

const rotateRight = (value, bits) =>
  ((value >> BigInt(bits)) |
    ((value << BigInt(64 - bits)) & BLAKE2B_MASK)) &
  BLAKE2B_MASK;

const readWord = (block, offset) => {
  let value = 0n;
  for (let index = 0; index < 8; index += 1) {
    value |= BigInt(block[offset + index]) << BigInt(index * 8);
  }
  return value;
};

const mix = (state, a, b, c, d, x, y) => {
  state[a] = (state[a] + state[b] + x) & BLAKE2B_MASK;
  state[d] = rotateRight(state[d] ^ state[a], 32);
  state[c] = (state[c] + state[d]) & BLAKE2B_MASK;
  state[b] = rotateRight(state[b] ^ state[c], 24);
  state[a] = (state[a] + state[b] + y) & BLAKE2B_MASK;
  state[d] = rotateRight(state[d] ^ state[a], 16);
  state[c] = (state[c] + state[d]) & BLAKE2B_MASK;
  state[b] = rotateRight(state[b] ^ state[c], 63);
};

const blake2b256 = (input) => {
  const bytes = Buffer.from(input);
  const hash = [...BLAKE2B_IV];
  hash[0] ^= 0x01010020n;
  let offset = 0;
  let counterLow = 0n;
  let counterHigh = 0n;

  do {
    const length = Math.min(128, Math.max(0, bytes.length - offset));
    const block = Buffer.alloc(128);
    if (length > 0) bytes.copy(block, 0, offset, offset + length);
    const previousLow = counterLow;
    counterLow = (counterLow + BigInt(length)) & BLAKE2B_MASK;
    if (counterLow < previousLow) {
      counterHigh = (counterHigh + 1n) & BLAKE2B_MASK;
    }
    offset += length;

    const message = Array.from({ length: 16 }, (_, index) =>
      readWord(block, index * 8),
    );
    const state = [...hash, ...BLAKE2B_IV];
    state[12] ^= counterLow;
    state[13] ^= counterHigh;
    if (offset >= bytes.length) state[14] ^= BLAKE2B_MASK;

    for (const permutation of BLAKE2B_SIGMA) {
      mix(state, 0, 4, 8, 12, message[permutation[0]], message[permutation[1]]);
      mix(state, 1, 5, 9, 13, message[permutation[2]], message[permutation[3]]);
      mix(state, 2, 6, 10, 14, message[permutation[4]], message[permutation[5]]);
      mix(state, 3, 7, 11, 15, message[permutation[6]], message[permutation[7]]);
      mix(state, 0, 5, 10, 15, message[permutation[8]], message[permutation[9]]);
      mix(state, 1, 6, 11, 12, message[permutation[10]], message[permutation[11]]);
      mix(state, 2, 7, 8, 13, message[permutation[12]], message[permutation[13]]);
      mix(state, 3, 4, 9, 14, message[permutation[14]], message[permutation[15]]);
    }
    for (let index = 0; index < 8; index += 1) {
      hash[index] =
        (hash[index] ^ state[index] ^ state[index + 8]) & BLAKE2B_MASK;
    }
    if (offset >= bytes.length) break;
  } while (true);

  const output = Buffer.alloc(32);
  for (let word = 0; word < 4; word += 1) {
    for (let byte = 0; byte < 8; byte += 1) {
      output[word * 8 + byte] = Number(
        (hash[word] >> BigInt(byte * 8)) & 0xffn,
      );
    }
  }
  return output.toString("hex");
};

assert.equal(
  blake2b256(Buffer.alloc(0)),
  "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8",
);
assert.equal(
  blake2b256(Buffer.from("abc", "utf8")),
  "bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319",
);

const privacy = () =>
  Object.fromEntries(PRIVACY_FIELDS.map((field) => [field, false]));

const signingContext = {
  accountIdHex: "11".repeat(32),
  nonce: 7,
  eraPeriod: 64,
  eraPhase: 16,
  finalizedBlockHash: `0x${"22".repeat(32)}`,
  tip: "0",
};

const refreshSigningPrehash = (vector) => {
  const rawPayload = Buffer.from(vector.rawSigningPayloadHex, "hex");
  vector.signingPrehashRule =
    rawPayload.length > 256 ? "blake2b-256" : "raw";
  vector.signingPrehashHex =
    vector.signingPrehashRule === "blake2b-256"
      ? blake2b256(rawPayload)
      : vector.rawSigningPayloadHex;
};

const refreshExtrinsicHash = (vector) => {
  vector.extrinsicHashHex = blake2b256(
    Buffer.from(vector.signedExtrinsicHex, "hex"),
  );
};

const platformByte = {
  reference: "31",
  android: "41",
  ios: "51",
};

const makeVector = (platform, expected, index, context) => {
  const metadataPalletIndex = 43;
  const metadataCallIndex = 10 + index;
  const indexPrefix =
    metadataPalletIndex.toString(16).padStart(2, "0") +
    metadataCallIndex.toString(16).padStart(2, "0");
  const fullCallHex = `${indexPrefix}${expected.scaleArgumentsHex}`;
  const rawSigningPayloadHex = [
    fullCallHex,
    GENESIS_HASH.slice(2),
    context.finalizedBlockHash.slice(2),
    index === 0 ? "ab".repeat(180) : "ab".repeat(4),
  ].join("");
  const rawPayload = Buffer.from(rawSigningPayloadHex, "hex");
  const signingPrehashRule =
    rawPayload.length > 256 ? "blake2b-256" : "raw";
  const signingPrehashHex =
    signingPrehashRule === "blake2b-256"
      ? blake2b256(rawPayload)
      : rawSigningPayloadHex;
  const signatureHex = platformByte[platform].repeat(64);
  const signedExtrinsicHex = [
    "f0".repeat(4),
    "00",
    context.accountIdHex,
    "01",
    signatureHex,
    "0f".repeat(4),
    fullCallHex,
  ].join("");
  const decodedProjection = {
    pallet: "Polkamarkt",
    call: expected.call,
    arguments: clone(expected.arguments),
    metadataPalletIndex,
    metadataCallIndex,
    signerAccountIdHex: context.accountIdHex,
    nonce: context.nonce,
    eraPeriod: context.eraPeriod,
    eraPhase: context.eraPhase,
    tip: context.tip,
    genesisHash: GENESIS_HASH,
    finalizedBlockHash: context.finalizedBlockHash,
    specVersion: SPEC_VERSION,
    transactionVersion: TRANSACTION_VERSION,
  };
  return {
    id: expected.call,
    call: expected.call,
    arguments: clone(expected.arguments),
    metadataPalletIndex,
    metadataCallIndex,
    scaleArgumentsHex: expected.scaleArgumentsHex,
    fullCallHex,
    rawSigningPayloadHex,
    signingPrehashHex,
    signingPrehashRule,
    decodedProjection,
    decodedProjectionSha256: sha256StableJson(decodedProjection),
    signerPublicKeyHex: context.accountIdHex,
    signatureHex,
    signedExtrinsicHex,
    extrinsicHashHex: blake2b256(Buffer.from(signedExtrinsicHex, "hex")),
  };
};

const makeCandidate = (
  platform,
  runtimeVectors,
  sourceManifest,
  candidateSigningContext,
) => ({
  format: CANDIDATE_SCHEMA,
  platform,
  sourceRevision: sourceManifest.revision,
  sourceTreeSha256: sourceManifest.sourceTreeSha256,
  generatorSha256: sourceManifest.generatorSha256,
  webRevision: WEB_REVISION,
  runtimeRevision: RUNTIME_REVISION,
  metadataSha256: METADATA_SHA256,
  genesisHash: GENESIS_HASH,
  specVersion: SPEC_VERSION,
  transactionVersion: TRANSACTION_VERSION,
  metadataResolvedDynamically: true,
  signingContext: clone(candidateSigningContext),
  vectors: runtimeVectors.map((vector, index) =>
    makeVector(platform, vector, index, candidateSigningContext),
  ),
  cryptographicProof: null,
  privacy: privacy(),
});

const writeNew = (path, bytes) => {
  writeFileSync(path, bytes, { flag: "wx", mode: 0o600 });
};

const duplicateTopLevelFormat = (bytes, format) => {
  const source = bytes.toString("utf8");
  assert.ok(source.startsWith("{\n"));
  return Buffer.from(
    source.replace("{\n", `{\n  "format": ${JSON.stringify(format)},\n`),
    "utf8",
  );
};

const makeEphemeralReviewKey = (temporaryRoot) => {
  const pair = generateKeyPairSync("ed25519");
  const privateKeyPath = join(temporaryRoot, "ephemeral-review-private.pk8");
  const privateDer = Buffer.from(
    pair.privateKey.export({ format: "der", type: "pkcs8" }),
  );
  let diskPrivateDer;
  try {
    writeNew(privateKeyPath, privateDer);
    diskPrivateDer = readFileSync(privateKeyPath);
    const signingKey = createPrivateKey({
      key: diskPrivateDer,
      format: "der",
      type: "pkcs8",
    });
    const publicDer = Buffer.from(
      pair.publicKey.export({ format: "der", type: "spki" }),
    );
    return { signingKey, publicDer };
  } finally {
    privateDer.fill(0);
    if (diskPrivateDer !== undefined) diskPrivateDer.fill(0);
    if (existsSync(privateKeyPath)) unlinkSync(privateKeyPath);
  }
};

const installedFixtureRecord = readStrictJsonFile(
  FIXTURE_PATH,
  MAXIMUM_INSTALLED_FIXTURE_BYTES,
);
assert.notEqual(installedFixtureRecord, null);
const installedFixture = installedFixtureRecord.value;
const installedQualification =
  installedFixture.canonicalVectors.fullExtrinsicQualification;
const installedFixtureIsQualified =
  installedQualification.reviewedWebAndRuntimeReceiptQualified;
assert.equal(
  typeof installedFixtureIsQualified,
  "boolean",
);
if (installedFixtureIsQualified) {
  assert.equal(installedQualification.blocker, null);
  assert.equal(
    installedQualification.reviewedReceipt?.format,
    QUALIFIED_SCHEMA,
  );
} else {
  assert.equal(installedQualification.reviewedReceipt, null);
  assert.equal(installedQualification.blocker, BLOCKER);
}

// The merger accepts only a blocked source and never overwrites it. Derive a
// private blocked input so this regression remains valid after a reviewed
// qualified receipt is installed in the production fixture.
const asBlockedFixture = (fixture) => {
  const blockedFixture = clone(fixture);
  const qualification =
    blockedFixture.canonicalVectors.fullExtrinsicQualification;
  qualification.canonicalContractSha256 = null;
  qualification.reviewedReceipt = null;
  qualification.reviewedWebAndRuntimeReceiptQualified = false;
  qualification.blocker = BLOCKER;
  return blockedFixture;
};
const baseFixture = asBlockedFixture(installedFixture);
const blockedQualification =
  baseFixture.canonicalVectors.fullExtrinsicQualification;
const blockedGeneration = blockedQualification.generationContract;
const checkedInSourceManifests = blockedGeneration.platformSourceManifests;
const checkedInQualificationAccountId =
  blockedGeneration.signingContext.qualificationAccountIdHex;
const checkedInSourceIdentitiesQualified =
  /^[0-9a-f]{64}$/.test(checkedInQualificationAccountId ?? "") &&
  !/^0+$/.test(checkedInQualificationAccountId ?? "") &&
  PLATFORMS.every((platform) => {
    const manifest = checkedInSourceManifests[platform];
    return (
      /^[0-9a-f]{40}$/.test(manifest.revision ?? "") &&
      !/^0+$/.test(manifest.revision ?? "") &&
      [
        "sourceTreeSha256",
        "generatorSha256",
        "proofVerifierBinarySha256",
        "proofVerifierPublicKeySha256",
      ].every(
        (field) =>
          /^[0-9a-f]{64}$/.test(manifest[field] ?? "") &&
          !/^0+$/.test(manifest[field] ?? ""),
      ) &&
      /^[0-9a-f]{40}$/.test(manifest.proofVerifierRevision ?? "") &&
      !/^0+$/.test(manifest.proofVerifierRevision ?? "")
    );
  });
const candidateSigningContext = {
  ...signingContext,
  accountIdHex: checkedInSourceIdentitiesQualified
    ? checkedInQualificationAccountId
    : signingContext.accountIdHex,
};
const runtimeVectors = baseFixture.canonicalVectors.runtimeCalls;
assert.equal(blockedQualification.reviewedReceipt, null);
assert.equal(blockedQualification.reviewedWebAndRuntimeReceiptQualified, false);
assert.equal(blockedQualification.blocker, BLOCKER);

const createdTemporaryRoot = mkdtempSync(
  join(tmpdir(), "sora-polkamarkt-merger-contract-"),
);
let cleanupRoot = createdTemporaryRoot;

try {
  const temporaryRoot = realpathSync(createdTemporaryRoot);
  cleanupRoot = temporaryRoot;
  chmodSync(temporaryRoot, 0o700);

  const { signingKey: reviewSigningKey, publicDer: reviewPublicDer } =
    makeEphemeralReviewKey(temporaryRoot);
  assert.equal(reviewPublicDer.length, 44);
  assert.equal(
    reviewPublicDer.subarray(0, 12).toString("hex"),
    "302a300506032b6570032100",
  );
  const protectedReviewKeySha256 = sha256(reviewPublicDer);
  let caseNumber = 0;
  const passed = [];
  const skipped = [];

  const prepareCase = (name, configure = () => {}) => {
    caseNumber += 1;
    const safeName = name.replace(/[^a-z0-9-]+/gi, "-").toLowerCase();
    const root = join(
      temporaryRoot,
      `${String(caseNumber).padStart(2, "0")}-${safeName}`,
    );
    mkdirSync(root, { mode: 0o700 });

    const state = {
      root,
      fixture: clone(baseFixture),
      rawFixture: null,
      candidates: Object.fromEntries(
        PLATFORMS.map((platform) => [
          platform,
          makeCandidate(
            platform,
            runtimeVectors,
            checkedInSourceManifests[platform],
            candidateSigningContext,
          ),
        ]),
      ),
      candidateByteMutators: Object.create(null),
      mutateReview: null,
      mutateKey: null,
      mutateSignature: null,
      reviewKeySha256: protectedReviewKeySha256,
      afterWrite: null,
    };
    configure(state);

    const fixturePath = join(root, "fixture.json");
    writeNew(
      fixturePath,
      state.rawFixture === null
        ? jsonBytes(state.fixture)
        : Buffer.from(state.rawFixture),
    );
    const fixtureInputBytes = readFileSync(fixturePath);

    const candidatePaths = Object.create(null);
    const candidateBytes = Object.create(null);
    for (const platform of PLATFORMS) {
      const path = join(root, `${platform}.json`);
      let bytes = jsonBytes(state.candidates[platform]);
      const mutateBytes = state.candidateByteMutators[platform];
      if (typeof mutateBytes === "function") {
        bytes = Buffer.from(mutateBytes(bytes, state.candidates[platform]));
      }
      writeNew(path, bytes);
      candidatePaths[platform] = path;
      candidateBytes[platform] = bytes;
    }

    const review = {
      format: REVIEW_SCHEMA,
      fixtureSha256: sha256(fixtureInputBytes),
      referenceReceiptSha256: sha256(candidateBytes.reference),
      androidReceiptSha256: sha256(candidateBytes.android),
      iosReceiptSha256: sha256(candidateBytes.ios),
      decision: "qualified",
      reviewedAtEpochSeconds: 1_700_000_000,
      reviewer: "public-synthetic-contract-harness",
      privacy: privacy(),
    };
    if (typeof state.mutateReview === "function") state.mutateReview(review);
    const reviewBytes = jsonBytes(review);
    const reviewPath = join(root, "review.json");
    writeNew(reviewPath, reviewBytes);

    const key = {
      format: REVIEW_KEY_SCHEMA,
      algorithm: "ed25519",
      publicKeySpkiDerHex: reviewPublicDer.toString("hex"),
    };
    if (typeof state.mutateKey === "function") state.mutateKey(key);
    const keyBytes = jsonBytes(key);
    const keyPath = join(root, "review-key.json");
    writeNew(keyPath, keyBytes);

    const signature = {
      format: REVIEW_SIGNATURE_SCHEMA,
      reviewReceiptSha256: sha256(reviewBytes),
      signatureHex: signEd25519(
        null,
        reviewBytes,
        reviewSigningKey,
      ).toString("hex"),
    };
    if (typeof state.mutateSignature === "function") {
      state.mutateSignature(signature);
    }
    const signatureBytes = jsonBytes(signature);
    const signaturePath = join(root, "review-signature.json");
    writeNew(signaturePath, signatureBytes);

    const paths = {
      fixture: fixturePath,
      reference: candidatePaths.reference,
      android: candidatePaths.android,
      ios: candidatePaths.ios,
      review: reviewPath,
      key: keyPath,
      signature: signaturePath,
      output: join(root, "qualified.json"),
    };
    if (typeof state.afterWrite === "function") {
      state.afterWrite({ paths, root });
    }
    return {
      name,
      root,
      paths,
      candidateBytes,
      reviewBytes,
      keyBytes,
      signatureBytes,
      fixtureInputBytes,
      reviewKeySha256: state.reviewKeySha256,
    };
  };

  const invoke = (testCase) =>
    spawnSync(
      process.execPath,
      [
        MERGER_PATH,
        "--fixture",
        testCase.paths.fixture,
        "--reference",
        testCase.paths.reference,
        "--android",
        testCase.paths.android,
        "--ios",
        testCase.paths.ios,
        "--review",
        testCase.paths.review,
        "--review-key",
        testCase.paths.key,
        "--review-signature",
        testCase.paths.signature,
        "--out",
        testCase.paths.output,
      ],
      {
        cwd: testCase.root,
        encoding: "utf8",
        env: {
          POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256:
            testCase.reviewKeySha256,
        },
        maxBuffer: 2 * 1024 * 1024,
        timeout: 15_000,
        windowsHide: true,
      },
    );

  const invokeQualifiedValidation = (fixturePath) =>
    spawnSync(
      process.execPath,
      [MERGER_PATH, "--validate-qualified", fixturePath],
      {
        cwd: temporaryRoot,
        encoding: "utf8",
        env: {
          POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256:
            process.env.POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256 ?? "",
        },
        maxBuffer: 2 * 1024 * 1024,
        timeout: 15_000,
        windowsHide: true,
      },
    );

  const childDiagnostic = (result) =>
    [result.stdout, result.stderr].filter(Boolean).join("\n");

  const exactPolkamarktErrorCodes = (diagnostic) =>
    [...diagnostic.matchAll(/^Error: (POLKAMARKT_[A-Z0-9_]+)$/gm)].map(
      (match) => match[1],
    );

  const expectFailure = (
    name,
    expectedCode,
    configure,
    { outputMayExist = false, verify = null } = {},
  ) => {
    const testCase = prepareCase(name, configure);
    const result = invoke(testCase);
    const diagnostic = childDiagnostic(result);
    assert.equal(result.error, undefined, `${name}: ${result.error}`);
    assert.equal(result.signal, null, `${name}: child signal ${result.signal}`);
    assert.equal(result.status, 1, `${name}: unexpected child exit status`);
    assert.equal(result.stdout, "", `${name}: rejected child wrote stdout`);
    assert.deepEqual(
      exactPolkamarktErrorCodes(diagnostic),
      [expectedCode],
      `${name}: expected exact ${expectedCode}\n${diagnostic}`,
    );
    if (!outputMayExist) {
      assert.equal(
        existsSync(testCase.paths.output),
        false,
        `${name}: rejected evidence created output`,
      );
    }
    if (typeof verify === "function") verify(testCase);
    passed.push(name);
  };

  const installedValidation = invokeQualifiedValidation(FIXTURE_PATH);
  const installedValidationDiagnostic = childDiagnostic(installedValidation);
  assert.equal(installedValidation.error, undefined);
  assert.equal(installedValidation.signal, null);
  if (installedFixtureIsQualified) {
    assert.equal(
      installedValidation.status,
      0,
      `qualified-fixture-transition failed\n${installedValidationDiagnostic}`,
    );
    assert.equal(
      installedValidation.stdout,
      "POLKAMARKT_QUALIFIED_FIXTURE_VALID\n",
    );
    passed.push("qualified-fixture-transition");
  } else {
    assert.equal(installedValidation.status, 1);
    assert.equal(installedValidation.stdout, "");
    assert.deepEqual(
      exactPolkamarktErrorCodes(installedValidationDiagnostic),
      ["POLKAMARKT_QUALIFIED_FIXTURE_INVALID"],
    );
    skipped.push("qualified-fixture-transition-pending-real-proof");
  }

  if (!checkedInSourceIdentitiesQualified) {
    expectFailure(
      "checked-in-source-manifests-unqualified",
      "POLKAMARKT_PLATFORM_SOURCE_IDENTITY_UNQUALIFIED",
      () => {},
    );
    skipped.push("deep-receipt-mutations-pending-reviewed-source-manifests");
  } else {
    expectFailure(
      "missing-reviewed-native-cryptographic-proof",
      "POLKAMARKT_REFERENCE_CRYPTOGRAPHIC_PROOF_REQUIRED",
      () => {},
    );

  expectFailure(
    "qualified-fixture-rejected-as-input",
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    (state) => {
      const qualification =
        state.fixture.canonicalVectors.fullExtrinsicQualification;
      qualification.canonicalContractSha256 = "a".repeat(64);
      qualification.reviewedReceipt = {};
      qualification.reviewedWebAndRuntimeReceiptQualified = true;
      qualification.blocker = null;
    },
  );

  expectFailure(
    "duplicate-json-key",
    "POLKAMARKT_REFERENCE_RECEIPT_UNREADABLE",
    (state) => {
      state.candidateByteMutators.reference = (bytes, candidate) =>
        duplicateTopLevelFormat(bytes, candidate.format);
    },
  );
  expectFailure(
    "unknown-json-key",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      state.candidates.reference.unreviewedField = true;
    },
  );
  expectFailure(
    "nul-delimited-exact-key-collision",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const candidatePrivacy = state.candidates.reference.privacy;
      delete candidatePrivacy.privateKeysIncluded;
      delete candidatePrivacy.productionAccountIdentifiersIncluded;
      candidatePrivacy[
        "privateKeysIncluded\0productionAccountIdentifiersIncluded"
      ] = false;
    },
  );
  expectFailure(
    "candidate-source-manifest-mismatch",
    "POLKAMARKT_ANDROID_SOURCE_IDENTITY_MISMATCH",
    (state) => {
      state.candidates.android.sourceTreeSha256 = "f".repeat(64);
    },
  );
  expectFailure(
    "fixture-unknown-qualification-field",
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    (state) => {
      state.fixture.canonicalVectors.fullExtrinsicQualification
        .unreviewedField = true;
    },
  );
  expectFailure(
    "fixture-unknown-source-blob-field",
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    (state) => {
      const source = state.fixture.webReference.sourceFiles[0];
      state.fixture.webReference.sourceBlobObjects[source].unreviewedField =
        true;
    },
  );
  expectFailure(
    "fixture-unknown-behavior-field",
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    (state) => {
      state.fixture.canonicalBehavior.unreviewedField = true;
    },
  );
  expectFailure(
    "fixture-object-smuggled-through-primitive-array",
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    (state) => {
      state.fixture.canonicalBehavior.categories[0] = {
        unreviewedField: true,
      };
    },
  );
  expectFailure(
    "fixture-unknown-runtime-argument-field",
    "POLKAMARKT_RUNTIME_VECTORS_INVALID",
    (state) => {
      state.fixture.canonicalVectors.runtimeCalls[0].arguments.unreviewedField =
        true;
    },
  );
  expectFailure(
    "fixture-private-field",
    "POLKAMARKT_PRIVATE_MATERIAL_PRESENT",
    (state) => {
      state.fixture.canonicalVectors.fullExtrinsicQualification.private_key =
        "public-synthetic-forbidden-field";
    },
  );
  expectFailure(
    "fixture-deep-private-field",
    "POLKAMARKT_PRIVATE_MATERIAL_PRESENT",
    (state) => {
      state.fixture.mobileClaimConfirmation.copy.privateKeyHex =
        "public-synthetic-forbidden-field";
    },
  );
  expectFailure(
    "fixture-wrong-required-metadata",
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    (state) => {
      state.fixture.canonicalVectors.fullExtrinsicQualification
        .requiredMetadataSha256 = replaceLastNibble(METADATA_SHA256);
    },
  );
  expectFailure(
    "wrong-metadata",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      state.candidates.reference.metadataSha256 =
        replaceLastNibble(METADATA_SHA256);
    },
  );
  expectFailure(
    "wrong-genesis",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      state.candidates.reference.genesisHash = replaceLastNibble(GENESIS_HASH);
    },
  );
  expectFailure(
    "wrong-runtime",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      state.candidates.reference.runtimeRevision =
        replaceLastNibble(RUNTIME_REVISION);
    },
  );
  expectFailure(
    "wrong-vector-order",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      state.candidates.reference.vectors.reverse();
    },
  );
  expectFailure(
    "wrong-metadata-indices",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      state.candidates.reference.vectors[0].metadataCallIndex += 1;
    },
  );
  expectFailure(
    "wrong-call",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      state.candidates.reference.vectors[0].call = "sell";
    },
  );
  expectFailure(
    "wrong-prehash",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.signingPrehashHex = replaceLastNibble(vector.signingPrehashHex);
    },
  );
  expectFailure(
    "wrong-extrinsic-hash",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.extrinsicHashHex = replaceLastNibble(vector.extrinsicHashHex);
    },
  );
  expectFailure(
    "wrong-decoded-projection",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.decodedProjection.genesisHash = replaceLastNibble(GENESIS_HASH);
      vector.decodedProjectionSha256 = sha256StableJson(
        vector.decodedProjection,
      );
    },
  );
  expectFailure(
    "wrong-signing-context",
    "POLKAMARKT_ANDROID_PARITY_MISMATCH",
    (state) => {
      const reference = state.candidates.reference;
      reference.signingContext.nonce += 1;
      for (const vector of reference.vectors) {
        vector.decodedProjection.nonce = reference.signingContext.nonce;
        vector.decodedProjectionSha256 = sha256StableJson(
          vector.decodedProjection,
        );
      }
    },
  );
  expectFailure(
    "noncanonical-era-phase",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const reference = state.candidates.reference;
      reference.signingContext.eraPeriod = 8_192;
      reference.signingContext.eraPhase = 1;
      for (const vector of reference.vectors) {
        vector.decodedProjection.eraPeriod = 8_192;
        vector.decodedProjection.eraPhase = 1;
        vector.decodedProjectionSha256 = sha256StableJson(
          vector.decodedProjection,
        );
      }
    },
  );
  expectFailure(
    "raw-payload-not-call-prefixed",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.rawSigningPayloadHex = `00${vector.rawSigningPayloadHex}`;
      refreshSigningPrehash(vector);
    },
  );
  expectFailure(
    "raw-payload-missing-finalized-hash",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.rawSigningPayloadHex = vector.rawSigningPayloadHex.replace(
        candidateSigningContext.finalizedBlockHash.slice(2),
        "33".repeat(32),
      );
      refreshSigningPrehash(vector);
    },
  );
  expectFailure(
    "raw-payload-missing-genesis-hash",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.rawSigningPayloadHex = vector.rawSigningPayloadHex.replace(
        GENESIS_HASH.slice(2),
        "44".repeat(32),
      );
      refreshSigningPrehash(vector);
    },
  );
  expectFailure(
    "signed-extrinsic-missing-signer-envelope",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.signedExtrinsicHex = vector.signedExtrinsicHex.replace(
        `00${candidateSigningContext.accountIdHex}01${vector.signatureHex}`,
        `00${"12".repeat(32)}01${vector.signatureHex}`,
      );
      refreshExtrinsicHash(vector);
    },
  );
  expectFailure(
    "signed-extrinsic-not-call-suffixed",
    "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
    (state) => {
      const vector = state.candidates.reference.vectors[0];
      vector.signedExtrinsicHex += "00";
      refreshExtrinsicHash(vector);
    },
  );
  for (const field of PRIVACY_FIELDS) {
    expectFailure(
      `candidate-privacy-${field}`,
      "POLKAMARKT_REFERENCE_RECEIPT_INVALID",
      (state) => {
        state.candidates.reference.privacy[field] = true;
      },
    );
  }
  expectFailure(
    "review-privacy-field",
    "POLKAMARKT_REVIEW_PRIVACY_INVALID",
    (state) => {
      state.mutateReview = (review) => {
        review.privacy.walletAddressesIncluded = true;
      };
    },
  );
  expectFailure(
    "review-wrong-fixture-hash",
    "POLKAMARKT_REVIEW_INVALID",
    (state) => {
      state.mutateReview = (review) => {
        review.fixtureSha256 = replaceLastNibble(review.fixtureSha256);
      };
    },
  );
  expectFailure(
    "review-wrong-platform-receipt-hash",
    "POLKAMARKT_REVIEW_INVALID",
    (state) => {
      state.mutateReview = (review) => {
        review.androidReceiptSha256 = replaceLastNibble(
          review.androidReceiptSha256,
        );
      };
    },
  );
  expectFailure(
    "review-not-qualified",
    "POLKAMARKT_REVIEW_INVALID",
    (state) => {
      state.mutateReview = (review) => {
        review.decision = "rejected";
      };
    },
  );
  expectFailure(
    "review-unknown-field",
    "POLKAMARKT_REVIEW_INVALID",
    (state) => {
      state.mutateReview = (review) => {
        review.unreviewedField = true;
      };
    },
  );
  expectFailure(
    "replaced-review-key",
    "POLKAMARKT_REVIEW_KEY_NOT_PROTECTED",
    (state) => {
      state.mutateKey = (key) => {
        key.publicKeySpkiDerHex = replaceLastNibble(key.publicKeySpkiDerHex);
      };
    },
  );
  expectFailure(
    "replaced-review-key-hash",
    "POLKAMARKT_REVIEW_KEY_NOT_PROTECTED",
    (state) => {
      state.reviewKeySha256 = replaceLastNibble(
        protectedReviewKeySha256,
      );
    },
  );
  expectFailure(
    "noncanonical-review-key",
    "POLKAMARKT_REVIEW_KEY_INVALID",
    (state) => {
      state.mutateKey = (key) => {
        key.publicKeySpkiDerHex += "00";
        state.reviewKeySha256 = sha256(
          Buffer.from(key.publicKeySpkiDerHex, "hex"),
        );
      };
    },
  );
  expectFailure(
    "replaced-review-receipt-hash",
    "POLKAMARKT_REVIEW_SIGNATURE_INVALID",
    (state) => {
      state.mutateSignature = (signature) => {
        signature.reviewReceiptSha256 = replaceLastNibble(
          signature.reviewReceiptSha256,
        );
      };
    },
  );
  expectFailure(
    "replaced-review-signature",
    "POLKAMARKT_REVIEW_SIGNATURE_INVALID",
    (state) => {
      state.mutateSignature = (signature) => {
        signature.signatureHex = replaceLastNibble(signature.signatureHex);
      };
    },
  );

  const outputSentinel = Buffer.from("existing-output-must-survive\n", "utf8");
  expectFailure(
    "existing-output",
    "POLKAMARKT_QUALIFICATION_OUTPUT_EXISTS",
    (state) => {
      state.afterWrite = ({ paths }) => {
        writeNew(paths.output, outputSentinel);
      };
    },
    {
      outputMayExist: true,
      verify: (testCase) => {
        assert.deepEqual(readFileSync(testCase.paths.output), outputSentinel);
      },
    },
  );

  const supportsSymlinks = (() => {
    const target = join(temporaryRoot, "symlink-probe-target");
    const link = join(temporaryRoot, "symlink-probe-link");
    try {
      writeNew(target, Buffer.from("public probe\n", "utf8"));
      symlinkSync(target, link, "file");
      return true;
    } catch (error) {
      if (!["EACCES", "EPERM", "ENOSYS", "ENOTSUP"].includes(error?.code)) {
        throw error;
      }
      return false;
    } finally {
      rmSync(link, { force: true });
      rmSync(target, { force: true });
    }
  })();

  if (supportsSymlinks) {
    expectFailure(
      "symlink-input",
      "POLKAMARKT_REFERENCE_RECEIPT_UNREADABLE",
      (state) => {
        state.afterWrite = ({ paths, root }) => {
          const link = join(root, "reference-link.json");
          symlinkSync(paths.reference, link, "file");
          paths.reference = link;
        };
      },
    );
    expectFailure(
      "symlink-output",
      "POLKAMARKT_QUALIFICATION_OUTPUT_EXISTS",
      (state) => {
        state.afterWrite = ({ paths, root }) => {
          const target = join(root, "output-symlink-target");
          writeNew(target, outputSentinel);
          symlinkSync(target, paths.output, "file");
        };
      },
      {
        outputMayExist: true,
        verify: (testCase) => {
          assert.deepEqual(
            readFileSync(join(testCase.root, "output-symlink-target")),
            outputSentinel,
          );
        },
      },
    );
    expectFailure(
      "symlink-output-parent",
      "POLKAMARKT_QUALIFICATION_PATHS_INVALID",
      (state) => {
        state.afterWrite = ({ paths, root }) => {
          const realParent = join(root, "real-output-parent");
          const linkedParent = join(root, "linked-output-parent");
          mkdirSync(realParent, { mode: 0o700 });
          symlinkSync(realParent, linkedParent, "dir");
          paths.output = join(linkedParent, "qualified.json");
        };
      },
    );
  } else {
    skipped.push(
      "symlink-input",
      "symlink-output",
      "symlink-output-parent",
    );
  }
  }

  const installedFixtureFinal = readStrictJsonFile(
    FIXTURE_PATH,
    MAXIMUM_INSTALLED_FIXTURE_BYTES,
  );
  assert.notEqual(installedFixtureFinal, null);
  assert.equal(installedFixtureFinal.sha256, installedFixtureRecord.sha256);

  const suffix =
    skipped.length === 0 ? "" : `; skipped safely: ${skipped.join(", ")}`;
  process.stdout.write(
    `Polkamarkt merger contract harness passed ${passed.length} cases${suffix}.\n`,
  );
} finally {
  rmSync(cleanupRoot, { recursive: true, force: true });
}
