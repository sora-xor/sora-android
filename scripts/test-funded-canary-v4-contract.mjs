#!/usr/bin/env node

import assert from "node:assert/strict";
import {
  createHash,
  generateKeyPairSync,
  sign,
  verify,
} from "node:crypto";
import {
  FUNDED_CANARY_FINALITY_CONTEXT_KEYS,
  FUNDED_CANARY_FINALITY_MANIFEST_KEYS,
  FUNDED_CANARY_FINALITY_NATIVE_KEYS,
  FUNDED_CANARY_V4_MINAMOTO_NETWORK,
  FUNDED_CANARY_V4_READINESS_PREREQUISITE_KEYS,
  fundedCanaryV4CanonicalSha256,
  fundedCanaryV4ChallengeBindingSha256,
  fundedCanaryV4FinalityBindingSha256,
  fundedCanaryV4FinalityManifestBindingSha256,
  fundedCanaryV4FinalityManifestProjection,
  fundedCanaryV4NativeFinalityProjection,
  fundedCanaryV4StageProjection,
  fundedCanaryV4TrustContextProjection,
  validateFundedCanaryV4PolicyChronology,
  validateFundedCanaryV4PairDistinct,
  validateFundedCanaryV4Proof,
} from "./lib/funded-canary-v4.mjs";

const sha256 = (label) =>
  createHash("sha256").update(`android-funded-v4:${label}`).digest("hex");
const privacy = () => ({
  redactedAggregateEvidenceOnly: true,
  accountIdentifiersIncluded: false,
  addressesIncluded: false,
  transactionIdentifiersIncluded: false,
  phrasesOrSeedsIncluded: false,
  privateKeysIncluded: false,
  rawSignedPayloadsIncluded: false,
  rawNetworkResponsesIncluded: false,
  deviceIdentifiersIncluded: false,
  operatorNamesIncluded: false,
});
const clone = (value) => structuredClone(value);
const { privateKey, publicKey } = generateKeyPairSync("ed25519");
const signedProjection = (projection) =>
  sign(null, Buffer.from(projection.join("\n"), "utf8"), privateKey).toString(
    "base64",
  );
const signatureQualified = (projection, signature) =>
  verify(
    null,
    Buffer.from(projection.join("\n"), "utf8"),
    publicKey,
    Buffer.from(signature, "base64"),
  );

const stageAssertions = {
  terminalStatusReadback: {
    exactHashStatusQueried: true,
    committedTerminalStatusObserved: true,
    globalStateResolutionObserved: true,
    positiveCommittedBlockHeightObserved: true,
  },
  finalityReadback: {
    freshChallengeBoundAttestationVerified: true,
    canonicalNoritoRoundTripVerified: true,
    attestationRouteMatched: true,
    bundleRouteMatched: true,
    reviewedVerifierBindingMatched: true,
    trustedContextReceiptMatched: true,
    genesisAndTipProofsVerified: true,
    nodeAndAggregateSignaturesVerified: true,
    singleStateViewVerified: true,
    liveBoundedSequentialStatefulSuccessorChainVerified: true,
    receiptBlockReadBack: true,
    walletNetworkIdMatched: true,
    chainIdMatched: true,
    positiveFinalizedBlockHeightObserved: true,
  },
};

const TAIRA_CHAIN_A = "809574f5-fee7-5e69-bfcf-52451e42d50f";
const TAIRA_CHAIN_B = "fc56984b-2be7-431d-840e-21514d1883f0";
const tairaNetwork = (chainId) => ({
  chainId,
  i105Discriminant: 369,
  toriiBaseUrl: "https://node-2.taira.sora.org",
  explorerBaseUrl: "https://taira-explorer.sora.org",
  isTestnet: true,
});

