#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  createHash,
  generateKeyPairSync,
  sign as signBytes,
} from "node:crypto";
import {
  copyFileSync,
  linkSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";
import {
  PRODUCTION_ADMISSION_IDENTITY_KEYS,
  QUALIFICATION_WORKFLOW_RELATIVE_PATH,
} from "./lib/qualified-candidate-provenance.mjs";
import { productionAdmissionV3ProjectionPrefix } from "./lib/production-rollout-v3-contract.mjs";
import {
  androidCandidateSigningVerificationV1BindingSha256,
} from "./lib/android-candidate-signing-verification-v1.mjs";
import { FUNDED_CANARY_CONTROLLER_BUNDLE_FILES } from "./lib/funded-canary-controller-bundle-v1.mjs";
import { ANDROID_MIGRATION_CONTROLLER_FILES } from "./lib/android-migration-controller-envelope-v1.mjs";
import { verifyTairaDeploymentManifestV1 } from "./lib/taira-deployment-manifest-v1.mjs";
import {
  ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS,
  ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_KEYS,
  androidDependencySigningReviewContractSha256V1,
  verifyAndroidDependencySigningReviewV1,
} from "./lib/android-dependency-signing-review-v1.mjs";
import {
  createAndroidQualifiedCandidatePackageV1,
  validateDownloadedAndroidQualifiedCandidatePackageV1,
  validateAndroidQualifiedCandidatePackageV1,
} from "./lib/android-qualified-candidate-package-v1.mjs";

const sha256Bytes = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const sha256File = (path) => sha256Bytes(readFileSync(path));
const writeJson = (path, value) =>
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
const canonicalSha256 = (lines) => sha256Bytes(Buffer.from(lines.join("\n")));
const FIXED_SHA = (digit) => digit.repeat(64);
const SOURCE_REVISION = "1".repeat(40);
const UPLOAD_CERTIFICATE = FIXED_SHA("a");
const P256_ORDER = BigInt(
  "0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
);
const lowSP256 = (signature) => {
  const r = signature.subarray(0, 32);
  const s = BigInt(`0x${signature.subarray(32).toString("hex")}`);
  const canonicalS = s > P256_ORDER / 2n ? P256_ORDER - s : s;
  return Buffer.concat([
    r,
    Buffer.from(canonicalS.toString(16).padStart(64, "0"), "hex"),
  ]);
};

const createFixture = () => {
  const root = realpathSync(
    resolve(mkdtempSync(join(tmpdir(), "sora-candidate-package-"))),
  );
  for (const path of [
    "config",
    "gradle",
    "vendor/soramitsu-maven",
    ".github/workflows",
    "inputs",
  ]) {
    mkdirSync(join(root, path), { recursive: true, mode: 0o700 });
  }

  const paths = {
    root,
    primaryAab: join(root, "inputs/primary.aab"),
    reproducedAab: join(root, "inputs/reproduced.aab"),
    primaryApk: join(root, "inputs/primary.apk"),
    reproducedApk: join(root, "inputs/reproduced.apk"),
    primaryMapping: join(root, "inputs/primary-mapping.txt"),
    reproducedMapping: join(root, "inputs/reproduced-mapping.txt"),
    primaryLog: join(root, "inputs/primary-build.log"),
    reproducedLog: join(root, "inputs/reproduced-build.log"),
    primarySigning: join(root, "inputs/primary-signing.json"),
    reproducedSigning: join(root, "inputs/reproduced-signing.json"),
    migrationEnvelope: join(
      root,
      "inputs/android-migration-controller-envelope-v1.json",
    ),
    migrationExtraction: join(
      root,
      "inputs/android-migration-controller-extraction-v1.json",
    ),
    tairaDeploymentManifest: join(root, "inputs/taira-deployment-manifest.json"),
    tairaDeploymentOperatorSignature: join(
      root,
      "inputs/taira-deployment-operator.sig",
    ),
    tairaDeploymentReviewerSignature: join(
      root,
      "inputs/taira-deployment-reviewer.sig",
    ),
    tairaDeploymentOperatorKey: join(
      root,
      "inputs/taira-deployment-operator.pem",
    ),
    tairaDeploymentReviewerKey: join(
      root,
      "inputs/taira-deployment-reviewer.pem",
    ),
    tairaDeploymentAdmission: join(
      root,
      "inputs/taira-deployment-admission.json",
    ),
    dependencySigningReviewManifest: join(
      root,
      "inputs/android-dependency-signing-review-manifest.json",
    ),
    dependencySigningReviewProducerSignature: join(
      root,
      "inputs/android-dependency-signing-review-producer.sig",
    ),
    dependencySigningReviewReviewerSignature: join(
      root,
      "inputs/android-dependency-signing-review-reviewer.sig",
    ),
    dependencySigningReviewProducerKey: join(
      root,
      "inputs/android-dependency-signing-review-producer.pem",
    ),
    dependencySigningReviewReviewerKey: join(
      root,
      "inputs/android-dependency-signing-review-reviewer.pem",
    ),
    dependencySigningReviewAdmission: join(
      root,
      "inputs/android-dependency-signing-review-admission.json",
    ),
    canaryBundle: join(root, "inputs/funded-canary-controller-bundle-v1.tar"),
    canaryExtraction: join(
      root,
      "inputs/funded-canary-controller-extraction-v1.json",
    ),
    admission: join(root, "inputs/production-admission.json"),
    pi: join(root, "inputs/candidate-pi.json"),
    piSignature: join(root, "inputs/candidate-pi-signature.json"),
    dependency: join(root, "config/gradle-dependency-provenance.json"),
    signing: join(root, "config/android-production-signing-identity.json"),
    iroha: join(root, "config/iroha-mobile-sdk-pin.json"),
    workflow: join(root, QUALIFICATION_WORKFLOW_RELATIVE_PATH),
    metadata: join(root, "gradle/verification-metadata.xml"),
    metadataDigest: join(root, "gradle/verification-metadata.sha256"),
    vendorSource: join(root, "vendor/soramitsu-maven/SOURCE_PROVENANCE.json"),
    vendorManifest: join(root, "vendor/soramitsu-maven/CONTENTS.sha256"),
  };
  writeFileSync(paths.primaryAab, "identical signed aab\n", { mode: 0o600 });
  writeFileSync(paths.reproducedAab, "identical signed aab\n", { mode: 0o600 });
  writeFileSync(paths.primaryApk, "identical signed apk\n", { mode: 0o600 });
  writeFileSync(paths.reproducedApk, "identical signed apk\n", { mode: 0o600 });
  writeFileSync(paths.primaryMapping, "identical r8 mapping\n", { mode: 0o600 });
  writeFileSync(paths.reproducedMapping, "identical r8 mapping\n", {
    mode: 0o600,
  });
  writeFileSync(paths.primaryLog, "primary build log\n", { mode: 0o600 });
  writeFileSync(paths.reproducedLog, "independent build log\n", { mode: 0o600 });
  writeFileSync(paths.workflow, "name: qualified candidate test\n", {
    mode: 0o600,
  });
  writeFileSync(paths.metadata, "<verification-metadata/>\n", { mode: 0o600 });
  writeFileSync(paths.metadataDigest, `${FIXED_SHA("b")}  verification-metadata.xml\n`, {
    mode: 0o600,
  });
  writeJson(paths.vendorSource, { status: "independently-reviewed" });
  writeFileSync(paths.vendorManifest, `${FIXED_SHA("c")}  artifact.bin\n`, {
    mode: 0o600,
  });
  writeJson(paths.pi, { schemaVersion: 3, status: "qualified" });
  writeJson(paths.piSignature, {
    schemaVersion: 1,
    contractId: "sora-pi-production-capability-probe-signature-v1",
    receiptSha256: sha256File(paths.pi),
  });

  const dependency = {
    schemaVersion: 3,
    platform: "android",
    status: "qualified",
    releaseEnabled: true,
    repositoryPolicy: {
      sourceQualifiedVendorRepository: {
        status: "qualified",
        productionAllowed: true,
        sourceProvenancePath: "vendor/soramitsu-maven/SOURCE_PROVENANCE.json",
        sourceProvenanceSha256: sha256File(paths.vendorSource),
        contentsManifestPath: "vendor/soramitsu-maven/CONTENTS.sha256",
        contentsManifestSha256: sha256File(paths.vendorManifest),
      },
    },
    dependencyVerification: {
      status: "qualified",
      independentlyReviewed: true,
      metadataPath: "gradle/verification-metadata.xml",
      metadataSha256: sha256File(paths.metadata),
      metadataDigestPath: "gradle/verification-metadata.sha256",
      metadataDigestSha256: sha256File(paths.metadataDigest),
    },
    dependencyLocking: {
      independentlyReviewed: true,
      materializedInventory: {
        lockFilePaths: Array.from({ length: 31 }, (_, index) =>
          index === 30 ? "settings-gradle.lockfile" : `module-${index}/gradle.lockfile`,
        ),
        lockFileSetSha256: FIXED_SHA("d"),
        configurationInventorySha256: FIXED_SHA("8"),
      },
    },
  };
  const signing = {
    schemaVersion: 1,
    platform: "android",
    applicationId: "jp.co.soramitsu.sora",
    status: "qualified",
    releaseEnabled: true,
    debugFallbackAllowedForRelease: false,
    productionAppSigningCertificateSha256: FIXED_SHA("e"),
    productionUploadCertificateSha256: UPLOAD_CERTIFICATE,
    retainedProductionCertificateMatched: true,
    signedBundleCertificateMatched: true,
    playAppSigningContinuityReviewed: true,
    secretsRecordedInEvidence: false,
  };
  const iroha = {
    schemaVersion: 4,
    platform: "android",
    status: "qualified",
    releaseEnabled: true,
  };
  writeJson(paths.dependency, dependency);
  writeJson(paths.signing, signing);
  writeJson(paths.iroha, iroha);

  const signingReceipt = (aabPath, apkPath) => {
    const payload = {
    schemaVersion: 1,
    contractId: "sora-android-candidate-signing-verification-v1",
    status: "verified",
    platform: "android",
    artifacts: {
      aabSha256: sha256File(aabPath),
      aabBytes: readFileSync(aabPath).length,
      apkSha256: sha256File(apkPath),
      apkBytes: readFileSync(apkPath).length,
    },
    signingIdentity: {
      configSha256: sha256File(paths.signing),
      productionAppSigningCertificateSha256: FIXED_SHA("e"),
      expectedUploadCertificateSha256: UPLOAD_CERTIFICATE,
      observedAabUploadCertificateSha256: UPLOAD_CERTIFICATE,
      observedUploadCertificateSha256: UPLOAD_CERTIFICATE,
    },
    verification: {
      aabJarSignatureVerifiedStrictly: true,
      aabUploadCertificateMatched: true,
      exactlyOneAabSigner: true,
      apkSignatureVerified: true,
      exactlyOneApkSigner: true,
      uploadCertificateMatched: true,
    },
    privacy: {
      certificatePublicFingerprintsOnly: true,
      privateKeysIncluded: false,
      keyPasswordsIncluded: false,
    },
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
    };
    return {
      ...payload,
      receiptBindingSha256:
        androidCandidateSigningVerificationV1BindingSha256(payload),
    };
  };
  writeJson(paths.primarySigning, signingReceipt(paths.primaryAab, paths.primaryApk));
  writeJson(
    paths.reproducedSigning,
    signingReceipt(paths.reproducedAab, paths.reproducedApk),
  );
  const migrationRunId = "12345678-1234-1234-1234-123456789abc";
  const migrationSequence = 7;
  const migrationAppIdentity = FIXED_SHA("6");
  const migrationPayloads = Object.fromEntries(
    Object.keys(ANDROID_MIGRATION_CONTROLLER_FILES).map((name) => [
      name,
      name.endsWith(".json")
        ? Buffer.from('{"schemaVersion":1,"status":"qualified"}\n')
        : Buffer.from(`synthetic ${name}\n`),
    ]),
  );
  migrationPayloads["android-migration-trust.json"] = Buffer.from(
    `${JSON.stringify({
      schemaVersion: 1,
      contractId: "sora-android-wallet-migration-qualification-trust-v1",
      platform: "android",
      status: "qualified",
    })}\n`,
  );
  const migrationArtifactFiles = {
    retainedReleaseSnapshotManifest:
      "android-migration-retained-snapshot-manifest.json",
    testResult: "android-migration-test-results.json",
    encryptedStorageEvidence:
      "android-migration-encrypted-storage-evidence.json",
    deviceExecutionEvidence:
      "android-migration-device-execution-evidence.json",
    rawExecutionEvidence: "android-migration-raw-execution-evidence.json",
  };
  migrationPayloads["android-migration-evidence.json"] = Buffer.from(
    `${JSON.stringify({
      schemaVersion: 2,
      contractId: "sora-android-wallet-migration-evidence-v2",
      platform: "android",
      status: "qualified",
      runId: migrationRunId,
      qualificationSequenceNumber: migrationSequence,
      sourceRevision: SOURCE_REVISION,
      trustRootSha256: sha256Bytes(
        migrationPayloads["android-migration-trust.json"],
      ),
      identity: { appBuildIdentitySha256: migrationAppIdentity },
      artifacts: Object.fromEntries(
        Object.entries(migrationArtifactFiles).map(([key, name]) => [
          key,
          {
            relativePath: `docs/modernization/qualification/${name}`,
            sha256: sha256Bytes(migrationPayloads[name]),
          },
        ]),
      ),
    })}\n`,
  );
  migrationPayloads["android-migration-matrix.json"] = Buffer.from(
    `${JSON.stringify({
      schemaVersion: 7,
      contractId: "sora-android-wallet-migration-qualification-v7",
      platform: "android",
      status: "qualified",
      runId: migrationRunId,
      qualificationSequenceNumber: migrationSequence,
      sourceRevision: SOURCE_REVISION,
      trustRootSha256: sha256Bytes(
        migrationPayloads["android-migration-trust.json"],
      ),
      evidenceManifestSha256: sha256Bytes(
        migrationPayloads["android-migration-evidence.json"],
      ),
      identity: { appBuildIdentitySha256: migrationAppIdentity },
    })}\n`,
  );
  const migrationEnvelope = {
    schemaVersion: 1,
    contractId: "sora-android-migration-controller-envelope-v1",
    status: "delivered-unreviewed",
    platform: "android",
    sourceRevision: SOURCE_REVISION,
    candidateAabSha256: sha256File(paths.primaryAab),
    candidateAabBytes: readFileSync(paths.primaryAab).length,
    runId: migrationRunId,
    qualificationSequenceNumber: migrationSequence,
    appBuildIdentitySha256: migrationAppIdentity,
    files: Object.fromEntries(
      Object.entries(migrationPayloads).map(([name, bytes]) => [
        name,
        bytes.toString("base64"),
      ]),
    ),
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
  };
  writeJson(paths.migrationEnvelope, migrationEnvelope);
  writeJson(paths.migrationExtraction, {
    schemaVersion: 1,
    contractId: "sora-android-migration-controller-envelope-v1",
    status: "extracted-unreviewed",
    platform: "android",
    sourceRevision: SOURCE_REVISION,
    candidateAabSha256: sha256File(paths.primaryAab),
    candidateAabBytes: readFileSync(paths.primaryAab).length,
    runId: migrationRunId,
    qualificationSequenceNumber: migrationSequence,
    appBuildIdentitySha256: migrationAppIdentity,
    envelopeSha256: sha256File(paths.migrationEnvelope),
    envelopeBytes: readFileSync(paths.migrationEnvelope).length,
    fileCount: Object.keys(ANDROID_MIGRATION_CONTROLLER_FILES).length,
    files: Object.fromEntries(
      Object.entries(migrationPayloads).map(([name, bytes]) => [
        name,
        { sha256: sha256Bytes(bytes), bytes: bytes.length },
      ]),
    ),
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
  });
  const tairaOperator = generateKeyPairSync("ed25519");
  const tairaReviewer = generateKeyPairSync("ed25519");
  const tairaOperatorDer = tairaOperator.publicKey.export({
    type: "spki",
    format: "der",
  });
  const tairaReviewerDer = tairaReviewer.publicKey.export({
    type: "spki",
    format: "der",
  });
  const tairaOperatorPin = sha256Bytes(tairaOperatorDer);
  const tairaReviewerPin = sha256Bytes(tairaReviewerDer);
  const tairaEvaluationEpochSeconds = Math.floor(Date.now() / 1000) - 2;
  const tairaManifest = {
    schemaVersion: 1,
    contractId: "sora-taira-deployment-epoch-manifest-v1",
    status: "qualified",
    networkId: "taira",
    manifestSequenceNumber: 17,
    issuedAtEpochSeconds: tairaEvaluationEpochSeconds - 120,
    reviewedAtEpochSeconds: tairaEvaluationEpochSeconds - 60,
    currentEpoch: 2,
    authorities: {
      operator: {
        keyId: "taira-operator-test",
        publicKeySha256: tairaOperatorPin,
      },
      independentReviewer: {
        keyId: "taira-reviewer-test",
        publicKeySha256: tairaReviewerPin,
      },
    },
    epochs: [
      {
        epoch: 1,
        chainId: "809574f5-fee7-5e69-bfcf-52451e42d50f",
        genesisSha256: FIXED_SHA("6"),
        i105Discriminant: 369,
        status: "retired",
        toriiBaseUrl: null,
        publicNodeMcpEndpoint: null,
        explorerBaseUrl: null,
      },
      {
        epoch: 2,
        chainId: "fc56984b-2be7-431d-840e-21514d1883f0",
        genesisSha256: FIXED_SHA("7"),
        i105Discriminant: 369,
        status: "current",
        toriiBaseUrl: "https://node-2.taira.sora.org",
        publicNodeMcpEndpoint: "https://node-2.taira.sora.org/v1/mcp",
        explorerBaseUrl: "https://taira-explorer.sora.org",
      },
    ],
    authorization: {
      authorizesDeploymentIdentity: true,
      authorizesFundedCanary: false,
      authorizesRelease: false,
    },
  };
  writeJson(paths.tairaDeploymentManifest, tairaManifest);
  const tairaManifestBytes = readFileSync(paths.tairaDeploymentManifest);
  writeFileSync(
    paths.tairaDeploymentOperatorSignature,
    signBytes(null, tairaManifestBytes, tairaOperator.privateKey),
    { mode: 0o600 },
  );
  writeFileSync(
    paths.tairaDeploymentReviewerSignature,
    signBytes(null, tairaManifestBytes, tairaReviewer.privateKey),
    { mode: 0o600 },
  );
  writeFileSync(
    paths.tairaDeploymentOperatorKey,
    tairaOperator.publicKey.export({ type: "spki", format: "pem" }),
    { mode: 0o600 },
  );
  writeFileSync(
    paths.tairaDeploymentReviewerKey,
    tairaReviewer.publicKey.export({ type: "spki", format: "pem" }),
    { mode: 0o600 },
  );
  const tairaDeploymentAdmission = verifyTairaDeploymentManifestV1({
    manifestPath: paths.tairaDeploymentManifest,
    operatorSignaturePath: paths.tairaDeploymentOperatorSignature,
    reviewerSignaturePath: paths.tairaDeploymentReviewerSignature,
    operatorPublicKeyPath: paths.tairaDeploymentOperatorKey,
    reviewerPublicKeyPath: paths.tairaDeploymentReviewerKey,
    expectedOperatorKeySha256: tairaOperatorPin,
    expectedReviewerKeySha256: tairaReviewerPin,
    evaluationEpochSeconds: tairaEvaluationEpochSeconds,
  });
  writeJson(paths.tairaDeploymentAdmission, tairaDeploymentAdmission);
  const dependencyReviewProducer = generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
  });
  const dependencyReviewReviewer = generateKeyPairSync("ec", {
    namedCurve: "prime256v1",
  });
  const dependencyReviewProducerPin = sha256Bytes(
    dependencyReviewProducer.publicKey.export({ type: "spki", format: "der" }),
  );
  const dependencyReviewReviewerPin = sha256Bytes(
    dependencyReviewReviewer.publicKey.export({ type: "spki", format: "der" }),
  );
  const dependencyReviewSequence = 42;
  const dependencyReviewInputs = {
    gradleDependencyProvenanceSha256: sha256File(paths.dependency),
    verificationMetadataSha256: sha256File(paths.metadata),
    verificationMetadataDigestSha256: sha256File(paths.metadataDigest),
    vendorSourceProvenanceSha256: sha256File(paths.vendorSource),
    vendorContentsManifestSha256: sha256File(paths.vendorManifest),
    lockFileSetSha256:
      dependency.dependencyLocking.materializedInventory.lockFileSetSha256,
    lockConfigurationInventorySha256:
      dependency.dependencyLocking.materializedInventory
        .configurationInventorySha256,
    androidProductionSigningIdentitySha256: sha256File(paths.signing),
    productionAppSigningCertificateSha256:
      signing.productionAppSigningCertificateSha256,
    productionUploadCertificateSha256:
      signing.productionUploadCertificateSha256,
  };
  const dependencyReviewContractSha256 =
    androidDependencySigningReviewContractSha256V1({
      sourceRevision: SOURCE_REVISION,
      reviewedInputs: dependencyReviewInputs,
    });
  const dependencyReviewEvaluationEpochSeconds = Math.floor(Date.now() / 1000) - 2;
  const dependencyReviewQualification = Object.fromEntries(
    ANDROID_DEPENDENCY_SIGNING_REVIEW_QUALIFICATION_KEYS.map((key, index) => [
      key,
      index === 0 ? 11 : index === 1 ? 31 : index === 2 ? 271 : true,
    ]),
  );
  const dependencyReviewManifest = {
    schemaVersion: 1,
    contractId: "sora-android-dependency-signing-review-v1",
    status: "qualified",
    platform: "android",
    applicationId: "jp.co.soramitsu.sora",
    reviewSequenceNumber: dependencyReviewSequence,
    sourceRevision: SOURCE_REVISION,
    issuedAtEpochSeconds: dependencyReviewEvaluationEpochSeconds - 120,
    reviewedAtEpochSeconds: dependencyReviewEvaluationEpochSeconds - 60,
    reviewContractSha256: dependencyReviewContractSha256,
    authorities: {
      producer: {
        keyId: "android-dependency-producer-test",
        publicKeySha256: dependencyReviewProducerPin,
      },
      independentReviewer: {
        keyId: "android-dependency-reviewer-test",
        publicKeySha256: dependencyReviewReviewerPin,
      },
    },
    reviewedInputs: dependencyReviewInputs,
    externalEvidence: Object.fromEntries(
      ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_KEYS.map(
        (key, index) => [key, `${(index % 9) + 1}`.repeat(64)],
      ),
    ),
    qualification: dependencyReviewQualification,
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
  };
  writeJson(paths.dependencySigningReviewManifest, dependencyReviewManifest);
  const dependencyReviewManifestBytes = readFileSync(
    paths.dependencySigningReviewManifest,
  );
  writeFileSync(
    paths.dependencySigningReviewProducerSignature,
    lowSP256(
      signBytes("sha256", dependencyReviewManifestBytes, {
        key: dependencyReviewProducer.privateKey,
        dsaEncoding: "ieee-p1363",
      }),
    ),
    { mode: 0o600 },
  );
  writeFileSync(
    paths.dependencySigningReviewReviewerSignature,
    lowSP256(
      signBytes("sha256", dependencyReviewManifestBytes, {
        key: dependencyReviewReviewer.privateKey,
        dsaEncoding: "ieee-p1363",
      }),
    ),
    { mode: 0o600 },
  );
  writeFileSync(
    paths.dependencySigningReviewProducerKey,
    dependencyReviewProducer.publicKey.export({ type: "spki", format: "pem" }),
    { mode: 0o600 },
  );
  writeFileSync(
    paths.dependencySigningReviewReviewerKey,
    dependencyReviewReviewer.publicKey.export({ type: "spki", format: "pem" }),
    { mode: 0o600 },
  );
  const dependencyReviewAdmission = verifyAndroidDependencySigningReviewV1({
    manifestPath: paths.dependencySigningReviewManifest,
    producerSignaturePath: paths.dependencySigningReviewProducerSignature,
    reviewerSignaturePath: paths.dependencySigningReviewReviewerSignature,
    producerPublicKeyPath: paths.dependencySigningReviewProducerKey,
    reviewerPublicKeyPath: paths.dependencySigningReviewReviewerKey,
    expectedProducerKeySha256: dependencyReviewProducerPin,
    expectedReviewerKeySha256: dependencyReviewReviewerPin,
    expectedReviewSequenceNumber: dependencyReviewSequence,
    expectedSourceRevision: SOURCE_REVISION,
    expectedReviewContractSha256: dependencyReviewContractSha256,
    expectedReviewedInputs: dependencyReviewInputs,
    evaluationEpochSeconds: dependencyReviewEvaluationEpochSeconds,
  });
  writeJson(paths.dependencySigningReviewAdmission, dependencyReviewAdmission);
  writeFileSync(paths.canaryBundle, "synthetic protected canary tar\n", {
    mode: 0o600,
  });
  writeJson(paths.canaryExtraction, {
    schemaVersion: 1,
    contractId: "sora-android-funded-canary-controller-bundle-v1",
    status: "extracted-unreviewed",
    platform: "android",
    tarSha256: sha256File(paths.canaryBundle),
    tarBytes: readFileSync(paths.canaryBundle).length,
    fileCount: Object.keys(FUNDED_CANARY_CONTROLLER_BUNDLE_FILES).length,
    files: Object.fromEntries(
      Object.keys(FUNDED_CANARY_CONTROLLER_BUNDLE_FILES).map((name, index) => [
        name,
        { sha256: `${(index % 9) + 1}`.repeat(64), bytes: 1 },
      ]),
    ),
    authorization: {
      authorizesRelease: false,
      authorizesProductionMutation: false,
    },
  });

  const evaluatedAtEpochSeconds = Math.floor(Date.now() / 1000) - 1;
  const identity = {
    candidateAabSha256: sha256File(paths.primaryAab),
    candidateAabBytes: readFileSync(paths.primaryAab).length,
    sourceRevision: SOURCE_REVISION,
    qualifiedAtEpochSeconds: evaluatedAtEpochSeconds,
    candidatePiProbeReceiptSha256: sha256File(paths.pi),
    qualificationRepository: "example/sora-android",
    qualificationWorkflowPath: QUALIFICATION_WORKFLOW_RELATIVE_PATH,
    qualificationWorkflowSha256: sha256File(paths.workflow),
    qualificationRunId: 123,
    qualificationRunAttempt: 1,
    sora2NetworkRevision: "2".repeat(40),
    runtimeSpecVersion: 130,
    runtimeTransactionVersion: 130,
    runtimeMetadataSha256: FIXED_SHA("f"),
    productionSigningIdentitySha256: sha256File(paths.signing),
    dependencySigningReviewManifestSha256: sha256File(
      paths.dependencySigningReviewManifest,
    ),
    irohaMobileSdkPinSha256: sha256File(paths.iroha),
    migrationQualificationSha256: sha256Bytes(
      migrationPayloads["android-migration-matrix.json"],
    ),
    tairaDeploymentManifestSha256: sha256File(
      paths.tairaDeploymentManifest,
    ),
    tairaCanaryReceiptSha256: FIXED_SHA("2"),
    minamotoCanaryReceiptSha256: FIXED_SHA("3"),
    fundedCanaryTrustSha256: FIXED_SHA("4"),
    productionRolloutTrustSha256: FIXED_SHA("5"),
  };
  assert.deepEqual(Object.keys(identity), PRODUCTION_ADMISSION_IDENTITY_KEYS);
  identity.bindingSha256 = canonicalSha256([
    ...productionAdmissionV3ProjectionPrefix(),
    ...PRODUCTION_ADMISSION_IDENTITY_KEYS.map((key) => `${key}=${identity[key]}`),
  ]);
  writeJson(paths.admission, {
    schemaVersion: 3,
    contractId: "sora-android-production-admission-v3",
    status: "qualified",
    platform: "android",
    identity,
  });

  return {
    root,
    paths,
    dependency,
    signing,
    iroha,
    evaluatedAtEpochSeconds,
    tairaOperatorPin,
    tairaReviewerPin,
    dependencyReviewProducerPin,
    dependencyReviewReviewerPin,
    dependencyReviewSequence,
    dependencyReviewContractSha256,
  };
};

