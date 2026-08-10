#!/usr/bin/env node

import {
  createHash,
  createPublicKey,
  verify as verifyEd25519,
} from "node:crypto";
import {
  closeSync,
  constants as fsConstants,
  fchmodSync,
  fstatSync,
  fsyncSync,
  lstatSync,
  openSync,
  realpathSync,
  unlinkSync,
  writeSync,
} from "node:fs";
import { dirname, isAbsolute, resolve } from "node:path";
import { readStrictJsonFile } from "./lib/strict-evidence.mjs";

const FIXTURE_FORMAT = "sora-mobile-polkamarkt-contract-v2";
const RECEIPT_SCHEMA =
  "sora-mobile-polkamarkt-full-extrinsic-receipt-v1";
const CANDIDATE_SCHEMA =
  "sora-mobile-polkamarkt-platform-extrinsic-receipt-v1";
const REVIEW_SCHEMA = "sora-mobile-polkamarkt-extrinsic-review-v1";
const REVIEW_KEY_SCHEMA =
  "sora-mobile-polkamarkt-extrinsic-review-key-v1";
const REVIEW_SIGNATURE_SCHEMA =
  "sora-mobile-polkamarkt-extrinsic-review-signature-v1";
const CRYPTOGRAPHIC_PROOF_SCHEMA =
  "sora-mobile-polkamarkt-native-cryptographic-proof-v1";
const WEB_REVISION = "893783ba6a19c33043eb5dabe42d949c14d0f257";
const RUNTIME_REVISION = "411dcdb70c5c00b21482a44d02334840d5f338c6";
const METADATA_SHA256 =
  "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf";
const GENESIS_HASH =
  "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5";
const SPEC_VERSION = 130;
const TRANSACTION_VERSION = 130;
const REQUIRED_IDS = [
  "buy",
  "sell",
  "claim_market",
  "claim_markets",
  "claim_creator_fees",
];
const REQUIRED_SHARED_VECTOR_FIELDS = [
  "id",
  "call",
  "arguments",
  "metadataPalletIndex",
  "metadataCallIndex",
  "scaleArgumentsHex",
  "fullCallHex",
  "rawSigningPayloadHex",
  "signingPrehashHex",
  "signingPrehashRule",
  "decodedProjection",
  "decodedProjectionSha256",
];
const REQUIRED_PLATFORM_VECTOR_FIELDS = [
  "id",
  "signerPublicKeyHex",
  "signatureHex",
  "signedExtrinsicHex",
  "extrinsicHashHex",
  "signingPrehashHex",
  "decodedProjection",
  "decodedProjectionSha256",
];
const REQUIRED_PARITY_RULES = [
  "reference-android-ios-full-call-bytes-equal",
  "reference-android-ios-signing-prehash-equal",
  "reference-android-ios-decoded-projection-sha256-equal",
  "each-platform-reviewed-cryptographic-proof-signature-verifies",
  "each-proof-binds-source-metadata-context-vectors-and-signed-extrinsics",
  "each-proof-attests-sr25519-verification-and-live-metadata-round-trip",
  "sr25519-signature-bytes-may-differ-but-must-verify",
];
const REQUIRED_PROOF_FIELDS = [
  "format",
  "platform",
  "candidateBindingSha256",
  "sourceRevision",
  "sourceTreeSha256",
  "generatorSha256",
  "verifierRevision",
  "verifierBinarySha256",
  "metadataSha256",
  "runtimeRevision",
  "genesisHash",
  "specVersion",
  "transactionVersion",
  "signingContextSha256",
  "vectorsSha256",
  "claims",
  "verifiedAtEpochSeconds",
  "publicKeySpkiDerHex",
  "signatureHex",
];
const REQUIRED_PROOF_CLAIMS = [
  "candidate-identity-bound",
  "sr25519-signatures-verified-over-reconstructed-signing-prehashes",
  "signed-extrinsics-decoded-through-pinned-live-metadata",
  "decoded-projections-match-canonical-runtime-call-vectors",
  "signed-extrinsic-hashes-recomputed",
  "non-production-qualification-account-used",
  "private-signing-material-absent-from-receipts",
];
const PLATFORM_REPOSITORIES = {
  reference: "polkaswap-exchange-web",
  android: "sora-wallet/sora-android",
  ios: "sora-wallet/sora-ios",
};
// These identities are a checked-in admission contract, not values that the
// qualification invocation may choose. Replace each null only with the exact
// reviewed current-source identity in this script and both platform fixtures.
// Until then the merge path remains deliberately blocked.
const EXPECTED_QUALIFICATION_ACCOUNT_ID_HEX = null;
const EXPECTED_PLATFORM_SOURCE_MANIFESTS = {
  reference: {
    repository: "polkaswap-exchange-web",
    revision: WEB_REVISION,
    sourceTreeSha256: null,
    generatorSha256: null,
    proofVerifierRevision: null,
    proofVerifierBinarySha256: null,
    proofVerifierPublicKeySha256: null,
  },
  android: {
    repository: "sora-wallet/sora-android",
    revision: null,
    sourceTreeSha256: null,
    generatorSha256: null,
    proofVerifierRevision: null,
    proofVerifierBinarySha256: null,
    proofVerifierPublicKeySha256: null,
  },
  ios: {
    repository: "sora-wallet/sora-ios",
    revision: null,
    sourceTreeSha256: null,
    generatorSha256: null,
    proofVerifierRevision: null,
    proofVerifierBinarySha256: null,
    proofVerifierPublicKeySha256: null,
  },
};
const EXPECTED_WEB_SOURCE_RECEIPTS = [
  ["components/CreateMarketDialog.vue", "9b78f78ca9fc6b5284842159b38322b73e77cf6c", 14_565],
  ["components/MarketDetail.vue", "4907baf693238cbf1776dd3ac9e5b694249a1c15", 13_224],
  ["components/MarketList.vue", "3046a4017b2c5693f7f5756519a421d06f9f86c7", 13_784],
  ["components/MarketOutcomeChart.vue", "ae5f5610a36154a052363bec24093395ed04297f", 11_779],
  ["components/MarketProbabilitySparkline.vue", "c1b66b37ad9ae134a6526dd0b9820460e237c460", 7_144],
  ["components/MarketShareWidget.vue", "d39a40de3d001f051a718052c0e9b3a8541ce3a5", 34_861],
  ["components/MyPositionsPanel.vue", "d0c0e0e1f42f26b0b32277f8822f1190d84c4dba", 5_658],
  ["components/PricingCurvePositionChart.vue", "595d922b8da1bac6c616f76e7f0220e68cc8b22a", 13_374],
  ["components/TradeTicket.vue", "96add09529b46aaef472097ad7e56f21ee3c3529", 46_457],
  ["consts.ts", "f59aed030540bf7a48e76a7b3d86731c044ddd1c", 1_606],
  ["index.ts", "a63af3010332591b60afd1d801dd191549ceab8d", 45],
  ["lib/amounts.ts", "45b3b8f658aeb3e3b21ace52be850635a0970e86", 2_185],
  ["lib/markets.ts", "e64900293e5271cb323404d597f93c98c8817c84", 11_773],
  ["lib/pricingCurve.ts", "16f1b2e686e34cbea604c4f1e9db35e59ebf82ea", 3_918],
  ["lib/share.ts", "ee1fc1f2f8c197b81b788bdf157b21d7b72ac5d3", 3_185],
  ["pages/PolkamarktPage.vue", "0fa3838706464347f5fa5d8b1ed033a9bfd32012", 14_994],
  ["routes.ts", "a4a822ad872df098b92e8c02695f378156932c25", 375],
  ["services/accountActivity.ts", "f0dd3a0a9be0beb260fdea4a01e049e41e9e9281", 8_185],
  ["services/marketHistory.ts", "063f6129b6c20c983c58a9280495057cc077461e", 3_629],
  ["services/markets.ts", "2dfedd05de3b3bacf66c8a31bc8021f2f9e8c033", 8_611],
  ["services/runtimeMarkets.ts", "e958629ad3fa60715fdd337e6374fc478ced4f30", 19_050],
  ["types.ts", "c2a68a3cf717526c231de2f09e8a6eb9d818df83", 2_620],
];
const EXPECTED_CANONICAL_BEHAVIOR = {
  collateralAsset: "KUSD",
  outcomes: ["YES", "NO"],
  tradeModes: ["buy", "sell", "claim"],
  excludedMobileModes: ["create", "report", "resolve", "governance"],
  statusFilters: ["active", "finalized", "all"],
  openStatuses: ["open", "active", "live"],
  finalizedStatuses: [
    "resolved",
    "cancelled",
    "canceled",
    "finalized",
    "closed",
  ],
  claimableStatuses: ["resolved", "cancelled", "canceled"],
  categories: [
    "Politics",
    "Geopolitics",
    "Elections",
    "Crypto",
    "Macro",
    "Finance",
    "Sports",
    "Technology",
    "AI",
    "Science",
    "Climate",
    "Health",
    "Business",
    "Entertainment",
    "Culture",
    "Legal",
    "Other",
  ],
  defaultSlippagePercent: "0.5",
  defaultSlippageBps: 50,
  minimumMobileSlippageBps: 1,
  maximumMobileSlippageBps: 1_000,
  slippagePresetsBps: [10, 50, 100],
  minimumOutputFormula: "floor(output * (10000 - slippageBps) / 10000)",
  quoteDebounceMilliseconds: 250,
  amountPrecision: 18,
  tradeFeeBps: 50,
  maxBatchClaims: 24,
  cardHistoryMarketLimit: 12,
  dpmCurvePointCount: 99,
  dpmPricingFormula: "yes / sqrt(yes^2 + no^2)",
  boardOpensBeforeDetail: true,
  refreshAfterConfirmedReceipt: true,
  receiptStates: ["submitting", "submitted", "confirmed", "failed"],
  authoritativeReads: "finalized-runtime-state",
  discoveryAndHistory: "https://pi.soramitsu.io/graphql",
  ambiguousSubmissionRetry: false,
};
const EXPECTED_DERIVATION_SOURCES = {
  minimumOutput: "lib/amounts.ts",
  runtimeCalls: "services/runtimeMarkets.ts",
  scaleArguments: "../sora2-network/pallets/polkamarkt/src/lib.rs",
  runtimeRevision: RUNTIME_REVISION,
};
const EXPECTED_MINIMUM_OUTPUT_VECTORS = [
  { quotedOutput: "1000", slippageBps: 50, expectedMinimum: "995" },
  { quotedOutput: "10000", slippageBps: 1, expectedMinimum: "9999" },
  { quotedOutput: "999", slippageBps: 1_000, expectedMinimum: "899" },
  {
    quotedOutput: "123456789012345678901234567890",
    slippageBps: 50,
    expectedMinimum: "122839505067283950506728395050",
  },
];
const EXPECTED_RUNTIME_VECTORS = [
  {
    call: "buy",
    arguments: {
      market_id: 7,
      outcome: "Yes",
      collateral_in: "1000",
      min_shares_out: "900",
    },
    scaleArgumentsHex:
      "0700000000e803000000000000000000000000000084030000000000000000000000000000",
  },
  {
    call: "sell",
    arguments: {
      market_id: 8,
      outcome: "No",
      shares_in: "700",
      min_collateral_out: "600",
    },
    scaleArgumentsHex:
      "0800000001bc02000000000000000000000000000058020000000000000000000000000000",
  },
  {
    call: "claim_market",
    arguments: { market_id: 12 },
    scaleArgumentsHex: "0c000000",
  },
  {
    call: "claim_markets",
    arguments: { market_ids: [1, 9, 4_294_967_295] },
    scaleArgumentsHex: "0c0100000009000000ffffffff",
  },
  {
    call: "claim_creator_fees",
    arguments: { market_id: 22 },
    scaleArgumentsHex: "16000000",
  },
];
const EXPECTED_CLAIM_CONFIRMATION = {
  requiresExplicitConfirmation: true,
  requiredReviewedFields: [
    "accountId",
    "source",
    "marketIds",
    "finalizedBlockHash",
    "claims",
  ],
  freshChecksBeforeSigning: [
    "account",
    "featureFlags",
    "runtimeMetadata",
    "claimValues",
    "xorFee",
    "xorBalance",
  ],
  copy: {
    title: "Confirm claim",
    batchTitle: "Confirm %1$d trader payouts",
    body: "Verify these finalized runtime values before signing.",
    feeNotice:
      "The exact XOR network fee and balance will be rechecked before signing.",
  },
};
const EXPECTED_TRANSLATION_KEYS = {
  pageTitle: "pageTitle.Polkamarkt",
  yes: "polkamarkt.outcomes.yes",
  no: "polkamarkt.outcomes.no",
  buy: "polkamarkt.actions.buy",
  sell: "polkamarkt.actions.sell",
  claimTrader: "polkamarkt.actions.claimTraderPayout",
  claimCreatorFees: "polkamarkt.actions.claimCreatorFees",
  sharesOut: "polkamarkt.ticket.sharesOut",
  collateralOut: "polkamarkt.ticket.collateralOut",
  slippage: "polkamarkt.ticket.slippage",
  takerFee: "polkamarkt.ticket.takerFee",
  networkFee: "networkFeeText",
};
const BLOCKER =
  "Generate independent reference, Android, and iOS receipts from the pinned web revision and exact runtime metadata; independently sign the composite receipt; then prove full call bytes, signing prehashes, signatures, signed extrinsics, and decoded projections before enabling mutations.";