const buildEvidence = (networkId, tairaChainId = TAIRA_CHAIN_B) => {
  const network = networkId === "taira"
    ? tairaNetwork(tairaChainId)
    : FUNDED_CANARY_V4_MINAMOTO_NETWORK;
  const readinessSha256 = sha256(`${networkId}:readiness`);
  const nativeSha256 = sha256(`${networkId}:native-finality`);
  const manifestSha256 = sha256(`${networkId}:finality-manifest`);
  const contextSha256 = sha256(`${networkId}:finality-context`);
  const verifierSourceRevision = "abcdef0123456789abcdef0123456789abcdef01";
  const verifierArtifactSha256 = sha256("reviewed-finality-verifier");
  const serverSourceRevision = "fedcba9876543210fedcba9876543210fedcba98";
  const serverOpenApiSha256 = sha256("finality-openapi");
  const serverRouteSourceSha256 = sha256("finality-route-source");
  const candidateBindingSha256 = sha256(`${networkId}:candidate-binding`);
  const canaryRunId = sha256(`${networkId}:canary-run`);
  const startedAtEpochSeconds = 120;
  const committedBlockHeight = 1_000;
  const finalizedBlockHeight = 1_001;

  const readiness = {
    schemaVersion: 4,
    productionFinalityBinding: {
      status: "qualified",
      adapterType: "ReviewedNexusFinalityReader",
      adapterSourcePath: "feature_wallet_impl/reviewed/NexusFinalityReader.kt",
      adapterSourceSha256: sha256("finality-adapter"),
      verifierSourceRevision,
      verifierArtifactSha256,
      nativeFinalityCanaryReceiptSha256: nativeSha256,
      finalityTrustManifestReceiptSha256: manifestSha256,
    },
    finalityTrustContract: {
      status: "qualified",
      serverContractSourceRevision: serverSourceRevision,
      serverOpenApiSha256,
      serverRouteSourceSha256,
      minamotoTrustedContextReceiptSha256:
        networkId === "minamoto" ? contextSha256 : sha256("minamoto-context"),
      tairaTrustedContextReceiptSha256:
        networkId === "taira" ? contextSha256 : sha256("taira-context"),
    },
    nativeFinalityCanaryContract: {
      status: "qualified",
      receiptSha256: nativeSha256,
      attestationRoute: "/v1/bridge/finality/attestation/{height}",
      bundleRoute: "/v1/bridge/finality/bundle/{height}",
      attestationResponseType: "BridgeFinalityAttestationV1",
      bundleResponseType: "BridgeFinalityBundle",
      reviewedPlatformArtifactPresent: true,
      canonicalNoritoRoundTripQualified: true,
      attestationDecodeProjectionQualified: true,
      bundleDecodeProjectionQualified: true,
      trustContextBindingQualified: true,
      dirtyLocalDebugArtifactAccepted: false,
    },
    releaseCriteria: {
      ...Object.fromEntries(
        FUNDED_CANARY_V4_READINESS_PREREQUISITE_KEYS.map((key) => [key, true]),
      ),
      fundedTairaCanaryQualified: false,
      fundedMinamotoCanaryQualified: false,
    },
  };
  const finalityManifest = {
    schemaVersion: 1,
    contractId: "sora-android-nexus-finality-trust-manifest-v1",
    status: "qualified",
    platform: "android",
    reviewedAtEpochSeconds: 110,
    verifierSourceRevision,
    verifierArtifactSha256,
    nativeFinalityCanaryReceiptSha256: nativeSha256,
    serverContractSourceRevision: serverSourceRevision,
    serverOpenApiSha256,
    serverRouteSourceSha256,
    attestationRoute: "/v1/bridge/finality/attestation/{height}",
    bundleRoute: "/v1/bridge/finality/bundle/{height}",
    minamotoTrustContextReceiptSha256:
      networkId === "minamoto" ? contextSha256 : sha256("minamoto-context"),
    tairaTrustContextReceiptSha256:
      networkId === "taira" ? contextSha256 : sha256("taira-context"),
    independentReviewerKeyId: "synthetic-independent-reviewer",
    independentReviewerSignatureBase64: "",
    privacy: privacy(),
    blockingReasons: [],
  };
  const networkTrustContext = {
    schemaVersion: 1,
    contractId: "sora-android-nexus-finality-trust-context-v1",
    status: "qualified",
    platform: "android",
    networkId,
    chainId: network.chainId,
    reviewedAtEpochSeconds: 100,
    expectedNodeKeySha256: sha256(`${networkId}:node-key`),
    expectedNodeBuildFingerprint: sha256(`${networkId}:node-build`),
    expectedProtocolVersion: "iroha.v2",
    expectedConsensusMode: "sumeragi.v2",
    validatorRosterSha256: sha256(`${networkId}:roster`),
    quorumNumerator: 2,
    quorumDenominator: 3,
    canonicalSignedGenesisSha256: sha256(`${networkId}:signed-genesis`),
    genesisPublicKeySha256: sha256(`${networkId}:genesis-key`),
    trustedFirstHeight: 1,
    trustedFirstHeightContextId: sha256(`${networkId}:first-height`),
    verifierSourceRevision,
    verifierArtifactSha256,
    serverContractSourceRevision: serverSourceRevision,
    serverOpenApiSha256,
    serverRouteSourceSha256,
    independentReviewerKeyId: "synthetic-independent-reviewer",
    independentReviewerSignatureBase64: "",
    privacy: privacy(),
    blockingReasons: [],
  };
  const katHashes = [
    "attestation",
    "bundle",
    "challenge",
    "genesis",
    "tip",
    "node-signature",
    "aggregate-signature",
    "successor",
    "finalized-projection",
  ].map((label) => sha256(`${networkId}:kat:${label}`));
  const nativeFinalityCanary = {
    schemaVersion: 1,
    contractId: "sora-android-nexus-finality-native-canary-v1",
    status: "qualified",
    platform: "android",
    reviewedAtEpochSeconds: 105,
    requiredNativeAbi: 21,
    observedNativeAbi: 21,
    artifactClass: "reviewed-platform-release",
    reviewedPlatformArtifactSha256: sha256("reviewed-platform-artifact"),
    verifierSourceRevision,
    verifierArtifactSha256,
    finalityTrustManifestBindingSha256:
      fundedCanaryV4FinalityManifestBindingSha256(finalityManifest),
    attestationRoute: "/v1/bridge/finality/attestation/{height}",
    bundleRoute: "/v1/bridge/finality/bundle/{height}",
    attestationResponseType: "BridgeFinalityAttestationV1",
    bundleResponseType: "BridgeFinalityBundle",
    requiredExportInventorySha256: sha256("required-finality-exports"),
    observedExportInventorySha256: sha256("required-finality-exports"),
    attestationNoritoRoundTripKatSha256: katHashes[0],
    bundleNoritoRoundTripKatSha256: katHashes[1],
    challengeBindingKatSha256: katHashes[2],
    genesisProofKatSha256: katHashes[3],
    tipProofKatSha256: katHashes[4],
    nodeSignatureKatSha256: katHashes[5],
    aggregateSignatureKatSha256: katHashes[6],
    successorProofKatSha256: katHashes[7],
    finalizedProjectionKatSha256: katHashes[8],
    attestationNoritoRoundTripKatPassed: true,
    bundleNoritoRoundTripKatPassed: true,
    challengeBindingKatPassed: true,
    genesisProofKatPassed: true,
    tipProofKatPassed: true,
    nodeSignatureKatPassed: true,
    aggregateSignatureKatPassed: true,
    successorProofKatPassed: true,
    finalizedProjectionKatPassed: true,
    artifactIdentityQualified: true,
    abiQualified: true,
    exportInventoryQualified: true,
    canonicalNoritoRoundTripQualified: true,
    attestationDecodeProjectionQualified: true,
    bundleDecodeProjectionQualified: true,
    trustContextBindingQualified: true,
    sourceTreeClean: true,
    dirtyLocalDebugArtifactAccepted: false,
    independentReviewerKeyId: "synthetic-independent-reviewer",
    independentReviewerSignatureBase64: "",
    privacy: privacy(),
    blockingReasons: [],
  };
  const finality = {
    status: "qualified",
    readinessReceiptSha256: readinessSha256,
    adapterType: readiness.productionFinalityBinding.adapterType,
    adapterSourcePath: readiness.productionFinalityBinding.adapterSourcePath,
    adapterSourceSha256: readiness.productionFinalityBinding.adapterSourceSha256,
    verifierSourceRevision,
    verifierArtifactSha256,
    nativeFinalityCanaryReceiptSha256: nativeSha256,
    finalityTrustManifestReceiptSha256: manifestSha256,
    networkTrustContextReceiptSha256: contextSha256,
    serverContractSourceRevision: serverSourceRevision,
    serverOpenApiSha256,
    serverRouteSourceSha256,
    attestationRoute: "/v1/bridge/finality/attestation/{height}",
    bundleRoute: "/v1/bridge/finality/bundle/{height}",
    bindingSha256: "",
  };
  finality.bindingSha256 = fundedCanaryV4FinalityBindingSha256(finality);
  const attestationChallengeSha256 = sha256(`${networkId}:challenge`);
  const attestationEvidenceSha256 = sha256(`${networkId}:attestation-evidence`);
  const bundleEvidenceSha256 = sha256(`${networkId}:bundle-evidence`);
  const finalizedBlockHash = sha256(`${networkId}:finalized-block`);
  const receipt = {
    schemaVersion: 4,
    contractId: "sora-android-funded-nexus-canary-v4",
    candidate: { candidateBindingSha256 },
    network: {
      networkId,
      chainId: network.chainId,
      i105Discriminant: network.i105Discriminant,
      toriiBaseUrl: network.toriiBaseUrl,
      explorerBaseUrl: network.explorerBaseUrl,
      isTestnet: network.isTestnet,
    },
    finality,
    operatorApproval: {
      approvalNonce: sha256(`${networkId}:approval-nonce`),
    },
    execution: {
      canaryRunId,
      startedAtEpochSeconds,
      terminalStatusReadback: {
        status: "qualified",
        observedAtEpochSeconds: 126,
        evidenceSha256: "",
        committedBlockHeight,
        ...stageAssertions.terminalStatusReadback,
      },
      finalityReadback: {
        status: "qualified",
        observedAtEpochSeconds: 127,
        evidenceSha256: "",
        networkId,
        chainId: network.chainId,
        finalizedBlockHeight,
        finalizedBlockHash,
        attestationChallengeSha256,
        attestationChallengeBindingSha256:
          fundedCanaryV4ChallengeBindingSha256({
            canaryRunId,
            candidateBindingSha256,
            networkId,
            chainId: network.chainId,
            finalizedBlockHeight,
            finalizedBlockHash,
            attestationChallengeSha256,
            attestationEvidenceSha256,
            bundleEvidenceSha256,
            finalityBindingSha256: finality.bindingSha256,
          }),
        attestationEvidenceSha256,
        bundleEvidenceSha256,
        reviewedVerifierSourceRevision: verifierSourceRevision,
        reviewedVerifierArtifactSha256: verifierArtifactSha256,
        nativeFinalityCanaryReceiptSha256: nativeSha256,
        finalityTrustManifestReceiptSha256: manifestSha256,
        networkTrustContextReceiptSha256: contextSha256,
        finalityBindingSha256: finality.bindingSha256,
        ...stageAssertions.finalityReadback,
      },
    },
    qualification: {
      currentChainIdentityMatched: true,
      finalityIdentityMatched: true,
    },
  };
  const evidenceRecord = { stages: {} };
  const resealStages = () => {
    for (const stageName of ["terminalStatusReadback", "finalityReadback"]) {
      receipt.execution[stageName].evidenceSha256 =
        fundedCanaryV4CanonicalSha256(
          fundedCanaryV4StageProjection({
            receipt,
            stageName,
            assertions: stageAssertions[stageName],
          }),
        );
      evidenceRecord.stages[stageName] = clone(receipt.execution[stageName]);
    }
  };
  const resignFinalityEvidence = () => {
    finalityManifest.independentReviewerSignatureBase64 = signedProjection(
      fundedCanaryV4FinalityManifestProjection(finalityManifest),
    );
    networkTrustContext.independentReviewerSignatureBase64 = signedProjection(
      fundedCanaryV4TrustContextProjection(networkTrustContext),
    );
    nativeFinalityCanary.independentReviewerSignatureBase64 = signedProjection(
      fundedCanaryV4NativeFinalityProjection(nativeFinalityCanary),
    );
  };
  const rebindFinalityIdentity = () => {
    nativeFinalityCanary.finalityTrustManifestBindingSha256 =
      fundedCanaryV4FinalityManifestBindingSha256(finalityManifest);
    finality.bindingSha256 = fundedCanaryV4FinalityBindingSha256(finality);
    const stage = receipt.execution.finalityReadback;
    stage.reviewedVerifierSourceRevision = finality.verifierSourceRevision;
    stage.reviewedVerifierArtifactSha256 = finality.verifierArtifactSha256;
    stage.finalityBindingSha256 = finality.bindingSha256;
    stage.attestationChallengeBindingSha256 =
      fundedCanaryV4ChallengeBindingSha256({
        canaryRunId,
        candidateBindingSha256,
        networkId,
        chainId: network.chainId,
        finalizedBlockHeight: stage.finalizedBlockHeight,
        finalizedBlockHash: stage.finalizedBlockHash,
        attestationChallengeSha256: stage.attestationChallengeSha256,
        attestationEvidenceSha256: stage.attestationEvidenceSha256,
        bundleEvidenceSha256: stage.bundleEvidenceSha256,
        finalityBindingSha256: stage.finalityBindingSha256,
      });
    resealStages();
    resignFinalityEvidence();
  };
  resealStages();
  resignFinalityEvidence();
  return {
    receipt,
    evidenceRecord,
    readiness,
    readinessSha256,
    finalityManifest,
    finalityManifestSha256: manifestSha256,
    networkTrustContext,
    networkTrustContextSha256: contextSha256,
    nativeFinalityCanary,
    nativeFinalityCanarySha256: nativeSha256,
    stageAssertions,
    expectedNetwork: network,
    resealStages,
    resignFinalityEvidence,
    rebindFinalityIdentity,
  };
};