const create = (fixture) =>
  createAndroidQualifiedCandidatePackageV1({
    root: fixture.root,
    sourceRevision: SOURCE_REVISION,
    evaluatedAtEpochSeconds: fixture.evaluatedAtEpochSeconds,
    observedUploadCertificateSha256: UPLOAD_CERTIFICATE,
    primaryAabPath: fixture.paths.primaryAab,
    reproducedAabPath: fixture.paths.reproducedAab,
    primaryApkPath: fixture.paths.primaryApk,
    reproducedApkPath: fixture.paths.reproducedApk,
    primaryMappingPath: fixture.paths.primaryMapping,
    reproducedMappingPath: fixture.paths.reproducedMapping,
    primaryBuildLogPath: fixture.paths.primaryLog,
    reproducedBuildLogPath: fixture.paths.reproducedLog,
    primarySigningVerificationPath: fixture.paths.primarySigning,
    reproducedSigningVerificationPath: fixture.paths.reproducedSigning,
    migrationControllerEnvelopePath: fixture.paths.migrationEnvelope,
    migrationControllerExtractionReceiptPath:
      fixture.paths.migrationExtraction,
    tairaDeploymentManifestPath: fixture.paths.tairaDeploymentManifest,
    tairaDeploymentOperatorSignaturePath:
      fixture.paths.tairaDeploymentOperatorSignature,
    tairaDeploymentReviewerSignaturePath:
      fixture.paths.tairaDeploymentReviewerSignature,
    tairaDeploymentOperatorPublicKeyPath:
      fixture.paths.tairaDeploymentOperatorKey,
    tairaDeploymentReviewerPublicKeyPath:
      fixture.paths.tairaDeploymentReviewerKey,
    tairaDeploymentAdmissionReceiptPath:
      fixture.paths.tairaDeploymentAdmission,
    expectedTairaDeploymentOperatorKeySha256: fixture.tairaOperatorPin,
    expectedTairaDeploymentReviewerKeySha256: fixture.tairaReviewerPin,
    dependencySigningReviewManifestPath:
      fixture.paths.dependencySigningReviewManifest,
    dependencySigningReviewProducerSignaturePath:
      fixture.paths.dependencySigningReviewProducerSignature,
    dependencySigningReviewReviewerSignaturePath:
      fixture.paths.dependencySigningReviewReviewerSignature,
    dependencySigningReviewProducerPublicKeyPath:
      fixture.paths.dependencySigningReviewProducerKey,
    dependencySigningReviewReviewerPublicKeyPath:
      fixture.paths.dependencySigningReviewReviewerKey,
    dependencySigningReviewAdmissionReceiptPath:
      fixture.paths.dependencySigningReviewAdmission,
    expectedDependencySigningReviewProducerKeySha256:
      fixture.dependencyReviewProducerPin,
    expectedDependencySigningReviewReviewerKeySha256:
      fixture.dependencyReviewReviewerPin,
    expectedDependencySigningReviewSequenceNumber:
      fixture.dependencyReviewSequence,
    expectedDependencySigningReviewContractSha256:
      fixture.dependencyReviewContractSha256,
    fundedCanaryControllerBundlePath: fixture.paths.canaryBundle,
    fundedCanaryControllerExtractionReceiptPath:
      fixture.paths.canaryExtraction,
    productionAdmissionPath: fixture.paths.admission,
    candidatePiReceiptPath: fixture.paths.pi,
    candidatePiReceiptSignaturePath: fixture.paths.piSignature,
  });

