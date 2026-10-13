import { createHash } from "node:crypto";

export const FUNDED_CANARY_V4_CONTRACT_ID =
  "sora-android-funded-nexus-canary-v4";
export const FUNDED_CANARY_FINALITY_MANIFEST_CONTRACT_ID =
  "sora-android-nexus-finality-trust-manifest-v1";
export const FUNDED_CANARY_FINALITY_CONTEXT_CONTRACT_ID =
  "sora-android-nexus-finality-trust-context-v1";
export const FUNDED_CANARY_FINALITY_NATIVE_CONTRACT_ID =
  "sora-android-nexus-finality-native-canary-v1";
export const FUNDED_CANARY_ATTESTATION_ROUTE =
  "/v1/bridge/finality/attestation/{height}";
export const FUNDED_CANARY_BUNDLE_ROUTE =
  "/v1/bridge/finality/bundle/{height}";
export const FUNDED_CANARY_ATTESTATION_RESPONSE_TYPE =
  "BridgeFinalityAttestationV1";
export const FUNDED_CANARY_BUNDLE_RESPONSE_TYPE = "BridgeFinalityBundle";

export const FUNDED_CANARY_V4_MINAMOTO_NETWORK = Object.freeze({
  chainId: "00000000-0000-0000-0000-000000000753",
  i105Discriminant: 753,
  toriiBaseUrl: "https://minamoto.sora.org",
  explorerBaseUrl: "https://minamoto-explorer.sora.org",
  isTestnet: false,
});

export const FUNDED_CANARY_V4_READINESS_PREREQUISITE_KEYS = Object.freeze([
  "exactCurrentArchiveReviewed",
  "currentArchiveCoreJarIdentityVerified",
  "transactionHasherFixReviewed",
  "minimalLifetimeSecretBoundaryReviewed",
  "authoritativeChainAssetAndFeeMappingQualified",
  "sdkDeployedNodeCompatibilityQualified",
  "androidDeviceAndR8Qualified",
  "nativeAbiAndExportInventoryQualified",
  "validationFeeReleaseVectorQualified",
  "transactionEnvelopeParityQualified",
  "reviewedPlatformCanaryQualified",
  "reviewedFinalityVerifierQualified",
  "finalityTrustedContextsQualified",
  "finalityAttestationCanaryQualified",
  "localReceiptHashParityQualified",
  "licenseAndNoticeReviewed",
  "sbomReviewed",
  "buildProvenanceReviewed",
  "artifactAttestationReviewed",
  "sourceToBinaryIdentityProven",
  "productionRuntimeDependencyPinned",
  "unavailableSignerReplaced",
  "unavailableFinalityReaderReplaced",
]);

export const FUNDED_CANARY_FINALITY_KEYS = Object.freeze([
  "status",
  "readinessReceiptSha256",
  "adapterType",
  "adapterSourcePath",
  "adapterSourceSha256",
  "verifierSourceRevision",
  "verifierArtifactSha256",
  "nativeFinalityCanaryReceiptSha256",
  "finalityTrustManifestReceiptSha256",
  "networkTrustContextReceiptSha256",
  "serverContractSourceRevision",
  "serverOpenApiSha256",
  "serverRouteSourceSha256",
  "attestationRoute",
  "bundleRoute",
  "bindingSha256",
]);

export const FUNDED_CANARY_TERMINAL_EXTRA_KEYS = Object.freeze([
  "committedBlockHeight",
]);

export const FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS = Object.freeze([
  "networkId",
  "chainId",
  "finalizedBlockHeight",
  "finalizedBlockHash",
  "attestationChallengeSha256",
  "attestationChallengeBindingSha256",
  "attestationEvidenceSha256",
  "bundleEvidenceSha256",
  "reviewedVerifierSourceRevision",
  "reviewedVerifierArtifactSha256",
  "nativeFinalityCanaryReceiptSha256",
  "finalityTrustManifestReceiptSha256",
  "networkTrustContextReceiptSha256",
  "finalityBindingSha256",
]);

const SHA256 = /^[0-9a-f]{64}$/;
const SHA1 = /^[0-9a-f]{40}$/;
const PRIVACY_KEYS = Object.freeze([
  "redactedAggregateEvidenceOnly",
  "accountIdentifiersIncluded",
  "addressesIncluded",
  "transactionIdentifiersIncluded",
  "phrasesOrSeedsIncluded",
  "privateKeysIncluded",
  "rawSignedPayloadsIncluded",
  "rawNetworkResponsesIncluded",
  "deviceIdentifiersIncluded",
  "operatorNamesIncluded",
]);
const safePositiveInteger = (value) =>
  Number.isSafeInteger(value) && value > 0 && value <= 9_999_999_999;
const exactKeys = (value, expected) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  Object.keys(value).length === expected.length &&
  expected.every((key) => Object.hasOwn(value, key));
const nonzeroSha256 = (value) =>
  typeof value === "string" &&
  SHA256.test(value) &&
  value !== "0".repeat(64);
const nonzeroSha1 = (value) =>
  typeof value === "string" && SHA1.test(value) && value !== "0".repeat(40);
const privacyQualified = (privacy) =>
  exactKeys(privacy, PRIVACY_KEYS) &&
  privacy.redactedAggregateEvidenceOnly === true &&
  Object.entries(privacy).every(
    ([key, value]) => key === "redactedAggregateEvidenceOnly" || value === false,
  );