const qualified = (evidence) =>
  signatureQualified(
    fundedCanaryV4FinalityManifestProjection(evidence.finalityManifest),
    evidence.finalityManifest.independentReviewerSignatureBase64,
  ) &&
  signatureQualified(
    fundedCanaryV4TrustContextProjection(evidence.networkTrustContext),
    evidence.networkTrustContext.independentReviewerSignatureBase64,
  ) &&
  signatureQualified(
    fundedCanaryV4NativeFinalityProjection(evidence.nativeFinalityCanary),
    evidence.nativeFinalityCanary.independentReviewerSignatureBase64,
  ) &&
  validateFundedCanaryV4Proof(evidence);

for (const networkId of ["taira", "minamoto"]) {
  assert.equal(qualified(buildEvidence(networkId)), true, `${networkId} positive`);
}
assert.equal(
  qualified(buildEvidence("taira", TAIRA_CHAIN_A)),
  true,
  "alternate operator-selected Taira mapping positive",
);

const pairRecord = (networkId, evidence) => ({
  receiptSha256: sha256(`${networkId}:qualified-receipt`),
  receipt: evidence.receipt,
});
const tairaPairEvidence = buildEvidence("taira");
const minamotoPairEvidence = buildEvidence("minamoto");
assert.equal(
  validateFundedCanaryV4PairDistinct({
    taira: pairRecord("taira", tairaPairEvidence),
    minamoto: pairRecord("minamoto", minamotoPairEvidence),
  }),
  true,
  "cross-network pair positive",
);
const crossNetworkReuseMutations = [
  ["receipt hash", (taira, minamoto) => {
    minamoto.receiptSha256 = taira.receiptSha256;
  }],
  ["candidate binding", (taira, minamoto) => {
    minamoto.receipt.candidate.candidateBindingSha256 =
      taira.receipt.candidate.candidateBindingSha256;
  }],
  ["finality binding", (taira, minamoto) => {
    minamoto.receipt.finality.bindingSha256 =
      taira.receipt.finality.bindingSha256;
  }],
  ["run ID", (taira, minamoto) => {
    minamoto.receipt.execution.canaryRunId =
      taira.receipt.execution.canaryRunId;
  }],
  ["approval nonce", (taira, minamoto) => {
    minamoto.receipt.operatorApproval.approvalNonce =
      taira.receipt.operatorApproval.approvalNonce;
  }],
  ["challenge hash", (taira, minamoto) => {
    minamoto.receipt.execution.finalityReadback.attestationChallengeSha256 =
      taira.receipt.execution.finalityReadback.attestationChallengeSha256;
  }],
];
for (const [dimension, mutate] of crossNetworkReuseMutations) {
  const taira = pairRecord("taira", buildEvidence("taira"));
  const minamoto = pairRecord("minamoto", buildEvidence("minamoto"));
  mutate(taira, minamoto);
  assert.equal(
    validateFundedCanaryV4PairDistinct({ taira, minamoto }),
    false,
    `reused cross-network ${dimension}`,
  );
}