const materializePackage = (fixture, manifest) => {
  const packageRoot = join(fixture.root, "downloaded-package");
  mkdirSync(packageRoot, { mode: 0o700 });
  const copies = [
    [fixture.paths.primaryAab, "candidate.aab"],
    [fixture.paths.primaryApk, "candidate.apk"],
    [fixture.paths.primaryMapping, "r8-mapping.txt"],
    [fixture.paths.reproducedAab, "reproduced-candidate.aab"],
    [fixture.paths.reproducedApk, "reproduced-candidate.apk"],
    [fixture.paths.reproducedMapping, "reproduced-r8-mapping.txt"],
    [fixture.paths.primaryLog, "primary-build.log"],
    [fixture.paths.reproducedLog, "reproduction-build.log"],
    [fixture.paths.primarySigning, "primary-signing-verification.json"],
    [fixture.paths.reproducedSigning, "reproduction-signing-verification.json"],
    [
      fixture.paths.migrationEnvelope,
      "android-migration-controller-envelope-v1.json",
    ],
    [
      fixture.paths.migrationExtraction,
      "android-migration-controller-extraction-v1.json",
    ],
    [fixture.paths.tairaDeploymentManifest, "taira-deployment-manifest.json"],
    [
      fixture.paths.tairaDeploymentOperatorSignature,
      "taira-deployment-operator.sig",
    ],
    [
      fixture.paths.tairaDeploymentReviewerSignature,
      "taira-deployment-reviewer.sig",
    ],
    [fixture.paths.tairaDeploymentOperatorKey, "taira-deployment-operator.pem"],
    [fixture.paths.tairaDeploymentReviewerKey, "taira-deployment-reviewer.pem"],
    [
      fixture.paths.tairaDeploymentAdmission,
      "taira-deployment-admission.json",
    ],
    [
      fixture.paths.dependencySigningReviewManifest,
      "android-dependency-signing-review-manifest.json",
    ],
    [
      fixture.paths.dependencySigningReviewProducerSignature,
      "android-dependency-signing-review-producer.sig",
    ],
    [
      fixture.paths.dependencySigningReviewReviewerSignature,
      "android-dependency-signing-review-reviewer.sig",
    ],
    [
      fixture.paths.dependencySigningReviewProducerKey,
      "android-dependency-signing-review-producer.pem",
    ],
    [
      fixture.paths.dependencySigningReviewReviewerKey,
      "android-dependency-signing-review-reviewer.pem",
    ],
    [
      fixture.paths.dependencySigningReviewAdmission,
      "android-dependency-signing-review-admission.json",
    ],
    [fixture.paths.canaryBundle, "funded-canary-controller-bundle-v1.tar"],
    [
      fixture.paths.canaryExtraction,
      "funded-canary-controller-extraction-v1.json",
    ],
    [fixture.paths.admission, "production-admission.json"],
    [fixture.paths.pi, "candidate-pi-receipt.json"],
    [fixture.paths.piSignature, "candidate-pi-receipt-signature.json"],
  ];
  for (const [source, name] of copies) copyFileSync(source, join(packageRoot, name));
  writeJson(join(packageRoot, "candidate-package-manifest.json"), manifest);
  writeFileSync(join(packageRoot, "source-revision.txt"), `${SOURCE_REVISION}\n`, {
    mode: 0o600,
  });
  return packageRoot;
};