const scalar = (value) => String(value);

export const fundedCanaryV4CanonicalSha256 = (lines) =>
  createHash("sha256").update(lines.join("\n"), "utf8").digest("hex");

export const fundedCanaryV4FinalityBindingProjection = (finality) => [
  `status=${finality.status}`,
  `readinessReceiptSha256=${finality.readinessReceiptSha256}`,
  `adapterType=${finality.adapterType}`,
  `adapterSourcePath=${finality.adapterSourcePath}`,
  `adapterSourceSha256=${finality.adapterSourceSha256}`,
  `verifierSourceRevision=${finality.verifierSourceRevision}`,
  `verifierArtifactSha256=${finality.verifierArtifactSha256}`,
  `nativeFinalityCanaryReceiptSha256=${finality.nativeFinalityCanaryReceiptSha256}`,
  `finalityTrustManifestReceiptSha256=${finality.finalityTrustManifestReceiptSha256}`,
  `networkTrustContextReceiptSha256=${finality.networkTrustContextReceiptSha256}`,
  `serverContractSourceRevision=${finality.serverContractSourceRevision}`,
  `serverOpenApiSha256=${finality.serverOpenApiSha256}`,
  `serverRouteSourceSha256=${finality.serverRouteSourceSha256}`,
  `attestationRoute=${finality.attestationRoute}`,
  `bundleRoute=${finality.bundleRoute}`,
];

export const fundedCanaryV4FinalityBindingSha256 = (finality) =>
  fundedCanaryV4CanonicalSha256(
    fundedCanaryV4FinalityBindingProjection(finality),
  );

export const fundedCanaryV4ChallengeBindingSha256 = ({
  canaryRunId,
  candidateBindingSha256,
  networkId,
  chainId,
  finalizedBlockHeight,
  finalizedBlockHash,
  attestationChallengeSha256,
  attestationEvidenceSha256,
  bundleEvidenceSha256,
  finalityBindingSha256,
}) =>
  fundedCanaryV4CanonicalSha256([
    "contractId=sora-android-funded-nexus-finality-challenge-binding-v1",
    `canaryRunId=${canaryRunId}`,
    `candidateBindingSha256=${candidateBindingSha256}`,
    `networkId=${networkId}`,
    `chainId=${chainId}`,
    `finalizedBlockHeight=${finalizedBlockHeight}`,
    `finalizedBlockHash=${finalizedBlockHash}`,
    `attestationChallengeSha256=${attestationChallengeSha256}`,
    `attestationEvidenceSha256=${attestationEvidenceSha256}`,
    `bundleEvidenceSha256=${bundleEvidenceSha256}`,
    `finalityBindingSha256=${finalityBindingSha256}`,
  ]);

export const validateFundedCanaryV4PolicyChronology = ({ policy, approval }) =>
  safePositiveInteger(policy?.validFromEpochSeconds) &&
  safePositiveInteger(policy?.validUntilEpochSeconds) &&
  safePositiveInteger(policy?.reviewedAtEpochSeconds) &&
  safePositiveInteger(approval?.approvedAtEpochSeconds) &&
  policy.validFromEpochSeconds <= policy.reviewedAtEpochSeconds &&
  policy.reviewedAtEpochSeconds <= approval.approvedAtEpochSeconds &&
  approval.approvedAtEpochSeconds < approval.expiresAtEpochSeconds &&
  approval.expiresAtEpochSeconds <= policy.validUntilEpochSeconds;

export const validateFundedCanaryV4PairDistinct = ({ taira, minamoto }) => {
  if (
    taira?.receipt?.network?.networkId !== "taira" ||
    minamoto?.receipt?.network?.networkId !== "minamoto"
  ) {
    return false;
  }
  const identities = (record) => [
    record.receiptSha256,
    record.receipt.candidate?.candidateBindingSha256,
    record.receipt.finality?.bindingSha256,
    record.receipt.execution?.canaryRunId,
    record.receipt.operatorApproval?.approvalNonce,
    record.receipt.execution?.finalityReadback?.attestationChallengeSha256,
  ];
  const tairaIdentities = identities(taira);
  const minamotoIdentities = identities(minamoto);
  return (
    tairaIdentities.every(nonzeroSha256) &&
    minamotoIdentities.every(nonzeroSha256) &&
    tairaIdentities.every(
      (identity, index) => identity !== minamotoIdentities[index],
    )
  );
};

export const fundedCanaryV4StageProjection = ({
  receipt,
  stageName,
  assertions,
}) => {
  const stage = receipt.execution[stageName];
  const lines = [
    "contractId=sora-android-funded-nexus-canary-stage-v1",
    `canaryRunId=${receipt.execution.canaryRunId}`,
    `candidateBindingSha256=${receipt.candidate.candidateBindingSha256}`,
    `networkId=${receipt.network.networkId}`,
    `stageName=${stageName}`,
    `status=${stage.status}`,
    `observedAtEpochSeconds=${stage.observedAtEpochSeconds}`,
  ];
  if (stageName === "terminalStatusReadback") {
    lines.push(`committedBlockHeight=${stage.committedBlockHeight}`);
  }
  if (stageName === "finalityReadback") {
    for (const key of FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS) {
      lines.push(`${key}=${scalar(stage[key])}`);
    }
  }
  for (const key of Object.keys(assertions)) {
    lines.push(`${key}=${scalar(stage[key])}`);
  }
  return lines;
};