let failClosedMutationCount = 0;
const rejectMutation = (name, mutate, { reseal = false, resign = false } = {}) => {
  const evidence = buildEvidence("taira");
  mutate(evidence);
  if (reseal) evidence.resealStages();
  if (resign) evidence.resignFinalityEvidence();
  assert.equal(qualified(evidence), false, name);
  failClosedMutationCount += 1;
};

rejectMutation("preclaimed readiness result", (value) => {
  value.readiness.releaseCriteria.fundedTairaCanaryQualified = true;
});
rejectMutation(
  "finalized below committed",
  (value) => {
    value.receipt.execution.finalityReadback.finalizedBlockHeight = 999;
  },
  { reseal: true },
);
rejectMutation(
  "trusted first height beyond finalized checkpoint",
  (value) => {
    value.networkTrustContext.trustedFirstHeight =
      value.receipt.execution.finalityReadback.finalizedBlockHeight + 1;
  },
  { resign: true },
);
rejectMutation(
  "missing live bounded sequential stateful successor-chain proof",
  (value) => {
    value.receipt.execution.finalityReadback.liveBoundedSequentialStatefulSuccessorChainVerified =
      false;
  },
  { reseal: true },
);
rejectMutation(
  "invalid challenge binding",
  (value) => {
    value.receipt.execution.finalityReadback.attestationChallengeBindingSha256 =
      sha256("wrong-challenge-binding");
  },
  { reseal: true },
);
rejectMutation(
  "prefixed finalized block hash",
  (value) => {
    value.receipt.execution.finalityReadback.finalizedBlockHash =
      `0x${sha256("prefixed-finality")}`;
  },
  { reseal: true },
);
rejectMutation(
  "finalized block hash challenge drift",
  (value) => {
    value.receipt.execution.finalityReadback.finalizedBlockHash = sha256(
      "changed-finalized-block",
    );
  },
  { reseal: true },
);
rejectMutation(
  "attestation and bundle evidence alias",
  (value) => {
    value.receipt.execution.finalityReadback.bundleEvidenceSha256 =
      value.receipt.execution.finalityReadback.attestationEvidenceSha256;
  },
  { reseal: true },
);
rejectMutation(
  "bundle evidence challenge drift",
  (value) => {
    value.receipt.execution.finalityReadback.bundleEvidenceSha256 = sha256(
      "changed-bundle-evidence",
    );
  },
  { reseal: true },
);
rejectMutation("current chain mismatch", (value) => {
  value.receipt.network.chainId = "00000000-0000-0000-0000-000000000753";
});
rejectMutation("finality context receipt mismatch", (value) => {
  value.receipt.finality.networkTrustContextReceiptSha256 = sha256(
    "wrong-context",
  );
  value.receipt.finality.bindingSha256 = fundedCanaryV4FinalityBindingSha256(
    value.receipt.finality,
  );
});
rejectMutation(
  "finality binding challenge drift",
  (value) => {
    const changedAdapterSha256 = sha256("changed-finality-adapter");
    value.readiness.productionFinalityBinding.adapterSourceSha256 =
      changedAdapterSha256;
    value.receipt.finality.adapterSourceSha256 = changedAdapterSha256;
    value.receipt.finality.bindingSha256 = fundedCanaryV4FinalityBindingSha256(
      value.receipt.finality,
    );
    value.receipt.execution.finalityReadback.finalityBindingSha256 =
      value.receipt.finality.bindingSha256;
  },
  { reseal: true },
);
rejectMutation(
  "invalid native ABI",
  (value) => {
    value.nativeFinalityCanary.observedNativeAbi = 20;
  },
  { resign: true },
);
rejectMutation(
  "retroactive network trust",
  (value) => {
    value.networkTrustContext.reviewedAtEpochSeconds = 111;
  },
  { resign: true },
);
rejectMutation(
  "retroactive finality manifest",
  (value) => {
    value.finalityManifest.reviewedAtEpochSeconds = 121;
  },
  { resign: true },
);
rejectMutation(
  "dirty local debug finality artifact",
  (value) => {
    value.nativeFinalityCanary.sourceTreeClean = false;
    value.nativeFinalityCanary.dirtyLocalDebugArtifactAccepted = true;
  },
  { resign: true },
);
rejectMutation(
  "aliased finality KAT identities",
  (value) => {
    value.nativeFinalityCanary.bundleNoritoRoundTripKatSha256 =
      value.nativeFinalityCanary.attestationNoritoRoundTripKatSha256;
  },
  { resign: true },
);
rejectMutation(
  "aliased finality artifact identities",
  (value) => {
    value.nativeFinalityCanary.reviewedPlatformArtifactSha256 =
      value.nativeFinalityCanary.verifierArtifactSha256;
  },
  { resign: true },
);
const katPassFlagMutations = [
  "attestationNoritoRoundTripKatPassed",
  "bundleNoritoRoundTripKatPassed",
  "challengeBindingKatPassed",
  "genesisProofKatPassed",
  "tipProofKatPassed",
  "nodeSignatureKatPassed",
  "aggregateSignatureKatPassed",
  "successorProofKatPassed",
  "finalizedProjectionKatPassed",
];
for (const field of katPassFlagMutations) {
  rejectMutation(
    `false native finality KAT pass flag ${field}`,
    (value) => {
      value.nativeFinalityCanary[field] = false;
    },
    { resign: true },
  );
}
rejectMutation(
  "non-array finality manifest blocking reasons",
  (value) => {
    value.finalityManifest.blockingReasons = "";
  },
  { resign: true },
);
rejectMutation(
  "non-array network trust context blocking reasons",
  (value) => {
    value.networkTrustContext.blockingReasons = "";
  },
  { resign: true },
);
rejectMutation(
  "non-array native finality blocking reasons",
  (value) => {
    value.nativeFinalityCanary.blockingReasons = "";
  },
  { resign: true },
);
rejectMutation("all-zero finality verifier source revision", (value) => {
  const allZeroRevision = "0".repeat(40);
  value.readiness.productionFinalityBinding.verifierSourceRevision =
    allZeroRevision;
  value.finalityManifest.verifierSourceRevision = allZeroRevision;
  value.networkTrustContext.verifierSourceRevision = allZeroRevision;
  value.nativeFinalityCanary.verifierSourceRevision = allZeroRevision;
  value.receipt.finality.verifierSourceRevision = allZeroRevision;
  value.rebindFinalityIdentity();
});
rejectMutation("all-zero finality server source revision", (value) => {
  const allZeroRevision = "0".repeat(40);
  value.readiness.finalityTrustContract.serverContractSourceRevision =
    allZeroRevision;
  value.finalityManifest.serverContractSourceRevision = allZeroRevision;
  value.networkTrustContext.serverContractSourceRevision = allZeroRevision;
  value.receipt.finality.serverContractSourceRevision = allZeroRevision;
  value.rebindFinalityIdentity();
});