const MAXIMUM_FIXTURE_BYTES = 512 * 1024;
const MAXIMUM_CANDIDATE_BYTES = 512 * 1024;
const MAXIMUM_CALL_BYTES = 64 * 1024;
const MAXIMUM_PAYLOAD_BYTES = 256 * 1024;
const MAXIMUM_EXTRINSIC_BYTES = 256 * 1024;
const MAXIMUM_REVIEW_BYTES = 64 * 1024;
const MAXIMUM_KEY_BYTES = 16 * 1024;
const MAXIMUM_SIGNATURE_BYTES = 16 * 1024;
const MAXIMUM_QUALIFIED_FIXTURE_BYTES = 16 * 1024 * 1024;
const HEX40 = /^[0-9a-f]{40}$/;
const HEX64 = /^[0-9a-f]{64}$/;
const DECIMAL = /^(?:0|[1-9][0-9]*)$/;

const fail = (code) => {
  throw new Error(code);
};

const exactKeys = (value, expected, code) => {
  const actual =
    value !== null && typeof value === "object" && !Array.isArray(value)
      ? Object.keys(value)
      : null;
  if (
    actual === null ||
    actual.length !== expected.length ||
    expected.some((key) => !Object.hasOwn(value, key))
  ) {
    fail(code);
  }
};

const requireArrayOf = (value, predicate, code) => {
  if (!Array.isArray(value) || value.some((item) => !predicate(item))) {
    fail(code);
  }
};

const nonEmptyString = (value) =>
  typeof value === "string" && value.length > 0;
const safeNonNegativeInteger = (value) =>
  Number.isSafeInteger(value) && value >= 0;

const nonZeroHex = (value, bytes) =>
  typeof value === "string" &&
  new RegExp(`^[0-9a-f]{${bytes * 2}}$`).test(value) &&
  !/^0+$/.test(value);

const evenHex = (value, minimumBytes = 1) =>
  typeof value === "string" &&
  value.length >= minimumBytes * 2 &&
  value.length % 2 === 0 &&
  /^[0-9a-f]+$/.test(value);

const canonicalAbsolutePath = (value) =>
  typeof value === "string" &&
  value.length > 0 &&
  isAbsolute(value) &&
  resolve(value) === value;

const canonicalOutputPath = (value) => {
  if (!canonicalAbsolutePath(value)) return false;
  try {
    const parent = dirname(value);
    const status = lstatSync(parent);
    return (
      status.isDirectory() &&
      !status.isSymbolicLink() &&
      realpathSync(parent) === parent
    );
  } catch {
    return false;
  }
};

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

const stableJson = (value) => JSON.stringify(stableValue(value));
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const sha256StableJson = (value) => sha256(Buffer.from(stableJson(value)));

const immutableContractProjection = (fixture) => ({
  format: fixture.format,
  sora2Network: fixture.sora2Network,
  webReference: fixture.webReference,
  runtime: fixture.runtime,
  canonicalBehavior: fixture.canonicalBehavior,
  canonicalVectors: {
    derivationSources: fixture.canonicalVectors?.derivationSources,
    minimumOutput: fixture.canonicalVectors?.minimumOutput,
    runtimeCalls: fixture.canonicalVectors?.runtimeCalls,
  },
  mobileClaimConfirmation: fixture.mobileClaimConfirmation,
  translationKeys: fixture.translationKeys,
});

const requireExactImmutableContract = (fixture, code) => {
  const sourceReceipts = fixture.webReference?.sourceFiles?.map((path) => [
    path,
    fixture.webReference?.sourceBlobObjects?.[path]?.gitSha1,
    fixture.webReference?.sourceBlobObjects?.[path]?.bytes,
  ]);
  if (
    stableJson(sourceReceipts) !== stableJson(EXPECTED_WEB_SOURCE_RECEIPTS) ||
    stableJson(fixture.canonicalBehavior) !==
      stableJson(EXPECTED_CANONICAL_BEHAVIOR) ||
    stableJson(fixture.canonicalVectors?.derivationSources) !==
      stableJson(EXPECTED_DERIVATION_SOURCES) ||
    stableJson(fixture.canonicalVectors?.minimumOutput) !==
      stableJson(EXPECTED_MINIMUM_OUTPUT_VECTORS) ||
    stableJson(fixture.canonicalVectors?.runtimeCalls) !==
      stableJson(EXPECTED_RUNTIME_VECTORS) ||
    stableJson(fixture.mobileClaimConfirmation) !==
      stableJson(EXPECTED_CLAIM_CONFIRMATION) ||
    stableJson(fixture.translationKeys) !== stableJson(EXPECTED_TRANSLATION_KEYS)
  ) {
    fail(code);
  }
  return sha256StableJson(immutableContractProjection(fixture));
};

const PLATFORM_SOURCE_MANIFEST_KEYS = [
  "repository",
  "revision",
  "sourceTreeSha256",
  "generatorSha256",
  "proofVerifierRevision",
  "proofVerifierBinarySha256",
  "proofVerifierPublicKeySha256",
];

const requirePlatformSourceManifestShape = (manifests, code) => {
  exactKeys(manifests, ["reference", "android", "ios"], code);
  for (const platform of ["reference", "android", "ios"]) {
    const manifest = manifests[platform];
    exactKeys(manifest, PLATFORM_SOURCE_MANIFEST_KEYS, code);
    if (
      manifest.repository !== PLATFORM_REPOSITORIES[platform] ||
      !(
        manifest.revision === null ||
        (HEX40.test(manifest.revision) && !/^0+$/.test(manifest.revision))
      ) ||
      ![
        "sourceTreeSha256",
        "generatorSha256",
        "proofVerifierBinarySha256",
        "proofVerifierPublicKeySha256",
      ].every(
        (field) =>
          manifest[field] === null || nonZeroHex(manifest[field], 32),
      ) ||
      !(
        manifest.proofVerifierRevision === null ||
        (HEX40.test(manifest.proofVerifierRevision) &&
          !/^0+$/.test(manifest.proofVerifierRevision))
      ) ||
      (platform === "reference" && manifest.revision !== WEB_REVISION)
    ) {
      fail(code);
    }
  }
};

const requireQualifiedPlatformSourceManifests = (
  manifests,
  qualificationAccountIdHex,
) => {
  requirePlatformSourceManifestShape(
    manifests,
    "POLKAMARKT_PLATFORM_SOURCE_MANIFEST_INVALID",
  );
  if (
    !nonZeroHex(qualificationAccountIdHex, 32) ||
    ["reference", "android", "ios"].some((platform) => {
      const manifest = manifests[platform];
      return (
        !HEX40.test(manifest.revision ?? "") ||
        /^0+$/.test(manifest.revision ?? "") ||
        !nonZeroHex(manifest.sourceTreeSha256, 32) ||
        !nonZeroHex(manifest.generatorSha256, 32) ||
        !HEX40.test(manifest.proofVerifierRevision ?? "") ||
        /^0+$/.test(manifest.proofVerifierRevision ?? "") ||
        !nonZeroHex(manifest.proofVerifierBinarySha256, 32) ||
        !nonZeroHex(manifest.proofVerifierPublicKeySha256, 32)
      );
    })
  ) {
    fail("POLKAMARKT_PLATFORM_SOURCE_IDENTITY_UNQUALIFIED");
  }
};

// Node's OpenSSL-backed `blake2b512` implementation does not consistently
// honor a shortened `outputLength`; on affected releases it either throws or
// produces a truncated BLAKE2b-512 digest, which is not BLAKE2b-256. Substrate
// uses the BLAKE2 parameter block with a 32-byte digest, so keep the small
// dependency-free implementation here and pin it with public RFC-compatible
// known-answer tests before it can inspect qualification evidence.
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

const blake2bRotateRight = (value, bits) =>
  ((value >> BigInt(bits)) |
    ((value << BigInt(64 - bits)) & BLAKE2B_MASK)) &
  BLAKE2B_MASK;

const blake2bReadWord = (block, offset) => {
  let value = 0n;
  for (let index = 0; index < 8; index += 1) {
    value |= BigInt(block[offset + index]) << BigInt(index * 8);
  }
  return value;
};