const privacyProjection = (privacy) =>
  PRIVACY_KEYS.map((key) => `privacy.${key}=${privacy[key]}`);

export const fundedCanaryV4FinalityManifestProjection = (manifest) => [
  `schemaVersion=${manifest.schemaVersion}`,
  `contractId=${manifest.contractId}`,
  `status=${manifest.status}`,
  `platform=${manifest.platform}`,
  `reviewedAtEpochSeconds=${manifest.reviewedAtEpochSeconds}`,
  `verifierSourceRevision=${manifest.verifierSourceRevision}`,
  `verifierArtifactSha256=${manifest.verifierArtifactSha256}`,
  `nativeFinalityCanaryReceiptSha256=${manifest.nativeFinalityCanaryReceiptSha256}`,
  `serverContractSourceRevision=${manifest.serverContractSourceRevision}`,
  `serverOpenApiSha256=${manifest.serverOpenApiSha256}`,
  `serverRouteSourceSha256=${manifest.serverRouteSourceSha256}`,
  `attestationRoute=${manifest.attestationRoute}`,
  `bundleRoute=${manifest.bundleRoute}`,
  `minamotoTrustContextReceiptSha256=${manifest.minamotoTrustContextReceiptSha256}`,
  `tairaTrustContextReceiptSha256=${manifest.tairaTrustContextReceiptSha256}`,
  `independentReviewerKeyId=${manifest.independentReviewerKeyId}`,
  ...privacyProjection(manifest.privacy),
];

// This projection intentionally omits the native receipt hash. The native
// receipt binds this material manifest identity first; the completed manifest
// can then bind the native receipt bytes without creating a hash cycle.
export const fundedCanaryV4FinalityManifestBindingProjection = (manifest) => [
  "contractId=sora-android-nexus-finality-trust-manifest-binding-v1",
  `schemaVersion=${manifest.schemaVersion}`,
  `platform=${manifest.platform}`,
  `verifierSourceRevision=${manifest.verifierSourceRevision}`,
  `verifierArtifactSha256=${manifest.verifierArtifactSha256}`,
  `serverContractSourceRevision=${manifest.serverContractSourceRevision}`,
  `serverOpenApiSha256=${manifest.serverOpenApiSha256}`,
  `serverRouteSourceSha256=${manifest.serverRouteSourceSha256}`,
  `attestationRoute=${manifest.attestationRoute}`,
  `bundleRoute=${manifest.bundleRoute}`,
  `minamotoTrustContextReceiptSha256=${manifest.minamotoTrustContextReceiptSha256}`,
  `tairaTrustContextReceiptSha256=${manifest.tairaTrustContextReceiptSha256}`,
];

export const fundedCanaryV4FinalityManifestBindingSha256 = (manifest) =>
  fundedCanaryV4CanonicalSha256(
    fundedCanaryV4FinalityManifestBindingProjection(manifest),
  );

export const fundedCanaryV4TrustContextProjection = (context) => [
  `schemaVersion=${context.schemaVersion}`,
  `contractId=${context.contractId}`,
  `status=${context.status}`,
  `platform=${context.platform}`,
  `networkId=${context.networkId}`,
  `chainId=${context.chainId}`,
  `reviewedAtEpochSeconds=${context.reviewedAtEpochSeconds}`,
  `expectedNodeKeySha256=${context.expectedNodeKeySha256}`,
  `expectedNodeBuildFingerprint=${context.expectedNodeBuildFingerprint}`,
  `expectedProtocolVersion=${context.expectedProtocolVersion}`,
  `expectedConsensusMode=${context.expectedConsensusMode}`,
  `validatorRosterSha256=${context.validatorRosterSha256}`,
  `quorumNumerator=${context.quorumNumerator}`,
  `quorumDenominator=${context.quorumDenominator}`,
  `canonicalSignedGenesisSha256=${context.canonicalSignedGenesisSha256}`,
  `genesisPublicKeySha256=${context.genesisPublicKeySha256}`,
  `trustedFirstHeight=${context.trustedFirstHeight}`,
  `trustedFirstHeightContextId=${context.trustedFirstHeightContextId}`,
  `verifierSourceRevision=${context.verifierSourceRevision}`,
  `verifierArtifactSha256=${context.verifierArtifactSha256}`,
  `serverContractSourceRevision=${context.serverContractSourceRevision}`,
  `serverOpenApiSha256=${context.serverOpenApiSha256}`,
  `serverRouteSourceSha256=${context.serverRouteSourceSha256}`,
  `independentReviewerKeyId=${context.independentReviewerKeyId}`,
  ...privacyProjection(context.privacy),
];