const approvedPolicy = {
  validFromEpochSeconds: 90,
  validUntilEpochSeconds: 160,
  reviewedAtEpochSeconds: 100,
};
const approval = {
  approvedAtEpochSeconds: 110,
  expiresAtEpochSeconds: 150,
};
assert.equal(
  validateFundedCanaryV4PolicyChronology({
    policy: approvedPolicy,
    approval,
  }),
  true,
  "policy positive",
);
assert.equal(
  validateFundedCanaryV4PolicyChronology({
    policy: { ...approvedPolicy, reviewedAtEpochSeconds: 111 },
    approval,
  }),
  false,
  "retroactive policy review",
);
failClosedMutationCount += 1;

assert.deepEqual(Object.keys(buildEvidence("taira").finalityManifest), [
  ...FUNDED_CANARY_FINALITY_MANIFEST_KEYS,
]);
assert.deepEqual(Object.keys(buildEvidence("taira").networkTrustContext), [
  ...FUNDED_CANARY_FINALITY_CONTEXT_KEYS,
]);
assert.deepEqual(Object.keys(buildEvidence("taira").nativeFinalityCanary), [
  ...FUNDED_CANARY_FINALITY_NATIVE_KEYS,
]);
assert.equal(
  failClosedMutationCount,
  33,
  "exact fail-closed mutation inventory",
);

process.stdout.write(
  `funded Nexus canary v4 hermetic contract: 2 networks / 3 operator-selected mapping positives, ${failClosedMutationCount} fail-closed mutations, and cross-network anti-reuse passed\n`,
);
