#!/usr/bin/env node

import {
  createHash,
  generateKeyPairSync,
  sign,
} from "node:crypto";
import {
  chmodSync,
  linkSync,
  mkdtempSync,
  realpathSync,
  rmSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { verifyTairaDeploymentManifestV1 } from "./lib/taira-deployment-manifest-v1.mjs";

const EVALUATION = 1_800_000_000;
const OLD_CHAIN = "809574f5-fee7-5e69-bfcf-52451e42d50f";
const NEW_CHAIN = "fc56984b-2be7-431d-840e-21514d1883f0";
const SHA_A = "1".repeat(64);
const SHA_B = "2".repeat(64);

const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const canonical = (value) => Buffer.from(`${JSON.stringify(value, null, 2)}\n`);
const clone = (value) => JSON.parse(JSON.stringify(value));

const operator = generateKeyPairSync("ed25519");
const reviewer = generateKeyPairSync("ed25519");
const operatorDer = operator.publicKey.export({ type: "spki", format: "der" });
const reviewerDer = reviewer.publicKey.export({ type: "spki", format: "der" });
const operatorPin = sha256(operatorDer);
const reviewerPin = sha256(reviewerDer);

const epoch = ({ number, chainId, genesisSha256, status }) => ({
  epoch: number,
  chainId,
  genesisSha256,
  i105Discriminant: 369,
  status,
  toriiBaseUrl: status === "current" ? "https://node-2.taira.sora.org" : null,
  publicNodeMcpEndpoint:
    status === "current" ? "https://node-2.taira.sora.org/v1/mcp" : null,
  explorerBaseUrl:
    status === "current" ? "https://taira-explorer.sora.org" : null,
});

const manifest = ({ currentChain = NEW_CHAIN } = {}) => {
  const retiredChain = currentChain === NEW_CHAIN ? OLD_CHAIN : NEW_CHAIN;
  return {
    schemaVersion: 1,
    contractId: "sora-taira-deployment-epoch-manifest-v1",
    status: "qualified",
    networkId: "taira",
    manifestSequenceNumber: 17,
    issuedAtEpochSeconds: EVALUATION - 120,
    reviewedAtEpochSeconds: EVALUATION - 60,
    currentEpoch: 2,
    authorities: {
      operator: {
        keyId: "taira-operator-2026",
        publicKeySha256: operatorPin,
      },
      independentReviewer: {
        keyId: "taira-independent-reviewer-2026",
        publicKeySha256: reviewerPin,
      },
    },
    epochs: [
      epoch({
        number: 1,
        chainId: retiredChain,
        genesisSha256: SHA_A,
        status: "retired",
      }),
      epoch({
        number: 2,
        chainId: currentChain,
        genesisSha256: SHA_B,
        status: "current",
      }),
    ],
    authorization: {
      authorizesDeploymentIdentity: true,
      authorizesFundedCanary: false,
      authorizesRelease: false,
    },
  };
};

const roots = [];
const protectedWrite = (path, bytes) => {
  writeFileSync(path, bytes, { mode: 0o600, flag: "wx" });
  chmodSync(path, 0o600);
};

const makeBundle = ({ value = manifest(), rawBytes = null } = {}) => {
  const root = realpathSync(
    mkdtempSync(join(tmpdir(), "sora-taira-manifest-test-")),
  );
  roots.push(root);
  const bytes = rawBytes ?? canonical(value);
  const paths = {
    manifestPath: join(root, "manifest.json"),
    operatorSignaturePath: join(root, "operator.sig"),
    reviewerSignaturePath: join(root, "reviewer.sig"),
    operatorPublicKeyPath: join(root, "operator.pem"),
    reviewerPublicKeyPath: join(root, "reviewer.pem"),
  };
  protectedWrite(paths.manifestPath, bytes);
  protectedWrite(paths.operatorSignaturePath, sign(null, bytes, operator.privateKey));
  protectedWrite(paths.reviewerSignaturePath, sign(null, bytes, reviewer.privateKey));
  protectedWrite(
    paths.operatorPublicKeyPath,
    operator.publicKey.export({ type: "spki", format: "pem" }),
  );
  protectedWrite(
    paths.reviewerPublicKeyPath,
    reviewer.publicKey.export({ type: "spki", format: "pem" }),
  );
  return paths;
};

const verify = (paths, overrides = {}) =>
  verifyTairaDeploymentManifestV1({
    ...paths,
    expectedOperatorKeySha256: operatorPin,
    expectedReviewerKeySha256: reviewerPin,
    evaluationEpochSeconds: EVALUATION,
    ...overrides,
  });

let mutations = 0;
const rejects = (name, mutate, expected) => {
  const value = manifest();
  mutate(value);
  const paths = makeBundle({ value });
  try {
    verify(paths);
    throw new Error(`${name}: accepted`);
  } catch (error) {
    if (!String(error?.message).includes(expected)) {
      throw new Error(`${name}: expected ${expected}, got ${error?.message}`);
    }
  }
  mutations += 1;
};

try {
  const first = verify(makeBundle());
  if (
    first.status !== "admitted-for-build-binding" ||
    first.current.chainId !== NEW_CHAIN ||
    first.retired.chainId !== OLD_CHAIN ||
    first.authorization.authorizesRelease !== false
  ) {
    throw new Error("positive manifest projection diverged");
  }

  const alternate = verify(
    makeBundle({ value: manifest({ currentChain: OLD_CHAIN }) }),
  );
  if (
    alternate.status !== "admitted-for-build-binding" ||
    alternate.current.chainId !== OLD_CHAIN ||
    alternate.retired.chainId !== NEW_CHAIN ||
    alternate.current.genesisSha256 !== SHA_B ||
    alternate.retired.genesisSha256 !== SHA_A ||
    alternate.authorization.authorizesRelease !== false
  ) {
    throw new Error("alternate operator-selected manifest projection diverged");
  }

  const cliBundle = makeBundle();
  const cli = spawnSync(
    process.execPath,
    [resolve("scripts/verify-taira-deployment-manifest.mjs")],
    {
      cwd: resolve("."),
      encoding: "utf8",
      env: {
        ...process.env,
        RELEASE_EVALUATION_EPOCH_SECONDS: String(EVALUATION),
        TAIRA_DEPLOYMENT_MANIFEST_PATH: cliBundle.manifestPath,
        TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_PATH: cliBundle.operatorSignaturePath,
        TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_PATH: cliBundle.reviewerSignaturePath,
        TAIRA_DEPLOYMENT_OPERATOR_PUBLIC_KEY_PATH: cliBundle.operatorPublicKeyPath,
        TAIRA_DEPLOYMENT_REVIEWER_PUBLIC_KEY_PATH: cliBundle.reviewerPublicKeyPath,
        TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256: operatorPin,
        TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256: reviewerPin,
      },
    },
  );
  if (cli.status !== 0 || JSON.parse(cli.stdout).manifestSha256 !== first.manifestSha256) {
    throw new Error(`CLI positive path failed: ${cli.stderr}`);
  }

  rejects("unknown UUID", (value) => {
    value.epochs[1].chainId = "00000000-0000-0000-0000-000000000369";
  }, "EPOCH_1_INVALID");
  rejects("duplicate UUID", (value) => {
    value.epochs[0].chainId = value.epochs[1].chainId;
  }, "EPOCH_MAPPING_INVALID");
  rejects("two current epochs", (value) => {
    value.epochs[0].status = "current";
    value.epochs[0].toriiBaseUrl = "https://node-1.taira.sora.org";
    value.epochs[0].publicNodeMcpEndpoint = "https://node-1.taira.sora.org/v1/mcp";
    value.epochs[0].explorerBaseUrl = "https://taira-explorer.sora.org";
  }, "EPOCH_MAPPING_INVALID");
  rejects("retired route", (value) => {
    value.epochs[0].toriiBaseUrl = "https://retired.taira.sora.org";
  }, "RETIRED_ROUTE_PRESENT");
  rejects("convenience route", (value) => {
    value.epochs[1].toriiBaseUrl = "https://taira.sora.org";
    value.epochs[1].publicNodeMcpEndpoint = "https://taira.sora.org/v1/mcp";
  }, "ROUTING_INVALID");
  rejects("wrong current epoch", (value) => {
    value.currentEpoch = 1;
  }, "EPOCH_MAPPING_INVALID");
  rejects("nonmonotonic current epoch", (value) => {
    value.epochs[0].epoch = 3;
  }, "EPOCH_MAPPING_INVALID");
  rejects("wrong discriminant", (value) => {
    value.epochs[1].i105Discriminant = 368;
  }, "EPOCH_1_INVALID");
  rejects("genesis reuse", (value) => {
    value.epochs[0].genesisSha256 = value.epochs[1].genesisSha256;
  }, "EPOCH_MAPPING_INVALID");
  rejects("duplicate epoch number", (value) => {
    value.epochs[0].epoch = value.epochs[1].epoch;
  }, "EPOCH_MAPPING_INVALID");
  rejects("release authorization", (value) => {
    value.authorization.authorizesRelease = true;
  }, "SHAPE_INVALID");
  rejects("stale review", (value) => {
    value.reviewedAtEpochSeconds = EVALUATION - 8 * 24 * 60 * 60;
    value.issuedAtEpochSeconds = value.reviewedAtEpochSeconds - 1;
  }, "SHAPE_INVALID");
  rejects("future review", (value) => {
    value.reviewedAtEpochSeconds = EVALUATION + 61;
  }, "SHAPE_INVALID");
  rejects("authority self review", (value) => {
    value.authorities.independentReviewer.keyId = value.authorities.operator.keyId;
  }, "AUTHORITIES_INVALID");

  const wrongPin = makeBundle();
  try {
    verify(wrongPin, { expectedReviewerKeySha256: operatorPin });
    throw new Error("wrong reviewer pin accepted");
  } catch (error) {
    if (!String(error?.message).includes("REVIEWER_KEY_INVALID")) throw error;
  }
  mutations += 1;

  const badOperatorSignature = makeBundle();
  chmodSync(badOperatorSignature.operatorSignaturePath, 0o600);
  unlinkSync(badOperatorSignature.operatorSignaturePath);
  protectedWrite(badOperatorSignature.operatorSignaturePath, Buffer.alloc(64, 7));
  try {
    verify(badOperatorSignature);
    throw new Error("bad operator signature accepted");
  } catch (error) {
    if (!String(error?.message).includes("OPERATOR_SIGNATURE_INVALID")) throw error;
  }
  mutations += 1;

  const duplicateJson = makeBundle({
    rawBytes: Buffer.from(
      canonical(manifest()).toString("utf8").replace(
        '  "schemaVersion": 1,',
        '  "schemaVersion": 1,\n  "schemaVersion": 1,',
      ),
    ),
  });
  try {
    verify(duplicateJson);
    throw new Error("duplicate JSON key accepted");
  } catch (error) {
    if (!String(error?.message).includes("JSON_INVALID")) throw error;
  }
  mutations += 1;

  const nonCanonical = makeBundle({
    rawBytes: Buffer.from(JSON.stringify(manifest())),
  });
  try {
    verify(nonCanonical);
    throw new Error("noncanonical JSON accepted");
  } catch (error) {
    if (!String(error?.message).includes("NOT_CANONICAL")) throw error;
  }
  mutations += 1;

  const looseMode = makeBundle();
  chmodSync(looseMode.manifestPath, 0o644);
  try {
    verify(looseMode);
    throw new Error("group-readable manifest accepted");
  } catch (error) {
    if (!String(error?.message).includes("FILE_INVALID")) throw error;
  }
  mutations += 1;

  const hardlinked = makeBundle();
  linkSync(hardlinked.manifestPath, join(resolve(hardlinked.manifestPath, ".."), "manifest-link.json"));
  try {
    verify(hardlinked);
    throw new Error("hardlinked manifest accepted");
  } catch (error) {
    if (!String(error?.message).includes("FILE_INVALID")) throw error;
  }
  mutations += 1;

  process.stdout.write(
    `Taira deployment manifest v1: 2 signed operator-selected mappings and ${mutations} fail-closed mutations passed.\n`,
  );
} finally {
  for (const root of roots) rmSync(root, { recursive: true, force: true });
}