export const fundedCanaryV4NativeFinalityProjection = (native) => [
  `schemaVersion=${native.schemaVersion}`,
  `contractId=${native.contractId}`,
  `status=${native.status}`,
  `platform=${native.platform}`,
  `reviewedAtEpochSeconds=${native.reviewedAtEpochSeconds}`,
  `requiredNativeAbi=${native.requiredNativeAbi}`,
  `observedNativeAbi=${native.observedNativeAbi}`,
  `artifactClass=${native.artifactClass}`,
  `reviewedPlatformArtifactSha256=${native.reviewedPlatformArtifactSha256}`,
  `verifierSourceRevision=${native.verifierSourceRevision}`,
  `verifierArtifactSha256=${native.verifierArtifactSha256}`,
  `finalityTrustManifestBindingSha256=${native.finalityTrustManifestBindingSha256}`,
  `attestationRoute=${native.attestationRoute}`,
  `bundleRoute=${native.bundleRoute}`,
  `attestationResponseType=${native.attestationResponseType}`,
  `bundleResponseType=${native.bundleResponseType}`,
  `requiredExportInventorySha256=${native.requiredExportInventorySha256}`,
  `observedExportInventorySha256=${native.observedExportInventorySha256}`,
  `attestationNoritoRoundTripKatSha256=${native.attestationNoritoRoundTripKatSha256}`,
  `bundleNoritoRoundTripKatSha256=${native.bundleNoritoRoundTripKatSha256}`,
  `challengeBindingKatSha256=${native.challengeBindingKatSha256}`,
  `genesisProofKatSha256=${native.genesisProofKatSha256}`,
  `tipProofKatSha256=${native.tipProofKatSha256}`,
  `nodeSignatureKatSha256=${native.nodeSignatureKatSha256}`,
  `aggregateSignatureKatSha256=${native.aggregateSignatureKatSha256}`,
  `successorProofKatSha256=${native.successorProofKatSha256}`,
  `finalizedProjectionKatSha256=${native.finalizedProjectionKatSha256}`,
  `attestationNoritoRoundTripKatPassed=${native.attestationNoritoRoundTripKatPassed}`,
  `bundleNoritoRoundTripKatPassed=${native.bundleNoritoRoundTripKatPassed}`,
  `challengeBindingKatPassed=${native.challengeBindingKatPassed}`,
  `genesisProofKatPassed=${native.genesisProofKatPassed}`,
  `tipProofKatPassed=${native.tipProofKatPassed}`,
  `nodeSignatureKatPassed=${native.nodeSignatureKatPassed}`,
  `aggregateSignatureKatPassed=${native.aggregateSignatureKatPassed}`,
  `successorProofKatPassed=${native.successorProofKatPassed}`,
  `finalizedProjectionKatPassed=${native.finalizedProjectionKatPassed}`,
  `artifactIdentityQualified=${native.artifactIdentityQualified}`,
  `abiQualified=${native.abiQualified}`,
  `exportInventoryQualified=${native.exportInventoryQualified}`,
  `canonicalNoritoRoundTripQualified=${native.canonicalNoritoRoundTripQualified}`,
  `attestationDecodeProjectionQualified=${native.attestationDecodeProjectionQualified}`,
  `bundleDecodeProjectionQualified=${native.bundleDecodeProjectionQualified}`,
  `trustContextBindingQualified=${native.trustContextBindingQualified}`,
  `sourceTreeClean=${native.sourceTreeClean}`,
  `dirtyLocalDebugArtifactAccepted=${native.dirtyLocalDebugArtifactAccepted}`,
  `independentReviewerKeyId=${native.independentReviewerKeyId}`,
  ...privacyProjection(native.privacy),
];

export const FUNDED_CANARY_FINALITY_MANIFEST_KEYS = [
  "schemaVersion",
  "contractId",
  "status",
  "platform",
  "reviewedAtEpochSeconds",
  "verifierSourceRevision",
  "verifierArtifactSha256",
  "nativeFinalityCanaryReceiptSha256",
  "serverContractSourceRevision",
  "serverOpenApiSha256",
  "serverRouteSourceSha256",
  "attestationRoute",
  "bundleRoute",
  "minamotoTrustContextReceiptSha256",
  "tairaTrustContextReceiptSha256",
  "independentReviewerKeyId",
  "independentReviewerSignatureBase64",
  "privacy",
  "blockingReasons",
];
export const FUNDED_CANARY_FINALITY_CONTEXT_KEYS = [
  "schemaVersion",
  "contractId",
  "status",
  "platform",
  "networkId",
  "chainId",
  "reviewedAtEpochSeconds",
  "expectedNodeKeySha256",
  "expectedNodeBuildFingerprint",
  "expectedProtocolVersion",
  "expectedConsensusMode",
  "validatorRosterSha256",
  "quorumNumerator",
  "quorumDenominator",
  "canonicalSignedGenesisSha256",
  "genesisPublicKeySha256",
  "trustedFirstHeight",
  "trustedFirstHeightContextId",
  "verifierSourceRevision",
  "verifierArtifactSha256",
  "serverContractSourceRevision",
  "serverOpenApiSha256",
  "serverRouteSourceSha256",
  "independentReviewerKeyId",
  "independentReviewerSignatureBase64",
  "privacy",
  "blockingReasons",
];
export const FUNDED_CANARY_FINALITY_NATIVE_KEYS = [
  "schemaVersion",
  "contractId",
  "status",
  "platform",
  "reviewedAtEpochSeconds",
  "requiredNativeAbi",
  "observedNativeAbi",
  "artifactClass",
  "reviewedPlatformArtifactSha256",
  "verifierSourceRevision",
  "verifierArtifactSha256",
  "finalityTrustManifestBindingSha256",
  "attestationRoute",
  "bundleRoute",
  "attestationResponseType",
  "bundleResponseType",
  "requiredExportInventorySha256",
  "observedExportInventorySha256",
  "attestationNoritoRoundTripKatSha256",
  "bundleNoritoRoundTripKatSha256",
  "challengeBindingKatSha256",
  "genesisProofKatSha256",
  "tipProofKatSha256",
  "nodeSignatureKatSha256",
  "aggregateSignatureKatSha256",
  "successorProofKatSha256",
  "finalizedProjectionKatSha256",
  "attestationNoritoRoundTripKatPassed",
  "bundleNoritoRoundTripKatPassed",
  "challengeBindingKatPassed",
  "genesisProofKatPassed",
  "tipProofKatPassed",
  "nodeSignatureKatPassed",
  "aggregateSignatureKatPassed",
  "successorProofKatPassed",
  "finalizedProjectionKatPassed",
  "artifactIdentityQualified",
  "abiQualified",
  "exportInventoryQualified",
  "canonicalNoritoRoundTripQualified",
  "attestationDecodeProjectionQualified",
  "bundleDecodeProjectionQualified",
  "trustContextBindingQualified",
  "sourceTreeClean",
  "dirtyLocalDebugArtifactAccepted",
  "independentReviewerKeyId",
  "independentReviewerSignatureBase64",
  "privacy",
  "blockingReasons",
];