const positive = createFixture();
try {
  const manifest = create(positive);
  assert.equal(validateAndroidQualifiedCandidatePackageV1(manifest), true);
  assert.equal(manifest.reproducibility.buildCount, 2);
  assert.equal(manifest.evidence.lockInventory.fileCount, 31);
  assert.equal(
    manifest.evidence.signingIdentity.observedAabUploadCertificateSha256,
    UPLOAD_CERTIFICATE,
  );
  assert.equal(
    manifest.evidence.tairaDeploymentManifest.sha256,
    sha256File(positive.paths.tairaDeploymentManifest),
  );
  assert.equal(
    manifest.evidence.dependencySigningReviewManifest.sha256,
    sha256File(positive.paths.dependencySigningReviewManifest),
  );
  assert.equal(manifest.authorization.authorizesRelease, false);
  assert.equal(manifest.authorization.authorizesProductionMutation, false);
  const tampered = structuredClone(manifest);
  tampered.reproducibility.primary.aab.bytes += 1;
  assert.equal(validateAndroidQualifiedCandidatePackageV1(tampered), false);
  const packageRoot = materializePackage(positive, manifest);
  const downloaded = validateDownloadedAndroidQualifiedCandidatePackageV1({
    root: positive.root,
    packageRoot,
    expectedSourceRevision: SOURCE_REVISION,
    expectedTairaDeploymentOperatorKeySha256: positive.tairaOperatorPin,
    expectedTairaDeploymentReviewerKeySha256: positive.tairaReviewerPin,
    expectedDependencySigningReviewProducerKeySha256:
      positive.dependencyReviewProducerPin,
    expectedDependencySigningReviewReviewerKeySha256:
      positive.dependencyReviewReviewerPin,
    expectedDependencySigningReviewSequenceNumber:
      positive.dependencyReviewSequence,
    expectedDependencySigningReviewContractSha256:
      positive.dependencyReviewContractSha256,
  });
  assert.equal(downloaded.status, "validated");
  assert.equal(downloaded.candidateAabSha256, manifest.reproducibility.primary.aab.sha256);
  assert.equal(downloaded.authorization.authorizesProductionMutation, false);
} finally {
  rmSync(positive.root, { recursive: true, force: true });
}