const blake2bMix = (state, a, b, c, d, x, y) => {
  state[a] = (state[a] + state[b] + x) & BLAKE2B_MASK;
  state[d] = blake2bRotateRight(state[d] ^ state[a], 32);
  state[c] = (state[c] + state[d]) & BLAKE2B_MASK;
  state[b] = blake2bRotateRight(state[b] ^ state[c], 24);
  state[a] = (state[a] + state[b] + y) & BLAKE2B_MASK;
  state[d] = blake2bRotateRight(state[d] ^ state[a], 16);
  state[c] = (state[c] + state[d]) & BLAKE2B_MASK;
  state[b] = blake2bRotateRight(state[b] ^ state[c], 63);
};

const blake2bCompress = (hash, block, counterLow, counterHigh, last) => {
  const message = Array.from({ length: 16 }, (_, index) =>
    blake2bReadWord(block, index * 8),
  );
  const state = [...hash, ...BLAKE2B_IV];
  state[12] ^= counterLow;
  state[13] ^= counterHigh;
  if (last) state[14] ^= BLAKE2B_MASK;

  for (const permutation of BLAKE2B_SIGMA) {
    blake2bMix(
      state,
      0,
      4,
      8,
      12,
      message[permutation[0]],
      message[permutation[1]],
    );
    blake2bMix(
      state,
      1,
      5,
      9,
      13,
      message[permutation[2]],
      message[permutation[3]],
    );
    blake2bMix(
      state,
      2,
      6,
      10,
      14,
      message[permutation[4]],
      message[permutation[5]],
    );
    blake2bMix(
      state,
      3,
      7,
      11,
      15,
      message[permutation[6]],
      message[permutation[7]],
    );
    blake2bMix(
      state,
      0,
      5,
      10,
      15,
      message[permutation[8]],
      message[permutation[9]],
    );
    blake2bMix(
      state,
      1,
      6,
      11,
      12,
      message[permutation[10]],
      message[permutation[11]],
    );
    blake2bMix(
      state,
      2,
      7,
      8,
      13,
      message[permutation[12]],
      message[permutation[13]],
    );
    blake2bMix(
      state,
      3,
      4,
      9,
      14,
      message[permutation[14]],
      message[permutation[15]],
    );
  }
  for (let index = 0; index < 8; index += 1) {
    hash[index] =
      (hash[index] ^ state[index] ^ state[index + 8]) & BLAKE2B_MASK;
  }
};

const blake2b256Bytes = (input) => {
  const bytes = Buffer.from(input);
  const hash = [...BLAKE2B_IV];
  hash[0] ^= 0x01010020n;
  let offset = 0;
  let counterLow = 0n;
  let counterHigh = 0n;
  do {
    const remaining = bytes.length - offset;
    const length = Math.min(128, Math.max(0, remaining));
    const block = Buffer.alloc(128);
    if (length > 0) bytes.copy(block, 0, offset, offset + length);
    const previousLow = counterLow;
    counterLow = (counterLow + BigInt(length)) & BLAKE2B_MASK;
    if (counterLow < previousLow) {
      counterHigh = (counterHigh + 1n) & BLAKE2B_MASK;
    }
    offset += length;
    const last = offset >= bytes.length;
    blake2bCompress(hash, block, counterLow, counterHigh, last);
    if (last) break;
  } while (true);

  const output = Buffer.alloc(32);
  for (let word = 0; word < 4; word += 1) {
    for (let byte = 0; byte < 8; byte += 1) {
      output[word * 8 + byte] = Number(
        (hash[word] >> BigInt(byte * 8)) & 0xffn,
      );
    }
  }
  return output;
};

const blake2b256 = (bytes) => blake2b256Bytes(bytes).toString("hex");
if (
  blake2b256(Buffer.alloc(0)) !==
    "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8" ||
  blake2b256(Buffer.from("abc", "utf8")) !==
    "bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319"
) {
  fail("POLKAMARKT_BLAKE2B256_SELF_TEST_FAILED");
}

const parseArguments = () => {
  if (
    process.argv.length === 4 &&
    process.argv[2] === "--validate-qualified"
  ) {
    if (!canonicalAbsolutePath(process.argv[3])) {
      fail("POLKAMARKT_QUALIFICATION_PATHS_INVALID");
    }
    return {
      mode: "validate-qualified",
      fixture: process.argv[3],
    };
  }
  const values = Object.create(null);
  for (let index = 2; index < process.argv.length; index += 2) {
    const key = process.argv[index];
    const value = process.argv[index + 1];
    if (
      ![
        "--fixture",
        "--reference",
        "--android",
        "--ios",
        "--review",
        "--review-key",
        "--review-signature",
        "--out",
      ].includes(key) ||
      typeof value !== "string" ||
      Object.hasOwn(values, key)
    ) {
      fail("POLKAMARKT_QUALIFICATION_ARGUMENTS_INVALID");
    }
    values[key] = value;
  }
  const required = [
    "--fixture",
    "--reference",
    "--android",
    "--ios",
    "--review",
    "--review-key",
    "--review-signature",
    "--out",
  ];
  if (
    process.argv.length !== 2 + required.length * 2 ||
    required.some(
      (key) =>
        !(key === "--out"
          ? canonicalOutputPath(values[key])
          : canonicalAbsolutePath(values[key])) ||
        (key !== "--out" && values[key] === values["--out"]),
    ) ||
    new Set(required.slice(0, -1).map((key) => values[key])).size !==
      required.length - 1
  ) {
    fail("POLKAMARKT_QUALIFICATION_PATHS_INVALID");
  }
  return { mode: "merge", values };
};

const requireRecord = (path, maximumBytes, code) => {
  const record = readStrictJsonFile(path, maximumBytes);
  if (record === null) fail(code);
  return record;
};

const parseStrictJsonBytes = (bytes, code) => {
  let source;
  try {
    source = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    fail(code);
  }
  let index = 0;
  const invalid = () => fail(code);
  const skipWhitespace = () => {
    while (
      source[index] === " " ||
      source[index] === "\t" ||
      source[index] === "\n" ||
      source[index] === "\r"
    ) {
      index += 1;
    }
  };
  const parseString = () => {
    if (source[index] !== '"') invalid();
    const start = index;
    index += 1;
    while (index < source.length) {
      const character = source[index];
      if (character === '"') {
        index += 1;
        let value;
        try {
          value = JSON.parse(source.slice(start, index));
        } catch {
          invalid();
        }
        for (const scalar of value) {
          const codePoint = scalar.codePointAt(0);
          if (codePoint >= 0xd800 && codePoint <= 0xdfff) invalid();
        }
        return value;
      }
      if (character === "\\") {
        index += 1;
        const escape = source[index];
        if (escape === "u") {
          if (!/^[0-9a-fA-F]{4}$/.test(source.slice(index + 1, index + 5))) {
            invalid();
          }
          index += 5;
          continue;
        }
        if (!['"', "\\", "/", "b", "f", "n", "r", "t"].includes(escape)) {
          invalid();
        }
        index += 1;
        continue;
      }
      if (character.charCodeAt(0) < 0x20) invalid();
      index += 1;
    }
    invalid();
  };
  const parseNumber = () => {
    const match = /^(?:0|-?[1-9][0-9]*)/.exec(source.slice(index));
    if (match === null) invalid();
    index += match[0].length;
    const value = Number(match[0]);
    if (!Number.isSafeInteger(value)) invalid();
    return value;
  };
  const parseValue = () => {
    skipWhitespace();
    const character = source[index];
    if (character === '"') return parseString();
    if (character === "{") {
      index += 1;
      skipWhitespace();
      const value = Object.create(null);
      const keys = new Set();
      if (source[index] === "}") {
        index += 1;
        return value;
      }
      while (index < source.length) {
        skipWhitespace();
        const key = parseString();
        if (keys.has(key)) invalid();
        keys.add(key);
        skipWhitespace();
        if (source[index] !== ":") invalid();
        index += 1;
        value[key] = parseValue();
        skipWhitespace();
        if (source[index] === "}") {
          index += 1;
          return value;
        }
        if (source[index] !== ",") invalid();
        index += 1;
      }
      invalid();
    }
    if (character === "[") {
      index += 1;
      skipWhitespace();
      const value = [];
      if (source[index] === "]") {
        index += 1;
        return value;
      }
      while (index < source.length) {
        value.push(parseValue());
        skipWhitespace();
        if (source[index] === "]") {
          index += 1;
          return value;
        }
        if (source[index] !== ",") invalid();
        index += 1;
      }
      invalid();
    }
    if (source.startsWith("true", index)) {
      index += 4;
      return true;
    }
    if (source.startsWith("false", index)) {
      index += 5;
      return false;
    }
    if (source.startsWith("null", index)) {
      index += 4;
      return null;
    }
    return parseNumber();
  };
  const result = parseValue();
  skipWhitespace();
  if (index !== source.length) invalid();
  return result;
};

const recordFromHex = (hex, maximumBytes, code) => {
  if (!evenHex(hex) || hex.length > maximumBytes * 2) fail(code);
  const bytes = Buffer.from(hex, "hex");
  return {
    bytes,
    sha256: sha256(bytes),
    value: parseStrictJsonBytes(bytes, code),
  };
};

