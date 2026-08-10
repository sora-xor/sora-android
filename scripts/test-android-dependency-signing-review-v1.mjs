#!/usr/bin/env node

import { strict as assert } from "node:assert";
import { spawnSync } from "node:child_process";
import {
  createHash,
  generateKeyPairSync,
  sign,
} from "node:crypto";
import {
  chmodSync,
  linkSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import {
  ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS,
  ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_KEYS,
  ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS,
  androidDependencySigningReviewContractSha256V1,
  lintAndroidDependencySigningReviewBlockedTemplatesV1,
  verifyAndroidDependencySigningReviewV1,
} from "./lib/android-dependency-signing-review-v1.mjs";

const EVALUATION = 1_800_000_000;
const SOURCE_REVISION = "a".repeat(40);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const canonical = (value) => Buffer.from(`${JSON.stringify(value, null, 2)}\n`);
const clone = (value) => JSON.parse(JSON.stringify(value));
const fixedSha = (index) => index.toString(16).repeat(64);
const P256_ORDER = BigInt(
  "0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
);
const toFixed32 = (value) =>
  Buffer.from(value.toString(16).padStart(64, "0"), "hex");
const lowSP256 = (signature) => {
  const r = signature.subarray(0, 32);
  const s = BigInt(`0x${signature.subarray(32).toString("hex")}`);
  return Buffer.concat([r, toFixed32(s > P256_ORDER / 2n ? P256_ORDER - s : s)]);
};

const producer = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const reviewer = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const producerPin = sha256(
  producer.publicKey.export({ type: "spki", format: "der" }),
);
const reviewerPin = sha256(
  reviewer.publicKey.export({ type: "spki", format: "der" }),
);
const reviewedInputs = Object.fromEntries(
  ANDROID_DEPENDENCY_SIGNING_REVIEWED_INPUT_KEYS.map((key, index) => [
    key,
    fixedSha((index % 14) + 1),
  ]),
);
const contractSha256 = androidDependencySigningReviewContractSha256V1({
  sourceRevision: SOURCE_REVISION,
  reviewedInputs,
});

const qualification = Object.fromEntries(
  ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS.map((key, index) => [
    key,
    index === 0 ? 11 : index === 1 ? 31 : index === 2 ? 271 : true,
  ]),
);
const manifest = () => ({
  schemaVersion: 1,
  contractId: "sora-android-dependency-signing-review-v1",
  status: "qualified",
  platform: "android",
  applicationId: "jp.co.soramitsu.sora",
  reviewSequenceNumber: 42,
  sourceRevision: SOURCE_REVISION,
  issuedAtEpochSeconds: EVALUATION - 120,
  reviewedAtEpochSeconds: EVALUATION - 60,
  reviewContractSha256: contractSha256,
  authorities: {
    producer: {
      keyId: "android-dependency-producer-2026",
      publicKeySha256: producerPin,
    },
    independentReviewer: {
      keyId: "android-dependency-independent-reviewer-2026",
      publicKeySha256: reviewerPin,
    },
  },
  reviewedInputs: clone(reviewedInputs),
  externalEvidence: Object.fromEntries(
    ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_KEYS.map(
      (key, index) => [key, fixedSha(((index + 3) % 14) + 1)],
    ),
  ),
  qualification: clone(qualification),
  privacy: {
    aggregateOnly: true,
    secretsIncluded: false,
    privateKeysIncluded: false,
    keyPasswordsIncluded: false,
  },
  authorization: {
    authorizesDependencySigningQualification: true,
    authorizesArtifactSigning: false,
    authorizesRelease: false,
    authorizesProductionMutation: false,
  },
  blockingReasons: [],
});

const roots = [];
const protectedWrite = (path, bytes) => {
  writeFileSync(path, bytes, { mode: 0o600, flag: "wx" });
  chmodSync(path, 0o600);
};
const signP256 = (bytes, privateKey) =>
  lowSP256(
    sign("sha256", bytes, { key: privateKey, dsaEncoding: "ieee-p1363" }),
  );
const makeBundle = ({ value = manifest(), rawBytes = null } = {}) => {
  const root = realpathSync(
    mkdtempSync(join(tmpdir(), "sora-android-review-test-")),
  );
  roots.push(root);
  const bytes = rawBytes ?? canonical(value);
  const paths = {
    manifestPath: join(root, "manifest.json"),
    producerSignaturePath: join(root, "producer.sig"),
    reviewerSignaturePath: join(root, "reviewer.sig"),
    producerPublicKeyPath: join(root, "producer.pem"),
    reviewerPublicKeyPath: join(root, "reviewer.pem"),
  };
  protectedWrite(paths.manifestPath, bytes);
  protectedWrite(paths.producerSignaturePath, signP256(bytes, producer.privateKey));
  protectedWrite(paths.reviewerSignaturePath, signP256(bytes, reviewer.privateKey));
  protectedWrite(
    paths.producerPublicKeyPath,
    producer.publicKey.export({ type: "spki", format: "pem" }),
  );
  protectedWrite(
    paths.reviewerPublicKeyPath,
    reviewer.publicKey.export({ type: "spki", format: "pem" }),
  );
  return paths;
};
const verify = (paths, overrides = {}) =>
  verifyAndroidDependencySigningReviewV1({
    ...paths,
    expectedProducerKeySha256: producerPin,
    expectedReviewerKeySha256: reviewerPin,
    expectedReviewSequenceNumber: 42,
    expectedSourceRevision: SOURCE_REVISION,
    expectedReviewContractSha256: contractSha256,
    expectedReviewedInputs: reviewedInputs,
    evaluationEpochSeconds: EVALUATION,
    ...overrides,
  });

let mutations = 0;
const rejects = (name, mutate, expected) => {
  const value = manifest();
  mutate(value);
  const paths = makeBundle({ value });
  assert.throws(
    () => verify(paths),
    (error) => error instanceof Error && error.message.includes(expected),
    name,
  );
  mutations += 1;
};

try {
  const blockedManifest = JSON.parse(
    readFileSync(
      "docs/modernization/qualification/android-dependency-signing-review-manifest.blocked.json",
      "utf8",
    ),
  );
  const blockedAdmission = JSON.parse(
    readFileSync(
      "docs/modernization/qualification/android-dependency-signing-review-admission.blocked.json",
      "utf8",
    ),
  );
  assert.equal(
    lintAndroidDependencySigningReviewBlockedTemplatesV1({
      manifest: blockedManifest,
      admission: blockedAdmission,
    }),
    true,
  );
  const staleBlockedManifest = clone(blockedManifest);
  staleBlockedManifest.status = "qualified";
  assert.equal(
    lintAndroidDependencySigningReviewBlockedTemplatesV1({
      manifest: staleBlockedManifest,
      admission: blockedAdmission,
    }),
    false,
  );
  mutations += 1;

  const printContract = spawnSync(
    process.execPath,
    [
      "scripts/verify-android-dependency-signing-review.mjs",
      "--print-contract-sha256",
    ],
    {
      encoding: "utf8",
      env: {
        ...process.env,
        ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION: SOURCE_REVISION,
      },
    },
  );
  assert.equal(printContract.status, 1);
  assert.equal(
    printContract.stderr,
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_INPUT_INVALID\n",
  );
  assert.equal(printContract.stdout, "");
  mutations += 1;

  const receipt = verify(makeBundle());
  assert.equal(receipt.status, "admitted-for-dependency-signing-gate");
  assert.equal(receipt.reviewSequenceNumber, 42);
  assert.equal(receipt.sourceRevision, SOURCE_REVISION);
  assert.equal(receipt.reviewContractSha256, contractSha256);
  assert.equal(receipt.producerKeySha256, producerPin);
  assert.equal(receipt.reviewerKeySha256, reviewerPin);
  assert.equal(receipt.authorization.authorizesRelease, false);

  rejects("sequence drift", (value) => {
    value.reviewSequenceNumber += 1;
  }, "SHAPE_INVALID");
  rejects("source drift", (value) => {
    value.sourceRevision = "b".repeat(40);
  }, "SHAPE_INVALID");
  rejects("contract drift", (value) => {
    value.reviewContractSha256 = fixedSha(14);
  }, "SHAPE_INVALID");
  rejects("reviewed input drift", (value) => {
    value.reviewedInputs.verificationMetadataSha256 = fixedSha(14);
  }, "INPUTS_DIVERGED");
  rejects("coordinate count drift", (value) => {
    value.qualification.vendoredCoordinateCountReviewed = 10;
  }, "SHAPE_INVALID");
  rejects("lock count drift", (value) => {
    value.qualification.lockFileCountReviewed = 30;
  }, "SHAPE_INVALID");
  rejects("configuration count drift", (value) => {
    value.qualification.lockConfigurationCountReviewed = 270;
  }, "SHAPE_INVALID");
  rejects("review incomplete", (value) => {
    value.qualification.sbomReviewed = false;
  }, "SHAPE_INVALID");
  rejects("external evidence absent", (value) => {
    value.externalEvidence.sbomReviewReceiptSha256 = null;
  }, "EXTERNAL_EVIDENCE_INVALID");
  rejects("release authorization", (value) => {
    value.authorization.authorizesRelease = true;
  }, "SHAPE_INVALID");
  rejects("secret disclosure", (value) => {
    value.privacy.secretsIncluded = true;
  }, "SHAPE_INVALID");
  rejects("stale review", (value) => {
    value.reviewedAtEpochSeconds = EVALUATION - 8 * 24 * 60 * 60;
    value.issuedAtEpochSeconds = value.reviewedAtEpochSeconds - 1;
  }, "SHAPE_INVALID");
  rejects("future review", (value) => {
    value.reviewedAtEpochSeconds = EVALUATION + 61;
  }, "SHAPE_INVALID");
  rejects("self review", (value) => {
    value.authorities.independentReviewer.keyId =
      value.authorities.producer.keyId;
  }, "AUTHORITIES_INVALID");
  rejects("extra field", (value) => {
    value.selfReviewed = true;
  }, "SHAPE_INVALID");

  const wrongSequence = makeBundle();
  assert.throws(
    () => verify(wrongSequence, { expectedReviewSequenceNumber: 43 }),
    /SHAPE_INVALID/,
  );
  mutations += 1;
  const wrongPin = makeBundle();
  assert.throws(
    () => verify(wrongPin, { expectedReviewerKeySha256: producerPin }),
    /REVIEWER_KEY_INVALID/,
  );
  mutations += 1;
  const wrongSignature = makeBundle();
  unlinkSync(wrongSignature.producerSignaturePath);
  protectedWrite(wrongSignature.producerSignaturePath, Buffer.alloc(64, 7));
  assert.throws(() => verify(wrongSignature), /PRODUCER_SIGNATURE_INVALID/);
  mutations += 1;
  const derSignature = makeBundle();
  unlinkSync(derSignature.reviewerSignaturePath);
  protectedWrite(
    derSignature.reviewerSignaturePath,
    sign("sha256", canonical(manifest()), reviewer.privateKey),
  );
  assert.throws(() => verify(derSignature), /REVIEWER_SIGNATURE_INVALID/);
  mutations += 1;
  const highSSignature = makeBundle();
  const canonicalReviewerSignature = readFileSync(
    highSSignature.reviewerSignaturePath,
  );
  const reviewerR = canonicalReviewerSignature.subarray(0, 32);
  const reviewerS = BigInt(
    `0x${canonicalReviewerSignature.subarray(32).toString("hex")}`,
  );
  unlinkSync(highSSignature.reviewerSignaturePath);
  protectedWrite(
    highSSignature.reviewerSignaturePath,
    Buffer.concat([reviewerR, toFixed32(P256_ORDER - reviewerS)]),
  );
  assert.throws(() => verify(highSSignature), /REVIEWER_SIGNATURE_INVALID/);
  mutations += 1;
  const nonCanonical = makeBundle({ rawBytes: Buffer.from(JSON.stringify(manifest())) });
  assert.throws(() => verify(nonCanonical), /NOT_CANONICAL/);
  mutations += 1;
  const duplicate = makeBundle({
    rawBytes: Buffer.from(
      canonical(manifest()).toString("utf8").replace(
        '  "schemaVersion": 1,',
        '  "schemaVersion": 1,\n  "schemaVersion": 1,',
      ),
    ),
  });
  assert.throws(() => verify(duplicate), /JSON_INVALID/);
  mutations += 1;
  const loose = makeBundle();
  chmodSync(loose.manifestPath, 0o644);
  assert.throws(() => verify(loose), /MANIFEST_FILE_INVALID/);
  mutations += 1;
  const hardlinked = makeBundle();
  linkSync(hardlinked.manifestPath, join(resolve(hardlinked.manifestPath, ".."), "alias.json"));
  assert.throws(() => verify(hardlinked), /MANIFEST_FILE_INVALID/);
  mutations += 1;
  const symbolic = makeBundle();
  const realKey = join(resolve(symbolic.reviewerPublicKeyPath, ".."), "real-reviewer.pem");
  unlinkSync(symbolic.reviewerPublicKeyPath);
  protectedWrite(realKey, reviewer.publicKey.export({ type: "spki", format: "pem" }));
  symlinkSync(realKey, symbolic.reviewerPublicKeyPath);
  assert.throws(() => verify(symbolic), /REVIEWER_KEY_FILE_INVALID/);
  mutations += 1;

  process.stdout.write(
    `Android dependency/signing review v1: P-256 positive admission and ${mutations} fail-closed mutations passed.\n`,
  );
} finally {
  for (const root of roots) rmSync(root, { recursive: true, force: true });
}