const mutations = [
  [
    "aab-divergence",
    "CANDIDATE_PACKAGE_AAB_NOT_REPRODUCIBLE",
    (fixture) => writeFileSync(fixture.paths.reproducedAab, "changed aab\n"),
  ],
  [
    "apk-divergence",
    "CANDIDATE_PACKAGE_APK_NOT_REPRODUCIBLE",
    (fixture) => writeFileSync(fixture.paths.reproducedApk, "changed apk\n"),
  ],
  [
    "mapping-divergence",
    "CANDIDATE_PACKAGE_R8_MAPPING_NOT_REPRODUCIBLE",
    (fixture) => writeFileSync(fixture.paths.reproducedMapping, "changed mapping\n"),
  ],
  [
    "hardlink-alias",
    "CANDIDATE_PACKAGE_PRIMARY_AAB_INVALID",
    (fixture) => {
      const alias = join(fixture.root, "inputs/primary-alias.aab");
      linkSync(fixture.paths.primaryAab, alias);
    },
  ],
  [
    "symlink-rebuild",
    "CANDIDATE_PACKAGE_REPRODUCED_AAB_INVALID",
    (fixture) => {
      unlinkSync(fixture.paths.reproducedAab);
      symlinkSync(fixture.paths.primaryAab, fixture.paths.reproducedAab);
    },
  ],
  [
    "blocked-dependencies",
    "CANDIDATE_PACKAGE_DEPENDENCIES_NOT_QUALIFIED",
    (fixture) => {
      fixture.dependency.status = "blocked";
      fixture.dependency.releaseEnabled = false;
      writeJson(fixture.paths.dependency, fixture.dependency);
    },
  ],
  [
    "noncanonical-source-evidence-path",
    "CANDIDATE_PACKAGE_VENDOR_PROVENANCE_DIVERGED",
    (fixture) => {
      fixture.dependency.repositoryPolicy.sourceQualifiedVendorRepository.sourceProvenancePath =
        "vendor/soramitsu-maven/../soramitsu-maven/SOURCE_PROVENANCE.json";
      writeJson(fixture.paths.dependency, fixture.dependency);
    },
  ],
  [
    "upload-certificate-mismatch",
    "CANDIDATE_PACKAGE_SIGNING_IDENTITY_NOT_QUALIFIED",
    (fixture) => {
      fixture.signing.productionUploadCertificateSha256 = FIXED_SHA("9");
      writeJson(fixture.paths.signing, fixture.signing);
    },
  ],
  [
    "blocked-iroha",
    "CANDIDATE_PACKAGE_IROHA_SDK_NOT_QUALIFIED",
    (fixture) => {
      fixture.iroha.status = "blocked";
      fixture.iroha.releaseEnabled = false;
      writeJson(fixture.paths.iroha, fixture.iroha);
    },
  ],
  [
    "duplicate-json-key",
    "CANDIDATE_PACKAGE_SIGNING_CONFIG_INVALID",
    (fixture) =>
      writeFileSync(
        fixture.paths.signing,
        '{"schemaVersion":1,"schemaVersion":1}\n',
      ),
  ],
  [
    "admission-candidate-drift",
    "CANDIDATE_PACKAGE_ADMISSION_IDENTITY_DIVERGED",
    (fixture) => {
      const admission = JSON.parse(readFileSync(fixture.paths.admission, "utf8"));
      admission.identity.candidateAabSha256 = FIXED_SHA("8");
      admission.identity.bindingSha256 = canonicalSha256([
        ...productionAdmissionV3ProjectionPrefix(),
        ...PRODUCTION_ADMISSION_IDENTITY_KEYS.map(
          (key) => `${key}=${admission.identity[key]}`,
        ),
      ]);
      writeJson(fixture.paths.admission, admission);
    },
  ],
  [
    "signing-verification-drift",
    "CANDIDATE_PACKAGE_SIGNING_VERIFICATION_DIVERGED",
    (fixture) => {
      const receipt = JSON.parse(
        readFileSync(fixture.paths.primarySigning, "utf8"),
      );
      receipt.artifacts.aabSha256 = FIXED_SHA("7");
      const { receiptBindingSha256: _ignored, ...payload } = receipt;
      receipt.receiptBindingSha256 =
        androidCandidateSigningVerificationV1BindingSha256(payload);
      writeJson(fixture.paths.primarySigning, receipt);
    },
  ],
  [
    "aab-signing-certificate-drift",
    "CANDIDATE_PACKAGE_SIGNING_VERIFICATION_DIVERGED",
    (fixture) => {
      const receipt = JSON.parse(
        readFileSync(fixture.paths.primarySigning, "utf8"),
      );
      receipt.signingIdentity.expectedUploadCertificateSha256 = FIXED_SHA("7");
      receipt.signingIdentity.observedAabUploadCertificateSha256 = FIXED_SHA("7");
      receipt.signingIdentity.observedUploadCertificateSha256 = FIXED_SHA("7");
      const { receiptBindingSha256: _ignored, ...payload } = receipt;
      receipt.receiptBindingSha256 =
        androidCandidateSigningVerificationV1BindingSha256(payload);
      writeJson(fixture.paths.primarySigning, receipt);
    },
  ],
  [
    "migration-envelope-candidate-drift",
    "CANDIDATE_PACKAGE_MIGRATION_CONTROLLER_ENVELOPE_DIVERGED",
    (fixture) => {
      const envelope = JSON.parse(
        readFileSync(fixture.paths.migrationEnvelope, "utf8"),
      );
      envelope.candidateAabSha256 = FIXED_SHA("9");
      writeJson(fixture.paths.migrationEnvelope, envelope);
    },
  ],
  [
    "migration-extraction-drift",
    "CANDIDATE_PACKAGE_MIGRATION_CONTROLLER_EXTRACTION_DIVERGED",
    (fixture) => {
      const receipt = JSON.parse(
        readFileSync(fixture.paths.migrationExtraction, "utf8"),
      );
      receipt.envelopeSha256 = FIXED_SHA("9");
      writeJson(fixture.paths.migrationExtraction, receipt);
    },
  ],
  [
    "taira-deployment-signature-drift",
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_VERIFICATION_FAILED",
    (fixture) =>
      writeFileSync(
        fixture.paths.tairaDeploymentReviewerSignature,
        Buffer.alloc(64, 9),
      ),
  ],
  [
    "taira-deployment-admission-drift",
    "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_ADMISSION_DIVERGED",
    (fixture) => {
      const receipt = JSON.parse(
        readFileSync(fixture.paths.tairaDeploymentAdmission, "utf8"),
      );
      receipt.manifestSequenceNumber += 1;
      writeJson(fixture.paths.tairaDeploymentAdmission, receipt);
    },
  ],
  [
    "dependency-review-signature-drift",
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_VERIFICATION_FAILED",
    (fixture) =>
      writeFileSync(
        fixture.paths.dependencySigningReviewReviewerSignature,
        Buffer.alloc(64, 9),
      ),
  ],
  [
    "dependency-review-admission-drift",
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_ADMISSION_DIVERGED",
    (fixture) => {
      const receipt = JSON.parse(
        readFileSync(fixture.paths.dependencySigningReviewAdmission, "utf8"),
      );
      receipt.reviewSequenceNumber += 1;
      writeJson(fixture.paths.dependencySigningReviewAdmission, receipt);
    },
  ],
  [
    "dependency-review-protected-sequence-drift",
    "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_VERIFICATION_FAILED",
    (fixture) => {
      fixture.dependencyReviewSequence += 1;
    },
  ],
];