export const validateFundedCanaryV4Proof = ({
  receipt,
  evidenceRecord,
  readiness,
  readinessSha256,
  finalityManifest,
  finalityManifestSha256,
  networkTrustContext,
  networkTrustContextSha256,
  nativeFinalityCanary,
  nativeFinalityCanarySha256,
  stageAssertions,
  expectedNetwork,
}) => {
  const networkId = receipt?.network?.networkId;
  const finality = receipt?.finality;
  const terminal = receipt?.execution?.terminalStatusReadback;
  const finalityStage = receipt?.execution?.finalityReadback;
  if (
    expectedNetwork === null ||
    typeof expectedNetwork !== "object" ||
    Array.isArray(expectedNetwork) ||
    receipt?.schemaVersion !== 4 ||
    receipt?.contractId !== FUNDED_CANARY_V4_CONTRACT_ID ||
    readiness?.schemaVersion !== 4 ||
    !exactKeys(readiness?.releaseCriteria, [
      ...FUNDED_CANARY_V4_READINESS_PREREQUISITE_KEYS,
      "fundedTairaCanaryQualified",
      "fundedMinamotoCanaryQualified",
    ]) ||
    !FUNDED_CANARY_V4_READINESS_PREREQUISITE_KEYS.every(
      (key) => readiness.releaseCriteria[key] === true,
    ) ||
    readiness?.releaseCriteria?.fundedTairaCanaryQualified !== false ||
    readiness?.releaseCriteria?.fundedMinamotoCanaryQualified !== false ||
    !exactKeys(finality, FUNDED_CANARY_FINALITY_KEYS) ||
    !nonzeroSha256(readinessSha256) ||
    finality.readinessReceiptSha256 !== readinessSha256 ||
    receipt.network.chainId !== expectedNetwork.chainId ||
    receipt.network.i105Discriminant !== expectedNetwork.i105Discriminant ||
    receipt.network.toriiBaseUrl !== expectedNetwork.toriiBaseUrl ||
    receipt.network.explorerBaseUrl !== expectedNetwork.explorerBaseUrl ||
    receipt.network.isTestnet !== expectedNetwork.isTestnet ||
    !exactKeys(terminal, [
      "status",
      "observedAtEpochSeconds",
      "evidenceSha256",
      ...FUNDED_CANARY_TERMINAL_EXTRA_KEYS,
      ...Object.keys(stageAssertions.terminalStatusReadback),
    ]) ||
    !exactKeys(finalityStage, [
      "status",
      "observedAtEpochSeconds",
      "evidenceSha256",
      ...FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS,
      ...Object.keys(stageAssertions.finalityReadback),
    ]) ||
    !safePositiveInteger(terminal.committedBlockHeight) ||
    !safePositiveInteger(finalityStage.finalizedBlockHeight) ||
    finalityStage.finalizedBlockHeight < terminal.committedBlockHeight ||
    finalityStage.networkId !== networkId ||
    finalityStage.chainId !== expectedNetwork.chainId ||
    !nonzeroSha256(finalityStage.finalizedBlockHash) ||
    !nonzeroSha256(finalityStage.attestationChallengeSha256) ||
    !nonzeroSha256(finalityStage.attestationEvidenceSha256) ||
    !nonzeroSha256(finalityStage.bundleEvidenceSha256) ||
    finalityStage.attestationEvidenceSha256 ===
      finalityStage.bundleEvidenceSha256 ||
    finalityStage.liveBoundedSequentialStatefulSuccessorChainVerified !== true
  ) {
    return false;
  }
  const productionFinality = readiness.productionFinalityBinding;
  const trustContract = readiness.finalityTrustContract;
  const nativeContract = readiness.nativeFinalityCanaryContract;
  if (
    productionFinality?.status !== "qualified" ||
    String(productionFinality.adapterType ?? "").includes("Unavailable") ||
    typeof productionFinality.adapterType !== "string" ||
    productionFinality.adapterType.length === 0 ||
    typeof productionFinality.adapterSourcePath !== "string" ||
    productionFinality.adapterSourcePath.length === 0 ||
    !nonzeroSha256(productionFinality.adapterSourceSha256) ||
    !nonzeroSha1(productionFinality.verifierSourceRevision) ||
    !nonzeroSha256(productionFinality.verifierArtifactSha256) ||
    productionFinality.nativeFinalityCanaryReceiptSha256 !==
      nativeFinalityCanarySha256 ||
    productionFinality.finalityTrustManifestReceiptSha256 !==
      finalityManifestSha256 ||
    trustContract?.status !== "qualified" ||
    !nonzeroSha1(trustContract.serverContractSourceRevision) ||
    !nonzeroSha256(trustContract.serverOpenApiSha256) ||
    !nonzeroSha256(trustContract.serverRouteSourceSha256) ||
    !nonzeroSha256(trustContract.minamotoTrustedContextReceiptSha256) ||
    !nonzeroSha256(trustContract.tairaTrustedContextReceiptSha256) ||
    trustContract[`${networkId}TrustedContextReceiptSha256`] !==
      networkTrustContextSha256 ||
    nativeContract?.status !== "qualified" ||
    nativeContract.receiptSha256 !== nativeFinalityCanarySha256 ||
    nativeContract.reviewedPlatformArtifactPresent !== true ||
    nativeContract.attestationRoute !== FUNDED_CANARY_ATTESTATION_ROUTE ||
    nativeContract.bundleRoute !== FUNDED_CANARY_BUNDLE_ROUTE ||
    nativeContract.attestationResponseType !==
      FUNDED_CANARY_ATTESTATION_RESPONSE_TYPE ||
    nativeContract.bundleResponseType !== FUNDED_CANARY_BUNDLE_RESPONSE_TYPE ||
    nativeContract.canonicalNoritoRoundTripQualified !== true ||
    nativeContract.attestationDecodeProjectionQualified !== true ||
    nativeContract.bundleDecodeProjectionQualified !== true ||
    nativeContract.trustContextBindingQualified !== true ||
    nativeContract.dirtyLocalDebugArtifactAccepted !== false
  ) {
    return false;
  }
  if (
    !exactKeys(finalityManifest, FUNDED_CANARY_FINALITY_MANIFEST_KEYS) ||
    finalityManifest.schemaVersion !== 1 ||
    finalityManifest.contractId !==
      FUNDED_CANARY_FINALITY_MANIFEST_CONTRACT_ID ||
    finalityManifest.status !== "qualified" ||
    finalityManifest.platform !== "android" ||
    !safePositiveInteger(finalityManifest.reviewedAtEpochSeconds) ||
    !Array.isArray(finalityManifest.blockingReasons) ||
    finalityManifest.blockingReasons.length !== 0 ||
    !privacyQualified(finalityManifest.privacy) ||
    finalityManifest.verifierSourceRevision !==
      productionFinality.verifierSourceRevision ||
    finalityManifest.verifierArtifactSha256 !==
      productionFinality.verifierArtifactSha256 ||
    finalityManifest.nativeFinalityCanaryReceiptSha256 !==
      nativeFinalityCanarySha256 ||
    finalityManifest.serverContractSourceRevision !==
      trustContract.serverContractSourceRevision ||
    finalityManifest.serverOpenApiSha256 !== trustContract.serverOpenApiSha256 ||
    finalityManifest.serverRouteSourceSha256 !==
      trustContract.serverRouteSourceSha256 ||
    finalityManifest.attestationRoute !== FUNDED_CANARY_ATTESTATION_ROUTE ||
    finalityManifest.bundleRoute !== FUNDED_CANARY_BUNDLE_ROUTE ||
    finalityManifest[`${networkId}TrustContextReceiptSha256`] !==
      networkTrustContextSha256 ||
    typeof finalityManifest.independentReviewerKeyId !== "string" ||
    finalityManifest.independentReviewerKeyId.length === 0
  ) {
    return false;
  }
  if (
    !exactKeys(networkTrustContext, FUNDED_CANARY_FINALITY_CONTEXT_KEYS) ||
    networkTrustContext.schemaVersion !== 1 ||
    networkTrustContext.contractId !== FUNDED_CANARY_FINALITY_CONTEXT_CONTRACT_ID ||
    networkTrustContext.status !== "qualified" ||
    networkTrustContext.platform !== "android" ||
    networkTrustContext.networkId !== networkId ||
    networkTrustContext.chainId !== expectedNetwork.chainId ||
    !safePositiveInteger(networkTrustContext.reviewedAtEpochSeconds) ||
    !nonzeroSha256(networkTrustContext.expectedNodeKeySha256) ||
    !nonzeroSha256(networkTrustContext.expectedNodeBuildFingerprint) ||
    !nonzeroSha256(networkTrustContext.validatorRosterSha256) ||
    !nonzeroSha256(networkTrustContext.canonicalSignedGenesisSha256) ||
    !nonzeroSha256(networkTrustContext.genesisPublicKeySha256) ||
    !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(
      networkTrustContext.expectedProtocolVersion ?? "",
    ) ||
    !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(
      networkTrustContext.expectedConsensusMode ?? "",
    ) ||
    !safePositiveInteger(networkTrustContext.trustedFirstHeight) ||
    networkTrustContext.trustedFirstHeight >
      finalityStage.finalizedBlockHeight ||
    !nonzeroSha256(networkTrustContext.trustedFirstHeightContextId) ||
    !safePositiveInteger(networkTrustContext.quorumNumerator) ||
    !safePositiveInteger(networkTrustContext.quorumDenominator) ||
    networkTrustContext.quorumNumerator >
      networkTrustContext.quorumDenominator ||
    networkTrustContext.verifierSourceRevision !==
      productionFinality.verifierSourceRevision ||
    networkTrustContext.verifierArtifactSha256 !==
      productionFinality.verifierArtifactSha256 ||
    networkTrustContext.serverContractSourceRevision !==
      trustContract.serverContractSourceRevision ||
    networkTrustContext.serverOpenApiSha256 !==
      trustContract.serverOpenApiSha256 ||
    networkTrustContext.serverRouteSourceSha256 !==
      trustContract.serverRouteSourceSha256 ||
    networkTrustContext.independentReviewerKeyId !==
      finalityManifest.independentReviewerKeyId ||
    !Array.isArray(networkTrustContext.blockingReasons) ||
    networkTrustContext.blockingReasons.length !== 0 ||
    !privacyQualified(networkTrustContext.privacy)
  ) {
    return false;
  }
  if (
    !exactKeys(nativeFinalityCanary, FUNDED_CANARY_FINALITY_NATIVE_KEYS) ||
    nativeFinalityCanary.schemaVersion !== 1 ||
    nativeFinalityCanary.contractId !== FUNDED_CANARY_FINALITY_NATIVE_CONTRACT_ID ||
    nativeFinalityCanary.status !== "qualified" ||
    nativeFinalityCanary.platform !== "android" ||
    !safePositiveInteger(nativeFinalityCanary.reviewedAtEpochSeconds) ||
    nativeFinalityCanary.requiredNativeAbi !== 21 ||
    nativeFinalityCanary.observedNativeAbi !== 21 ||
    nativeFinalityCanary.artifactClass !== "reviewed-platform-release" ||
    !nonzeroSha256(nativeFinalityCanary.reviewedPlatformArtifactSha256) ||
    nativeFinalityCanary.verifierSourceRevision !==
      productionFinality.verifierSourceRevision ||
    nativeFinalityCanary.verifierArtifactSha256 !==
      productionFinality.verifierArtifactSha256 ||
    nativeFinalityCanary.finalityTrustManifestBindingSha256 !==
      fundedCanaryV4FinalityManifestBindingSha256(finalityManifest) ||
    nativeFinalityCanary.attestationRoute !== FUNDED_CANARY_ATTESTATION_ROUTE ||
    nativeFinalityCanary.bundleRoute !== FUNDED_CANARY_BUNDLE_ROUTE ||
    nativeFinalityCanary.attestationResponseType !==
      FUNDED_CANARY_ATTESTATION_RESPONSE_TYPE ||
    nativeFinalityCanary.bundleResponseType !==
      FUNDED_CANARY_BUNDLE_RESPONSE_TYPE ||
    !nonzeroSha256(nativeFinalityCanary.requiredExportInventorySha256) ||
    nativeFinalityCanary.observedExportInventorySha256 !==
      nativeFinalityCanary.requiredExportInventorySha256 ||
    ![
      nativeFinalityCanary.reviewedPlatformArtifactSha256,
      nativeFinalityCanary.verifierArtifactSha256,
      nativeFinalityCanary.finalityTrustManifestBindingSha256,
      nativeFinalityCanary.requiredExportInventorySha256,
      nativeFinalityCanary.attestationNoritoRoundTripKatSha256,
      nativeFinalityCanary.bundleNoritoRoundTripKatSha256,
      nativeFinalityCanary.challengeBindingKatSha256,
      nativeFinalityCanary.genesisProofKatSha256,
      nativeFinalityCanary.tipProofKatSha256,
      nativeFinalityCanary.nodeSignatureKatSha256,
      nativeFinalityCanary.aggregateSignatureKatSha256,
      nativeFinalityCanary.successorProofKatSha256,
      nativeFinalityCanary.finalizedProjectionKatSha256,
    ].every(nonzeroSha256) ||
    new Set([
      nativeFinalityCanary.reviewedPlatformArtifactSha256,
      nativeFinalityCanary.verifierArtifactSha256,
      nativeFinalityCanary.finalityTrustManifestBindingSha256,
      nativeFinalityCanary.requiredExportInventorySha256,
      nativeFinalityCanary.attestationNoritoRoundTripKatSha256,
      nativeFinalityCanary.bundleNoritoRoundTripKatSha256,
      nativeFinalityCanary.challengeBindingKatSha256,
      nativeFinalityCanary.genesisProofKatSha256,
      nativeFinalityCanary.tipProofKatSha256,
      nativeFinalityCanary.nodeSignatureKatSha256,
      nativeFinalityCanary.aggregateSignatureKatSha256,
      nativeFinalityCanary.successorProofKatSha256,
      nativeFinalityCanary.finalizedProjectionKatSha256,
    ]).size !== 13 ||
    ![
      "attestationNoritoRoundTripKatPassed",
      "bundleNoritoRoundTripKatPassed",
      "challengeBindingKatPassed",
      "genesisProofKatPassed",
      "tipProofKatPassed",
      "nodeSignatureKatPassed",
      "aggregateSignatureKatPassed",
      "successorProofKatPassed",
      "finalizedProjectionKatPassed",
    ].every((field) => nativeFinalityCanary[field] === true) ||
    nativeFinalityCanary.artifactIdentityQualified !== true ||
    nativeFinalityCanary.abiQualified !== true ||
    nativeFinalityCanary.exportInventoryQualified !== true ||
    nativeFinalityCanary.canonicalNoritoRoundTripQualified !== true ||
    nativeFinalityCanary.attestationDecodeProjectionQualified !== true ||
    nativeFinalityCanary.bundleDecodeProjectionQualified !== true ||
    nativeFinalityCanary.trustContextBindingQualified !== true ||
    nativeFinalityCanary.sourceTreeClean !== true ||
    nativeFinalityCanary.dirtyLocalDebugArtifactAccepted !== false ||
    nativeFinalityCanary.independentReviewerKeyId !==
      finalityManifest.independentReviewerKeyId ||
    !Array.isArray(nativeFinalityCanary.blockingReasons) ||
    nativeFinalityCanary.blockingReasons.length !== 0 ||
    !privacyQualified(nativeFinalityCanary.privacy)
  ) {
    return false;
  }
  if (
    networkTrustContext.reviewedAtEpochSeconds >
      nativeFinalityCanary.reviewedAtEpochSeconds ||
    nativeFinalityCanary.reviewedAtEpochSeconds >
      finalityManifest.reviewedAtEpochSeconds ||
    finalityManifest.reviewedAtEpochSeconds >
      receipt.execution.startedAtEpochSeconds
  ) {
    return false;
  }
  const expectedFinalityBinding = fundedCanaryV4FinalityBindingSha256(finality);
  const expectedChallengeBinding = fundedCanaryV4ChallengeBindingSha256({
    canaryRunId: receipt.execution.canaryRunId,
    candidateBindingSha256: receipt.candidate.candidateBindingSha256,
    networkId,
    chainId: expectedNetwork.chainId,
    finalizedBlockHeight: finalityStage.finalizedBlockHeight,
    finalizedBlockHash: finalityStage.finalizedBlockHash,
    attestationChallengeSha256: finalityStage.attestationChallengeSha256,
    attestationEvidenceSha256: finalityStage.attestationEvidenceSha256,
    bundleEvidenceSha256: finalityStage.bundleEvidenceSha256,
    finalityBindingSha256: finalityStage.finalityBindingSha256,
  });
  if (
    finality.bindingSha256 !== expectedFinalityBinding ||
    finality.status !== "qualified" ||
    finality.adapterType !== productionFinality.adapterType ||
    finality.adapterSourcePath !== productionFinality.adapterSourcePath ||
    finality.adapterSourceSha256 !== productionFinality.adapterSourceSha256 ||
    finality.readinessReceiptSha256 !== readinessSha256 ||
    finality.verifierSourceRevision !==
      productionFinality.verifierSourceRevision ||
    finality.verifierArtifactSha256 !==
      productionFinality.verifierArtifactSha256 ||
    finality.nativeFinalityCanaryReceiptSha256 !== nativeFinalityCanarySha256 ||
    finality.finalityTrustManifestReceiptSha256 !== finalityManifestSha256 ||
    finality.networkTrustContextReceiptSha256 !== networkTrustContextSha256 ||
    finality.serverContractSourceRevision !==
      trustContract.serverContractSourceRevision ||
    finality.serverOpenApiSha256 !== trustContract.serverOpenApiSha256 ||
    finality.serverRouteSourceSha256 !== trustContract.serverRouteSourceSha256 ||
    finality.attestationRoute !== FUNDED_CANARY_ATTESTATION_ROUTE ||
    finality.bundleRoute !== FUNDED_CANARY_BUNDLE_ROUTE ||
    finalityStage.attestationChallengeBindingSha256 !== expectedChallengeBinding ||
    finalityStage.reviewedVerifierSourceRevision !==
      finality.verifierSourceRevision ||
    finalityStage.reviewedVerifierArtifactSha256 !==
      finality.verifierArtifactSha256 ||
    finalityStage.nativeFinalityCanaryReceiptSha256 !==
      finality.nativeFinalityCanaryReceiptSha256 ||
    finalityStage.finalityTrustManifestReceiptSha256 !==
      finality.finalityTrustManifestReceiptSha256 ||
    finalityStage.networkTrustContextReceiptSha256 !==
      finality.networkTrustContextReceiptSha256 ||
    finalityStage.finalityBindingSha256 !== finality.bindingSha256 ||
    receipt.qualification.currentChainIdentityMatched !== true ||
    receipt.qualification.finalityIdentityMatched !== true
  ) {
    return false;
  }
  for (const stageName of ["terminalStatusReadback", "finalityReadback"]) {
    const stage = receipt.execution[stageName];
    const evidenceStage = evidenceRecord?.stages?.[stageName];
    if (
      evidenceStage === undefined ||
      JSON.stringify(stage) !== JSON.stringify(evidenceStage) ||
      stage.evidenceSha256 !==
        fundedCanaryV4CanonicalSha256(
          fundedCanaryV4StageProjection({
            receipt,
            stageName,
            assertions: stageAssertions[stageName],
          }),
        )
    ) {
      return false;
    }
  }
  return true;
};