const requireFixture = (record, expectedState = "blocked") => {
  const fixture = record.value;
  exactKeys(
    fixture,
    [
      "format",
      "sora2Network",
      "webReference",
      "runtime",
      "canonicalBehavior",
      "canonicalVectors",
      "mobileClaimConfirmation",
      "translationKeys",
    ],
    "POLKAMARKT_FIXTURE_KEYS_INVALID",
  );
  exactKeys(
    fixture.sora2Network,
    ["revision", "specVersion", "transactionVersion"],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  exactKeys(
    fixture.webReference,
    [
      "path",
      "branch",
      "inspectedRevision",
      "commitTreeObject",
      "polkamarktTreeObject",
      "commitSignatureStatus",
      "version",
      "polkamarktContractPresentAtInspectedRevision",
      "sourceFiles",
      "sourceBlobObjects",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  if (
    !Array.isArray(fixture.webReference.sourceFiles) ||
    fixture.webReference.sourceFiles.some(
      (source) => typeof source !== "string" || source.length === 0,
    ) ||
    new Set(fixture.webReference.sourceFiles).size !==
      fixture.webReference.sourceFiles.length
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  exactKeys(
    fixture.webReference.sourceBlobObjects,
    fixture.webReference.sourceFiles,
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  Object.values(fixture.webReference.sourceBlobObjects).forEach((blob) =>
    exactKeys(
      blob,
      ["gitSha1", "bytes"],
      "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    ),
  );
  if (
    fixture.webReference.path !==
      "/Users/takemiyamakoto/dev/polkaswap-exchange-web" ||
    fixture.webReference.branch !== "ui-updates" ||
    fixture.webReference.inspectedRevision !== WEB_REVISION ||
    fixture.webReference.commitTreeObject !==
      "e391982c0921dea5278e919a7558b7a6a2afc0d4" ||
    fixture.webReference.polkamarktTreeObject !==
      "57f0fe7623f2b93b34faecfc66d6c5da96d54e1b" ||
    fixture.webReference.commitSignatureStatus !== "verified" ||
    fixture.webReference.version !== "1.46.0" ||
    fixture.webReference.polkamarktContractPresentAtInspectedRevision !== true ||
    fixture.webReference.sourceFiles.length !== 22 ||
    Object.values(fixture.webReference.sourceBlobObjects).some(
      (blob) =>
        !HEX40.test(blob.gitSha1 ?? "") ||
        !Number.isSafeInteger(blob.bytes) ||
        blob.bytes <= 0,
    )
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  exactKeys(
    fixture.runtime,
    [
      "module",
      "marketIdScaleType",
      "marketIdMaximum",
      "closeBlockScaleType",
      "closeBlockMaximum",
      "calls",
      "rpc",
      "indices",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  requireArrayOf(
    fixture.runtime.calls,
    nonEmptyString,
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  requireArrayOf(
    fixture.runtime.rpc,
    nonEmptyString,
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  if (
    fixture.runtime.module !== "Polkamarkt" ||
    fixture.runtime.marketIdScaleType !== "u32" ||
    fixture.runtime.marketIdMaximum !== 4_294_967_295 ||
    fixture.runtime.closeBlockScaleType !== "u32" ||
    fixture.runtime.closeBlockMaximum !== 4_294_967_295 ||
    stableJson(fixture.runtime.calls) !== stableJson(REQUIRED_IDS) ||
    stableJson(fixture.runtime.rpc) !==
      stableJson([
        "polkamarkt_quoteBuy",
        "polkamarkt_quoteSell",
        "polkamarkt_marketState",
        "polkamarkt_claimable",
      ]) ||
    fixture.runtime.indices !== "resolve-from-live-metadata"
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  exactKeys(
    fixture.canonicalBehavior,
    [
      "collateralAsset",
      "outcomes",
      "tradeModes",
      "excludedMobileModes",
      "statusFilters",
      "openStatuses",
      "finalizedStatuses",
      "claimableStatuses",
      "categories",
      "defaultSlippagePercent",
      "defaultSlippageBps",
      "minimumMobileSlippageBps",
      "maximumMobileSlippageBps",
      "slippagePresetsBps",
      "minimumOutputFormula",
      "quoteDebounceMilliseconds",
      "amountPrecision",
      "tradeFeeBps",
      "maxBatchClaims",
      "cardHistoryMarketLimit",
      "dpmCurvePointCount",
      "dpmPricingFormula",
      "boardOpensBeforeDetail",
      "refreshAfterConfirmedReceipt",
      "receiptStates",
      "authoritativeReads",
      "discoveryAndHistory",
      "ambiguousSubmissionRetry",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  [
    "outcomes",
    "tradeModes",
    "excludedMobileModes",
    "statusFilters",
    "openStatuses",
    "finalizedStatuses",
    "claimableStatuses",
    "categories",
    "receiptStates",
  ].forEach((field) =>
    requireArrayOf(
      fixture.canonicalBehavior[field],
      nonEmptyString,
      "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    ),
  );
  requireArrayOf(
    fixture.canonicalBehavior.slippagePresetsBps,
    safeNonNegativeInteger,
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  const behaviorStringFields = [
    "collateralAsset",
    "defaultSlippagePercent",
    "minimumOutputFormula",
    "dpmPricingFormula",
    "authoritativeReads",
    "discoveryAndHistory",
  ];
  const behaviorIntegerFields = [
    "defaultSlippageBps",
    "minimumMobileSlippageBps",
    "maximumMobileSlippageBps",
    "quoteDebounceMilliseconds",
    "amountPrecision",
    "tradeFeeBps",
    "maxBatchClaims",
    "cardHistoryMarketLimit",
    "dpmCurvePointCount",
  ];
  if (
    behaviorStringFields.some(
      (field) => !nonEmptyString(fixture.canonicalBehavior[field]),
    ) ||
    behaviorIntegerFields.some(
      (field) => !safeNonNegativeInteger(fixture.canonicalBehavior[field]),
    ) ||
    typeof fixture.canonicalBehavior.boardOpensBeforeDetail !== "boolean" ||
    typeof fixture.canonicalBehavior.refreshAfterConfirmedReceipt !==
      "boolean" ||
    typeof fixture.canonicalBehavior.ambiguousSubmissionRetry !== "boolean"
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  exactKeys(
    fixture.canonicalVectors,
    [
      "derivationSources",
      "minimumOutput",
      "runtimeCalls",
      "fullExtrinsicQualification",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  exactKeys(
    fixture.canonicalVectors.derivationSources,
    ["minimumOutput", "runtimeCalls", "scaleArguments", "runtimeRevision"],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  if (
    Object.values(fixture.canonicalVectors.derivationSources).some(
      (value) => !nonEmptyString(value),
    )
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  if (!Array.isArray(fixture.canonicalVectors.minimumOutput)) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  fixture.canonicalVectors.minimumOutput.forEach((vector) => {
    exactKeys(
      vector,
      ["quotedOutput", "slippageBps", "expectedMinimum"],
      "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
    );
    if (
      !DECIMAL.test(vector.quotedOutput ?? "") ||
      !safeNonNegativeInteger(vector.slippageBps) ||
      !DECIMAL.test(vector.expectedMinimum ?? "")
    ) {
      fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
    }
  });
  if (!Array.isArray(fixture.canonicalVectors.runtimeCalls)) {
    fail("POLKAMARKT_RUNTIME_VECTORS_INVALID");
  }
  const runtimeArgumentKeys = {
    buy: ["market_id", "outcome", "collateral_in", "min_shares_out"],
    sell: ["market_id", "outcome", "shares_in", "min_collateral_out"],
    claim_market: ["market_id"],
    claim_markets: ["market_ids"],
    claim_creator_fees: ["market_id"],
  };
  fixture.canonicalVectors.runtimeCalls.forEach((vector) => {
    exactKeys(
      vector,
      ["call", "arguments", "scaleArgumentsHex"],
      "POLKAMARKT_RUNTIME_VECTORS_INVALID",
    );
    const expectedArgumentKeys = runtimeArgumentKeys[vector.call];
    if (expectedArgumentKeys === undefined) {
      fail("POLKAMARKT_RUNTIME_VECTORS_INVALID");
    }
    exactKeys(
      vector.arguments,
      expectedArgumentKeys,
      "POLKAMARKT_RUNTIME_VECTORS_INVALID",
    );
    const marketIdIsValid = (value) =>
      safeNonNegativeInteger(value) && value <= 4_294_967_295;
    const positiveDecimal = (value) =>
      typeof value === "string" && DECIMAL.test(value) && value !== "0";
    const argumentsValid =
      vector.call === "buy"
        ? marketIdIsValid(vector.arguments.market_id) &&
          ["Yes", "No"].includes(vector.arguments.outcome) &&
          positiveDecimal(vector.arguments.collateral_in) &&
          positiveDecimal(vector.arguments.min_shares_out)
        : vector.call === "sell"
          ? marketIdIsValid(vector.arguments.market_id) &&
            ["Yes", "No"].includes(vector.arguments.outcome) &&
            positiveDecimal(vector.arguments.shares_in) &&
            positiveDecimal(vector.arguments.min_collateral_out)
          : vector.call === "claim_markets"
            ? Array.isArray(vector.arguments.market_ids) &&
              vector.arguments.market_ids.length > 0 &&
              vector.arguments.market_ids.every(marketIdIsValid)
            : marketIdIsValid(vector.arguments.market_id);
    if (!argumentsValid || !evenHex(vector.scaleArgumentsHex)) {
      fail("POLKAMARKT_RUNTIME_VECTORS_INVALID");
    }
  });
  exactKeys(
    fixture.mobileClaimConfirmation,
    [
      "requiresExplicitConfirmation",
      "requiredReviewedFields",
      "freshChecksBeforeSigning",
      "copy",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  exactKeys(
    fixture.mobileClaimConfirmation.copy,
    ["title", "batchTitle", "body", "feeNotice"],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  requireArrayOf(
    fixture.mobileClaimConfirmation.requiredReviewedFields,
    nonEmptyString,
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  requireArrayOf(
    fixture.mobileClaimConfirmation.freshChecksBeforeSigning,
    nonEmptyString,
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  if (
    fixture.mobileClaimConfirmation.requiresExplicitConfirmation !== true ||
    Object.values(fixture.mobileClaimConfirmation.copy).some(
      (value) => !nonEmptyString(value),
    )
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  exactKeys(
    fixture.translationKeys,
    [
      "pageTitle",
      "yes",
      "no",
      "buy",
      "sell",
      "claimTrader",
      "claimCreatorFees",
      "sharesOut",
      "collateralOut",
      "slippage",
      "takerFee",
      "networkFee",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  if (
    Object.values(fixture.translationKeys).some(
      (value) => !nonEmptyString(value),
    )
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  const qualification =
    fixture.canonicalVectors?.fullExtrinsicQualification;
  const generation = qualification?.generationContract;
  exactKeys(
    qualification,
    [
      "receiptSchema",
      "requiredMetadataSha256",
      "requiredGenesisHash",
      "requiredSpecVersion",
      "requiredTransactionVersion",
      "metadataIndicesMustBeResolvedDynamically",
      "requiredVectorOrder",
      "canonicalContractSha256",
      "generationContract",
      "reviewedReceipt",
      "reviewedWebAndRuntimeReceiptQualified",
      "blocker",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  exactKeys(
    generation,
    [
      "candidateReceiptSchema",
      "independentReviewSchema",
      "merger",
      "referenceImplementation",
      "runtimeMetadata",
      "signingContext",
      "cryptographicProofSchema",
      "requiredCryptographicProofFields",
      "requiredCryptographicProofClaims",
      "platformSourceManifests",
      "requiredSharedVectorFields",
      "requiredPlatformVectorFields",
      "parityRules",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  exactKeys(
    generation.referenceImplementation,
    ["repository", "revision", "package", "packageVersion"],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  exactKeys(
    generation.runtimeMetadata,
    ["source", "sha256", "palletAndCallIndices"],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  exactKeys(
    generation.signingContext,
    [
      "cryptoType",
      "accountIdSource",
      "nonceSource",
      "mortalEraSource",
      "finalizedBlockHashSource",
      "tipSource",
      "signaturePayloadHashingThresholdBytes",
      "signaturePayloadHashingRule",
      "privateSigningMaterialPermittedInFixture",
      "qualificationAccountIdHex",
    ],
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  requirePlatformSourceManifestShape(
    generation.platformSourceManifests,
    "POLKAMARKT_BLOCKED_FIXTURE_INVALID",
  );
  if (
    stableJson(generation.platformSourceManifests) !==
      stableJson(EXPECTED_PLATFORM_SOURCE_MANIFESTS) ||
    generation.signingContext.qualificationAccountIdHex !==
      EXPECTED_QUALIFICATION_ACCOUNT_ID_HEX
  ) {
    fail("POLKAMARKT_CHECKED_IN_SOURCE_MANIFEST_MISMATCH");
  }
  const immutableContractSha256 = requireExactImmutableContract(
    fixture,
    "POLKAMARKT_IMMUTABLE_CONTRACT_MISMATCH",
  );
  if (
    fixture.format !== FIXTURE_FORMAT ||
    fixture.sora2Network?.revision !== RUNTIME_REVISION ||
    fixture.sora2Network?.specVersion !== SPEC_VERSION ||
    fixture.sora2Network?.transactionVersion !== TRANSACTION_VERSION ||
    fixture.webReference?.inspectedRevision !== WEB_REVISION ||
    qualification?.receiptSchema !== RECEIPT_SCHEMA ||
    qualification?.requiredMetadataSha256 !== METADATA_SHA256 ||
    qualification?.requiredGenesisHash !== GENESIS_HASH ||
    qualification?.requiredSpecVersion !== SPEC_VERSION ||
    qualification?.requiredTransactionVersion !== TRANSACTION_VERSION ||
    qualification?.metadataIndicesMustBeResolvedDynamically !== true ||
    stableJson(qualification?.requiredVectorOrder) !==
      stableJson(REQUIRED_IDS) ||
    generation?.candidateReceiptSchema !== CANDIDATE_SCHEMA ||
    generation?.independentReviewSchema !== REVIEW_SCHEMA ||
    generation?.merger !==
      "scripts/qualify-polkamarkt-extrinsic-receipts.mjs" ||
    generation?.referenceImplementation?.repository !==
      "/Users/takemiyamakoto/dev/polkaswap-exchange-web" ||
    generation?.referenceImplementation?.revision !== WEB_REVISION ||
    generation?.referenceImplementation?.package !== "polkadotApi" ||
    generation?.referenceImplementation?.packageVersion !== "11.2.1" ||
    generation?.runtimeMetadata?.source !==
      "common/src/production/assets/sora2_metadata" ||
    generation?.runtimeMetadata?.sha256 !== METADATA_SHA256 ||
    generation?.runtimeMetadata?.palletAndCallIndices !==
      "resolve-from-this-metadata-never-hardcode" ||
    generation?.signingContext?.cryptoType !== "sr25519" ||
    generation?.signingContext?.accountIdSource !== "reviewed-receipt" ||
    generation?.signingContext?.nonceSource !== "reviewed-receipt" ||
    generation?.signingContext?.mortalEraSource !== "reviewed-receipt" ||
    generation?.signingContext?.finalizedBlockHashSource !==
      "reviewed-receipt" ||
    generation?.signingContext?.tipSource !== "reviewed-receipt" ||
    generation?.signingContext?.signaturePayloadHashingThresholdBytes !==
      256 ||
    generation?.signingContext?.signaturePayloadHashingRule !==
      "blake2b-256-only-when-raw-payload-length-is-greater-than-256" ||
    generation?.signingContext?.privateSigningMaterialPermittedInFixture !==
      false ||
    !(
      generation?.signingContext?.qualificationAccountIdHex === null ||
      nonZeroHex(
        generation?.signingContext?.qualificationAccountIdHex,
        32,
      )
    ) ||
    generation?.cryptographicProofSchema !== CRYPTOGRAPHIC_PROOF_SCHEMA ||
    stableJson(generation?.requiredCryptographicProofFields) !==
      stableJson(REQUIRED_PROOF_FIELDS) ||
    stableJson(generation?.requiredCryptographicProofClaims) !==
      stableJson(REQUIRED_PROOF_CLAIMS) ||
    stableJson(generation?.requiredSharedVectorFields) !==
      stableJson(REQUIRED_SHARED_VECTOR_FIELDS) ||
    stableJson(generation?.requiredPlatformVectorFields) !==
      stableJson(REQUIRED_PLATFORM_VECTOR_FIELDS) ||
    stableJson(generation?.parityRules) !== stableJson(REQUIRED_PARITY_RULES)
  ) {
    fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
  }
  if (expectedState === "blocked") {
    if (
      qualification?.canonicalContractSha256 !== null ||
      qualification?.reviewedReceipt !== null ||
      qualification?.reviewedWebAndRuntimeReceiptQualified !== false ||
      qualification?.blocker !== BLOCKER
    ) {
      fail("POLKAMARKT_BLOCKED_FIXTURE_INVALID");
    }
  } else if (expectedState === "qualified") {
    if (
      qualification?.canonicalContractSha256 !== immutableContractSha256 ||
      qualification?.reviewedReceipt === null ||
      typeof qualification?.reviewedReceipt !== "object" ||
      Array.isArray(qualification?.reviewedReceipt) ||
      qualification?.reviewedWebAndRuntimeReceiptQualified !== true ||
      qualification?.blocker !== null
    ) {
      fail("POLKAMARKT_QUALIFIED_FIXTURE_INVALID");
    }
    requireQualifiedPlatformSourceManifests(
      generation.platformSourceManifests,
      generation.signingContext.qualificationAccountIdHex,
    );
  } else {
    fail("POLKAMARKT_QUALIFICATION_STATE_INVALID");
  }
  const runtimeVectors = fixture.canonicalVectors?.runtimeCalls;
  if (
    !Array.isArray(runtimeVectors) ||
    stableJson(runtimeVectors.map((vector) => vector?.call)) !==
      stableJson(REQUIRED_IDS)
  ) {
    fail("POLKAMARKT_RUNTIME_VECTORS_INVALID");
  }
  return {
    fixture,
    qualification,
    runtimeVectors,
    immutableContractSha256,
    generation,
  };
};

const requirePrivacy = (privacy, code) => {
  exactKeys(
    privacy,
    [
      "privateKeysIncluded",
      "seedsIncluded",
      "mnemonicsIncluded",
      "walletAddressesIncluded",
      "productionAccountIdentifiersIncluded",
    ],
    code,
  );
  if (Object.values(privacy).some((value) => value !== false)) fail(code);
};

const requireSigningContext = (context, code) => {
  exactKeys(
    context,
    [
      "accountIdHex",
      "nonce",
      "eraPeriod",
      "eraPhase",
      "finalizedBlockHash",
      "tip",
    ],
    code,
  );
  if (
    !nonZeroHex(context.accountIdHex, 32) ||
    !Number.isSafeInteger(context.nonce) ||
    context.nonce < 0 ||
    !Number.isSafeInteger(context.eraPeriod) ||
    context.eraPeriod < 4 ||
    context.eraPeriod > 65_536 ||
    (context.eraPeriod & (context.eraPeriod - 1)) !== 0 ||
    !Number.isSafeInteger(context.eraPhase) ||
    context.eraPhase < 0 ||
    context.eraPhase >= context.eraPeriod ||
    context.eraPhase % Math.max(context.eraPeriod >> 12, 1) !== 0 ||
    typeof context.finalizedBlockHash !== "string" ||
    !nonZeroHex(context.finalizedBlockHash.replace(/^0x/, ""), 32) ||
    !context.finalizedBlockHash.startsWith("0x") ||
    !DECIMAL.test(context.tip)
  ) {
    fail(code);
  }
};

const PROJECTION_KEYS = [
  "pallet",
  "call",
  "arguments",
  "metadataPalletIndex",
  "metadataCallIndex",
  "signerAccountIdHex",
  "nonce",
  "eraPeriod",
  "eraPhase",
  "tip",
  "genesisHash",
  "finalizedBlockHash",
  "specVersion",
  "transactionVersion",
];

const CANDIDATE_VECTOR_KEYS = [
  "id",
  "call",
  "arguments",
  "metadataPalletIndex",
  "metadataCallIndex",
  "scaleArgumentsHex",
  "fullCallHex",
  "rawSigningPayloadHex",
  "signingPrehashHex",
  "signingPrehashRule",
  "decodedProjection",
  "decodedProjectionSha256",
  "signerPublicKeyHex",
  "signatureHex",
  "signedExtrinsicHex",
  "extrinsicHashHex",
];

const requireVector = (vector, expected, context, code) => {
  exactKeys(vector, CANDIDATE_VECTOR_KEYS, code);
  exactKeys(vector.decodedProjection, PROJECTION_KEYS, code);
  if (
    !Number.isSafeInteger(vector.metadataPalletIndex) ||
    vector.metadataPalletIndex < 0 ||
    vector.metadataPalletIndex > 255 ||
    !Number.isSafeInteger(vector.metadataCallIndex) ||
    vector.metadataCallIndex < 0 ||
    vector.metadataCallIndex > 255
  ) {
    fail(code);
  }
  const expectedIndexPrefix =
    vector.metadataPalletIndex.toString(16).padStart(2, "0") +
    vector.metadataCallIndex.toString(16).padStart(2, "0");
  const rawPayload = evenHex(vector.rawSigningPayloadHex)
    ? Buffer.from(vector.rawSigningPayloadHex, "hex")
    : null;
  const expectedPrehashRule =
    rawPayload !== null && rawPayload.length > 256 ? "blake2b-256" : "raw";
  const expectedPrehash =
    rawPayload === null
      ? null
      : expectedPrehashRule === "blake2b-256"
        ? blake2b256(rawPayload)
        : vector.rawSigningPayloadHex;
  if (
    vector.id !== expected.call ||
    vector.call !== expected.call ||
    stableJson(vector.arguments) !== stableJson(expected.arguments) ||
    vector.scaleArgumentsHex !== expected.scaleArgumentsHex ||
    !evenHex(vector.fullCallHex, 3) ||
    vector.fullCallHex.length > MAXIMUM_CALL_BYTES * 2 ||
    vector.fullCallHex !== expectedIndexPrefix + vector.scaleArgumentsHex ||
    rawPayload === null ||
    rawPayload.length > MAXIMUM_PAYLOAD_BYTES ||
    !vector.rawSigningPayloadHex.startsWith(vector.fullCallHex) ||
    !vector.rawSigningPayloadHex.includes(GENESIS_HASH.slice(2)) ||
    !vector.rawSigningPayloadHex.includes(
      context.finalizedBlockHash.slice(2),
    ) ||
    vector.signingPrehashRule !== expectedPrehashRule ||
    vector.signingPrehashHex !== expectedPrehash ||
    !nonZeroHex(vector.signerPublicKeyHex, 32) ||
    vector.signerPublicKeyHex !== context.accountIdHex ||
    !nonZeroHex(vector.signatureHex, 64) ||
    !evenHex(vector.signedExtrinsicHex, 100) ||
    vector.signedExtrinsicHex.length > MAXIMUM_EXTRINSIC_BYTES * 2 ||
    !vector.signedExtrinsicHex.endsWith(vector.fullCallHex) ||
    !vector.signedExtrinsicHex.includes(
      `00${context.accountIdHex}01${vector.signatureHex}`,
    ) ||
    !nonZeroHex(vector.extrinsicHashHex, 32) ||
    blake2b256(Buffer.from(vector.signedExtrinsicHex, "hex")) !==
      vector.extrinsicHashHex ||
    vector.decodedProjectionSha256 !==
      sha256StableJson(vector.decodedProjection) ||
    vector.decodedProjection.pallet !== "Polkamarkt" ||
    vector.decodedProjection.call !== expected.call ||
    stableJson(vector.decodedProjection.arguments) !==
      stableJson(expected.arguments) ||
    vector.decodedProjection.metadataPalletIndex !==
      vector.metadataPalletIndex ||
    vector.decodedProjection.metadataCallIndex !== vector.metadataCallIndex ||
    vector.decodedProjection.signerAccountIdHex !== context.accountIdHex ||
    vector.decodedProjection.nonce !== context.nonce ||
    vector.decodedProjection.eraPeriod !== context.eraPeriod ||
    vector.decodedProjection.eraPhase !== context.eraPhase ||
    vector.decodedProjection.tip !== context.tip ||
    vector.decodedProjection.genesisHash !== GENESIS_HASH ||
    vector.decodedProjection.finalizedBlockHash !==
      context.finalizedBlockHash ||
    vector.decodedProjection.specVersion !== SPEC_VERSION ||
    vector.decodedProjection.transactionVersion !== TRANSACTION_VERSION
  ) {
    fail(code);
  }
};

const CANDIDATE_KEYS = [
  "format",
  "platform",
  "sourceRevision",
  "sourceTreeSha256",
  "generatorSha256",
  "webRevision",
  "runtimeRevision",
  "metadataSha256",
  "genesisHash",
  "specVersion",
  "transactionVersion",
  "metadataResolvedDynamically",
  "signingContext",
  "vectors",
  "cryptographicProof",
  "privacy",
];

const requireCandidate = (
  record,
  platform,
  runtimeVectors,
  sourceManifest,
  qualificationAccountIdHex,
) => {
  const candidate = record.value;
  const code = `POLKAMARKT_${platform.toUpperCase()}_RECEIPT_INVALID`;
  exactKeys(candidate, CANDIDATE_KEYS, code);
  requireSigningContext(candidate.signingContext, code);
  requirePrivacy(candidate.privacy, code);
  if (
    candidate.format !== CANDIDATE_SCHEMA ||
    candidate.platform !== platform ||
    !HEX40.test(candidate.sourceRevision) ||
    /^0+$/.test(candidate.sourceRevision) ||
    !nonZeroHex(candidate.sourceTreeSha256, 32) ||
    !nonZeroHex(candidate.generatorSha256, 32) ||
    candidate.webRevision !== WEB_REVISION ||
    candidate.runtimeRevision !== RUNTIME_REVISION ||
    candidate.metadataSha256 !== METADATA_SHA256 ||
    candidate.genesisHash !== GENESIS_HASH ||
    candidate.specVersion !== SPEC_VERSION ||
    candidate.transactionVersion !== TRANSACTION_VERSION ||
    candidate.metadataResolvedDynamically !== true ||
    !Array.isArray(candidate.vectors) ||
    candidate.vectors.length !== REQUIRED_IDS.length ||
    stableJson(candidate.vectors.map((vector) => vector?.id)) !==
      stableJson(REQUIRED_IDS)
  ) {
    fail(code);
  }
  if (platform === "reference" && candidate.sourceRevision !== WEB_REVISION) {
    fail(code);
  }
  if (
    sourceManifest.repository !== PLATFORM_REPOSITORIES[platform] ||
    candidate.sourceRevision !== sourceManifest.revision ||
    candidate.sourceTreeSha256 !== sourceManifest.sourceTreeSha256 ||
    candidate.generatorSha256 !== sourceManifest.generatorSha256 ||
    candidate.signingContext.accountIdHex !== qualificationAccountIdHex
  ) {
    fail(`POLKAMARKT_${platform.toUpperCase()}_SOURCE_IDENTITY_MISMATCH`);
  }
  candidate.vectors.forEach((vector, index) =>
    requireVector(
      vector,
      runtimeVectors[index],
      candidate.signingContext,
      code,
    ),
  );
  return candidate;
};

const candidateBinding = (candidate) =>
  Object.fromEntries(
    CANDIDATE_KEYS.filter((key) => key !== "cryptographicProof").map((key) => [
      key,
      candidate[key],
    ]),
  );

const proofStatement = (proof) =>
  Object.fromEntries(
    REQUIRED_PROOF_FIELDS.filter(
      (key) => key !== "publicKeySpkiDerHex" && key !== "signatureHex",
    ).map((key) => [key, proof[key]]),
  );

const requireCryptographicProof = (candidate, platform, sourceManifest) => {
  const code = `POLKAMARKT_${platform.toUpperCase()}_CRYPTOGRAPHIC_PROOF_INVALID`;
  const proof = candidate.cryptographicProof;
  if (proof === null) {
    fail(
      `POLKAMARKT_${platform.toUpperCase()}_CRYPTOGRAPHIC_PROOF_REQUIRED`,
    );
  }
  exactKeys(proof, REQUIRED_PROOF_FIELDS, code);
  requireArrayOf(proof.claims, nonEmptyString, code);
  if (
    proof.format !== CRYPTOGRAPHIC_PROOF_SCHEMA ||
    proof.platform !== platform ||
    proof.candidateBindingSha256 !==
      sha256StableJson(candidateBinding(candidate)) ||
    proof.sourceRevision !== candidate.sourceRevision ||
    proof.sourceTreeSha256 !== candidate.sourceTreeSha256 ||
    proof.generatorSha256 !== candidate.generatorSha256 ||
    proof.verifierRevision !== sourceManifest.proofVerifierRevision ||
    proof.verifierBinarySha256 !==
      sourceManifest.proofVerifierBinarySha256 ||
    proof.metadataSha256 !== METADATA_SHA256 ||
    proof.runtimeRevision !== RUNTIME_REVISION ||
    proof.genesisHash !== GENESIS_HASH ||
    proof.specVersion !== SPEC_VERSION ||
    proof.transactionVersion !== TRANSACTION_VERSION ||
    proof.signingContextSha256 !==
      sha256StableJson(candidate.signingContext) ||
    proof.vectorsSha256 !== sha256StableJson(candidate.vectors) ||
    stableJson(proof.claims) !== stableJson(REQUIRED_PROOF_CLAIMS) ||
    !Number.isSafeInteger(proof.verifiedAtEpochSeconds) ||
    proof.verifiedAtEpochSeconds <= 0 ||
    !evenHex(proof.publicKeySpkiDerHex, 44) ||
    !nonZeroHex(proof.signatureHex, 64)
  ) {
    fail(code);
  }
  const publicKeyDer = Buffer.from(proof.publicKeySpkiDerHex, "hex");
  if (
    publicKeyDer.length !== 44 ||
    sha256(publicKeyDer) !== sourceManifest.proofVerifierPublicKeySha256
  ) {
    fail(
      `POLKAMARKT_${platform.toUpperCase()}_CRYPTOGRAPHIC_PROOF_KEY_NOT_PROTECTED`,
    );
  }
  let publicKey;
  try {
    publicKey = createPublicKey({
      key: publicKeyDer,
      format: "der",
      type: "spki",
    });
  } catch {
    fail(code);
  }
  const canonicalPublicKeyDer = publicKey.export({
    format: "der",
    type: "spki",
  });
  if (
    publicKey.asymmetricKeyType !== "ed25519" ||
    !publicKeyDer.subarray(0, 12).equals(
      Buffer.from("302a300506032b6570032100", "hex"),
    ) ||
    !canonicalPublicKeyDer.equals(publicKeyDer) ||
    !verifyEd25519(
      null,
      Buffer.from(stableJson(proofStatement(proof)), "utf8"),
      publicKey,
      Buffer.from(proof.signatureHex, "hex"),
    )
  ) {
    fail(code);
  }
  return proof;
};

const sharedVector = (vector) => ({
  id: vector.id,
  call: vector.call,
  arguments: vector.arguments,
  metadataPalletIndex: vector.metadataPalletIndex,
  metadataCallIndex: vector.metadataCallIndex,
  scaleArgumentsHex: vector.scaleArgumentsHex,
  fullCallHex: vector.fullCallHex,
  rawSigningPayloadHex: vector.rawSigningPayloadHex,
  signingPrehashHex: vector.signingPrehashHex,
  signingPrehashRule: vector.signingPrehashRule,
  decodedProjection: vector.decodedProjection,
  decodedProjectionSha256: vector.decodedProjectionSha256,
});

const platformVector = (vector) => ({
  id: vector.id,
  signerPublicKeyHex: vector.signerPublicKeyHex,
  signatureHex: vector.signatureHex,
  signedExtrinsicHex: vector.signedExtrinsicHex,
  extrinsicHashHex: vector.extrinsicHashHex,
  signingPrehashHex: vector.signingPrehashHex,
  decodedProjection: vector.decodedProjection,
  decodedProjectionSha256: vector.decodedProjectionSha256,
});

const requireCrossPlatformParity = (candidates) => {
  const reference = candidates.reference;
  for (const platform of ["android", "ios"]) {
    const candidate = candidates[platform];
    if (
      stableJson(candidate.signingContext) !==
        stableJson(reference.signingContext) ||
      stableJson(candidate.vectors.map(sharedVector)) !==
        stableJson(reference.vectors.map(sharedVector))
    ) {
      fail(`POLKAMARKT_${platform.toUpperCase()}_PARITY_MISMATCH`);
    }
  }
};

const requireReview = ({
  fixtureRecord,
  candidateRecords,
  reviewRecord,
  keyRecord,
  signatureRecord,
}) => {
  const review = reviewRecord.value;
  exactKeys(
    review,
    [
      "format",
      "fixtureSha256",
      "referenceReceiptSha256",
      "androidReceiptSha256",
      "iosReceiptSha256",
      "decision",
      "reviewedAtEpochSeconds",
      "reviewer",
      "privacy",
    ],
    "POLKAMARKT_REVIEW_INVALID",
  );
  requirePrivacy(review.privacy, "POLKAMARKT_REVIEW_PRIVACY_INVALID");
  if (
    review.format !== REVIEW_SCHEMA ||
    review.fixtureSha256 !== fixtureRecord.sha256 ||
    review.referenceReceiptSha256 !== candidateRecords.reference.sha256 ||
    review.androidReceiptSha256 !== candidateRecords.android.sha256 ||
    review.iosReceiptSha256 !== candidateRecords.ios.sha256 ||
    review.decision !== "qualified" ||
    !Number.isSafeInteger(review.reviewedAtEpochSeconds) ||
    review.reviewedAtEpochSeconds <= 0 ||
    typeof review.reviewer !== "string" ||
    !/^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$/.test(review.reviewer)
  ) {
    fail("POLKAMARKT_REVIEW_INVALID");
  }

  const key = keyRecord.value;
  exactKeys(
    key,
    ["format", "algorithm", "publicKeySpkiDerHex"],
    "POLKAMARKT_REVIEW_KEY_INVALID",
  );
  if (
    key.format !== REVIEW_KEY_SCHEMA ||
    key.algorithm !== "ed25519" ||
    !evenHex(key.publicKeySpkiDerHex, 44)
  ) {
    fail("POLKAMARKT_REVIEW_KEY_INVALID");
  }
  const publicKeyDer = Buffer.from(key.publicKeySpkiDerHex, "hex");
  const expectedKeySha256 =
    process.env.POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256 ?? "";
  if (
    !HEX64.test(expectedKeySha256) ||
    /^0+$/.test(expectedKeySha256) ||
    sha256(publicKeyDer) !== expectedKeySha256
  ) {
    fail("POLKAMARKT_REVIEW_KEY_NOT_PROTECTED");
  }

  const signature = signatureRecord.value;
  exactKeys(
    signature,
    ["format", "reviewReceiptSha256", "signatureHex"],
    "POLKAMARKT_REVIEW_SIGNATURE_INVALID",
  );
  if (
    signature.format !== REVIEW_SIGNATURE_SCHEMA ||
    signature.reviewReceiptSha256 !== reviewRecord.sha256 ||
    !nonZeroHex(signature.signatureHex, 64)
  ) {
    fail("POLKAMARKT_REVIEW_SIGNATURE_INVALID");
  }
  let publicKey;
  try {
    publicKey = createPublicKey({
      key: publicKeyDer,
      format: "der",
      type: "spki",
    });
  } catch {
    fail("POLKAMARKT_REVIEW_KEY_INVALID");
  }
  const canonicalPublicKeyDer = publicKey.export({
    format: "der",
    type: "spki",
  });
  if (
    publicKey.asymmetricKeyType !== "ed25519" ||
    publicKeyDer.length !== 44 ||
    !publicKeyDer.subarray(0, 12).equals(
      Buffer.from("302a300506032b6570032100", "hex"),
    ) ||
    !canonicalPublicKeyDer.equals(publicKeyDer)
  ) {
    fail("POLKAMARKT_REVIEW_KEY_INVALID");
  }
  if (
    !verifyEd25519(
      null,
      reviewRecord.bytes,
      publicKey,
      Buffer.from(signature.signatureHex, "hex"),
    )
  ) {
    fail("POLKAMARKT_REVIEW_SIGNATURE_INVALID");
  }
  return {
    review,
    publicKeySha256: expectedKeySha256,
    reviewSignatureReceiptSha256: signatureRecord.sha256,
  };
};

const prohibitSecretFields = (value, path = "$") => {
  const forbidden = new Set([
    "mnemonic",
    "mnemonicphrase",
    "passphrase",
    "phrase",
    "recoveryphrase",
    "rawseed",
    "seed",
    "seedhex",
    "seedphrase",
    "entropy",
    "entropyhex",
    "secret",
    "secretmaterial",
    "secretkey",
    "secretkeyhex",
    "signingkey",
    "signingkeyhex",
    "privatekey",
    "privatekeyhex",
  ]);
  if (Array.isArray(value)) {
    value.forEach((item, index) =>
      prohibitSecretFields(item, `${path}[${index}]`),
    );
    return;
  }
  if (value !== null && typeof value === "object") {
    for (const [key, item] of Object.entries(value)) {
      const normalizedKey = key.replace(/[^a-z0-9]/gi, "").toLowerCase();
      if (forbidden.has(normalizedKey)) {
        fail("POLKAMARKT_PRIVATE_MATERIAL_PRESENT");
      }
      prohibitSecretFields(item, `${path}.${key}`);
    }
  }
};

const buildReviewedReceipt = ({
  fixtureRecord,
  immutableContractSha256,
  candidateRecords,
  candidates,
  reviewRecord,
  keyRecord,
  signatureRecord,
  review,
}) => ({
  format: RECEIPT_SCHEMA,
  fixtureInputSha256: fixtureRecord.sha256,
  fixtureInputHex: fixtureRecord.bytes.toString("hex"),
  canonicalContractSha256: immutableContractSha256,
  webRevision: WEB_REVISION,
  runtimeRevision: RUNTIME_REVISION,
  metadataSha256: METADATA_SHA256,
  genesisHash: GENESIS_HASH,
  specVersion: SPEC_VERSION,
  transactionVersion: TRANSACTION_VERSION,
  signingContext: candidates.reference.signingContext,
  sharedVectors: candidates.reference.vectors.map(sharedVector),
  platforms: Object.fromEntries(
    ["reference", "android", "ios"].map((platform) => [
      platform,
      {
        sourceRevision: candidates[platform].sourceRevision,
        sourceTreeSha256: candidates[platform].sourceTreeSha256,
        generatorSha256: candidates[platform].generatorSha256,
        candidateReceiptSha256: candidateRecords[platform].sha256,
        candidateReceiptHex: candidateRecords[platform].bytes.toString("hex"),
        vectors: candidates[platform].vectors.map(platformVector),
        cryptographicProof: candidates[platform].cryptographicProof,
      },
    ]),
  ),
  review: {
    receiptSha256: reviewRecord.sha256,
    receiptHex: reviewRecord.bytes.toString("hex"),
    signatureReceiptSha256: review.reviewSignatureReceiptSha256,
    signatureReceiptHex: signatureRecord.bytes.toString("hex"),
    publicKeySha256: review.publicKeySha256,
    publicKeyReceiptSha256: keyRecord.sha256,
    publicKeyReceiptHex: keyRecord.bytes.toString("hex"),
    reviewedAtEpochSeconds: review.review.reviewedAtEpochSeconds,
    reviewer: review.review.reviewer,
    decision: review.review.decision,
  },
  parityQualified: true,
});

const requireInstalledQualifiedFixture = (fixtureRecord) => {
  prohibitSecretFields(fixtureRecord.value);
  const {
    fixture,
    qualification,
    immutableContractSha256,
  } = requireFixture(fixtureRecord, "qualified");
  const reviewedReceipt = qualification.reviewedReceipt;
  exactKeys(
    reviewedReceipt,
    [
      "format",
      "fixtureInputSha256",
      "fixtureInputHex",
      "canonicalContractSha256",
      "webRevision",
      "runtimeRevision",
      "metadataSha256",
      "genesisHash",
      "specVersion",
      "transactionVersion",
      "signingContext",
      "sharedVectors",
      "platforms",
      "review",
      "parityQualified",
    ],
    "POLKAMARKT_QUALIFIED_RECEIPT_KEYS_INVALID",
  );
  if (
    reviewedReceipt.format !== RECEIPT_SCHEMA ||
    reviewedReceipt.canonicalContractSha256 !== immutableContractSha256 ||
    reviewedReceipt.webRevision !== WEB_REVISION ||
    reviewedReceipt.runtimeRevision !== RUNTIME_REVISION ||
    reviewedReceipt.metadataSha256 !== METADATA_SHA256 ||
    reviewedReceipt.genesisHash !== GENESIS_HASH ||
    reviewedReceipt.specVersion !== SPEC_VERSION ||
    reviewedReceipt.transactionVersion !== TRANSACTION_VERSION ||
    reviewedReceipt.parityQualified !== true
  ) {
    fail("POLKAMARKT_QUALIFIED_RECEIPT_INVALID");
  }

  const blockedFixtureRecord = recordFromHex(
    reviewedReceipt.fixtureInputHex,
    MAXIMUM_FIXTURE_BYTES,
    "POLKAMARKT_QUALIFIED_FIXTURE_INPUT_INVALID",
  );
  if (blockedFixtureRecord.sha256 !== reviewedReceipt.fixtureInputSha256) {
    fail("POLKAMARKT_QUALIFIED_FIXTURE_INPUT_INVALID");
  }
  prohibitSecretFields(blockedFixtureRecord.value);
  const blocked = requireFixture(blockedFixtureRecord, "blocked");
  if (blocked.immutableContractSha256 !== immutableContractSha256) {
    fail("POLKAMARKT_QUALIFIED_FIXTURE_INPUT_INVALID");
  }
  const reconstructedFixture = blocked.fixture;
  const reconstructedQualification =
    reconstructedFixture.canonicalVectors.fullExtrinsicQualification;
  reconstructedQualification.canonicalContractSha256 =
    immutableContractSha256;
  reconstructedQualification.reviewedReceipt = reviewedReceipt;
  reconstructedQualification.reviewedWebAndRuntimeReceiptQualified = true;
  reconstructedQualification.blocker = null;
  if (stableJson(reconstructedFixture) !== stableJson(fixture)) {
    fail("POLKAMARKT_QUALIFIED_FIXTURE_TRANSITION_INVALID");
  }

  exactKeys(
    reviewedReceipt.platforms,
    ["reference", "android", "ios"],
    "POLKAMARKT_QUALIFIED_PLATFORMS_INVALID",
  );
  const candidateRecords = Object.create(null);
  const candidates = Object.create(null);
  for (const platform of ["reference", "android", "ios"]) {
    const platformReceipt = reviewedReceipt.platforms[platform];
    exactKeys(
      platformReceipt,
      [
        "sourceRevision",
        "sourceTreeSha256",
        "generatorSha256",
        "candidateReceiptSha256",
        "candidateReceiptHex",
        "vectors",
        "cryptographicProof",
      ],
      "POLKAMARKT_QUALIFIED_PLATFORMS_INVALID",
    );
    const candidateRecord = recordFromHex(
      platformReceipt.candidateReceiptHex,
      MAXIMUM_CANDIDATE_BYTES,
      `POLKAMARKT_${platform.toUpperCase()}_RECEIPT_UNREADABLE`,
    );
    if (candidateRecord.sha256 !== platformReceipt.candidateReceiptSha256) {
      fail(`POLKAMARKT_${platform.toUpperCase()}_RECEIPT_INVALID`);
    }
    prohibitSecretFields(candidateRecord.value);
    const candidate = requireCandidate(
      candidateRecord,
      platform,
      blocked.runtimeVectors,
      blocked.generation.platformSourceManifests[platform],
      blocked.generation.signingContext.qualificationAccountIdHex,
    );
    requireCryptographicProof(
      candidate,
      platform,
      blocked.generation.platformSourceManifests[platform],
    );
    if (
      platformReceipt.sourceRevision !== candidate.sourceRevision ||
      platformReceipt.sourceTreeSha256 !== candidate.sourceTreeSha256 ||
      platformReceipt.generatorSha256 !== candidate.generatorSha256 ||
      stableJson(platformReceipt.vectors) !==
        stableJson(candidate.vectors.map(platformVector)) ||
      stableJson(platformReceipt.cryptographicProof) !==
        stableJson(candidate.cryptographicProof)
    ) {
      fail("POLKAMARKT_QUALIFIED_PLATFORMS_INVALID");
    }
    candidateRecords[platform] = candidateRecord;
    candidates[platform] = candidate;
  }
  requireCrossPlatformParity(candidates);
  if (
    stableJson(reviewedReceipt.signingContext) !==
      stableJson(candidates.reference.signingContext) ||
    stableJson(reviewedReceipt.sharedVectors) !==
      stableJson(candidates.reference.vectors.map(sharedVector))
  ) {
    fail("POLKAMARKT_QUALIFIED_SHARED_VECTORS_INVALID");
  }

  exactKeys(
    reviewedReceipt.review,
    [
      "receiptSha256",
      "receiptHex",
      "signatureReceiptSha256",
      "signatureReceiptHex",
      "publicKeySha256",
      "publicKeyReceiptSha256",
      "publicKeyReceiptHex",
      "reviewedAtEpochSeconds",
      "reviewer",
      "decision",
    ],
    "POLKAMARKT_QUALIFIED_REVIEW_INVALID",
  );
  const reviewRecord = recordFromHex(
    reviewedReceipt.review.receiptHex,
    MAXIMUM_REVIEW_BYTES,
    "POLKAMARKT_REVIEW_UNREADABLE",
  );
  const keyRecord = recordFromHex(
    reviewedReceipt.review.publicKeyReceiptHex,
    MAXIMUM_KEY_BYTES,
    "POLKAMARKT_REVIEW_KEY_UNREADABLE",
  );
  const signatureRecord = recordFromHex(
    reviewedReceipt.review.signatureReceiptHex,
    MAXIMUM_SIGNATURE_BYTES,
    "POLKAMARKT_REVIEW_SIGNATURE_UNREADABLE",
  );
  [reviewRecord, keyRecord, signatureRecord].forEach((record) =>
    prohibitSecretFields(record.value),
  );
  if (
    reviewRecord.sha256 !== reviewedReceipt.review.receiptSha256 ||
    keyRecord.sha256 !== reviewedReceipt.review.publicKeyReceiptSha256 ||
    signatureRecord.sha256 !==
      reviewedReceipt.review.signatureReceiptSha256
  ) {
    fail("POLKAMARKT_QUALIFIED_REVIEW_INVALID");
  }
  const review = requireReview({
    fixtureRecord: blockedFixtureRecord,
    candidateRecords,
    reviewRecord,
    keyRecord,
    signatureRecord,
  });
  const expectedReviewedReceipt = buildReviewedReceipt({
    fixtureRecord: blockedFixtureRecord,
    immutableContractSha256,
    candidateRecords,
    candidates,
    reviewRecord,
    keyRecord,
    signatureRecord,
    review,
  });
  prohibitSecretFields(expectedReviewedReceipt);
  if (stableJson(expectedReviewedReceipt) !== stableJson(reviewedReceipt)) {
    fail("POLKAMARKT_QUALIFIED_RECEIPT_INVALID");
  }
};

const requireOutputAbsent = (path) => {
  let exists = false;
  try {
    lstatSync(path);
    exists = true;
  } catch (error) {
    if (error?.code !== "ENOENT") {
      fail("POLKAMARKT_QUALIFICATION_OUTPUT_CREATE_FAILED");
    }
  }
  if (exists) fail("POLKAMARKT_QUALIFICATION_OUTPUT_EXISTS");
};

const writeExclusive = (path, value) => {
  if (!canonicalOutputPath(path)) {
    fail("POLKAMARKT_QUALIFICATION_OUTPUT_PARENT_INVALID");
  }
  const bytes = Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
  if (bytes.length > MAXIMUM_QUALIFIED_FIXTURE_BYTES) {
    fail("POLKAMARKT_QUALIFICATION_OUTPUT_TOO_LARGE");
  }
  let descriptor = null;
  let createdIdentity = null;
  let failure = null;
  try {
    try {
      descriptor = openSync(
        path,
        fsConstants.O_WRONLY |
          fsConstants.O_CREAT |
          fsConstants.O_EXCL |
          fsConstants.O_NOFOLLOW,
        0o600,
      );
    } catch (error) {
      if (error?.code === "EEXIST") {
        fail("POLKAMARKT_QUALIFICATION_OUTPUT_EXISTS");
      }
      fail("POLKAMARKT_QUALIFICATION_OUTPUT_CREATE_FAILED");
    }
    const openedStatus = fstatSync(descriptor);
    createdIdentity = { dev: openedStatus.dev, ino: openedStatus.ino };
    fchmodSync(descriptor, 0o600);
    let offset = 0;
    while (offset < bytes.length) {
      const count = writeSync(
        descriptor,
        bytes,
        offset,
        bytes.length - offset,
      );
      if (count <= 0) fail("POLKAMARKT_QUALIFICATION_OUTPUT_WRITE_FAILED");
      offset += count;
    }
    fsyncSync(descriptor);
    const status = fstatSync(descriptor);
    const pathStatus = lstatSync(path);
    if (
      !status.isFile() ||
      status.size !== bytes.length ||
      (status.mode & 0o777) !== 0o600 ||
      !pathStatus.isFile() ||
      pathStatus.isSymbolicLink() ||
      pathStatus.dev !== status.dev ||
      pathStatus.ino !== status.ino ||
      !canonicalOutputPath(path)
    ) {
      fail("POLKAMARKT_QUALIFICATION_OUTPUT_CHANGED");
    }
  } catch (error) {
    failure = /^POLKAMARKT_QUALIFICATION_[A-Z_]+$/.test(
      error?.message ?? "",
    )
      ? error
      : new Error("POLKAMARKT_QUALIFICATION_OUTPUT_WRITE_FAILED");
  }
  if (descriptor !== null) {
    try {
      closeSync(descriptor);
    } catch {
      failure ??= new Error("POLKAMARKT_QUALIFICATION_OUTPUT_CLOSE_FAILED");
    }
  }
  if (failure !== null && createdIdentity !== null) {
    try {
      const pathStatus = lstatSync(path);
      if (
        pathStatus.isFile() &&
        !pathStatus.isSymbolicLink() &&
        pathStatus.dev === createdIdentity.dev &&
        pathStatus.ino === createdIdentity.ino
      ) {
        unlinkSync(path);
      }
    } catch {
      // The evidence remains rejected; never remove a path whose identity is
      // no longer the file created by this invocation.
    }
  }
  if (failure !== null) {
    throw failure;
  }
};

const args = parseArguments();
if (args.mode === "validate-qualified") {
  const qualifiedFixtureRecord = requireRecord(
    args.fixture,
    MAXIMUM_QUALIFIED_FIXTURE_BYTES,
    "POLKAMARKT_FIXTURE_UNREADABLE",
  );
  requireInstalledQualifiedFixture(qualifiedFixtureRecord);
  process.stdout.write("POLKAMARKT_QUALIFIED_FIXTURE_VALID\n");
} else {
  const values = args.values;
  requireOutputAbsent(values["--out"]);
  const fixtureRecord = requireRecord(
    values["--fixture"],
    MAXIMUM_FIXTURE_BYTES,
    "POLKAMARKT_FIXTURE_UNREADABLE",
  );
  prohibitSecretFields(fixtureRecord.value);
  const {
    fixture,
    qualification,
    runtimeVectors,
    immutableContractSha256,
    generation,
  } = requireFixture(fixtureRecord, "blocked");
  requireQualifiedPlatformSourceManifests(
    generation.platformSourceManifests,
    generation.signingContext.qualificationAccountIdHex,
  );
  const candidateRecords = {
    reference: requireRecord(
      values["--reference"],
      MAXIMUM_CANDIDATE_BYTES,
      "POLKAMARKT_REFERENCE_RECEIPT_UNREADABLE",
    ),
    android: requireRecord(
      values["--android"],
      MAXIMUM_CANDIDATE_BYTES,
      "POLKAMARKT_ANDROID_RECEIPT_UNREADABLE",
    ),
    ios: requireRecord(
      values["--ios"],
      MAXIMUM_CANDIDATE_BYTES,
      "POLKAMARKT_IOS_RECEIPT_UNREADABLE",
    ),
  };
  Object.values(candidateRecords).forEach((record) =>
    prohibitSecretFields(record.value),
  );
  const candidates = Object.fromEntries(
    Object.entries(candidateRecords).map(([platform, record]) => [
      platform,
      requireCandidate(
        record,
        platform,
        runtimeVectors,
        generation.platformSourceManifests[platform],
        generation.signingContext.qualificationAccountIdHex,
      ),
    ]),
  );
  requireCrossPlatformParity(candidates);
  const reviewRecord = requireRecord(
    values["--review"],
    MAXIMUM_REVIEW_BYTES,
    "POLKAMARKT_REVIEW_UNREADABLE",
  );
  const keyRecord = requireRecord(
    values["--review-key"],
    MAXIMUM_KEY_BYTES,
    "POLKAMARKT_REVIEW_KEY_UNREADABLE",
  );
  const signatureRecord = requireRecord(
    values["--review-signature"],
    MAXIMUM_SIGNATURE_BYTES,
    "POLKAMARKT_REVIEW_SIGNATURE_UNREADABLE",
  );
  [reviewRecord, keyRecord, signatureRecord].forEach((record) =>
    prohibitSecretFields(record.value),
  );
  const review = requireReview({
    fixtureRecord,
    candidateRecords,
    reviewRecord,
    keyRecord,
    signatureRecord,
  });
  for (const platform of ["reference", "android", "ios"]) {
    requireCryptographicProof(
      candidates[platform],
      platform,
      generation.platformSourceManifests[platform],
    );
  }
  const reviewedReceipt = buildReviewedReceipt({
    fixtureRecord,
    immutableContractSha256,
    candidateRecords,
    candidates,
    reviewRecord,
    keyRecord,
    signatureRecord,
    review,
  });
  prohibitSecretFields(reviewedReceipt);
  qualification.canonicalContractSha256 = immutableContractSha256;
  qualification.reviewedReceipt = reviewedReceipt;
  qualification.reviewedWebAndRuntimeReceiptQualified = true;
  qualification.blocker = null;
  writeExclusive(values["--out"], fixture);
}