for (const [name, expectedCode, mutate] of mutations) {
  const fixture = createFixture();
  try {
    mutate(fixture);
    assert.throws(
      () => create(fixture),
      (error) => error instanceof Error && error.message === expectedCode,
      name,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
}

const downloadMutations = [
  [
    "unindexed-package-file",
    "DOWNLOADED_CANDIDATE_PACKAGE_INVENTORY_INVALID",
    (_fixture, packageRoot) =>
      writeFileSync(join(packageRoot, "unexpected.txt"), "unexpected\n"),
  ],
  [
    "downloaded-rebuild-drift",
    "DOWNLOADED_CANDIDATE_REPRODUCIBILITY_DIVERGED",
    (_fixture, packageRoot) =>
      writeFileSync(join(packageRoot, "reproduced-candidate.aab"), "drift\n"),
  ],
  [
    "downloaded-source-revision-drift",
    "DOWNLOADED_CANDIDATE_SOURCE_REVISION_FILE_INVALID",
    (_fixture, packageRoot) =>
      writeFileSync(join(packageRoot, "source-revision.txt"), `${"9".repeat(40)}\n`),
  ],
  [
    "downloaded-manifest-unknown-field",
    "DOWNLOADED_CANDIDATE_MANIFEST_INVALID",
    (_fixture, packageRoot) => {
      const path = join(packageRoot, "candidate-package-manifest.json");
      const manifest = JSON.parse(readFileSync(path, "utf8"));
      manifest.unreviewed = true;
      writeJson(path, manifest);
    },
  ],
  [
    "downloaded-source-workflow-drift",
    "DOWNLOADED_CANDIDATE_SOURCE_IDENTITY_DIVERGED",
    (fixture) => writeFileSync(fixture.paths.workflow, "changed workflow\n"),
  ],
  [
    "downloaded-canary-bundle-drift",
    "DOWNLOADED_CANDIDATE_EVIDENCE_DIVERGED",
    (_fixture, packageRoot) =>
      writeFileSync(
        join(packageRoot, "funded-canary-controller-bundle-v1.tar"),
        "changed protected canary tar\n",
      ),
  ],
  [
    "downloaded-migration-envelope-drift",
    "DOWNLOADED_CANDIDATE_EVIDENCE_DIVERGED",
    (_fixture, packageRoot) =>
      writeJson(
        join(packageRoot, "android-migration-controller-envelope-v1.json"),
        { changed: true },
      ),
  ],
  [
    "downloaded-taira-manifest-drift",
    "DOWNLOADED_CANDIDATE_EVIDENCE_DIVERGED",
    (_fixture, packageRoot) =>
      writeJson(join(packageRoot, "taira-deployment-manifest.json"), {
        changed: true,
      }),
  ],
  [
    "downloaded-dependency-review-manifest-drift",
    "DOWNLOADED_CANDIDATE_EVIDENCE_DIVERGED",
    (_fixture, packageRoot) =>
      writeJson(
        join(
          packageRoot,
          "android-dependency-signing-review-manifest.json",
        ),
        { changed: true },
      ),
  ],
];

for (const [name, expectedCode, mutate] of downloadMutations) {
  const fixture = createFixture();
  try {
    const manifest = create(fixture);
    const packageRoot = materializePackage(fixture, manifest);
    mutate(fixture, packageRoot);
    assert.throws(
      () =>
        validateDownloadedAndroidQualifiedCandidatePackageV1({
          root: fixture.root,
          packageRoot,
          expectedSourceRevision: SOURCE_REVISION,
          expectedTairaDeploymentOperatorKeySha256: fixture.tairaOperatorPin,
          expectedTairaDeploymentReviewerKeySha256: fixture.tairaReviewerPin,
          expectedDependencySigningReviewProducerKeySha256:
            fixture.dependencyReviewProducerPin,
          expectedDependencySigningReviewReviewerKeySha256:
            fixture.dependencyReviewReviewerPin,
          expectedDependencySigningReviewSequenceNumber:
            fixture.dependencyReviewSequence,
          expectedDependencySigningReviewContractSha256:
            fixture.dependencyReviewContractSha256,
        }),
      (error) => error instanceof Error && error.message === expectedCode,
      name,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
}

process.stdout.write(
  `Android qualified-candidate package v1: positive seal/download validation and ${mutations.length + downloadMutations.length} fail-closed mutations passed.\n`,
);
