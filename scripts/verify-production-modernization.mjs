#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import {
  createHash,
  createPublicKey,
  verify as verifySignature,
} from "node:crypto";
import {
  closeSync,
  constants as fsConstants,
  existsSync,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  readSync,
  realpathSync,
  readdirSync,
} from "node:fs";
import { isAbsolute, join, relative, resolve } from "node:path";
import {
  PRODUCTION_ADMISSION_IDENTITY_KEYS,
  QUALIFICATION_WORKFLOW_RELATIVE_PATH,
} from "./lib/qualified-candidate-provenance.mjs";
import {
  PRODUCTION_PI_CAPABILITY_KEYS,
  validateProductionPiRawLiveReceipt,
  verifyProductionPiCandidateReceiptV3,
} from "./lib/production-pi-receipt.mjs";
import {
  ANDROID_PRODUCTION_ADMISSION_V3,
  isProductionAdmissionV3Envelope,
  productionAdmissionV3ProjectionPrefix,
} from "./lib/production-rollout-v3-contract.mjs";
import {
  FUNDED_CANARY_ATTESTATION_ROUTE,
  FUNDED_CANARY_BUNDLE_ROUTE,
  FUNDED_CANARY_FINALITY_KEYS,
  FUNDED_CANARY_FINALITY_CONTEXT_KEYS,
  FUNDED_CANARY_FINALITY_MANIFEST_KEYS,
  FUNDED_CANARY_FINALITY_NATIVE_KEYS,
  FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS,
  FUNDED_CANARY_TERMINAL_EXTRA_KEYS,
  FUNDED_CANARY_V4_CONTRACT_ID,
  fundedCanaryV4FinalityManifestProjection,
  fundedCanaryV4NativeFinalityProjection,
  fundedCanaryV4TrustContextProjection,
  validateFundedCanaryV4PolicyChronology,
  validateFundedCanaryV4PairDistinct,
  validateFundedCanaryV4Proof,
} from "./lib/funded-canary-v4.mjs";
import {
  lintAndroidMigrationQualificationTemplates,
  verifyAndroidMigrationQualificationV7,
} from "./lib/android-migration-qualification-v7.mjs";
import { verifyTairaDeploymentManifestV1 } from "./lib/taira-deployment-manifest-v1.mjs";
import {
  androidDependencySigningReviewContractSha256V1,
  lintAndroidDependencySigningReviewBlockedTemplatesV1,
  verifyAndroidDependencySigningReviewV1,
} from "./lib/android-dependency-signing-review-v1.mjs";

const root = resolve(new URL("..", import.meta.url).pathname);
const strictRelease = process.argv.includes("--release");
const preCanaryMode = process.argv.includes("--pre-canary");
const dependencyPreflightMode = process.argv.includes(
  "--dependency-preflight",
);
const failures = [];
const blockers = [];
const SORA2_REVISION = "411dcdb70c5c00b21482a44d02334840d5f338c6";

const read = (path) => readFileSync(join(root, path), "utf8");
const json = (path) => JSON.parse(read(path));
const assert = (condition, code) => {
  if (!condition) failures.push(code);
};
const block = (condition, code) => {
  if (condition) blockers.push(code);
};
const sha256 = (path) =>
  createHash("sha256").update(readFileSync(join(root, path))).digest("hex");
const sha256Files = (paths) => {
  const hash = createHash("sha256");
  for (const path of paths) {
    hash.update(path);
    hash.update("\0");
    hash.update(readFileSync(join(root, path)));
    hash.update("\0");
  }
  return hash.digest("hex");
};
const escapeRegExp = (value) =>
  value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const isSortedUniqueStringList = (values) =>
  Array.isArray(values) &&
  values.length > 0 &&
  values.every((value) => typeof value === "string" && value.length > 0) &&
  values.join("\n") === [...new Set(values)].sort().join("\n");
const hasExactKeys = (value, expectedKeys) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  Object.keys(value).length === expectedKeys.length &&
  expectedKeys.every((key) => Object.hasOwn(value, key));
const isCanonicalRegularRepoFile = (path) => {
  if (
    typeof path !== "string" ||
    path.length === 0 ||
    path.includes("\\") ||
    path.split("/").some((component) =>
      component.length === 0 || component === "." || component === ".."
    ) ||
    realpathSync(root) !== root
  ) return false;
  const absolutePath = resolve(root, path);
  if (relative(root, absolutePath) !== path) return false;
  let cursor = root;
  try {
    for (const component of path.split("/").slice(0, -1)) {
      cursor = join(cursor, component);
      const parent = lstatSync(cursor);
      if (!parent.isDirectory() || parent.isSymbolicLink()) return false;
    }
    const file = lstatSync(absolutePath);
    return (
      file.isFile() &&
      !file.isSymbolicLink() &&
      realpathSync(absolutePath) === absolutePath
    );
  } catch {
    return false;
  }
};
const canonicalSha256 = (lines) =>
  createHash("sha256").update(lines.join("\n"), "utf8").digest("hex");
const parseStrictJsonBytes = (bytes) => {
  const source = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  let index = 0;
  const invalid = () => {
    throw new Error("STRICT_JSON_INVALID");
  };
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
        const value = JSON.parse(source.slice(start, index));
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
const readStrictJsonFile = (absolutePath, maximumBytes) => {
  let descriptor = null;
  try {
    const normalized = resolve(absolutePath);
    if (
      !isAbsolute(absolutePath) ||
      normalized !== absolutePath ||
      !existsSync(normalized) ||
      realpathSync(normalized) !== normalized
    ) {
      return null;
    }
    descriptor = openSync(
      normalized,
      fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW,
    );
    const before = fstatSync(descriptor);
    if (
      !before.isFile() ||
      before.size < 1 ||
      before.size > maximumBytes
    ) {
      return null;
    }
    const chunks = [];
    let totalBytes = 0;
    while (true) {
      const chunk = Buffer.allocUnsafe(
        Math.min(64 * 1024, maximumBytes - totalBytes + 1),
      );
      const bytesRead = readSync(descriptor, chunk, 0, chunk.length, null);
      if (bytesRead === 0) break;
      chunks.push(chunk.subarray(0, bytesRead));
      totalBytes += bytesRead;
      if (totalBytes > maximumBytes) return null;
    }
    const after = fstatSync(descriptor);
    const pathAfter = lstatSync(normalized);
    if (
      totalBytes !== before.size ||
      !after.isFile() ||
      after.dev !== before.dev ||
      after.ino !== before.ino ||
      after.size !== before.size ||
      after.mtimeMs !== before.mtimeMs ||
      after.ctimeMs !== before.ctimeMs ||
      !pathAfter.isFile() ||
      pathAfter.isSymbolicLink() ||
      pathAfter.dev !== before.dev ||
      pathAfter.ino !== before.ino ||
      realpathSync(normalized) !== normalized
    ) {
      return null;
    }
    const bytes = Buffer.concat(chunks, totalBytes);
    return {
      bytes: totalBytes,
      sha256: createHash("sha256").update(bytes).digest("hex"),
      value: parseStrictJsonBytes(bytes),
    };
  } catch {
    return null;
  } finally {
    if (descriptor !== null) {
      try {
        closeSync(descriptor);
      } catch {
        // A failed close cannot turn an invalid evidence read into qualification.
      }
    }
  }
};
const hashExternalRegularFile = ({ path, suffix, maximumBytes }) => {
  if (
    typeof path !== "string" ||
    path.length === 0 ||
    !isAbsolute(path) ||
    resolve(path) !== path ||
    !path.endsWith(suffix)
  ) {
    return null;
  }
  let descriptor = null;
  try {
    if (!existsSync(path) || realpathSync(path) !== path) return null;
    descriptor = openSync(path, fsConstants.O_RDONLY | fsConstants.O_NOFOLLOW);
    const before = fstatSync(descriptor);
    if (!before.isFile() || before.size < 1 || before.size > maximumBytes) {
      return null;
    }
    const digest = createHash("sha256");
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let totalBytes = 0;
    while (true) {
      const bytesRead = readSync(descriptor, buffer, 0, buffer.length, null);
      if (bytesRead === 0) break;
      digest.update(buffer.subarray(0, bytesRead));
      totalBytes += bytesRead;
      if (totalBytes > maximumBytes) return null;
    }
    const after = fstatSync(descriptor);
    const pathAfter = lstatSync(path);
    if (
      totalBytes !== before.size ||
      !after.isFile() ||
      after.dev !== before.dev ||
      after.ino !== before.ino ||
      after.size !== before.size ||
      after.mtimeMs !== before.mtimeMs ||
      after.ctimeMs !== before.ctimeMs ||
      !pathAfter.isFile() ||
      pathAfter.isSymbolicLink() ||
      pathAfter.dev !== before.dev ||
      pathAfter.ino !== before.ino ||
      realpathSync(path) !== path
    ) {
      return null;
    }
    return { bytes: totalBytes, sha256: digest.digest("hex") };
  } catch {
    return null;
  } finally {
    if (descriptor !== null) {
      try {
        closeSync(descriptor);
      } catch {
        // A failed close cannot turn an invalid artifact read into qualification.
      }
    }
  }
};

const EXPECTED_VENDOR_MODULES = [
  {
    coordinate: "com.paywings.kyc:android-sdk:1.2.2",
    modulePath: "com/paywings/kyc/android-sdk/1.2.2",
    originKind: "github-packages-asset",
    packagePage:
      "https://github.com/PayWings/integration/packages/2038085?version=1.2.2",
  },
  {
    coordinate: "com.paywings.oauth:android-sdk:2.0.0",
    modulePath: "com/paywings/oauth/android-sdk/2.0.0",
    originKind: "github-packages-asset",
    packagePage:
      "https://github.com/PayWings/integration/packages/1654891?version=2.0.0",
  },
  {
    coordinate:
      "com.paywings.onboarding.kyc.android-libs:idensic-mobile-sdk:1.31.3",
    modulePath:
      "com/paywings/onboarding/kyc/android-libs/idensic-mobile-sdk/1.31.3",
    originKind: "github-packages-asset",
    packagePage:
      "https://github.com/PayWings/integration/packages/1527444?version=1.31.3",
  },
  {
    coordinate: "io.emeraldpay.polkaj:polkaj-scale:0.2.3",
    modulePath: "io/emeraldpay/polkaj/polkaj-scale/0.2.3",
    originKind: "git-tracked-binary",
    repository:
      "https://github.com/soramitsu/sora2-load-library-java.git",
    revision: "4deeca68a5560462c13aec704a50e7ba02ece70e",
    tree: "997c38188870b9d9a21af5acacee845dc0e62bff",
    sourcePath: "libs/io/emeraldpay/polkaj/polkaj-scale/0.2.3",
  },
  {
    coordinate: "jp.co.soramitsu.xnetworking:lib-android:1.0.15-K2",
    modulePath: "jp/co/soramitsu/xnetworking/lib-android/1.0.15-K2",
    originKind: "git-source-build",
    repository: "https://github.com/soramitsu/x-networking.git",
    revision: "fa94a1d1c0b5457b50885ec72727934a2fea12d5",
    tree: "56a87310923d615819819d682a945496e40b3dd7",
  },
  {
    coordinate: "jp.co.soramitsu:android-foundation:0.0.4",
    modulePath: "jp/co/soramitsu/android-foundation/0.0.4",
    originKind: "git-source-build",
    repository: "https://github.com/soramitsu/android-foundation.git",
    revision: "732d87fde58a2662a55b2d8b71825d6de7ba6702",
    tree: "b9c0b336b72d1cc5d93b2824e4ef13480b53d300",
  },
  {
    coordinate: "jp.co.soramitsu:android-sora-card:1.2.1-K2",
    modulePath: "jp/co/soramitsu/android-sora-card/1.2.1-K2",
    originKind: "git-source-build",
    repository: "https://github.com/sora-xor/sora-card-android.git",
    revision: "60113f80237a7c6fa4329eca55ddb22d89e12831",
    tree: "f5fbb2e9ca40b90a8efd3265542fd64f71b658de",
  },
  {
    coordinate: "jp.co.soramitsu:ui-core:0.2.39",
    modulePath: "jp/co/soramitsu/ui-core/0.2.39",
    originKind: "git-source-build",
    repository:
      "https://github.com/soramitsu/android-ui-libraries.git",
    revision: "f2b6f27adef101fa997432e46886dcbf3625ac77",
    tree: "a7fdacc63ebce6dd473e304c072b830f9d95e2a8",
  },
  {
    coordinate: "jp.co.soramitsu:xbackup:1.2.3",
    modulePath: "jp/co/soramitsu/xbackup/1.2.3",
    originKind: "git-source-build",
    repository: "https://github.com/soramitsu/x-backup.git",
    revision: "851999f4fb3d8610c500c4879cb4bcc7082aae55",
    tree: "60eb9c401e2814b090eb6340d3e2d3abdef84868",
  },
  {
    coordinate: "jp.co.soramitsu:xcrypto:1.2.7",
    modulePath: "jp/co/soramitsu/xcrypto/1.2.7",
    originKind: "git-source-build",
    repository: "https://github.com/soramitsu/x-crypto.git",
    revision: "2346144a127c1121ae3166800b7ab06ed9c5bf20",
    tree: "c0dab19ed314bbe9bf939dc0c33fe0c4d13fbb11",
    buildInputs: ["build-inputs/xcrypto-1.2.7-Cargo.lock"],
  },
  {
    coordinate: "jp.co.soramitsu:xsubstrate:1.2.7",
    modulePath: "jp/co/soramitsu/xsubstrate/1.2.7",
    originKind: "git-source-build",
    repository: "https://github.com/soramitsu/x-substrate.git",
    revision: "f020817a10590f94d6e7c75d040557e1b9afad56",
    tree: "744c7b4923fdd8f7db58dbe9c3d60b04091a12d7",
    semanticTransformations: [
      "build-inputs/xsubstrate-1.2.7-source-normalization.patch",
    ],
  },
];
const EXPECTED_VENDOR_BUILD_INPUT_SHA256 = {
  "build-inputs/README.md":
    "df1d55a57903fcc1f8b21a1d779a23733f974bd495dddae0f67c1e55995b8ded",
  "build-inputs/xcrypto-1.2.7-Cargo.lock":
    "72e4aa8f2365dbdff249820abe7cf07593d63a371b220c34bad1733a5694d395",
  "build-inputs/xsubstrate-1.2.7-source-normalization.patch":
    "d490eaac87bf29feec7e443098c2b451e7cfc1aea671d285bd9a13541afc98c5",
};
const EXPECTED_JITPACK_MODULES = [
  "com.github.WycliffeAssociates:jdenticon-kotlin",
  "com.github.warchant:ed25519-sha3-java",
];
const isSafeManifestRelativePath = (path) =>
  typeof path === "string" &&
  path.length > 0 &&
  path.length <= 512 &&
  !path.includes("\\") &&
  !path.includes("\0") &&
  !path.startsWith("/") &&
  path.split("/").every(
    (component) =>
      component.length > 0 &&
      component !== "." &&
      component !== ".." &&
      /^[A-Za-z0-9@+_.-]+$/.test(component),
  );
const walkSymlinkFreeRegularFiles = (absoluteRoot) => {
  const files = [];
  const visit = (directory, prefix, depth) => {
    if (depth > 32 || files.length > 512) throw new Error("VENDOR_TREE_BOUND");
    const directoryStat = lstatSync(directory);
    if (!directoryStat.isDirectory() || directoryStat.isSymbolicLink()) {
      throw new Error("VENDOR_TREE_DIRECTORY_INVALID");
    }
    const entries = readdirSync(directory, { withFileTypes: true }).sort(
      (left, right) => left.name.localeCompare(right.name, "en"),
    );
    for (const entry of entries) {
      const relativePath = prefix ? `${prefix}/${entry.name}` : entry.name;
      if (!isSafeManifestRelativePath(relativePath)) {
        throw new Error("VENDOR_TREE_PATH_INVALID");
      }
      const absolutePath = join(directory, entry.name);
      const entryStat = lstatSync(absolutePath);
      if (entryStat.isSymbolicLink()) throw new Error("VENDOR_TREE_SYMBOLIC");
      if (entryStat.isDirectory()) {
        visit(absolutePath, relativePath, depth + 1);
      } else if (entryStat.isFile()) {
        files.push(relativePath);
      } else {
        throw new Error("VENDOR_TREE_NON_REGULAR");
      }
    }
  };
  visit(absoluteRoot, "", 0);
  return files.sort();
};
const sha256AbsoluteRegularFile = (absolutePath, maximumBytes = 64 * 1024 * 1024) =>
  hashExternalRegularFile({
    path: absolutePath,
    suffix: "",
    maximumBytes,
  })?.sha256 ?? null;
const exactStringSet = (values, expected) =>
  Array.isArray(values) &&
  values.every((value) => typeof value === "string") &&
  values.length === new Set(values).size &&
  [...values].sort().join("\n") === [...expected].sort().join("\n");
const uniqueBalancedBlockContaining = (source, declaration, marker) => {
  const matches = [];
  let cursor = 0;
  while (cursor < source.length) {
    const declarationIndex = source.indexOf(declaration, cursor);
    if (declarationIndex < 0) break;
    const openingBrace = source.indexOf("{", declarationIndex + declaration.length);
    if (openingBrace < 0) return null;
    let depth = 0;
    let closingBrace = -1;
    for (let index = openingBrace; index < source.length; index += 1) {
      if (source[index] === "{") depth += 1;
      if (source[index] === "}") depth -= 1;
      if (depth === 0) {
        closingBrace = index;
        break;
      }
    }
    if (closingBrace < 0) return null;
    const block = source.slice(declarationIndex, closingBrace + 1);
    if (block.includes(marker)) matches.push(block);
    cursor = closingBrace + 1;
  }
  return matches.length === 1 ? matches[0] : null;
};
const includeModulesIn = (source) => [
  ...(source ?? "").matchAll(
    /includeModule\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,?\s*\)/g,
  ),
].map(([, group, module]) => `${group}:${module}`);
const EXPECTED_VENDOR_SOURCE_PROVENANCE_SHA256 =
  "39264fee02d09548e04806fbffcdaedebef29ce4715f3aa804093e44b51f5118";
const EXPECTED_VENDOR_CONTENTS_MANIFEST_SHA256 =
  "d632afc3ebbd1d801a41d444d63c2879cb78c7241da3ed259667825a0c366c1f";
const EXPECTED_GRADLE_VERIFICATION_METADATA_SHA256 =
  "90b196d775f064b7f80b9520582eec8fc40f874b6f750a49546141a8b793bfab";
const EXPECTED_GRADLE_VERIFICATION_METADATA_DIGEST_SHA256 =
  "4993f3789151edb393340b1677e7fa2893689c4876d4ae6ebc23bd243bd0ceb2";
const EXPECTED_GRADLE_LOCK_FILE_SET_SHA256 =
  "1b91b6168ff2c0ec74e0f239e90ff8125742ae8a8fea3458f2a7cbfe472eb9ea";
const EXPECTED_GRADLE_LOCK_CONFIGURATION_INVENTORY_SHA256 =
  "d35d7512074d387ae71dfad3f52833af70875e3afe4a84d46b667a31e3bbfd1f";
const androidDependencySigningReviewQualificationDirectory = join(
  root,
  "docs/modernization/qualification",
);
const androidDependencySigningReviewBlockedManifestPath =
  "docs/modernization/qualification/android-dependency-signing-review-manifest.blocked.json";
const androidDependencySigningReviewBlockedAdmissionPath =
  "docs/modernization/qualification/android-dependency-signing-review-admission.blocked.json";
const androidDependencySigningReviewBlockedManifestRecord = readStrictJsonFile(
  join(root, androidDependencySigningReviewBlockedManifestPath),
  128 * 1024,
);
const androidDependencySigningReviewBlockedAdmissionRecord = readStrictJsonFile(
  join(root, androidDependencySigningReviewBlockedAdmissionPath),
  128 * 1024,
);
const androidDependencySigningReviewBlockedManifest =
  androidDependencySigningReviewBlockedManifestRecord?.value;
const androidDependencySigningReviewBlockedAdmission =
  androidDependencySigningReviewBlockedAdmissionRecord?.value;
assert(
  androidDependencySigningReviewBlockedManifestRecord !== null &&
    androidDependencySigningReviewBlockedAdmissionRecord !== null &&
    readFileSync(
      join(root, androidDependencySigningReviewBlockedManifestPath),
    ).equals(
      Buffer.from(
        `${JSON.stringify(androidDependencySigningReviewBlockedManifest, null, 2)}\n`,
      ),
    ) &&
    readFileSync(
      join(root, androidDependencySigningReviewBlockedAdmissionPath),
    ).equals(
      Buffer.from(
        `${JSON.stringify(androidDependencySigningReviewBlockedAdmission, null, 2)}\n`,
      ),
    ) &&
  lintAndroidDependencySigningReviewBlockedTemplatesV1({
    manifest: androidDependencySigningReviewBlockedManifest,
    admission: androidDependencySigningReviewBlockedAdmission,
  }) &&
    readdirSync(androidDependencySigningReviewQualificationDirectory)
      .filter((name) =>
        name.startsWith("android-dependency-signing-review"),
      )
      .sort()
      .join("\0") ===
      [
        "android-dependency-signing-review-README.md",
        "android-dependency-signing-review-admission.blocked.json",
        "android-dependency-signing-review-manifest.blocked.json",
      ].sort().join("\0"),
  "ANDROID_DEPENDENCY_SIGNING_REVIEW_BLOCKED_TEMPLATES_INVALID",
);
let authenticatedAndroidDependencySigningReview = null;
const authenticateAndroidDependencySigningReview = ({ dependencyRecord }) => {
  const names = [
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_PATH",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_PATH",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_PATH",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_PUBLIC_KEY_PATH",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_PUBLIC_KEY_PATH",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_KEY_SHA256",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_KEY_SHA256",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_SEQUENCE_NUMBER",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_SHA256",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_EVALUATION_EPOCH_SECONDS",
    "PRODUCTION_CANDIDATE_SOURCE_REVISION",
  ];
  const values = Object.fromEntries(
    names.map((name) => [name, process.env[name] ?? ""]),
  );
  if (Object.values(values).every((value) => value.length === 0)) {
    return { state: "absent", receipt: null };
  }
  if (Object.values(values).some((value) => value.length === 0)) {
    return { state: "invalid", receipt: null };
  }
  const signingRecord = readStrictJsonFile(
    join(root, "config/android-production-signing-identity.json"),
    64 * 1024,
  );
  if (signingRecord === null) return { state: "invalid", receipt: null };
  const dependency = dependencyRecord.value;
  const signing = signingRecord.value;
  const reviewedInputs = {
    gradleDependencyProvenanceSha256: dependencyRecord.sha256,
    verificationMetadataSha256:
      dependency.dependencyVerification?.metadataSha256,
    verificationMetadataDigestSha256:
      dependency.dependencyVerification?.metadataDigestSha256,
    vendorSourceProvenanceSha256:
      dependency.repositoryPolicy?.sourceQualifiedVendorRepository
        ?.sourceProvenanceSha256,
    vendorContentsManifestSha256:
      dependency.repositoryPolicy?.sourceQualifiedVendorRepository
        ?.contentsManifestSha256,
    lockFileSetSha256:
      dependency.dependencyLocking?.materializedInventory?.lockFileSetSha256,
    lockConfigurationInventorySha256:
      dependency.dependencyLocking?.materializedInventory
        ?.configurationInventorySha256,
    androidProductionSigningIdentitySha256: signingRecord.sha256,
    productionAppSigningCertificateSha256:
      signing.productionAppSigningCertificateSha256,
    productionUploadCertificateSha256:
      signing.productionUploadCertificateSha256,
  };
  const sequence = Number(
    values.ANDROID_DEPENDENCY_SIGNING_REVIEW_SEQUENCE_NUMBER,
  );
  const evaluation = Number(
    values.ANDROID_DEPENDENCY_SIGNING_REVIEW_EVALUATION_EPOCH_SECONDS,
  );
  try {
    const computedContract = androidDependencySigningReviewContractSha256V1({
      sourceRevision:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION,
      reviewedInputs,
    });
    if (
      computedContract !==
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_SHA256 ||
      values.PRODUCTION_CANDIDATE_SOURCE_REVISION !==
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION ||
      !Number.isSafeInteger(sequence) ||
      sequence < 1 ||
      !Number.isSafeInteger(evaluation) ||
      evaluation < 1
    ) {
      return { state: "invalid", receipt: null };
    }
    const receipt = verifyAndroidDependencySigningReviewV1({
      manifestPath:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_PATH,
      producerSignaturePath:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_PATH,
      reviewerSignaturePath:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_PATH,
      producerPublicKeyPath:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_PUBLIC_KEY_PATH,
      reviewerPublicKeyPath:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_PUBLIC_KEY_PATH,
      expectedProducerKeySha256:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_KEY_SHA256,
      expectedReviewerKeySha256:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_KEY_SHA256,
      expectedReviewSequenceNumber: sequence,
      expectedSourceRevision:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION,
      expectedReviewContractSha256:
        values.ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_SHA256,
      expectedReviewedInputs: reviewedInputs,
      evaluationEpochSeconds: evaluation,
    });
    const sourceStateQualified =
      dependency.status === "qualified" &&
      dependency.releaseEnabled === true &&
      signing.status === "qualified" &&
      signing.releaseEnabled === true &&
      signing.retainedProductionCertificateMatched === true &&
      signing.signedBundleCertificateMatched === true &&
      signing.playAppSigningContinuityReviewed === true;
    return sourceStateQualified
      ? { state: "admitted", receipt }
      : { state: "invalid", receipt: null };
  } catch {
    return { state: "invalid", receipt: null };
  }
};
const validateAndroidDependencyPreflight = () => {
  const dependencyFailures = [];
  const dependencyBlockers = [];
  const fail = (condition, code) => {
    if (condition) dependencyFailures.push(code);
  };
  const releaseBlock = (condition, code) => {
    if (condition) dependencyBlockers.push(code);
  };
  const configPath = join(root, "config/gradle-dependency-provenance.json");
  const configRecord = readStrictJsonFile(configPath, 512 * 1024);
  fail(configRecord === null, "GRADLE_DEPENDENCY_PROVENANCE_JSON_INVALID");
  if (configRecord === null) {
    return {
      mode: "dependency-preflight",
      inventoryStatus: "INVALID",
      failures: [...new Set(dependencyFailures)].sort(),
      releaseBlockers: [],
    };
  }
  const config = configRecord.value;
  fail(
    !hasExactKeys(config, [
      "schemaVersion",
      "platform",
      "assessedAt",
      "status",
      "releaseEnabled",
      "wrapper",
      "repositoryPolicy",
      "workflowPolicy",
      "dependencyVerification",
      "dependencyLocking",
      "credentialIncident",
      "releaseCriteria",
      "blocker",
      "exitCriteria",
    ]) ||
      config.schemaVersion !== 3 ||
      config.platform !== "android" ||
      config.assessedAt !== "2026-08-09" ||
      !["blocked", "qualified"].includes(config.status) ||
      typeof config.releaseEnabled !== "boolean" ||
      (config.status === "qualified") !== config.releaseEnabled,
    "GRADLE_DEPENDENCY_PROVENANCE_SCHEMA_INVALID",
  );

  const settingsSource = read("settings.gradle.kts");
  const expectedVendorModules = EXPECTED_VENDOR_MODULES.map(({ coordinate }) =>
    coordinate.split(":").slice(0, 2).join(":"),
  );
  const settingsModules = [
    ...settingsSource.matchAll(
      /includeModule\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,?\s*\)/g,
    ),
  ].map(([, group, module]) => `${group}:${module}`);
  const vendorRepositoryBlock = uniqueBalancedBlockContaining(
    settingsSource,
    "exclusiveContent",
    'name = "SourceQualifiedSoramitsu"',
  );
  const jitpackRepositoryBlock = uniqueBalancedBlockContaining(
    settingsSource,
    "exclusiveContent",
    'name = "JitPack"',
  );
  fail(
    !settingsSource.includes("RepositoriesMode.FAIL_ON_PROJECT_REPOS") ||
      !settingsSource.includes('name = "SourceQualifiedSoramitsu"') ||
      !settingsSource.includes(
        'url = uri(rootDir.resolve("vendor/soramitsu-maven"))',
      ) ||
      (settingsSource.match(/\bgradleMetadata\(\)/g) ?? []).length !== 1 ||
      (settingsSource.match(/\bmavenPom\(\)/g) ?? []).length !== 1 ||
      settingsSource.includes("artifact()") ||
      vendorRepositoryBlock === null ||
      !exactStringSet(
        includeModulesIn(vendorRepositoryBlock),
        expectedVendorModules,
      ) ||
      (vendorRepositoryBlock?.match(/\bgradleMetadata\(\)/g) ?? []).length !==
        1 ||
      (vendorRepositoryBlock?.match(/\bmavenPom\(\)/g) ?? []).length !== 1 ||
      vendorRepositoryBlock?.includes("artifact()") ||
      jitpackRepositoryBlock === null ||
      !exactStringSet(
        includeModulesIn(jitpackRepositoryBlock),
        EXPECTED_JITPACK_MODULES,
      ) ||
      !exactStringSet(settingsModules, [
        ...expectedVendorModules,
        ...EXPECTED_JITPACK_MODULES,
      ]) ||
      /\bincludeGroup(?:ByRegex)?\s*\(/.test(settingsSource) ||
      !settingsSource.includes('name = "JitPack"') ||
      !settingsSource.includes('url = uri("https://jitpack.io")') ||
      /nexus\.iroha\.tech|PAY_WINGS_REPOSITORY_URL|mavenLocal|flatDir|includeBuild|dependencySubstitution|file:\/\//i.test(
        settingsSource,
      ),
    "GRADLE_REPOSITORY_ALLOWLIST_MISSING",
  );
  const rootBuildSource = read("build.gradle.kts");
  fail(
    !rootBuildSource.includes("LockMode.STRICT") ||
      !rootBuildSource.includes("activateDependencyLocking()") ||
      !rootBuildSource.includes("failOnDynamicVersions()") ||
      !rootBuildSource.includes("failOnChangingVersions()") ||
      !rootBuildSource.includes(
        'configurationName.contains("productionRelease", ignoreCase = true)',
      ),
    "PRODUCTION_RELEASE_STRICT_DEPENDENCY_POLICY_MISSING",
  );

  const wrapper = config.wrapper;
  const wrapperJarHash = sha256AbsoluteRegularFile(
    join(root, "gradle/wrapper/gradle-wrapper.jar"),
    256 * 1024,
  );
  const wrapperProperties = read("gradle/wrapper/gradle-wrapper.properties");
  fail(
    !hasExactKeys(wrapper, [
      "distributionVersion",
      "distributionUrl",
      "distributionSha256",
      "expectedWrapperJarSha256",
      "observedWrapperJarSha256",
      "wrapperJarMatchesDistribution",
      "checksumSource",
    ]) ||
      wrapper?.distributionVersion !== "9.5.1" ||
      wrapper?.distributionUrl !==
        "https://services.gradle.org/distributions/gradle-9.5.1-bin.zip" ||
      wrapper?.distributionSha256 !==
        "bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f" ||
      wrapper?.expectedWrapperJarSha256 !==
        "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7" ||
      wrapper?.observedWrapperJarSha256 !== wrapperJarHash ||
      wrapper?.wrapperJarMatchesDistribution !== true ||
      wrapper?.checksumSource !== "https://gradle.org/release-checksums/" ||
      !wrapperProperties.includes(
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip",
      ) ||
      !wrapperProperties.includes(
        "distributionSha256Sum=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f",
      ) ||
      !wrapperProperties.includes("networkTimeout=10000") ||
      !wrapperProperties.includes("validateDistributionUrl=true"),
    "GRADLE_WRAPPER_PROVENANCE_EVIDENCE_INVALID",
  );

  const vendorPolicy = config.repositoryPolicy?.sourceQualifiedVendorRepository;
  const namespaceCompatibility =
    config.repositoryPolicy?.idensicTensorflowNamespaceCompatibility;
  const expectedCoordinates = EXPECTED_VENDOR_MODULES.map(
    ({ coordinate }) => coordinate,
  );
  fail(
    !hasExactKeys(config.repositoryPolicy, [
      "projectRepositoriesForbidden",
      "mavenLocalForbidden",
      "unmanifestedFileRepositoriesForbidden",
      "flatDirectoryRepositoriesForbidden",
      "compositeBuildSubstitutionsForbidden",
      "arbitraryPayWingsRepositoryRemoved",
      "deadSoramitsuNexusRemoved",
      "sourceQualifiedVendorRepository",
      "idensicTensorflowNamespaceCompatibility",
      "jitpackRepository",
      "jitpackExclusiveModules",
      "reviewedStagedIrohaRepository",
    ]) ||
      !hasExactKeys(vendorPolicy, [
        "path",
        "gradleRepositoryName",
        "metadataSources",
        "exactModules",
        "materializedCoordinates",
        "sourceProvenancePath",
        "sourceProvenanceSha256",
        "contentsManifestPath",
        "contentsManifestSha256",
        "manifestFormat",
        "symlinkFree",
        "exactRegularFileCoverage",
        "status",
        "productionAllowed",
      ]) ||
      config.repositoryPolicy?.projectRepositoriesForbidden !== true ||
      config.repositoryPolicy?.mavenLocalForbidden !== true ||
      config.repositoryPolicy?.unmanifestedFileRepositoriesForbidden !== true ||
      config.repositoryPolicy?.flatDirectoryRepositoriesForbidden !== true ||
      config.repositoryPolicy?.compositeBuildSubstitutionsForbidden !== true ||
      config.repositoryPolicy?.arbitraryPayWingsRepositoryRemoved !== true ||
      config.repositoryPolicy?.deadSoramitsuNexusRemoved !== true ||
      vendorPolicy?.path !== "vendor/soramitsu-maven" ||
      vendorPolicy?.gradleRepositoryName !== "SourceQualifiedSoramitsu" ||
      !exactStringSet(vendorPolicy?.metadataSources, [
        "gradleMetadata",
        "mavenPom",
      ]) ||
      !exactStringSet(vendorPolicy?.exactModules, expectedVendorModules) ||
      !exactStringSet(
        vendorPolicy?.materializedCoordinates,
        expectedCoordinates,
      ) ||
      vendorPolicy?.sourceProvenancePath !==
        "vendor/soramitsu-maven/SOURCE_PROVENANCE.json" ||
      vendorPolicy?.sourceProvenanceSha256 !==
        EXPECTED_VENDOR_SOURCE_PROVENANCE_SHA256 ||
      vendorPolicy?.contentsManifestPath !==
        "vendor/soramitsu-maven/CONTENTS.sha256" ||
      vendorPolicy?.contentsManifestSha256 !==
        EXPECTED_VENDOR_CONTENTS_MANIFEST_SHA256 ||
      vendorPolicy?.manifestFormat !==
        "sha256-two-space-relative-path-lf" ||
      vendorPolicy?.symlinkFree !== true ||
      vendorPolicy?.exactRegularFileCoverage !== true ||
      !["materialized-unreviewed", "qualified"].includes(
        vendorPolicy?.status,
      ) ||
      typeof vendorPolicy?.productionAllowed !== "boolean" ||
      (vendorPolicy?.status === "qualified") !==
        vendorPolicy?.productionAllowed,
    "VENDORED_REPOSITORY_POLICY_INVALID",
  );
  const namespaceCompatibilityCoordinates = [
    "com.paywings.onboarding.kyc.android-libs:idensic-mobile-sdk:1.31.3",
    "org.tensorflow:tensorflow-lite-api:2.12.0",
    "org.tensorflow:tensorflow-lite:2.12.0",
  ];
  const uniquePackageNamePropertyMatches = [
    ...read("gradle.properties").matchAll(
      /^android\.uniquePackageNames=(true|false)$/gm,
    ),
  ];
  fail(
    !hasExactKeys(namespaceCompatibility, [
      "status",
      "gradleProperty",
      "requiredValue",
      "pinnedCoordinates",
      "scope",
      "broadQualificationGranted",
      "independentlyReviewed",
    ]) ||
      ![
        "scoped-unreviewed-workaround",
        "scoped-reviewed-workaround",
      ].includes(namespaceCompatibility?.status) ||
      namespaceCompatibility?.gradleProperty !==
        "android.uniquePackageNames" ||
      namespaceCompatibility?.requiredValue !== false ||
      !exactStringSet(
        namespaceCompatibility?.pinnedCoordinates,
        namespaceCompatibilityCoordinates,
      ) ||
      namespaceCompatibility?.scope !==
        "idensic-1.31.3-tensorflow-lite-2.12.0-agp9-namespace-migration" ||
      namespaceCompatibility?.broadQualificationGranted !== false ||
      typeof namespaceCompatibility?.independentlyReviewed !== "boolean" ||
      (namespaceCompatibility?.status === "scoped-reviewed-workaround") !==
        namespaceCompatibility?.independentlyReviewed ||
      uniquePackageNamePropertyMatches.length !== 1 ||
      uniquePackageNamePropertyMatches[0][1] !== "false",
    "IDENSIC_TENSORFLOW_NAMESPACE_COMPATIBILITY_POLICY_INVALID",
  );
  fail(
    config.repositoryPolicy?.jitpackRepository !== "https://jitpack.io" ||
      !exactStringSet(
        config.repositoryPolicy?.jitpackExclusiveModules,
        EXPECTED_JITPACK_MODULES,
      ),
    "JITPACK_REPOSITORY_POLICY_INVALID",
  );

  const vendorRoot = join(root, "vendor/soramitsu-maven");
  let actualVendorFiles = null;
  try {
    actualVendorFiles = walkSymlinkFreeRegularFiles(vendorRoot);
  } catch {
    fail(true, "VENDORED_REPOSITORY_NOT_SYMLINK_FREE");
  }
  const manifestPath = join(vendorRoot, "CONTENTS.sha256");
  let manifestEntries = null;
  if (
    actualVendorFiles !== null &&
    sha256AbsoluteRegularFile(manifestPath, 256 * 1024) ===
      vendorPolicy?.contentsManifestSha256
  ) {
    const manifestSource = readFileSync(manifestPath, "utf8");
    const manifestLines = manifestSource.endsWith("\n") &&
      !manifestSource.includes("\r") &&
      !manifestSource.includes("\0")
      ? manifestSource.slice(0, -1).split("\n")
      : [];
    const parsed = manifestLines.map((line) =>
      /^([0-9a-f]{64})  (.+)$/.exec(line),
    );
    const paths = parsed.map((match) => match?.[2] ?? "");
    const expectedFiles = actualVendorFiles.filter(
      (path) => path !== "CONTENTS.sha256",
    );
    const valid =
      manifestLines.length > 0 &&
      parsed.every(
        (match) => match !== null && isSafeManifestRelativePath(match[2]),
      ) &&
      exactStringSet(paths, expectedFiles) &&
      paths.join("\n") === [...paths].sort().join("\n") &&
      !paths.includes("CONTENTS.sha256");
    if (valid) {
      manifestEntries = new Map(
        parsed.map((match) => [match[2], match[1]]),
      );
      for (const [path, expectedHash] of manifestEntries) {
        if (
          sha256AbsoluteRegularFile(join(vendorRoot, path)) !== expectedHash
        ) {
          fail(true, `VENDORED_REPOSITORY_CONTENT_DRIFT:${path}`);
        }
      }
    } else {
      fail(true, "VENDORED_REPOSITORY_MANIFEST_INVALID");
    }
  } else {
    fail(true, "VENDORED_REPOSITORY_MANIFEST_MISSING_OR_MISMATCHED");
  }

  const sourceProvenancePath = join(vendorRoot, "SOURCE_PROVENANCE.json");
  const sourceProvenanceRecord = readStrictJsonFile(
    sourceProvenancePath,
    512 * 1024,
  );
  fail(
    sourceProvenanceRecord === null ||
      sourceProvenanceRecord?.sha256 !== vendorPolicy?.sourceProvenanceSha256,
    "VENDORED_SOURCE_PROVENANCE_MISSING_OR_MISMATCHED",
  );
  const provenance = sourceProvenanceRecord?.value;
  if (provenance !== undefined) {
    fail(
      !hasExactKeys(provenance, [
        "schemaVersion",
        "repositoryPath",
        "assessedAt",
        "status",
        "productionAllowed",
        "repositoryManifest",
        "buildInputs",
        "modules",
        "reviewState",
        "blockers",
        ]) ||
        provenance.schemaVersion !== 1 ||
        provenance.repositoryPath !== "vendor/soramitsu-maven" ||
        provenance.assessedAt !== config.assessedAt ||
        !["materialized-unreviewed", "qualified"].includes(
          provenance.status,
        ) ||
        typeof provenance.productionAllowed !== "boolean" ||
        (provenance.status === "qualified") !== provenance.productionAllowed ||
        provenance.status !== vendorPolicy?.status ||
        provenance.productionAllowed !== vendorPolicy?.productionAllowed ||
        !hasExactKeys(provenance.repositoryManifest, [
          "path",
          "format",
          "excludedPaths",
          "exactRegularFileCoverage",
          "symlinkFree",
          "independentlyReviewed",
        ]) ||
        provenance.repositoryManifest.path !== "CONTENTS.sha256" ||
        provenance.repositoryManifest.format !==
          "sha256-two-space-relative-path-lf" ||
        !exactStringSet(provenance.repositoryManifest.excludedPaths, [
          "CONTENTS.sha256",
        ]) ||
        provenance.repositoryManifest.exactRegularFileCoverage !== true ||
        provenance.repositoryManifest.symlinkFree !== true ||
        typeof provenance.repositoryManifest.independentlyReviewed !==
          "boolean",
      "VENDORED_SOURCE_PROVENANCE_SCHEMA_INVALID",
    );
    const buildInputPaths = Object.keys(EXPECTED_VENDOR_BUILD_INPUT_SHA256);
    fail(
      !Array.isArray(provenance.buildInputs) ||
        provenance.buildInputs.length !== buildInputPaths.length ||
        !exactStringSet(
          provenance.buildInputs?.map((input) => input?.path),
          buildInputPaths,
        ) ||
        provenance.buildInputs?.some(
          (input) =>
            !hasExactKeys(input, ["path", "sha256"]) ||
            input.sha256 !== EXPECTED_VENDOR_BUILD_INPUT_SHA256[input.path] ||
            manifestEntries?.get(input.path) !== input.sha256,
        ),
      "VENDORED_BUILD_INPUT_INVENTORY_INVALID",
    );
    fail(
      !hasExactKeys(provenance.reviewState, [
        "artifactHashesPinned",
        "sourceIdentitiesPinned",
        "sourceToBinaryReproductionReviewed",
        "licensesAndNoticesReviewed",
        "sbomReviewed",
        "buildAttestationReviewed",
      ]) ||
        provenance.reviewState.artifactHashesPinned !== true ||
        provenance.reviewState.sourceIdentitiesPinned !== true ||
        typeof provenance.reviewState.sourceToBinaryReproductionReviewed !==
          "boolean" ||
        typeof provenance.reviewState.licensesAndNoticesReviewed !== "boolean" ||
        typeof provenance.reviewState.sbomReviewed !== "boolean" ||
        typeof provenance.reviewState.buildAttestationReviewed !== "boolean" ||
        !Array.isArray(provenance.blockers) ||
        provenance.blockers.some(
          (blocker) => typeof blocker !== "string" || blocker.length === 0,
        ) ||
        (provenance.status === "qualified" &&
          (provenance.repositoryManifest.independentlyReviewed !== true ||
            provenance.reviewState.sourceToBinaryReproductionReviewed !== true ||
            provenance.reviewState.licensesAndNoticesReviewed !== true ||
            provenance.reviewState.sbomReviewed !== true ||
            provenance.reviewState.buildAttestationReviewed !== true ||
            provenance.blockers.length !== 0)) ||
        (provenance.status === "materialized-unreviewed" &&
          provenance.blockers.length === 0),
      "VENDORED_REVIEW_STATE_INVALID",
    );

    const modulesByCoordinate = new Map(
      Array.isArray(provenance.modules)
        ? provenance.modules.map((module) => [module?.coordinate, module])
        : [],
    );
    fail(
      !Array.isArray(provenance.modules) ||
        modulesByCoordinate.size !== EXPECTED_VENDOR_MODULES.length ||
        !exactStringSet(
          provenance.modules?.map((module) => module?.coordinate),
          expectedCoordinates,
        ),
      "VENDORED_MODULE_INVENTORY_INVALID",
    );
    for (const expected of EXPECTED_VENDOR_MODULES) {
      const module = modulesByCoordinate.get(expected.coordinate);
      if (
        !hasExactKeys(module, [
          "coordinate",
          "modulePath",
          "origin",
          "materializedFiles",
        ]) ||
        module.modulePath !== expected.modulePath ||
        !Array.isArray(module.materializedFiles)
      ) {
        fail(true, `VENDORED_MODULE_SCHEMA_INVALID:${expected.coordinate}`);
        continue;
      }
      let actualModuleFiles = [];
      try {
        actualModuleFiles = readdirSync(join(vendorRoot, module.modulePath), {
          withFileTypes: true,
        })
          .map((entry) => {
            if (!entry.isFile() || entry.isSymbolicLink()) {
              throw new Error("MODULE_NON_REGULAR");
            }
            return entry.name;
          })
          .sort();
      } catch {
        fail(true, `VENDORED_MODULE_DIRECTORY_INVALID:${expected.coordinate}`);
        continue;
      }
      const declaredNames = module.materializedFiles.map((file) => file?.name);
      fail(
        !exactStringSet(declaredNames, actualModuleFiles) ||
          declaredNames.join("\n") !== [...declaredNames].sort().join("\n") ||
          module.materializedFiles.some(
            (file) =>
              !hasExactKeys(file, ["name", "sha256"]) ||
              !/^[A-Za-z0-9@+_.-]+$/.test(file.name ?? "") ||
              !/^[0-9a-f]{64}$/.test(file.sha256 ?? "") ||
              manifestEntries?.get(`${module.modulePath}/${file.name}`) !==
                file.sha256,
          ),
        `VENDORED_MODULE_FILES_INVALID:${expected.coordinate}`,
      );
      const [, expectedArtifact, expectedVersion] = expected.coordinate.split(":");
      const expectedPomName = `${expectedArtifact}-${expectedVersion}.pom`;
      const primaryFiles = module.materializedFiles.filter(
        (file) =>
          /\.(?:aar|jar)$/.test(file.name) &&
          !file.name.endsWith("-sources.jar"),
      );
      fail(
        !declaredNames.includes(expectedPomName) || primaryFiles.length !== 1,
        `VENDORED_MODULE_PRIMARY_INPUT_INVALID:${expected.coordinate}`,
      );
      if (declaredNames.includes(expectedPomName)) {
        const pom = readFileSync(
          join(vendorRoot, module.modulePath, expectedPomName),
          "utf8",
        );
        const pomIdentity = /<project[\s\S]*?<groupId>([^<]+)<\/groupId>\s*<artifactId>([^<]+)<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(
          pom,
        );
        const [expectedGroup] = expected.coordinate.split(":");
        fail(
          pomIdentity === null ||
            pomIdentity[1] !== expectedGroup ||
            pomIdentity[2] !== expectedArtifact ||
            pomIdentity[3] !== expectedVersion,
          `VENDORED_MODULE_POM_IDENTITY_INVALID:${expected.coordinate}`,
        );
      }

      const origin = module.origin;
      if (expected.originKind === "git-source-build") {
        fail(
          !hasExactKeys(origin, [
            "kind",
            "repository",
            "revision",
            "tree",
            "commitVerification",
            "buildInputs",
            "semanticTransformations",
            "sourceToBinaryReproductionReviewed",
          ]) ||
            origin.kind !== expected.originKind ||
            origin.repository !== expected.repository ||
            origin.revision !== expected.revision ||
            origin.tree !== expected.tree ||
            !hasExactKeys(origin.commitVerification, [
              "provider",
              "status",
              "observedAt",
              "independentlyReviewed",
            ]) ||
            origin.commitVerification.provider !== "github" ||
            origin.commitVerification.status !== "valid" ||
            origin.commitVerification.observedAt !== "2026-08-09" ||
            typeof origin.commitVerification.independentlyReviewed !==
              "boolean" ||
            !exactStringSet(
              origin.buildInputs,
              expected.buildInputs ?? [],
            ) ||
            !exactStringSet(
              origin.semanticTransformations,
              expected.semanticTransformations ?? [],
            ) ||
            typeof origin.sourceToBinaryReproductionReviewed !== "boolean",
          `VENDORED_GIT_SOURCE_IDENTITY_INVALID:${expected.coordinate}`,
        );
      } else if (expected.originKind === "git-tracked-binary") {
        fail(
          !hasExactKeys(origin, [
            "kind",
            "repository",
            "revision",
            "tree",
            "sourcePath",
            "commitVerification",
            "independentBinaryReviewCompleted",
            "sourceToBinaryReproductionReviewed",
          ]) ||
            origin.kind !== expected.originKind ||
            origin.repository !== expected.repository ||
            origin.revision !== expected.revision ||
            origin.tree !== expected.tree ||
            origin.sourcePath !== expected.sourcePath ||
            origin.commitVerification?.provider !== "github" ||
            origin.commitVerification?.status !== "valid" ||
            origin.commitVerification?.observedAt !== "2026-08-09" ||
            typeof origin.commitVerification?.independentlyReviewed !==
              "boolean" ||
            typeof origin.independentBinaryReviewCompleted !== "boolean" ||
            typeof origin.sourceToBinaryReproductionReviewed !== "boolean",
          `VENDORED_TRACKED_BINARY_IDENTITY_INVALID:${expected.coordinate}`,
        );
      } else {
        fail(
          !hasExactKeys(origin, [
            "kind",
            "packagePage",
            "publisherMd5AndSha1SidecarsMatched",
            "sourceAvailableInPackage",
            "independentBinaryReviewCompleted",
            "sourceToBinaryReproductionReviewed",
          ]) ||
            origin.kind !== expected.originKind ||
            origin.packagePage !== expected.packagePage ||
            origin.publisherMd5AndSha1SidecarsMatched !== true ||
            origin.sourceAvailableInPackage !== false ||
            typeof origin.independentBinaryReviewCompleted !== "boolean" ||
            typeof origin.sourceToBinaryReproductionReviewed !== "boolean",
          `VENDORED_PACKAGE_IDENTITY_INVALID:${expected.coordinate}`,
        );
        for (const primaryName of [
          `${expectedArtifact}-${expectedVersion}.aar`,
          `${expectedArtifact}-${expectedVersion}.pom`,
        ]) {
          const primaryPath = join(vendorRoot, module.modulePath, primaryName);
          const primaryBytes = existsSync(primaryPath)
            ? readFileSync(primaryPath)
            : null;
          const md5Path = `${primaryPath}.md5`;
          const sha1Path = `${primaryPath}.sha1`;
          const publisherMd5 = existsSync(md5Path)
            ? readFileSync(md5Path, "utf8").trim()
            : "";
          const publisherSha1 = existsSync(sha1Path)
            ? readFileSync(sha1Path, "utf8").trim()
            : "";
          fail(
            primaryBytes === null ||
              !/^[0-9a-f]{32}$/.test(publisherMd5) ||
              !/^[0-9a-f]{40}$/.test(publisherSha1) ||
              createHash("md5").update(primaryBytes ?? "").digest("hex") !==
                publisherMd5 ||
              createHash("sha1").update(primaryBytes ?? "").digest("hex") !==
                publisherSha1,
            `VENDORED_PACKAGE_SIDECAR_MISMATCH:${expected.coordinate}:${primaryName}`,
          );
        }
      }
    }
    const applicableModuleReviewsComplete = provenance.modules.every(
      (module) => {
        const origin = module?.origin;
        if (origin?.kind === "git-source-build") {
          return (
            origin.commitVerification?.independentlyReviewed === true &&
            origin.sourceToBinaryReproductionReviewed === true
          );
        }
        if (origin?.kind === "git-tracked-binary") {
          return (
            origin.commitVerification?.independentlyReviewed === true &&
            origin.independentBinaryReviewCompleted === true &&
            origin.sourceToBinaryReproductionReviewed === true
          );
        }
        if (origin?.kind === "github-packages-asset") {
          return origin.independentBinaryReviewCompleted === true;
        }
        return false;
      },
    );
    fail(
      provenance.reviewState.sourceToBinaryReproductionReviewed !==
        applicableModuleReviewsComplete,
      "VENDORED_MODULE_REVIEW_AGGREGATE_INVALID",
    );
  }

  const verification = config.dependencyVerification;
  const metadataPath = verification?.metadataPath;
  const metadataDigestPath = verification?.metadataDigestPath;
  const metadataPathSafe =
    metadataPath === "gradle/verification-metadata.xml" &&
    isCanonicalRegularRepoFile(metadataPath);
  const metadataDigestPathSafe =
    metadataDigestPath === "gradle/verification-metadata.sha256" &&
    isCanonicalRegularRepoFile(metadataDigestPath);
  fail(
    !hasExactKeys(verification, [
      "status",
      "mode",
      "metadataPath",
      "metadataPresent",
      "metadataSha256",
      "metadataDigestPath",
      "metadataDigestSha256",
      "verifyMetadata",
      "verifySignatures",
      "sha256ForEveryResolvedArtifact",
      "materializedVendorCoordinates",
      "independentlyReviewed",
      "trustedArtifactExceptions",
    ]) ||
      !["materialized-unreviewed", "qualified"].includes(
      verification?.status,
    ) ||
      verification?.mode !== "strict" ||
      verification?.metadataPresent !== true ||
      verification?.verifyMetadata !== true ||
      verification?.verifySignatures !== false ||
      verification?.sha256ForEveryResolvedArtifact !== true ||
      typeof verification?.independentlyReviewed !== "boolean" ||
      (verification?.status === "qualified") !==
        verification?.independentlyReviewed ||
      !Array.isArray(verification?.trustedArtifactExceptions) ||
      verification.trustedArtifactExceptions.length !== 0 ||
      !exactStringSet(
        verification?.materializedVendorCoordinates,
        expectedCoordinates,
      ) ||
      !metadataPathSafe ||
      !metadataDigestPathSafe ||
      verification?.metadataSha256 !==
        EXPECTED_GRADLE_VERIFICATION_METADATA_SHA256 ||
      verification?.metadataDigestSha256 !==
        EXPECTED_GRADLE_VERIFICATION_METADATA_DIGEST_SHA256,
    "GRADLE_VERIFICATION_METADATA_POLICY_INVALID",
  );
  let verificationComponentCoordinates = new Set();
  if (metadataPathSafe && metadataDigestPathSafe) {
    const metadataHash = sha256(metadataPath);
    const metadata = read(metadataPath);
    const componentMatches = [
      ...metadata.matchAll(
        /<component\s+group="([^"]+)"\s+name="([^"]+)"\s+version="([^"]+)">([\s\S]*?)<\/component>/g,
      ),
    ];
    const componentTagCount =
      (metadata.match(/<component\s+group="[^"]+"\s+name="[^"]+"\s+version="[^"]+"/g) ?? [])
        .length;
    const artifactTagCount =
      (metadata.match(/<artifact\s+name="[^"]+"/g) ?? []).length;
    const componentDigestSets = new Map();
    let parsedArtifactCount = 0;
    let metadataStructureValid = componentMatches.length > 0;
    for (const [, group, name, version, componentBody] of componentMatches) {
      const coordinate = `${group}:${name}:${version}`;
      if (componentDigestSets.has(coordinate)) metadataStructureValid = false;
      const artifactMatches = [
        ...componentBody.matchAll(
          /<artifact\s+name="([^"]+)">([\s\S]*?)<\/artifact>/g,
        ),
      ];
      const componentResidue = componentBody
        .replace(/<artifact\s+name="[^"]+">[\s\S]*?<\/artifact>/g, "")
        .replace(/<!--[\s\S]*?-->/g, "")
        .trim();
      if (artifactMatches.length === 0 || componentResidue !== "") {
        metadataStructureValid = false;
      }
      const digests = new Set();
      for (const [, , artifactBody] of artifactMatches) {
        parsedArtifactCount += 1;
        const shaMatches = [
          ...artifactBody.matchAll(/<sha256\b([^>]*)\/>/g),
        ];
        const artifactResidue = artifactBody
          .replace(/<sha256\b[^>]*\/>/g, "")
          .replace(/<!--[\s\S]*?-->/g, "")
          .trim();
        if (
          shaMatches.length === 0 ||
          artifactResidue !== "" ||
          shaMatches.some(
            ([, attributes]) =>
              !/\bvalue="[0-9a-f]{64}"/.test(attributes),
          )
        ) {
          metadataStructureValid = false;
        }
        for (const [, attributes] of shaMatches) {
          const value = /\bvalue="([0-9a-f]{64})"/.exec(attributes)?.[1];
          if (value) digests.add(value);
        }
      }
      componentDigestSets.set(coordinate, digests);
    }
    verificationComponentCoordinates = new Set(componentDigestSets.keys());
    fail(
      metadataHash !== verification.metadataSha256 ||
        sha256(metadataDigestPath) !== verification.metadataDigestSha256 ||
        read(metadataDigestPath) !==
          `${verification.metadataSha256}  verification-metadata.xml\n` ||
        !metadata.includes("<verify-metadata>true</verify-metadata>") ||
        !metadata.includes("<verify-signatures>false</verify-signatures>") ||
        /<(?:trusted-artifacts|trusted-keys|ignored-keys|md5|sha1)\b/.test(
          metadata,
        ) ||
        componentMatches.length !== componentTagCount ||
        parsedArtifactCount !== artifactTagCount ||
        !metadataStructureValid,
      "GRADLE_VERIFICATION_METADATA_INCOMPLETE",
    );
    if (provenance !== undefined) {
      for (const module of provenance.modules ?? []) {
        const digests = componentDigestSets.get(module.coordinate);
        const primary = module.materializedFiles?.find(
          (file) =>
            /\.(?:aar|jar)$/.test(file.name) &&
            !file.name.endsWith("-sources.jar"),
        );
        const metadataInput =
          module.materializedFiles?.find((file) => file.name.endsWith(".module")) ??
          module.materializedFiles?.find((file) => file.name.endsWith(".pom"));
        fail(
          !(digests instanceof Set) ||
            primary === undefined ||
            metadataInput === undefined ||
            !digests?.has(primary.sha256) ||
            !digests?.has(metadataInput.sha256),
          `GRADLE_VENDOR_VERIFICATION_METADATA_MISSING:${module.coordinate}`,
        );
      }
    }
    fail(
      namespaceCompatibilityCoordinates.some(
        (coordinate) => !componentDigestSets.has(coordinate),
      ),
      "IDENSIC_TENSORFLOW_NAMESPACE_METADATA_BINDING_MISSING",
    );
  }

  const locking = config.dependencyLocking;
  const includedProjects = [
    ...settingsSource.matchAll(/^include\(":([A-Za-z0-9_.-]+)"\)$/gm),
  ].map(([, project]) => project);
  const expectedLockPaths = [
    ...includedProjects.map((project) => `${project}/gradle.lockfile`),
    "settings-gradle.lockfile",
  ].sort();
  const actualLockFileSetSha256 = expectedLockPaths.every(
    isCanonicalRegularRepoFile,
  )
    ? sha256Files(expectedLockPaths)
    : null;
  const declaredLockFiles = Array.isArray(locking?.lockFiles)
    ? locking.lockFiles
    : [];
  fail(
    !hasExactKeys(locking, [
      "status",
      "mode",
      "allConfiguredProductionReleaseConfigurationsMaterialized",
      "independentlyReviewed",
      "materializedInventory",
      "lockFiles",
    ]) ||
      !["materialized-unreviewed", "qualified"].includes(locking?.status) ||
      locking?.mode !== "strict" ||
      locking?.allConfiguredProductionReleaseConfigurationsMaterialized !==
        true ||
      typeof locking?.independentlyReviewed !== "boolean" ||
      (locking?.status === "qualified") !== locking?.independentlyReviewed ||
      !hasExactKeys(locking?.materializedInventory, [
        "status",
        "complete",
        "lockFilePaths",
        "lockFileSetFormat",
        "lockFileSetSha256",
        "configurationInventoryFormat",
        "configurationInventorySha256",
        "configurationCount",
        "productionReleaseConfigurationCount",
      ]) ||
      locking.materializedInventory.status !== "materialized" ||
      locking.materializedInventory.complete !== true ||
      !exactStringSet(
        locking.materializedInventory.lockFilePaths,
        expectedLockPaths,
      ) ||
      locking.materializedInventory.lockFileSetFormat !==
        "sorted-path-nul-file-bytes-nul-sha256" ||
      locking.materializedInventory.lockFileSetSha256 !==
        EXPECTED_GRADLE_LOCK_FILE_SET_SHA256 ||
      actualLockFileSetSha256 !==
        locking.materializedInventory.lockFileSetSha256 ||
      locking.materializedInventory.configurationInventoryFormat !==
        "project-path-colon-configuration-lf-no-terminal-lf-sha256" ||
      locking.materializedInventory.configurationInventorySha256 !==
        EXPECTED_GRADLE_LOCK_CONFIGURATION_INVENTORY_SHA256 ||
      locking.materializedInventory.configurationCount !== 271 ||
      locking.materializedInventory.productionReleaseConfigurationCount !==
        270 ||
      declaredLockFiles.length !== expectedLockPaths.length ||
      !exactStringSet(
        declaredLockFiles.map((lock) => lock?.path),
        expectedLockPaths,
      ),
    "GRADLE_DEPENDENCY_LOCK_INVENTORY_SCHEMA_INVALID",
  );
  const qualifiedConfigurations = [];
  const lockedModuleCoordinates = new Set();
  for (const lock of declaredLockFiles) {
    if (
      !hasExactKeys(lock, ["path", "sha256", "configurationCount"]) ||
      !expectedLockPaths.includes(lock.path) ||
      !isCanonicalRegularRepoFile(lock.path)
    ) {
      fail(true, `GRADLE_DEPENDENCY_LOCK_INVALID:${lock?.path ?? "unknown"}`);
      continue;
    }
    const lockSource = read(lock.path);
    const lines = lockSource.endsWith("\n") && !lockSource.includes("\r")
      ? lockSource.slice(0, -1).split("\n")
      : [];
    const header = [
      "# This is a Gradle generated file for dependency locking.",
      "# Manual edits can break the build and are not advised.",
      "# This file is expected to be part of source control.",
    ];
    const entries = lines.slice(3);
    const dependencyEntries = entries.filter(
      (entry) => !entry.startsWith("empty="),
    );
    const emptyEntries = entries.filter((entry) => entry.startsWith("empty="));
    const projectPath = lock.path === "settings-gradle.lockfile"
      ? ":settings"
      : `:${lock.path.split("/")[0]}`;
    const configurations = new Set();
    let lockShapeValid =
      lines.slice(0, 3).join("\n") === header.join("\n") &&
      entries.length > 0 &&
      dependencyEntries.join("\n") ===
        [...dependencyEntries].sort().join("\n") &&
      emptyEntries.length <= 1 &&
      (emptyEntries.length === 0 || entries.at(-1) === emptyEntries[0]);
    for (const entry of entries) {
      const match = /^(empty|[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+)=([A-Za-z0-9_.,-]+)$/.exec(
        entry,
      );
      if (match === null || /\+|latest|SNAPSHOT/i.test(match?.[1] ?? "")) {
        lockShapeValid = false;
        continue;
      }
      const entryConfigurations = match[2].split(",");
      if (match[1] !== "empty") lockedModuleCoordinates.add(match[1]);
      if (
        entryConfigurations.join(",") !==
          [...new Set(entryConfigurations)].sort().join(",")
      ) {
        lockShapeValid = false;
      }
      for (const configuration of entryConfigurations) {
        configurations.add(configuration);
      }
    }
    const qualified = [...configurations]
      .sort()
      .map((configuration) => `${projectPath}:${configuration}`);
    qualifiedConfigurations.push(...qualified);
    fail(
      !lockShapeValid ||
        sha256(lock.path) !== lock.sha256 ||
        configurations.size !== lock.configurationCount,
      `GRADLE_DEPENDENCY_LOCK_INVALID:${lock.path}`,
    );
  }
  const sortedQualifiedConfigurations = [...qualifiedConfigurations].sort();
  const productionConfigurationCount = sortedQualifiedConfigurations.filter(
    (configuration) =>
      !configuration.startsWith(":settings:") &&
      configuration.toLowerCase().includes("productionrelease"),
  ).length;
  fail(
    sortedQualifiedConfigurations.length !== new Set(sortedQualifiedConfigurations).size ||
      canonicalSha256(sortedQualifiedConfigurations) !==
        locking?.materializedInventory?.configurationInventorySha256 ||
      sortedQualifiedConfigurations.length !==
        locking?.materializedInventory?.configurationCount ||
      productionConfigurationCount !==
        locking?.materializedInventory?.productionReleaseConfigurationCount ||
      sortedQualifiedConfigurations.some(
        (configuration) =>
          !configuration.startsWith(":settings:") &&
          !configuration.toLowerCase().includes("productionrelease"),
      ),
    "GRADLE_DEPENDENCY_LOCK_CONFIGURATION_INVENTORY_INVALID",
  );
  fail(
    namespaceCompatibilityCoordinates.some(
      (coordinate) => !lockedModuleCoordinates.has(coordinate),
    ),
    "IDENSIC_TENSORFLOW_NAMESPACE_LOCK_BINDING_MISSING",
  );
  fail(
    [...lockedModuleCoordinates].some(
      (coordinate) => !verificationComponentCoordinates.has(coordinate),
    ),
    "GRADLE_LOCKED_MODULE_VERIFICATION_METADATA_MISSING",
  );

  const workflow = read(".github/workflows/production_release_qualification.yml");
  const dependencyPreflightIndex = workflow.indexOf(
    "run: node scripts/verify-production-modernization.mjs --dependency-preflight",
  );
  const firstGradleIndex = workflow.indexOf("./gradlew");
  fail(
    dependencyPreflightIndex < 0 ||
      firstGradleIndex < 0 ||
      dependencyPreflightIndex > firstGradleIndex ||
      /--dependency-verification(?:=|\s+)(?:off|lenient)|--write-verification-metadata|--write-locks|mavenLocal|includeBuild|dependencySubstitution|PAY_WINGS_REPOSITORY_URL/i.test(
        workflow,
      ),
    "PRODUCTION_WORKFLOW_DEPENDENCY_PREFLIGHT_ORDER_INVALID",
  );

  const credentials = config.credentialIncident;
  const releaseCriteria = config.releaseCriteria;
  fail(
    !hasExactKeys(credentials, [
      "trackedProjectAuthTokenRemoved",
      "priorTokenRevoked",
      "replacementCredentialRotated",
      "rotationEvidenceReviewed",
      "secretValueRetainedInEvidence",
    ]) ||
      !hasExactKeys(releaseCriteria, [
        "distributionChecksumPinned",
        "wrapperJarMatchesDistribution",
        "vendoredRepositoryManifestValid",
        "vendoredSourceIdentitiesPinned",
        "vendoredSourceToBinaryReviewComplete",
        "strictVerificationMetadataMaterialized",
        "strictVerificationMetadataReviewed",
        "everyResolvedArtifactHasSha256",
        "productionReleaseLocksMaterialized",
        "productionReleaseLocksReviewed",
        "repositoryAllowlistReviewed",
        "noLocalOrCompositeSubstitution",
        "idensicTensorflowNamespaceWorkaroundReviewed",
        "githubActionsPinnedToReviewedCommits",
        "jenkinsSharedLibraryPinned",
        "exposedCredentialRotatedAndRevoked",
      ]) ||
      Object.values(releaseCriteria ?? {}).some(
        (criterion) => typeof criterion !== "boolean",
      ) ||
      !hasExactKeys(config.blocker, ["code", "reason"]) ||
      (config.status === "blocked" &&
        (config.blocker?.code !==
          "gradle_dependency_provenance_not_qualified" ||
          typeof config.blocker?.reason !== "string" ||
          config.blocker.reason.length < 1)) ||
      (config.status === "qualified" &&
        (config.blocker?.code !== null || config.blocker?.reason !== null)) ||
      !Array.isArray(config.exitCriteria) ||
      (config.status === "blocked" && config.exitCriteria.length !== 8) ||
      (config.status === "qualified" && config.exitCriteria.length !== 0) ||
      config.exitCriteria.length !== new Set(config.exitCriteria).size ||
      config.exitCriteria.some(
        (criterion) => typeof criterion !== "string" || criterion.length < 1,
      ) ||
      releaseCriteria?.distributionChecksumPinned !== true ||
      releaseCriteria?.wrapperJarMatchesDistribution !==
        wrapper?.wrapperJarMatchesDistribution ||
      releaseCriteria?.vendoredRepositoryManifestValid !== true ||
      releaseCriteria?.vendoredSourceIdentitiesPinned !==
        provenance?.reviewState?.sourceIdentitiesPinned ||
      releaseCriteria?.vendoredSourceToBinaryReviewComplete !==
        provenance?.reviewState?.sourceToBinaryReproductionReviewed ||
      releaseCriteria?.strictVerificationMetadataMaterialized !==
        verification?.metadataPresent ||
      releaseCriteria?.strictVerificationMetadataReviewed !==
        verification?.independentlyReviewed ||
      releaseCriteria?.everyResolvedArtifactHasSha256 !==
        verification?.sha256ForEveryResolvedArtifact ||
      releaseCriteria?.productionReleaseLocksMaterialized !==
        locking?.allConfiguredProductionReleaseConfigurationsMaterialized ||
      releaseCriteria?.productionReleaseLocksReviewed !==
        locking?.independentlyReviewed ||
      releaseCriteria?.noLocalOrCompositeSubstitution !== true ||
      releaseCriteria?.idensicTensorflowNamespaceWorkaroundReviewed !==
        namespaceCompatibility?.independentlyReviewed ||
      releaseCriteria?.exposedCredentialRotatedAndRevoked !==
        (credentials?.priorTokenRevoked === true &&
          credentials?.replacementCredentialRotated === true &&
          credentials?.rotationEvidenceReviewed === true) ||
      (config.status === "qualified" &&
        !Object.values(releaseCriteria ?? {}).every(
          (criterion) => criterion === true,
        )),
    "GRADLE_DEPENDENCY_RELEASE_STATE_SCHEMA_INVALID",
  );
  fail(
    credentials?.trackedProjectAuthTokenRemoved !== true ||
      credentials?.secretValueRetainedInEvidence !== false ||
      /^\s*(?:authToken|[^#=\r\n]*(?:password|secret|token|api[_-]?key|access[_-]?key)[^=\r\n]*)\s*=/im.test(
        read("gradle.properties"),
      ),
    "TRACKED_GRADLE_CREDENTIAL_PRESENT",
  );
  releaseBlock(
    provenance?.reviewState?.sourceToBinaryReproductionReviewed !== true ||
      provenance?.reviewState?.licensesAndNoticesReviewed !== true ||
      provenance?.reviewState?.sbomReviewed !== true ||
      provenance?.reviewState?.buildAttestationReviewed !== true ||
      provenance?.repositoryManifest?.independentlyReviewed !== true ||
      releaseCriteria?.repositoryAllowlistReviewed !== true ||
      vendorPolicy?.productionAllowed !== true,
    "VENDORED_DEPENDENCY_REVIEW_NOT_COMPLETE",
  );
  releaseBlock(
    namespaceCompatibility?.independentlyReviewed !== true ||
      namespaceCompatibility?.status !== "scoped-reviewed-workaround",
    "IDENSIC_TENSORFLOW_NAMESPACE_WORKAROUND_NOT_REVIEWED",
  );
  releaseBlock(
    verification?.independentlyReviewed !== true ||
      verification?.status !== "qualified",
    "GRADLE_VERIFICATION_METADATA_NOT_INDEPENDENTLY_REVIEWED",
  );
  releaseBlock(
    locking?.independentlyReviewed !== true || locking?.status !== "qualified",
    "GRADLE_PRODUCTION_LOCKS_NOT_INDEPENDENTLY_REVIEWED",
  );
  releaseBlock(
    credentials?.priorTokenRevoked !== true ||
      credentials?.replacementCredentialRotated !== true ||
      credentials?.rotationEvidenceReviewed !== true,
    "GRADLE_CREDENTIAL_INCIDENT_NOT_CLOSED",
  );
  const dependencySigningReview = authenticateAndroidDependencySigningReview({
    dependencyRecord: configRecord,
  });
  fail(
    dependencySigningReview.state === "invalid",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_AUTHENTICATION_FAILED",
  );
  releaseBlock(
    dependencySigningReview.state !== "admitted",
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_ADMISSION_NOT_QUALIFIED",
  );
  if (dependencySigningReview.state === "admitted") {
    authenticatedAndroidDependencySigningReview =
      dependencySigningReview.receipt;
  }
  return {
    mode: "dependency-preflight",
    inventoryStatus: dependencyFailures.length === 0 ? "STABLE" : "INVALID",
    failures: [...new Set(dependencyFailures)].sort(),
    releaseBlockers: [...new Set(dependencyBlockers)].sort(),
  };
};
const androidDependencyPreflight = validateAndroidDependencyPreflight();
if (dependencyPreflightMode) {
  if (strictRelease || process.argv.length !== 3) {
    androidDependencyPreflight.failures.push(
      "DEPENDENCY_PREFLIGHT_ARGUMENTS_INVALID",
    );
    androidDependencyPreflight.inventoryStatus = "INVALID";
    androidDependencyPreflight.failures = [
      ...new Set(androidDependencyPreflight.failures),
    ].sort();
  }
  process.stdout.write(`${JSON.stringify(androidDependencyPreflight, null, 2)}\n`);
  if (
    androidDependencyPreflight.failures.length > 0 ||
    androidDependencyPreflight.releaseBlockers.length > 0
  ) {
    process.exitCode = 1;
  }
  process.exit();
}
const countKotlinTestMethods = (source) =>
  (source.match(/^\s*@Test\s*$/gm) ?? []).length;
const sourceBetween = (source, startMarker, endMarker) => {
  const start = source.indexOf(startMarker);
  if (start < 0) return "";
  const end = source.indexOf(endMarker, start + startMarker.length);
  if (end < 0) return "";
  return source.slice(start, end);
};
const maskedLexicalCharacter = (character) =>
  character === "\n" || character === "\r" ? character : " ";
const lexKotlinSource = (source) => {
  let withoutComments = "";
  let codeMask = "";
  let index = 0;
  const frames = [{ type: "code", templateDepth: null }];
  const currentFrame = () => frames[frames.length - 1];
  const append = (text, { comment = false, code = false } = {}) => {
    for (let offset = 0; offset < text.length; offset += 1) {
      const character = text[offset];
      withoutComments += comment
        ? maskedLexicalCharacter(character)
        : character;
      codeMask += code ? character : maskedLexicalCharacter(character);
    }
  };
  while (index < source.length) {
    const frame = currentFrame();
    const character = source[index];
    const next = source[index + 1];
    if (frame.type === "line-comment") {
      if (character === "\n" || character === "\r") {
        frames.pop();
        append(character, {
          code:
            currentFrame().type === "code" &&
            currentFrame().templateDepth === null,
        });
      } else {
        append(character, { comment: true });
      }
      index += 1;
      continue;
    }
    if (frame.type === "block-comment") {
      if (character === "/" && next === "*") {
        frame.depth += 1;
        append("/*", { comment: true });
        index += 2;
      } else if (character === "*" && next === "/") {
        frame.depth -= 1;
        append("*/", { comment: true });
        index += 2;
        if (frame.depth === 0) frames.pop();
      } else {
        append(character, { comment: true });
        index += 1;
      }
      continue;
    }
    if (frame.type === "double-quoted" || frame.type === "single-quoted") {
      const closing = frame.type === "double-quoted" ? '"' : "'";
      if (character === "\\" && next !== undefined) {
        append(source.slice(index, index + 2));
        index += 2;
      } else if (
        frame.type === "double-quoted" &&
        character === "$" &&
        next === "{"
      ) {
        append("${");
        index += 2;
        frames.push({ type: "code", templateDepth: 1 });
      } else {
        append(character);
        index += 1;
        if (character === closing) frames.pop();
      }
      continue;
    }
    if (frame.type === "triple-quoted") {
      if (source.startsWith('"""', index)) {
        append('"""');
        index += 3;
        frames.pop();
      } else if (character === "$" && next === "{") {
        append("${");
        index += 2;
        frames.push({ type: "code", templateDepth: 1 });
      } else {
        append(character);
        index += 1;
      }
      continue;
    }

    const isTemplateCode = frame.templateDepth !== null;
    if (character === "/" && next === "/") {
      append("//", { comment: true });
      index += 2;
      frames.push({ type: "line-comment" });
    } else if (character === "/" && next === "*") {
      append("/*", { comment: true });
      index += 2;
      frames.push({ type: "block-comment", depth: 1 });
    } else if (source.startsWith('"""', index)) {
      append('"""');
      index += 3;
      frames.push({ type: "triple-quoted" });
    } else if (character === '"') {
      append(character);
      index += 1;
      frames.push({ type: "double-quoted" });
    } else if (character === "'") {
      append(character);
      index += 1;
      frames.push({ type: "single-quoted" });
    } else {
      append(character, { code: !isTemplateCode });
      index += 1;
      if (isTemplateCode && character === "{") {
        frame.templateDepth += 1;
      } else if (isTemplateCode && character === "}") {
        frame.templateDepth -= 1;
        if (frame.templateDepth === 0) frames.pop();
      }
    }
  }
  if (frames.length === 2 && currentFrame().type === "line-comment") {
    frames.pop();
  }
  if (frames.length !== 1 || currentFrame().templateDepth !== null) {
    throw new Error("INVALID_KOTLIN_LEXICAL_SOURCE");
  }
  return { withoutComments, codeMask };
};
const stripGradleComments = (source) => lexKotlinSource(source).withoutComments;
const sourceBraceDepths = (codeMask) => {
  const depths = new Int32Array(codeMask.length + 1);
  let depth = 0;
  for (let index = 0; index < codeMask.length; index += 1) {
    depths[index] = depth;
    if (codeMask[index] === "{") depth += 1;
    if (codeMask[index] === "}") {
      depth -= 1;
      if (depth < 0) throw new Error("UNBALANCED_KOTLIN_SOURCE_BRACES");
    }
  }
  depths[codeMask.length] = depth;
  if (depth !== 0) throw new Error("UNBALANCED_KOTLIN_SOURCE_BRACES");
  return depths;
};
const findMatchingSourceBrace = (codeMask, openingBraceIndex) => {
  let depth = 0;
  for (let index = openingBraceIndex; index < codeMask.length; index += 1) {
    if (codeMask[index] === "{") depth += 1;
    if (codeMask[index] === "}") {
      depth -= 1;
      if (depth === 0) return index;
      if (depth < 0) return -1;
    }
  }
  return -1;
};
const extractUniqueBracedBlock = (source, marker) => {
  const codeMask = lexKotlinSource(source).codeMask;
  const depths = sourceBraceDepths(codeMask);
  const flags = marker.flags.includes("g") ? marker.flags : `${marker.flags}g`;
  const candidates = [
    ...source.matchAll(new RegExp(marker.source, flags)),
  ].flatMap((match) => {
    let openingBraceIndex = match.index + match[0].length;
    while (/\s/.test(source[openingBraceIndex] ?? "")) {
      openingBraceIndex += 1;
    }
    const firstCodeIndex = match.index + (match[0].search(/\S/) < 0
      ? 0
      : match[0].search(/\S/));
    return source[openingBraceIndex] === "{" &&
      codeMask[openingBraceIndex] === "{" &&
      codeMask[firstCodeIndex] === source[firstCodeIndex] &&
      depths[firstCodeIndex] === 0
      ? [openingBraceIndex]
      : [];
  });
  if (candidates.length !== 1) return null;
  const openingBraceIndex = candidates[0];
  const closingBraceIndex = findMatchingSourceBrace(codeMask, openingBraceIndex);
  if (closingBraceIndex < 0) return null;
  return source.slice(openingBraceIndex + 1, closingBraceIndex);
};
const codeMatches = (source, pattern, { direct = false } = {}) => {
  const { codeMask } = lexKotlinSource(source);
  const depths = direct ? sourceBraceDepths(codeMask) : null;
  const flags = pattern.flags.includes("g") ? pattern.flags : `${pattern.flags}g`;
  return [...codeMask.matchAll(new RegExp(pattern.source, flags))].filter(
    (match) => !direct || depths[match.index] === 0,
  );
};
const codeMatchCount = (source, pattern, options) =>
  codeMatches(source, pattern, options).length;
const extractCodeBracedBlocks = (
  source,
  marker,
  { directDepth = null } = {},
) => {
  const { codeMask } = lexKotlinSource(source);
  const depths = directDepth === null ? null : sourceBraceDepths(codeMask);
  const flags = marker.flags.includes("g") ? marker.flags : `${marker.flags}g`;
  return [...codeMask.matchAll(new RegExp(marker.source, flags))].flatMap(
    (match) => {
      const relativeCodeIndex = match[0].search(/\S/);
      if (relativeCodeIndex < 0) return [];
      const firstCodeIndex = match.index + relativeCodeIndex;
      if (depths !== null && depths[firstCodeIndex] !== directDepth) return [];
      let openingBraceIndex = match.index + match[0].length;
      while (/\s/.test(codeMask[openingBraceIndex] ?? "")) {
        openingBraceIndex += 1;
      }
      if (codeMask[openingBraceIndex] !== "{") return [];
      const closingBraceIndex = findMatchingSourceBrace(
        codeMask,
        openingBraceIndex,
      );
      return closingBraceIndex < 0
        ? []
        : [source.slice(openingBraceIndex + 1, closingBraceIndex)];
    },
  );
};
const extractUniqueCodeBracedBlock = (source, marker, options) => {
  const blocks = extractCodeBracedBlocks(source, marker, options);
  return blocks.length === 1 ? blocks[0] : null;
};
const lexicalMatches = (source, pattern, { direct = false } = {}) => {
  const { codeMask } = lexKotlinSource(source);
  const depths = direct ? sourceBraceDepths(codeMask) : null;
  const flags = pattern.flags.includes("g") ? pattern.flags : `${pattern.flags}g`;
  return [...source.matchAll(new RegExp(pattern.source, flags))].filter((match) => {
    const relativeCodeIndex = match[0].search(/\S/);
    if (relativeCodeIndex < 0) return false;
    const codeIndex = match.index + relativeCodeIndex;
    if (codeMask[codeIndex] !== source[codeIndex]) return false;
    return !direct || depths[codeIndex] === 0;
  });
};
const hasSingleDirectLexicalMatch = (source, pattern) =>
  lexicalMatches(source, pattern, { direct: true }).length === 1;
const hasLexicalMatch = (source, pattern) =>
  lexicalMatches(source, pattern).length > 0;
const lexicalMatchCount = (source, pattern) =>
  lexicalMatches(source, pattern).length;
const sourceMatchCount = (source, pattern) => {
  const flags = pattern.flags.includes("g") ? pattern.flags : `${pattern.flags}g`;
  return [...source.matchAll(new RegExp(pattern.source, flags))].length;
};
const collectGradleBuildSources = (directory) => {
  const sources = [];
  for (const name of readdirSync(directory)) {
    if ([".git", ".gradle", "build", "node_modules"].includes(name)) continue;
    const path = join(directory, name);
    const stat = lstatSync(path);
    if (stat.isSymbolicLink()) continue;
    if (stat.isDirectory()) {
      sources.push(...collectGradleBuildSources(path));
    } else if (
      stat.isFile() &&
      (path.endsWith(".gradle") ||
        path.endsWith(".gradle.kts") ||
        path.endsWith(".toml"))
    ) {
      sources.push({
        path: relative(root, path),
        source: stripGradleComments(readFileSync(path, "utf8")),
      });
    }
  }
  return sources;
};
const gradleBuildSources = collectGradleBuildSources(root);

const appBuild = read("app/build.gradle.kts");
const rootBuildForRollout = read("build.gradle.kts");
const productionRolloutValidator = read(
  "scripts/verify-production-rollout.mjs",
);
const productionRolloutStrictEvidence = read(
  "scripts/lib/strict-evidence.mjs",
);
const productionRolloutCandidateProvenance = read(
  "scripts/lib/qualified-candidate-provenance.mjs",
);
const productionRolloutPiReceipt = read(
  "scripts/lib/production-pi-receipt.mjs",
);
const productionPiCandidateRequest = read(
  "scripts/create-production-pi-candidate-controller-request.mjs",
);
const productionPiCandidateVerifier = read(
  "scripts/verify-production-pi-candidate-receipt.mjs",
);
const productionPiV3ContractHarness = read(
  "scripts/test-production-pi-receipt-v3.mjs",
);
const productionRolloutV3Contract = read(
  "scripts/lib/production-rollout-v3-contract.mjs",
);
const productionRolloutV3ContractHarness = read(
  "scripts/test-production-rollout-v3-contract.mjs",
);
const productionRolloutChainLinkExtractor = read(
  "scripts/extract-production-rollout-prior-link.mjs",
);
const productionRolloutControllerRequest = read(
  "scripts/create-production-rollout-controller-request.mjs",
);
const productionRolloutTemplate = json(
  "docs/modernization/qualification/production-rollout-advancement.blocked.json",
);
const productionRolloutCandidateTemplate = json(
  "docs/modernization/qualification/production-rollout-candidate.blocked.json",
);
const productionRolloutDocumentation = read(
  "docs/modernization/qualification/production-rollout-README.md",
);
const productionRolloutTrustStrictRecord = readStrictJsonFile(
  join(root, "config/production-rollout-trust.json"),
  64 * 1024,
);
const productionRolloutTrust = productionRolloutTrustStrictRecord?.value;
const productionReleaseWorkflowForRollout = read(
  ".github/workflows/production_release_qualification.yml",
);
const jenkinsPipelineForRollout = read("Jenkinsfile");
const gitignore = read(".gitignore");
const soraApplication = read(
  "app/src/main/java/jp/co/soramitsu/sora/SoraApp.kt",
);
const database = read("core_db/src/main/java/jp/co/soramitsu/core_db/AppDatabase.kt");
const walletUpgradeBackup = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/WalletUpgradeBackup.kt",
);
const migrationManager = read(
  "app/src/main/java/jp/co/soramitsu/sora/splash/domain/MigrationManager.kt",
);
const migrationManagerSafetyTest = read(
  "app/src/test/java/jp/co/soramitsu/sora/splash/domain/MigrationManagerSafetyTest.kt",
);
const migrationSr25519CryptoBlock = extractUniqueCodeBracedBlock(
  migrationManager,
  /\bclass\s+MigrationSr25519Crypto\s+@Inject\s+constructor\s*\(\s*\)\s*/,
  { directDepth: 0 },
);
const migrationManagerClassBlock = extractUniqueCodeBracedBlock(
  migrationManager,
  /\bclass\s+MigrationManager\s+@Inject\s+constructor\s*\([^)]*\)\s*/,
  { directDepth: 0 },
);
const migrationSr25519ProductionBoundaryGate =
  migrationSr25519CryptoBlock !== null &&
  migrationSr25519CryptoBlock.includes("SubstrateKeypairFactory.generate(") &&
  migrationSr25519CryptoBlock.includes("SignWrapper.sign(") &&
  migrationSr25519CryptoBlock.includes("Sr25519JNI.verify(") &&
  migrationSr25519CryptoBlock.includes("signature.fill(0)");
const migrationSr25519ManagerWiringGate =
  migrationManagerClassBlock !== null &&
  migrationManager.includes(
    "private val migrationSr25519Crypto: MigrationSr25519Crypto",
  ) &&
  migrationManagerClassBlock.includes(
    "migrationSr25519Crypto.generateKeypair(seed)",
  ) &&
  (migrationManagerClassBlock.match(
    /migrationSr25519Crypto\.signAndVerify\(/g,
  ) ?? []).length === 2;
const migrationSr25519HostSeamGate =
  migrationManagerSafetyTest.includes("testMigrationSr25519Crypto") &&
  migrationManagerSafetyTest.includes(
    "migrationSr25519Crypto = testMigrationSr25519Crypto",
  ) &&
  !migrationManagerSafetyTest.includes("Sr25519JNI") &&
  !migrationManagerSafetyTest.includes("SubstrateKeypairFactory");
const migrationSr25519BoundaryGate =
  migrationSr25519ProductionBoundaryGate &&
  migrationSr25519ManagerWiringGate &&
  migrationSr25519HostSeamGate;
const migrationManagerStartLockedBlock = migrationManagerClassBlock === null
  ? null
  : extractUniqueCodeBracedBlock(
      migrationManagerClassBlock,
      /\bprivate\s+suspend\s+fun\s+startLocked\s*\([^)]*\)\s*:\s*Boolean\s*/,
      { directDepth: 0 },
    );
const migrationManagerContinuityHelperBlock = migrationManagerClassBlock === null
  ? null
  : extractUniqueCodeBracedBlock(
      migrationManagerClassBlock,
      /\bprivate\s+suspend\s+fun\s+requireActiveSecretSourceContinuity\s*\([^)]*\)\s*/,
      { directDepth: 0 },
    );
const activeSecretSourceContinuityCall =
  /\brequireActiveSecretSourceContinuity\s*\(\s*preparedWallets\s*\)/;
const migrationManagerTransactionBlocks = migrationManagerStartLockedBlock === null
  ? []
  : extractCodeBracedBlocks(
      migrationManagerStartLockedBlock,
      /\bdatabase\s*\.\s*withTransaction\s*/,
    );
const activeSecretSourceContinuityTransactions =
  migrationManagerTransactionBlocks.filter(
    (block) =>
      codeMatchCount(block, activeSecretSourceContinuityCall, { direct: true }) ===
        1 &&
      codeMatchCount(
        block,
        /^\s*requireActiveSecretSourceContinuity\s*\(\s*preparedWallets\s*\)/,
      ) === 1,
  );
const activeSecretSourceContinuityTransaction =
  activeSecretSourceContinuityTransactions.length === 1
    ? activeSecretSourceContinuityTransactions[0]
    : null;
const activeSecretSourceContinuityMatches =
  activeSecretSourceContinuityTransaction === null
    ? []
    : codeMatches(
        activeSecretSourceContinuityTransaction,
        activeSecretSourceContinuityCall,
        { direct: true },
      );
const activeSecretSourceWalletUpserts =
  activeSecretSourceContinuityTransaction === null
    ? []
    : codeMatches(
        activeSecretSourceContinuityTransaction,
        /\bdatabase\s*\.\s*walletIdentityDao\s*\(\s*\)\s*\.\s*upsertWallet\s*\(/,
      );
const activeSecretSourceNetworkUpserts =
  activeSecretSourceContinuityTransaction === null
    ? []
    : codeMatches(
        activeSecretSourceContinuityTransaction,
        /\bdatabase\s*\.\s*walletIdentityDao\s*\(\s*\)\s*\.\s*upsertNetworkAccounts\s*\(/,
      );
const activeSecretSourceComparisonMatches =
  migrationManagerContinuityHelperBlock === null
    ? []
    : codeMatches(
        migrationManagerContinuityHelperBlock,
        /\bpreparedByWalletId\s*\[\s*active\s*\.\s*walletId\s*\]\s*\?\s*\.\s*identity\s*\?\s*\.\s*secretSource\s*!=\s*active\s*\.\s*secretSource/,
      );
const activeSecretSourceThrows = migrationManagerContinuityHelperBlock === null
  ? []
  : codeMatches(
      migrationManagerContinuityHelperBlock,
      /\bthrow\s+MigrationVerificationException\s*\(/,
    );
const activeSecretSourceReturns = migrationManagerContinuityHelperBlock === null
  ? []
  : codeMatches(migrationManagerContinuityHelperBlock, /\breturn\b/);
const activeSecretSourceContinuityGate =
  migrationManagerStartLockedBlock !== null &&
  migrationManagerContinuityHelperBlock !== null &&
  codeMatchCount(migrationManager, activeSecretSourceContinuityCall) === 1 &&
  activeSecretSourceContinuityMatches.length === 1 &&
  activeSecretSourceWalletUpserts.length === 1 &&
  activeSecretSourceNetworkUpserts.length === 1 &&
  activeSecretSourceContinuityMatches[0].index <
    activeSecretSourceWalletUpserts[0].index &&
  activeSecretSourceContinuityMatches[0].index <
    activeSecretSourceNetworkUpserts[0].index &&
  codeMatchCount(
    migrationManagerContinuityHelperBlock,
    /\bval\s+preparedByWalletId\s*=\s*preparedWallets\s*\.\s*associateBy\s*\{/,
  ) === 1 &&
  hasSingleDirectLexicalMatch(
    migrationManagerContinuityHelperBlock,
    /\bdatabase\s*\.\s*walletIdentityDao\s*\(\s*\)\s*\.\s*getWallets\s*\(\s*\)\s*\.\s*filter\s*\{\s*it\s*\.\s*migrationState\s*==\s*"VERIFIED"\s*\}/,
  ) &&
  activeSecretSourceComparisonMatches.length === 1 &&
  activeSecretSourceThrows.length === 1 &&
  activeSecretSourceComparisonMatches[0].index < activeSecretSourceThrows[0].index &&
  activeSecretSourceReturns.every(
    (match) => activeSecretSourceComparisonMatches[0].index < match.index,
  ) &&
  hasLexicalMatch(
    migrationManagerContinuityHelperBlock,
    /throw\s+MigrationVerificationException\s*\(\s*"ACTIVE_SECRET_SOURCE_MISMATCH"\s*\)/,
  );
const migrationManagerSafetyTestClassBlock = extractUniqueCodeBracedBlock(
  migrationManagerSafetyTest,
  /\bclass\s+MigrationManagerSafetyTest\s*/,
  { directDepth: 0 },
);
const legacySecretToRawSeedTestBlock = migrationManagerSafetyTestClassBlock === null
  ? null
  : extractUniqueCodeBracedBlock(
      migrationManagerSafetyTestClassBlock,
      /@Test\s+fun\s+`verified legacy secret cannot be reclassified as raw seed during migration`\s*\(\s*\)\s*=\s*runTest\s*/,
      { directDepth: 0 },
    );
const rawSeedToLegacySecretTestBlock = migrationManagerSafetyTestClassBlock === null
  ? null
  : extractUniqueCodeBracedBlock(
      migrationManagerSafetyTestClassBlock,
      /@Test\s+fun\s+`verified raw seed cannot be reclassified as legacy secret during migration`\s*\(\s*\)\s*=\s*runTest\s*/,
      { directDepth: 0 },
    );
const activeSecretSourceRegressionHelperBlock =
  migrationManagerSafetyTestClassBlock === null
    ? null
    : extractUniqueCodeBracedBlock(
        migrationManagerSafetyTestClassBlock,
        /\bprivate\s+suspend\s+fun\s+assertActiveSecretSourceReclassificationFailsClosed\s*\([^)]*\)\s*/,
        { directDepth: 0 },
      );
const zeroMutationVerificationBlocks =
  activeSecretSourceRegressionHelperBlock === null
    ? []
    : extractCodeBracedBlocks(
        activeSecretSourceRegressionHelperBlock,
        /\bcoVerify\s*\(\s*exactly\s*=\s*0\s*\)\s*/,
        { directDepth: 0 },
      );
const recoveryMarkerVerificationBlocks =
  activeSecretSourceRegressionHelperBlock === null
    ? []
    : extractCodeBracedBlocks(
        activeSecretSourceRegressionHelperBlock,
        /\bcoVerify\s*\(\s*exactly\s*=\s*1\s*\)\s*/,
        { directDepth: 0 },
      );
const hasUniqueVerificationBlock = (blocks, pattern) =>
  blocks.filter((block) => codeMatchCount(block, pattern) === 1).length === 1;
const activeSecretSourceRollbackRegression =
  legacySecretToRawSeedTestBlock !== null &&
  rawSeedToLegacySecretTestBlock !== null &&
  activeSecretSourceRegressionHelperBlock !== null &&
  hasSingleDirectLexicalMatch(
    legacySecretToRawSeedTestBlock,
    /assertActiveSecretSourceReclassificationFailsClosed\s*\(\s*activeSecretSource\s*=\s*"LEGACY_SECRET"\s*,\s*retainedSeed\s*=\s*RAW_SEED\s*,?\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    rawSeedToLegacySecretTestBlock,
    /assertActiveSecretSourceReclassificationFailsClosed\s*\(\s*activeSecretSource\s*=\s*"RAW_SEED"\s*,\s*retainedSeed\s*=\s*""\s*,?\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    activeSecretSourceRegressionHelperBlock,
    /assertFalse\s*\(\s*manager\s*\.\s*start\s*\(\s*\)\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    activeSecretSourceRegressionHelperBlock,
    /assertEquals\s*\(\s*"VERIFYING"\s*,\s*verifyingJournal\s*\.\s*captured\s*\.\s*state\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    activeSecretSourceRegressionHelperBlock,
    /assertEquals\s*\(\s*0\s*,\s*verifyingJournal\s*\.\s*captured\s*\.\s*verifiedAccountCount\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    activeSecretSourceRegressionHelperBlock,
    /assertEquals\s*\(\s*"RECOVERY_REQUIRED"\s*,\s*recoveryJournal\s*\.\s*captured\s*\.\s*state\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    activeSecretSourceRegressionHelperBlock,
    /assertEquals\s*\(\s*"ACTIVE_SECRET_SOURCE_MISMATCH"\s*,\s*recoveryJournal\s*\.\s*captured\s*\.\s*failureCode\s*,?\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    activeSecretSourceRegressionHelperBlock,
    /assertEquals\s*\(\s*0\s*,\s*recoveryJournal\s*\.\s*captured\s*\.\s*verifiedAccountCount\s*\)/,
  ) &&
  hasSingleDirectLexicalMatch(
    activeSecretSourceRegressionHelperBlock,
    /assertEquals\s*\(\s*WalletRecoveryCapabilityGate\s*\.\s*Mode\s*\.\s*RECOVERY_INSPECTED\s*,\s*WalletRecoveryCapabilityGate\s*\.\s*mode\s*\(\s*\)\s*,?\s*\)/,
  ) &&
  hasUniqueVerificationBlock(
    recoveryMarkerVerificationBlocks,
    /\bwalletDao\s*\.\s*upsertMigrationJournal\s*\(/,
  ) &&
  hasUniqueVerificationBlock(
    recoveryMarkerVerificationBlocks,
    /\bwalletDao\s*\.\s*recordMigrationFailure\s*\(/,
  ) &&
  hasUniqueVerificationBlock(
    recoveryMarkerVerificationBlocks,
    /\bwalletDao\s*\.\s*markWalletsRecoveryRequired\s*\(/,
  ) &&
  hasUniqueVerificationBlock(
    zeroMutationVerificationBlocks,
    /\bwalletDao\s*\.\s*upsertWallet\s*\(/,
  ) &&
  hasUniqueVerificationBlock(
    zeroMutationVerificationBlocks,
    /\bwalletDao\s*\.\s*upsertNetworkAccounts\s*\(/,
  ) &&
  hasUniqueVerificationBlock(
    zeroMutationVerificationBlocks,
    /\bwalletDao\s*\.\s*activateMigrationJournal\s*\(/,
  );
const migrationManagerProductionPathQualificationTest = read(
  "app/src/androidTestQualification/java/jp/co/soramitsu/sora/splash/domain/MigrationManagerProductionPathQualificationTest.kt",
);
const migrationPinnedNativeVectorGate =
  migrationManagerProductionPathQualificationTest.includes(
    "private fun assertPinnedSora2NativeVector()",
  ) &&
  /resetIsolatedQualificationState\(\)\s*assertPinnedSora2NativeVector\(\)/.test(
    migrationManagerProductionPathQualificationTest,
  ) &&
  migrationManagerProductionPathQualificationTest.includes(
    "PINNED_SORA2_ADDRESS",
  ) &&
  migrationManagerProductionPathQualificationTest.includes(
    "Sr25519JNI.verify(signature, challenge, keypair.publicKey)",
  ) &&
  migrationManagerProductionPathQualificationTest.includes(
    "44a996beb1eef7bdcab976ab6d2ca26104834164ecf28fb375600576fcc6eb0f",
  );
const migrationManagerProductionPathQualificationScript = read(
  "scripts/run-migration-manager-production-path-qualification.sh",
);
const migrationManagerProductionPathQualificationManifest = read(
  "app/src/qualification/AndroidManifest.xml",
);
const migrationManagerProductionPathQualificationApplication = read(
  "app/src/qualification/java/jp/co/soramitsu/sora/qualification/MigrationQualificationApplication.kt",
);
const migrationManagerProductionPathQualifyBody = sourceBetween(
  migrationManagerProductionPathQualificationTest,
  "private suspend fun qualify(",
  "private suspend fun persistFixture(",
);
const sora2AddressCodec = read(
  "common/src/main/java/jp/co/soramitsu/common/account/Sora2AddressCodec.kt",
);
const sora2AddressCodecTest = read(
  "common/src/test/java/jp/co/soramitsu/common/account/Sora2AddressCodecTest.kt",
);
const splashInteractor = read(
  "app/src/main/java/jp/co/soramitsu/sora/splash/domain/SplashInteractor.kt",
);
const splashActivity = read(
  "app/src/main/java/jp/co/soramitsu/sora/splash/presentation/SplashActivity.kt",
);
const splashViewModel = read(
  "app/src/main/java/jp/co/soramitsu/sora/splash/presentation/SplashViewModel.kt",
);
const splashViewModelTest = read(
  "app/src/test/java/jp/co/soramitsu/sora/splash/presentation/SplashViewModelTest.kt",
);
const splashInteractorTest = read(
  "app/src/test/java/jp/co/soramitsu/sora/splash/domain/SplashInteractorTest.kt",
);
const retainedSchemaMigrationTest = read(
  "core_db/src/androidTest/java/jp/co/soramitsu/core_db/WalletIdentityMigration75Test.kt",
);
const room74SchemaFixture = read(
  "core_db/src/androidTest/java/jp/co/soramitsu/core_db/AppDatabaseV74SchemaFixture.kt",
);
const room75SchemaFixture = read(
  "core_db/src/androidTest/java/jp/co/soramitsu/core_db/AppDatabaseV75SchemaFixture.kt",
);
const coreDbBuild = read("core_db/build.gradle.kts");
const walletUpgradeBackupTest = read(
  "core_db/src/androidTest/java/jp/co/soramitsu/core_db/WalletUpgradeBackupTest.kt",
);
const irohaKeyDerivationTest = read(
  "common/src/test/java/jp/co/soramitsu/common/nexus/IrohaKeyDerivationTest.kt",
);
const walletIdentityMigration = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/WalletIdentityMigration74.kt",
);
const walletDeletionMigration = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/WalletDeletionMigration75.kt",
);
const sora2PendingSubmissionMigration = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/Sora2PendingSubmissionMigration76.kt",
);
const pendingNetworkTransactionChainMigration = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/PendingNetworkTransactionChainMigration77.kt",
);
const pendingNetworkTransactionModel = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/model/PendingNetworkTransactionLocal.kt",
);
const sora2PendingSubmissionModel = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/model/Sora2PendingSubmissionLocal.kt",
);
const walletPreferenceKeys = read(
  "common/src/main/java/jp/co/soramitsu/common/data/WalletPreferenceKeys.kt",
);
const walletMutationCoordinator = read(
  "common/src/main/java/jp/co/soramitsu/common/account/WalletMutationCoordinator.kt",
);
const walletMutationCoordinatorTest = read(
  "common/src/test/java/jp/co/soramitsu/common/account/WalletMutationCoordinatorTest.kt",
);
const walletRecoveryGate = read(
  "common/src/main/java/jp/co/soramitsu/common/account/WalletRecoveryCapabilityGate.kt",
);
const walletRecoveryGateTest = read(
  "common/src/test/java/jp/co/soramitsu/common/account/WalletRecoveryCapabilityGateTest.kt",
);
const encryptionUtil = read(
  "common/src/main/java/jp/co/soramitsu/common/util/EncryptionUtil.kt",
);
const soraPreferences = read(
  "common/src/main/java/jp/co/soramitsu/common/data/SoraPreferences.kt",
);
const prefsUserDatasource = read(
  "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/datasource/PrefsUserDatasource.kt",
);
const appBuildSource = stripGradleComments(appBuild);
const androidBuildBlock = extractUniqueBracedBlock(
  appBuildSource,
  /^\s*android\s*/m,
);
const androidDefaultConfigBlock = androidBuildBlock === null
  ? null
  : extractUniqueBracedBlock(androidBuildBlock, /^\s*defaultConfig\s*/m);
const androidBuildTypesBlock = androidBuildBlock === null
  ? null
  : extractUniqueBracedBlock(androidBuildBlock, /^\s*buildTypes\s*/m);
const androidReleaseBuildTypeBlock = androidBuildTypesBlock === null
  ? null
  : extractUniqueBracedBlock(androidBuildTypesBlock, /^\s*release\s*/m);
const androidDebugBuildTypeBlock = androidBuildTypesBlock === null
  ? null
  : extractUniqueBracedBlock(androidBuildTypesBlock, /^\s*debug\s*/m);
const androidProductFlavorsBlock = androidBuildBlock === null
  ? null
  : extractUniqueBracedBlock(androidBuildBlock, /^\s*productFlavors\s*/m);
const androidProductionFlavorBlock = androidProductFlavorsBlock === null
  ? null
  : extractUniqueBracedBlock(
    androidProductFlavorsBlock,
    /^\s*create\(\s*"production"\s*\)\s*/m,
  );
const androidQualificationFlavorBlock = androidProductFlavorsBlock === null
  ? null
  : extractUniqueBracedBlock(
    androidProductFlavorsBlock,
    /^\s*create\(\s*"qualification"\s*\)\s*/m,
  );
const databaseSource = stripGradleComments(database);
const appDatabaseClassBlock = extractUniqueBracedBlock(
  databaseSource,
  /^\s*abstract\s+class\s+AppDatabase\s*:\s*RoomDatabase\(\)\s*/m,
);
const appDatabaseCompanionBlock = appDatabaseClassBlock === null
  ? null
  : extractUniqueBracedBlock(appDatabaseClassBlock, /^\s*companion\s+object\s*/m);
const appDatabaseBuilderBlock = appDatabaseCompanionBlock === null
  ? null
  : extractUniqueBracedBlock(
    appDatabaseCompanionBlock,
    /^\s*private\s+fun\s+buildDatabase\s*\(\s*context\s*:\s*Context\s*\)\s*:\s*AppDatabase\s*/m,
  );
const walletUpgradeBackupBlock = extractUniqueBracedBlock(
  stripGradleComments(walletUpgradeBackup),
  /^\s*object\s+WalletUpgradeBackup\s*/m,
);
const encryptionUtilClassBlock = extractUniqueBracedBlock(
  stripGradleComments(encryptionUtil),
  /^\s*class\s+EncryptionUtil\s*\(\s*private\s+val\s+context\s*:\s*Context\s*\)\s*/m,
);
const encryptionUtilCompanionBlock = encryptionUtilClassBlock === null
  ? null
  : extractUniqueBracedBlock(encryptionUtilClassBlock, /^\s*companion\s+object\s*/m);
const encryptionPreferenceKeyBlock = encryptionUtilClassBlock === null
  ? null
  : extractUniqueBracedBlock(
    encryptionUtilClassBlock,
    /^\s*private\s+fun\s+getPreferenceAesKey\s*\(\s*\)\s*:\s*Key\s*/m,
  );
const encryptionInitKeystoreBlock = encryptionUtilClassBlock === null
  ? null
  : extractUniqueBracedBlock(
    encryptionUtilClassBlock,
    /^\s*private\s+fun\s+initKeystore\s*\(\s*\)\s*/m,
  );
const encryptionCreateKeysBlock = encryptionUtilClassBlock === null
  ? null
  : extractUniqueBracedBlock(
    encryptionUtilClassBlock,
    /^\s*private\s+fun\s+createKeys\s*\(\s*\)\s*/m,
  );
const encryptionWalletMaterialBlock = encryptionUtilClassBlock === null
  ? null
  : extractUniqueBracedBlock(
    encryptionUtilClassBlock,
    /^\s*private\s+fun\s+hasExistingWalletMaterial\s*\(\s*\)\s*:\s*Boolean\s*/m,
  );
const soraPreferencesClassBlock = extractUniqueBracedBlock(
  stripGradleComments(soraPreferences),
  /^\s*class\s+SoraPreferences\s*\(\s*context\s*:\s*Context\s*,?\s*\)\s*/m,
);
const soraPreferencesCompanionBlock = soraPreferencesClassBlock === null
  ? null
  : extractUniqueBracedBlock(soraPreferencesClassBlock, /^\s*companion\s+object\s*/m);
const walletPreferenceKeysBlock = extractUniqueBracedBlock(
  stripGradleComments(walletPreferenceKeys),
  /^\s*object\s+WalletPreferenceKeys\s*/m,
);
const prefsUserDatasourceClassBlock = extractUniqueBracedBlock(
  stripGradleComments(prefsUserDatasource),
  /^\s*class\s+PrefsUserDatasource\s*\(\s*private\s+val\s+soraPreferences\s*:\s*SoraPreferences\s*,\s*private\s+val\s+encryptedPreferences\s*:\s*EncryptedPreferences\s*\)\s*:\s*UserDatasource\s*/m,
);
const prefsUserDatasourceCompanionBlock = prefsUserDatasourceClassBlock === null
  ? null
  : extractUniqueBracedBlock(
    prefsUserDatasourceClassBlock,
    /^\s*companion\s+object\s*/m,
  );
const nodeManager = read(
  "feature_select_node_impl/src/main/java/jp/co/soramitsu/feature_select_node_impl/NodeManagerImpl.kt",
);
const userRepository = read(
  "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/UserRepositoryImpl.kt",
);
const accountFeatureModule = read(
  "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/di/AccountFeatureModule.kt",
);
const userRepositoryTest = read(
  "feature_account_impl/src/test/java/jp/co/soramitsu/feature_account_impl/data/repository/UserRepositoryTest.kt",
);
const userRepositorySr25519CryptoBlock = extractUniqueCodeBracedBlock(
  userRepository,
  /@Singleton\s+class\s+UserRepositorySr25519Crypto\s+@Inject\s+constructor\s*\(\s*\)\s*/,
  { directDepth: 0 },
);
const userRepositorySr25519SignAndVerifyBlock =
  userRepositorySr25519CryptoBlock === null
    ? null
    : extractUniqueCodeBracedBlock(
        userRepositorySr25519CryptoBlock,
        /\bfun\s+signAndVerify\s*\([^)]*\)\s*:\s*Boolean\s*/,
        { directDepth: 0 },
      );
const userRepositoryImplClassBlock = extractUniqueCodeBracedBlock(
  userRepository,
  /\bclass\s+UserRepositoryImpl\s*\([^)]*\)\s*:\s*UserRepository\s*/,
  { directDepth: 0 },
);
const userRepositorySr25519ProductionBoundaryGate =
  userRepositorySr25519CryptoBlock !== null &&
  /fun\s+generateKeypair\s*\(\s*seed\s*:\s*ByteArray\s*\)\s*:\s*Sr25519Keypair\?\s*=\s*SubstrateKeypairFactory\.generate\([\s\S]*?SubstrateOptionsProvider\.encryptionType\s*,\s*seed\s*,?\s*\)\s*as\?\s*Sr25519Keypair/.test(
    userRepositorySr25519CryptoBlock,
  ) &&
  userRepositorySr25519SignAndVerifyBlock !== null &&
  userRepositorySr25519SignAndVerifyBlock.includes("SignWrapper.sign(") &&
  userRepositorySr25519SignAndVerifyBlock.includes(
    "MultiChainEncryption.Substrate(",
  ) &&
  userRepositorySr25519SignAndVerifyBlock.includes(
    "Sr25519JNI.verify(signature, challenge, expectedPublicKey)",
  ) &&
  /finally\s*\{\s*signature\.fill\(0\)\s*\}/.test(
    userRepositorySr25519SignAndVerifyBlock,
  );
const userRepositorySr25519RepositoryWiringGate =
  userRepositoryImplClassBlock !== null &&
  userRepository.includes(
    "private val userRepositorySr25519Crypto: UserRepositorySr25519Crypto",
  ) &&
  (userRepositoryImplClassBlock.match(
    /userRepositorySr25519Crypto\.generateKeypair\(/g,
  ) ?? []).length === 1 &&
  (userRepositoryImplClassBlock.match(
    /userRepositorySr25519Crypto\.signAndVerify\(/g,
  ) ?? []).length === 1 &&
  userRepositoryImplClassBlock.includes(
    "verifyStoredSigningKey(keyPair, mnemonic, rawSeed)",
  ) &&
  userRepositoryImplClassBlock.includes(
    "verifyLegacySecretSigningKey(keyPair)",
  ) &&
  !userRepositoryImplClassBlock.includes("SubstrateKeypairFactory") &&
  !userRepositoryImplClassBlock.includes("SignWrapper") &&
  !userRepositoryImplClassBlock.includes("Sr25519JNI");
const userRepositorySr25519ModuleWiringGate =
  /fun\s+provideUserRepository\s*\([\s\S]*?userRepositorySr25519Crypto\s*:\s*UserRepositorySr25519Crypto\s*,?[\s\S]*?\)\s*:\s*UserRepository\s*=\s*UserRepositoryImpl\s*\([\s\S]*?userRepositorySr25519Crypto\s*,?\s*\)/.test(
    accountFeatureModule,
  );
const userRepositorySr25519HostSeamGate =
  userRepositoryTest.includes(
    "lateinit var userRepositorySr25519Crypto: UserRepositorySr25519Crypto",
  ) &&
  userRepositoryTest.includes(
    "userRepositorySr25519Crypto.generateKeypair(any())",
  ) &&
  userRepositoryTest.includes(
    "userRepositorySr25519Crypto.signAndVerify(any(), any(), any())",
  ) &&
  !userRepositoryTest.includes("SubstrateKeypairFactory") &&
  !userRepositoryTest.includes("SignWrapper") &&
  !userRepositoryTest.includes("Sr25519JNI");
const userRepositorySr25519QualificationGate =
  migrationManagerProductionPathQualificationTest.includes(
    "userRepositorySr25519Crypto = UserRepositorySr25519Crypto()",
  ) &&
  migrationManagerProductionPathQualificationTest.includes(
    "migrationSr25519Crypto = MigrationSr25519Crypto()",
  );
const userRepositorySr25519BoundaryGate =
  userRepositorySr25519ProductionBoundaryGate &&
  userRepositorySr25519RepositoryWiringGate &&
  userRepositorySr25519ModuleWiringGate &&
  userRepositorySr25519HostSeamGate &&
  userRepositorySr25519QualificationGate;
const credentialsDatasource = read(
  "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/datasource/PrefsCredentialsDatasource.kt",
);
const encryptedWalletMigrationStorageTest = read(
  "feature_account_impl/src/androidTest/java/jp/co/soramitsu/feature_account_impl/data/repository/datasource/EncryptedWalletMigrationStorageTest.kt",
);
const encryptedWalletQualificationScript = read(
  "scripts/run-encrypted-wallet-upgrade-qualification.sh",
);
const credentialsRepository = read(
  "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/CredentialsRepositoryImpl.kt",
);
const credentialsRepositoryTest = read(
  "feature_account_impl/src/test/java/jp/co/soramitsu/feature_account_impl/data/repository/CredentialsRepositoryTest.kt",
);
const walletInteractor = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/domain/WalletInteractorImpl.kt",
);
const walletInteractorTest = read(
  "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/domain/WalletInteractorTest.kt",
);
const walletRepository = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/repository/WalletRepositoryImpl.kt",
);
const nexusRecoveryCoordinator = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusTransactionCoordinator.kt",
);
const piIndexerClient = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PolkaswapIndexerClient.kt",
);
const piIndexerModels = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerModels.kt",
);
const piCanonicalIntegerLexemeSerializer = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiCanonicalIntegerLexemeSerializer.kt",
);
const piCanonicalIntegerLexemeSerializerTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiCanonicalIntegerLexemeSerializerTest.kt",
);
const piHealthIntegerFields = [
  "latestIndexedBlock",
  "latestIndexedAt",
  "workerLatestFinalizedBlock",
  "workerLatestIndexedBlock",
  "workerLag",
  "workerLastSuccessfulIndexTimestamp",
  "workerLastErrorTimestamp",
];
const piIndexerClientTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PolkaswapIndexerClientTest.kt",
);
const piIndexerOfflineFallbackPolicy = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerOfflineFallbackPolicy.kt",
);
const piIndexerOfflineFallbackPolicyTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerOfflineFallbackPolicyTest.kt",
);
const piIndexerResponseCache = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerResponseCache.kt",
);
const piIndexerResponseCacheTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerResponseCacheTest.kt",
);
const soraConfigManagerSource = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/SoraConfigManager.kt",
);
const soraConfigPayloadAdmissionTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/SoraConfigPayloadAdmissionTest.kt",
);
const piHistoryCheckpointValidator = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiHistoryCheckpointValidator.kt",
);
const piHistoryCheckpointValidatorTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiHistoryCheckpointValidatorTest.kt",
);
const piValidatedHistoryCache = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiValidatedHistoryCache.kt",
);
const piValidatedHistoryCacheTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiValidatedHistoryCacheTest.kt",
);
const polkamarktCatalogCache = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PolkamarktCatalogCache.kt",
);
const polkamarktCatalogCacheTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PolkamarktCatalogCacheTest.kt",
);
const piLiveContractTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/SoraMetricsLiveContractTest.kt",
);
const transactionHistoryRepository = read(
  "feature_blockexplorer_impl/src/main/java/jp/co/soramitsu/feature_blockexplorer_impl/data/TransactionHistoryRepositoryImpl.kt",
);
const transactionHistoryRepositoryTest = read(
  "feature_blockexplorer_impl/src/test/java/jp/co/soramitsu/feature_blockexplorer_impl/data/TransactionHistoryRepositoryTest.kt",
);
const transactionMapper = read(
  "feature_blockexplorer_impl/src/main/java/jp/co/soramitsu/feature_blockexplorer_impl/data/TransactionMapper.kt",
);
const transactionMapperAdversarialTest = read(
  "feature_blockexplorer_impl/src/test/java/jp/co/soramitsu/feature_blockexplorer_impl/data/TransactionMapperAdversarialTest.kt",
);
const firebaseWrapper = read(
  "common/src/main/java/jp/co/soramitsu/common/logger/FirebaseWrapper.kt",
);
const firebaseWrapperTest = read(
  "common/src/test/java/jp/co/soramitsu/common/logger/FirebaseWrapperTest.kt",
);
const walletIdentityDao = read(
  "core_db/src/main/java/jp/co/soramitsu/core_db/dao/WalletIdentityDao.kt",
);
const extrinsicManager = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicManager.kt",
);
const substrateCalls = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/SubstrateCalls.kt",
);
const extrinsicManagerSafetyTest = read(
  "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicManagerSafetyTest.kt",
);
const sora2PendingSubmissionCoordinator = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/Sora2PendingSubmissionCoordinator.kt",
);
const sora2PendingRecoveryBatchPlannerTest = read(
  "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/Sora2PendingRecoveryBatchPlannerTest.kt",
);
const sora2PendingRecoveryWorker = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/recovery/Sora2PendingRecoveryWorker.kt",
);
const referralRepository = read(
  "feature_referral_impl/src/main/java/jp/co/soramitsu/feature_referral_impl/data/ReferralRepositoryImpl.kt",
);
const referralRepositoryTest = read(
  "feature_referral_impl/src/test/java/jp/co/soramitsu/feature_referral_impl/data/ReferralRepositoryTest.kt",
);
const assetsInteractorImpl = read(
  "feature_assets_impl/src/main/java/jp/co/soramitsu/feature_assets_impl/domain/AssetsInteractorImpl.kt",
);
const assetsInteractorTest = read(
  "feature_assets_impl/src/test/java/jp/co/soramitsu/feature_assets_impl/domain/AssetsInteractorTest.kt",
);
const assetsRepositoryImpl = read(
  "feature_assets_impl/src/main/java/jp/co/soramitsu/feature_assets_impl/data/AssetsRepositoryImpl.kt",
);
const assetsRepositoryTest = read(
  "feature_assets_impl/src/test/java/jp/co/soramitsu/feature_assets_impl/data/AssetsRepositoryTest.kt",
);
const transferAmountViewModel = read(
  "feature_assets_impl/src/main/java/jp/co/soramitsu/feature_assets_impl/presentation/screens/send/TransferAmountViewModel.kt",
);
const transferAmountViewModelTest = read(
  "feature_assets_impl/src/test/java/jp/co/soramitsu/feature_assets_impl/presentation/send/TransferAmountViewModelTest.kt",
);
const pinCodeViewModel = read(
  "feature_main_impl/src/main/java/jp/co/soramitsu/feature_main_impl/presentation/pincode/PinCodeViewModel.kt",
);
const pinCodeViewModelTest = read(
  "feature_main_impl/src/test/java/jp/co/soramitsu/feature_main_impl/presentation/pincode/PinCodeViewModelTest.kt",
);
const polkamarktTrader = read(
  "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktTraderRepository.kt",
);
const polkamarktScreen = read(
  "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktScreen.kt",
);
const polkamarktViewModel = read(
  "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktViewModel.kt",
);
const polkamarktAccountObservationSource = sourceBetween(
  polkamarktViewModel,
  "private fun observeAccount()",
  "fun refresh()",
);
const polkamarktRefreshSource = sourceBetween(
  polkamarktViewModel,
  "private fun launchRefresh(",
  "private fun isCurrentAccount(",
);
const polkamarktMarketDetailSource = sourceBetween(
  polkamarktViewModel,
  "fun selectMarket(",
  "fun setSide(",
);
const polkamarktRuntimeDetailCallbackSource = sourceBetween(
  polkamarktViewModel,
  "trader.getAuthoritativeMarketDetail(",
  "indexer.getMarketSnapshotsQualified(id)",
);
const polkamarktSnapshotCallbackSource = sourceBetween(
  polkamarktViewModel,
  "indexer.getMarketSnapshotsQualified(id)",
  "fun setSide(",
);
const polkamarktQuoteSource = sourceBetween(
  polkamarktViewModel,
  "private suspend fun loadQuote(",
  "fun confirmTrade()",
);
const polkamarktQuoteBeforePostAwaitGuard = sourceBetween(
  polkamarktQuoteSource,
  "val quote = when (current.side)",
  "version == quoteRequestVersion &&",
);
const polkamarktTradeMutationSource = sourceBetween(
  polkamarktViewModel,
  "fun confirmTrade()",
  "fun reviewClaim(",
);
const polkamarktClaimReviewSource = sourceBetween(
  polkamarktViewModel,
  "private fun reviewClaims(",
  "fun requestClaim(",
);
const polkamarktClaimMutationSource = sourceBetween(
  polkamarktViewModel,
  "fun confirmClaim()",
  "private fun requireCurrentClaimConfirmation(",
);
const polkamarktClaimMutationBeforePostAwaitGuard = sourceBetween(
  polkamarktClaimMutationSource,
  "val mutation = when {",
  "if (isCurrentAccount(accountAddress, generation))",
);
const polkamarktPendingObservationPolicyTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktPendingObservationPolicyTest.kt",
);
const fearlessLibExt = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/FearlessLibExt.kt",
);
const polkamarktRuntimeDtos = read(
  "common/src/main/java/jp/co/soramitsu/common/data/network/dto/PolkamarktRuntimeDtos.kt",
);
const polkamarktExtrinsics = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/Extrinsics.kt",
);
const polkamarktCalls = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/SubstrateCalls.kt",
);
const polkamarktMarketIdTest = read(
  "common/src/test/java/jp/co/soramitsu/common/data/network/dto/PolkamarktMarketIdTest.kt",
);
const polkamarktClaimValidatorTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktClaimValidatorTest.kt",
);
const polkamarktPendingRecoveryTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktPendingRecoveryTest.kt",
);
const polkamarktPendingFailurePolicyTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktPendingFailurePolicyTest.kt",
);
const polkamarktMutationAccountValidatorTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktMutationAccountValidatorTest.kt",
);
const polkamarktMutationSubmissionCoordinatorTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktMutationSubmissionCoordinatorTest.kt",
);
const polkamarktMutationQualificationTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktMutationQualificationTest.kt",
);
const polkamarktPreTransportGateTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktPreTransportGateTest.kt",
);
const polkamarktRuntimeCallFactoryTest = read(
  "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/PolkamarktRuntimeCallFactoryTest.kt",
);
const polkamarktReadProvenanceTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktReadProvenanceTest.kt",
);
const polkamarktWebContractTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktWebContractTest.kt",
);
const polkamarktClaimConfirmation = read(
  "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktClaimConfirmation.kt",
);
const polkamarktClaimConfirmationTest = read(
  "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktClaimConfirmationPolicyTest.kt",
);
const sora2RuntimeContract = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/runtime/Sora2RuntimeContract.kt",
);
const sora2RuntimeContractTest = read(
  "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/runtime/Sora2RuntimeContractTest.kt",
);
const runtimeManagerSource = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/runtime/RuntimeManager.kt",
);
const boundedRuntimeRpcClientSource = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/runtime/Sora2BoundedRuntimeRpcClient.kt",
);
const boundedRuntimeRpcClientTest = read(
  "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/runtime/Sora2BoundedRuntimeRpcClientTest.kt",
);
const connectionManagerSource = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/ConnectionManager.kt",
);
const wsConnectionManagerSource = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/WsConnectionManager.kt",
);
const mutationTransportCoordinatorSource = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/Sora2MutationTransportCoordinator.kt",
);
const mutationTransportCoordinatorTest = read(
  "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/Sora2MutationTransportCoordinatorTest.kt",
);
const extrinsicBuilderFactorySource = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicBuilderFactory.kt",
);
const extrinsicBuilderFactoryTest = read(
  "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicBuilderFactoryTest.kt",
);
const substrateModuleSource = read(
  "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/di/SubstrateModule.kt",
);
const fileManagerContract = read(
  "common/src/main/java/jp/co/soramitsu/common/io/FileManager.kt",
);
const fileManagerImplementation = read(
  "common/src/main/java/jp/co/soramitsu/common/io/FileManagerImpl.kt",
);
const fileManagerAtomicEntryPolicyTest = read(
  "common/src/test/java/jp/co/soramitsu/common/io/FileManagerAtomicEntryPolicyTest.kt",
);
const fileManagerAtomicRecoveryTest = read(
  "common/src/androidTest/java/jp/co/soramitsu/common/io/FileManagerAtomicRecoveryTest.kt",
);
const boundedHttpTextClient = read(
  "common/src/main/java/jp/co/soramitsu/common/network/BoundedHttpTextClient.kt",
);
const boundedHttpTextClientTest = read(
  "common/src/test/java/jp/co/soramitsu/common/network/BoundedHttpTextClientTest.kt",
);
const strictJsonDocumentAdmission = read(
  "common/src/main/java/jp/co/soramitsu/common/network/StrictJsonDocumentAdmission.kt",
);
const strictJsonDocumentAdmissionTest = read(
  "common/src/test/java/jp/co/soramitsu/common/network/StrictJsonDocumentAdmissionTest.kt",
);
const runtimeCacheAtomicRecoveryTest = read(
  "sorasubstrate/src/androidTest/java/jp/co/soramitsu/sora/substrate/runtime/RuntimeCacheAtomicRecoveryTest.kt",
);
const productionFeatureManager = read(
  "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/ProductionFeatureManager.kt",
);
const settings = read("settings.gradle.kts");
const rootBuild = read("build.gradle.kts");
const walletFeatureBuild = read("feature_wallet_impl/build.gradle.kts");
const gradleProperties = read("gradle.properties");
const gradleWrapperProperties = read("gradle/wrapper/gradle-wrapper.properties");
const gradleDependencyProvenance = json(
  "config/gradle-dependency-provenance.json",
);
const androidProductionSigning = json(
  "config/android-production-signing-identity.json",
);
const fundedCanaryTrustRecord = readStrictJsonFile(
  join(root, "config/funded-canary-trust.json"),
  64 * 1024,
);
const fundedCanaryTrust = fundedCanaryTrustRecord?.value ?? Object.create(null);
const fundedCanaryFinalityManifestTemplate = readStrictJsonFile(
  join(
    root,
    "docs/modernization/qualification/android-finality-trust-manifest.blocked.json",
  ),
  64 * 1024,
);
const fundedCanaryFinalityContextTemplates = {
  taira: readStrictJsonFile(
    join(
      root,
      "docs/modernization/qualification/taira-finality-trust-context.blocked.json",
    ),
    64 * 1024,
  ),
  minamoto: readStrictJsonFile(
    join(
      root,
      "docs/modernization/qualification/minamoto-finality-trust-context.blocked.json",
    ),
    64 * 1024,
  ),
};
const fundedCanaryFinalityNativeTemplate = readStrictJsonFile(
  join(
    root,
    "docs/modernization/qualification/android-finality-native-canary.blocked.json",
  ),
  64 * 1024,
);
const fundedCanaryV4LibrarySource = read("scripts/lib/funded-canary-v4.mjs");
const fundedCanaryV4HarnessSource = read(
  "scripts/test-funded-canary-v4-contract.mjs",
);
const fundedCanaryControllerBundleSource = read(
  "scripts/lib/funded-canary-controller-bundle-v1.mjs",
);
const fundedCanaryControllerBundleExtractorSource = read(
  "scripts/extract-funded-canary-controller-bundle.mjs",
);
const fundedCanaryControllerBundleTestSource = read(
  "scripts/test-funded-canary-controller-bundle-v1.mjs",
);
const androidMigrationControllerEnvelopeSource = read(
  "scripts/lib/android-migration-controller-envelope-v1.mjs",
);
const androidMigrationControllerEnvelopeExtractorSource = read(
  "scripts/extract-android-migration-controller-envelope.mjs",
);
const androidMigrationControllerEnvelopeTestSource = read(
  "scripts/test-android-migration-controller-envelope-v1.mjs",
);
const gradleWrapperJarSha256 = sha256("gradle/wrapper/gradle-wrapper.jar");
const androidVerificationWorkflow = read(
  ".github/workflows/android_verification.yml",
);
const productionReleaseWorkflow = read(
  ".github/workflows/production_release_qualification.yml",
);
const productionReleaseWorkflowJobEnvironment =
  productionReleaseWorkflow.match(/\n    env:\n[\s\S]*?\n    steps:\n/)?.[0] ?? "";
const productionRunnerTempPathBindings = Object.freeze([
  ["PRODUCTION_CANDIDATE_RUN_RECEIPT_PATH", "sora-qualified-candidate-run.json"],
  [
    "PRODUCTION_CANDIDATE_ARTIFACT_RECEIPT_PATH",
    "sora-qualified-candidate-artifacts.json",
  ],
  [
    "PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH",
    "sora-pi-production-raw-live-v1.json",
  ],
  [
    "PRODUCTION_CANDIDATE_PI_RECEIPT_PATH",
    "sora-pi-production-capability-probe-v3.json",
  ],
  [
    "PRODUCTION_CANDIDATE_PI_RECEIPT_SIGNATURE_PATH",
    "sora-pi-production-capability-probe-v3-signature.json",
  ],
  [
    "PRODUCTION_PI_CANDIDATE_CONTROLLER_REQUEST_PATH",
    "sora-pi-production-candidate-controller-request-v1.json",
  ],
  ["PRODUCTION_QUALIFICATION_RECEIPT_PATH", "sora-production-admission.json"],
  ["PRODUCTION_PRIMARY_BUILD_LOG_PATH", "sora-android-primary-build.log"],
  [
    "PRODUCTION_PRIMARY_SIGNING_VERIFICATION_PATH",
    "sora-android-primary-signing-verification.json",
  ],
  [
    "PRODUCTION_REPRODUCED_AAB_PATH",
    "sora-android-reproduction/app/build/outputs/bundle/productionRelease/app-production-release.aab",
  ],
  [
    "PRODUCTION_REPRODUCED_APK_PATH",
    "sora-android-reproduction/app/build/outputs/apk/production/release/app-production-release.apk",
  ],
  [
    "PRODUCTION_REPRODUCED_MAPPING_PATH",
    "sora-android-reproduction/app/build/outputs/mapping/productionRelease/mapping.txt",
  ],
  [
    "PRODUCTION_REPRODUCED_BUILD_LOG_PATH",
    "sora-android-reproduction-build.log",
  ],
  [
    "PRODUCTION_REPRODUCED_SIGNING_VERIFICATION_PATH",
    "sora-android-reproduction-signing-verification.json",
  ],
  [
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_MANIFEST_PATH",
    "sora-android-dependency-signing-review/manifest.json",
  ],
  [
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_SIGNATURE_PATH",
    "sora-android-dependency-signing-review/producer.sig",
  ],
  [
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_SIGNATURE_PATH",
    "sora-android-dependency-signing-review/reviewer.sig",
  ],
  [
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_PRODUCER_PUBLIC_KEY_PATH",
    "sora-android-dependency-signing-review/producer.pem",
  ],
  [
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_REVIEWER_PUBLIC_KEY_PATH",
    "sora-android-dependency-signing-review/reviewer.pem",
  ],
  [
    "ANDROID_DEPENDENCY_SIGNING_REVIEW_ADMISSION_PATH",
    "sora-android-dependency-signing-review/admission.json",
  ],
  [
    "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_PATH",
    "sora-android-migration-controller-envelope-v1.json",
  ],
  [
    "ANDROID_MIGRATION_CONTROLLER_EVIDENCE_ROOT",
    "sora-android-migration-controller-evidence-v1",
  ],
  [
    "ANDROID_MIGRATION_CONTROLLER_EXTRACTION_RECEIPT_PATH",
    "sora-android-migration-controller-extraction-v1.json",
  ],
  ["TAIRA_DEPLOYMENT_MANIFEST_PATH", "sora-taira-deployment/manifest.json"],
  [
    "TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_PATH",
    "sora-taira-deployment/operator.sig",
  ],
  [
    "TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_PATH",
    "sora-taira-deployment/reviewer.sig",
  ],
  [
    "TAIRA_DEPLOYMENT_OPERATOR_PUBLIC_KEY_PATH",
    "sora-taira-deployment/operator.pem",
  ],
  [
    "TAIRA_DEPLOYMENT_REVIEWER_PUBLIC_KEY_PATH",
    "sora-taira-deployment/reviewer.pem",
  ],
  [
    "TAIRA_DEPLOYMENT_ADMISSION_RECEIPT_PATH",
    "sora-taira-deployment/admission.json",
  ],
  ["PRODUCTION_ROLLOUT_EVIDENCE_PATH", "sora-production-rollout-evidence.json"],
  [
    "FUNDED_CANARY_CONTROLLER_BUNDLE_PATH",
    "sora-funded-canary-controller-bundle-v1.tar",
  ],
  [
    "FUNDED_CANARY_CONTROLLER_EVIDENCE_ROOT",
    "sora-funded-canary-controller-evidence-v1",
  ],
  [
    "FUNDED_CANARY_CONTROLLER_EXTRACTION_RECEIPT_PATH",
    "sora-funded-canary-controller-extraction-v1.json",
  ],
]);
const androidCandidateSigningVerificationSource = read(
  "scripts/lib/android-candidate-signing-verification-v1.mjs",
);
const androidCandidateSigningVerifierSource = read(
  "scripts/verify-android-candidate-signing.mjs",
);
const androidCandidateSigningVerificationTestSource = read(
  "scripts/test-android-candidate-signing-verification-v1.mjs",
);
const androidQualifiedCandidatePackageSource = read(
  "scripts/lib/android-qualified-candidate-package-v1.mjs",
);
const androidQualifiedCandidatePackageCreatorSource = read(
  "scripts/create-android-qualified-candidate-package.mjs",
);
const androidDownloadedCandidatePackageVerifierSource = read(
  "scripts/verify-downloaded-android-qualified-candidate-package.mjs",
);
const androidQualifiedCandidatePackageTestSource = read(
  "scripts/test-android-qualified-candidate-package-v1.mjs",
);
const androidDependencySigningReviewSource = read(
  "scripts/lib/android-dependency-signing-review-v1.mjs",
);
const androidDependencySigningReviewVerifierSource = read(
  "scripts/verify-android-dependency-signing-review.mjs",
);
const androidDependencySigningReviewTestSource = read(
  "scripts/test-android-dependency-signing-review-v1.mjs",
);
const piProductionProbe = read("scripts/probe-pi-production-contract.mjs");
const ciPipeline = read("Jenkinsfile");
const githubWorkflows = [
  ".github/workflows/android_verification.yml",
  ".github/workflows/production_release_qualification.yml",
  ".github/workflows/on_pullrequest.yml",
  ".github/workflows/on_push.yml",
].map((path) => ({ path, source: read(path) }));
const kvmAccessContractLines = Object.freeze([
  "set -euo pipefail",
  "if [[ ! -e /dev/kvm ]]; then",
  "[[ -c /dev/kvm && ! -L /dev/kvm ]] || {",
  "kvm_rules_dir=/etc/udev/rules.d",
  'kvm_rule_path="$kvm_rules_dir/99-kvm4all.rules"',
  '[[ -d "$kvm_rules_dir" && ! -L "$kvm_rules_dir" ]] || {',
  '[[ -f "$kvm_rule_path" && ! -L "$kvm_rule_path" ]] || {',
  `kvm_rule='KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"'`,
  `printf '%s\\n' "$kvm_rule" | sudo tee "$kvm_rule_path" >/dev/null`,
  '[[ "$(sudo cat "$kvm_rule_path")" == "$kvm_rule" ]] || {',
  "sudo udevadm control --reload-rules",
  "sudo udevadm trigger --name-match=kvm",
  "sudo chmod 0666 /dev/kvm",
  `[[ "$(stat -c '%a' /dev/kvm)" == 666 ]] || {`,
  "[[ -r /dev/kvm && -w /dev/kvm ]] || {",
]);
const hasKvmAccessContract = (source) =>
  kvmAccessContractLines.every((line) => source.includes(line));
const androidVerificationKvmAccessIndex = androidVerificationWorkflow.indexOf(
  "Enable KVM access for Android emulator",
);
const androidVerificationEmulatorIndex = androidVerificationWorkflow.indexOf(
  "reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
);
const productionKvmAccessIndex = productionReleaseWorkflow.indexOf(
  "Enable KVM access for Android emulators",
);
const productionFirstEmulatorIndex = productionReleaseWorkflow.indexOf(
  "reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
);
assert(
  hasKvmAccessContract(androidVerificationWorkflow) &&
    hasKvmAccessContract(productionReleaseWorkflow) &&
    sourceMatchCount(
      androidVerificationWorkflow,
      /^\s*- name: Enable KVM access for Android emulator\s*$/gm,
    ) === 1 &&
    sourceMatchCount(
      productionReleaseWorkflow,
      /^\s*- name: Enable KVM access for Android emulators\s*$/gm,
    ) === 1 &&
    androidVerificationKvmAccessIndex >= 0 &&
    androidVerificationKvmAccessIndex < androidVerificationEmulatorIndex &&
    productionKvmAccessIndex >= 0 &&
    productionKvmAccessIndex < productionFirstEmulatorIndex &&
    productionReleaseWorkflow.includes(
      "- name: Enable KVM access for Android emulators\n        if: ${{ env.PRODUCTION_ROLLOUT_TARGET_PERCENT == '' }}",
    ),
  "ANDROID_EMULATOR_KVM_PERMISSION_CONTRACT_INVALID",
);
const workflowActions = githubWorkflows.flatMap(({ path, source }) =>
  [...source.matchAll(/^\s*uses:\s*([^@\s]+)@([^\s#]+)(?:\s+#.*)?\s*$/gm)].map(
    ([, action, revision]) => ({ path, action, revision }),
  ),
);
const reviewedWorkflowActions =
  gradleDependencyProvenance.workflowPolicy?.actions ?? {};
const jenkinsLibraryRevision = ciPipeline.match(
  /^@Library\('jenkins-library@([0-9a-f]{40})'\)\s+_\s*$/m,
)?.[1];
assert(
  androidBuildBlock !== null &&
    androidDefaultConfigBlock !== null &&
    androidBuildTypesBlock !== null &&
    androidReleaseBuildTypeBlock !== null &&
    androidDebugBuildTypeBlock !== null &&
    androidProductFlavorsBlock !== null &&
    androidProductionFlavorBlock !== null &&
    androidQualificationFlavorBlock !== null &&
    lexicalMatchCount(appBuildSource, /\bnamespace\b/) === 1 &&
    lexicalMatchCount(appBuildSource, /\bapplicationId\b/) === 1 &&
    lexicalMatchCount(appBuildSource, /\bapplicationIdSuffix\b/) === 3 &&
    lexicalMatchCount(appBuildSource, /\btestApplicationId\b/) === 1 &&
    lexicalMatchCount(appBuildSource, /\bsigningConfig\b/) === 3 &&
    lexicalMatchCount(appBuildSource, /\binitWith\b/) === 1 &&
    lexicalMatchCount(appBuildSource, /\bdimension\b/) === 4 &&
    lexicalMatchCount(appBuildSource, /\bresValue\b/) === 4 &&
    lexicalMatchCount(appBuildSource, /\bmanifestPlaceholders\b/) === 12 &&
    sourceMatchCount(appBuildSource, /\bnamespace\b/) === 1 &&
    sourceMatchCount(appBuildSource, /\bapplicationId\b/) === 1 &&
    sourceMatchCount(appBuildSource, /\bapplicationIdSuffix\b/) === 3 &&
    sourceMatchCount(appBuildSource, /\btestApplicationId\b/) === 1 &&
    sourceMatchCount(appBuildSource, /\bsigningConfig\b/) === 3 &&
    sourceMatchCount(appBuildSource, /\binitWith\b/) === 1 &&
    sourceMatchCount(appBuildSource, /\bdimension\b/) === 4 &&
    sourceMatchCount(appBuildSource, /\bresValue\b/) === 4 &&
    sourceMatchCount(appBuildSource, /\bmanifestPlaceholders\b/) === 12 &&
    hasSingleDirectLexicalMatch(
      androidBuildBlock,
      /^\s*namespace\s*=\s*"jp\.co\.soramitsu\.sora"\s*$/m,
    ) &&
    lexicalMatchCount(androidBuildBlock, /\bnamespace\b/) === 1 &&
    hasSingleDirectLexicalMatch(
      androidDefaultConfigBlock,
      /^\s*applicationId\s*=\s*"jp\.co\.soramitsu\.sora"\s*$/m,
    ) &&
    lexicalMatchCount(androidDefaultConfigBlock, /\bapplicationId\b/) === 1 &&
    hasSingleDirectLexicalMatch(
      androidReleaseBuildTypeBlock,
      /^\s*signingConfig\s*=\s*signingConfigs\.getByName\(\s*"productionRelease"\s*\)\s*$/m,
    ) &&
    lexicalMatchCount(androidReleaseBuildTypeBlock, /\bsigningConfig\s*=/) === 1 &&
    lexicalMatchCount(androidReleaseBuildTypeBlock, /\binitWith\s*\(/) === 0 &&
    lexicalMatchCount(androidReleaseBuildTypeBlock, /\bapplicationId\b/) === 0 &&
    lexicalMatchCount(androidReleaseBuildTypeBlock, /\bapplicationIdSuffix\b/) === 0 &&
    lexicalMatchCount(androidReleaseBuildTypeBlock, /\btestApplicationId\b/) === 0 &&
    !hasLexicalMatch(androidReleaseBuildTypeBlock, /\bcidebug\b/) &&
    hasSingleDirectLexicalMatch(
      androidDebugBuildTypeBlock,
      /^\s*signingConfig\s*=\s*signingConfigs\.getByName\(\s*"cidebug"\s*\)\s*$/m,
    ) &&
    lexicalMatchCount(androidDebugBuildTypeBlock, /\bsigningConfig\s*=/) === 1 &&
    hasSingleDirectLexicalMatch(
      androidProductionFlavorBlock,
      /^\s*dimension\s*=\s*"default"\s*$/m,
    ) &&
    lexicalMatchCount(androidProductionFlavorBlock, /\bdimension\s*=/) === 1 &&
    hasSingleDirectLexicalMatch(
      androidProductionFlavorBlock,
      /^\s*resValue\(\s*"string"\s*,\s*"app_name"\s*,\s*"SORA"\s*\)\s*$/m,
    ) &&
    lexicalMatchCount(
      androidProductionFlavorBlock,
      /\bresValue\(\s*"string"\s*,\s*"app_name"\s*,/,
    ) === 1 &&
    lexicalMatchCount(androidProductionFlavorBlock, /\bresValue\b/) === 1 &&
    hasSingleDirectLexicalMatch(
      androidProductionFlavorBlock,
      /^\s*manifestPlaceholders\[\s*"pathPrefix"\s*\]\s*=\s*"\/#\/referral"\s*$/m,
    ) &&
    lexicalMatchCount(
      androidProductionFlavorBlock,
      /\bmanifestPlaceholders\[\s*"pathPrefix"\s*\]\s*=/,
    ) === 1 &&
    hasSingleDirectLexicalMatch(
      androidProductionFlavorBlock,
      /^\s*manifestPlaceholders\[\s*"appIcon"\s*\]\s*=\s*"@mipmap\/ic_prod_launcher"\s*$/m,
    ) &&
    lexicalMatchCount(
      androidProductionFlavorBlock,
      /\bmanifestPlaceholders\[\s*"appIcon"\s*\]\s*=/,
    ) === 1 &&
    hasSingleDirectLexicalMatch(
      androidProductionFlavorBlock,
      /^\s*manifestPlaceholders\[\s*"roundedIcon"\s*\]\s*=\s*"@mipmap\/ic_prod_launcher_rounded"\s*$/m,
    ) &&
    lexicalMatchCount(
      androidProductionFlavorBlock,
      /\bmanifestPlaceholders\[\s*"roundedIcon"\s*\]\s*=/,
    ) === 1 &&
    lexicalMatchCount(
      androidProductionFlavorBlock,
      /\bmanifestPlaceholders\b/,
    ) === 3 &&
    lexicalMatchCount(androidProductionFlavorBlock, /\bapplicationId\b/) === 0 &&
    lexicalMatchCount(androidProductionFlavorBlock, /\bapplicationIdSuffix\b/) === 0 &&
    lexicalMatchCount(androidProductionFlavorBlock, /\btestApplicationId\b/) === 0 &&
    lexicalMatchCount(androidProductionFlavorBlock, /\bsigningConfig\b/) === 0 &&
    lexicalMatchCount(androidProductionFlavorBlock, /\binitWith\s*\(/) === 0 &&
    hasSingleDirectLexicalMatch(
      androidQualificationFlavorBlock,
      /^\s*applicationIdSuffix\s*=\s*"\.qualification"\s*$/m,
    ) &&
    lexicalMatchCount(
      androidQualificationFlavorBlock,
      /\bapplicationIdSuffix\s*=/,
    ) === 1 &&
    hasSingleDirectLexicalMatch(
      androidQualificationFlavorBlock,
      /^\s*testApplicationId\s*=\s*"jp\.co\.soramitsu\.sora\.qualification\.test"\s*$/m,
    ) &&
    lexicalMatchCount(
      androidQualificationFlavorBlock,
      /\btestApplicationId\s*=/,
    ) === 1 &&
    lexicalMatchCount(androidQualificationFlavorBlock, /\bapplicationId\b/) === 0 &&
    lexicalMatchCount(androidQualificationFlavorBlock, /\binitWith\s*\(/) === 0 &&
    hasSingleDirectLexicalMatch(
      androidQualificationFlavorBlock,
      /^\s*dimension\s*=\s*"default"\s*$/m,
    ) &&
    lexicalMatchCount(androidQualificationFlavorBlock, /\bdimension\s*=/) === 1,
  "PRODUCTION_ANDROID_VARIANT_IDENTITY_CHANGED",
);
assert(
  appDatabaseClassBlock !== null &&
    appDatabaseCompanionBlock !== null &&
    appDatabaseBuilderBlock !== null &&
    hasSingleDirectLexicalMatch(
      databaseSource,
      /^\s*@Database\(\s*version\s*=\s*77\s*,/ms,
    ) &&
    hasSingleDirectLexicalMatch(
      appDatabaseBuilderBlock,
      /^\s*return\s+Room\.databaseBuilder\(\s*context\.applicationContext\s*,\s*AppDatabase::class\.java\s*,\s*"app\.db"\s*\)/ms,
    ) &&
    walletUpgradeBackupBlock !== null &&
    hasSingleDirectLexicalMatch(
      walletUpgradeBackupBlock,
      /^\s*private\s+const\s+val\s+DATABASE_NAME\s*=\s*"app\.db"\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      walletUpgradeBackupBlock,
      /^\s*private\s+const\s+val\s+CURRENT_DATABASE_VERSION\s*=\s*77\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      walletUpgradeBackupBlock,
      /^\s*private\s+const\s+val\s+REQUIRED_DATABASE_BACKUP\s*=\s*"databases\/app\.db"\s*$/m,
    ) &&
    hasLexicalMatch(
      walletUpgradeBackupBlock,
      /\bgetDatabasePath\(\s*DATABASE_NAME\s*\)/,
    ) &&
    soraPreferencesClassBlock !== null &&
    soraPreferencesCompanionBlock !== null &&
    hasSingleDirectLexicalMatch(
      soraPreferencesCompanionBlock,
      /^\s*private\s+const\s+val\s+SHARED_PREFERENCES_FILE\s*=\s*"sora_prefs"\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      soraPreferencesCompanionBlock,
      /^\s*private\s+const\s+val\s+SORA_COMMON_PREFS\s*=\s*"sora_prefs_datastore"\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      soraPreferencesClassBlock,
      /^\s*private\s+val\s+Context\.dataStorePreferences\s*:\s*DataStore<Preferences>\s+by\s+preferencesDataStore\(\s*name\s*=\s*SORA_COMMON_PREFS\s*,\s*produceMigrations\s*=\s*\{\s*listOf\(\s*SharedPreferencesMigration\(\s*it\s*,\s*SHARED_PREFERENCES_FILE\s*\)/ms,
    ),
  "PRODUCTION_STORAGE_IDENTITY_CHANGED",
);
assert(
  encryptionUtilClassBlock !== null &&
    encryptionUtilCompanionBlock !== null &&
    encryptionPreferenceKeyBlock !== null &&
    encryptionInitKeystoreBlock !== null &&
    encryptionCreateKeysBlock !== null &&
    encryptionWalletMaterialBlock !== null &&
    hasSingleDirectLexicalMatch(
      encryptionUtilCompanionBlock,
      /^\s*private\s+const\s+val\s+KEY_STORE_PROVIDER\s*=\s*"AndroidKeyStore"\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      encryptionUtilCompanionBlock,
      /^\s*private\s+const\s+val\s+KEY_ALIAS\s*=\s*"key_alias"\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      encryptionUtilCompanionBlock,
      /^\s*private\s+const\s+val\s+DATABASE_NAME\s*=\s*"app\.db"\s*$/m,
    ) &&
    hasLexicalMatch(
      encryptionPreferenceKeyBlock,
      /\bcontext\.getSharedPreferences\(\s*KEY_ALIAS\s*,\s*Context\.MODE_PRIVATE\s*\)/,
    ) &&
    hasLexicalMatch(
      encryptionInitKeystoreBlock,
      /\bkeyStore!!\.getKey\(\s*KEY_ALIAS\s*,\s*null\s*\)/,
    ) &&
    hasLexicalMatch(
      encryptionCreateKeysBlock,
      /\bKeyGenParameterSpec\.Builder\(\s*KEY_ALIAS\s*,/s,
    ) &&
    hasLexicalMatch(
      encryptionWalletMaterialBlock,
      /\bcontext\.getDatabasePath\(\s*DATABASE_NAME\s*\)/,
    ),
  "PRODUCTION_KEYSTORE_IDENTITY_CHANGED",
);
assert(
  walletPreferenceKeysBlock !== null &&
    prefsUserDatasourceClassBlock !== null &&
    prefsUserDatasourceCompanionBlock !== null &&
    hasSingleDirectLexicalMatch(
      walletPreferenceKeysBlock,
      /^\s*const\s+val\s+CURRENT_ACCOUNT_ADDRESS\s*=\s*"cur_account_address"\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      walletPreferenceKeysBlock,
      /^\s*val\s+generalEvidenceKeys\s*=\s*setOf\([\s\S]*?\bCURRENT_ACCOUNT_ADDRESS\b[\s\S]*?^\s*\)\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      prefsUserDatasourceCompanionBlock,
      /^\s*private\s+const\s+val\s+KEY_CUR_ACCOUNT_ADDRESS[ \t]*=[ \t]*\r?\n[ \t]*WalletPreferenceKeys\.CURRENT_ACCOUNT_ADDRESS[ \t]*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      prefsUserDatasourceClassBlock,
      /^\s*override\s+suspend\s+fun\s+getCurAccountAddress\(\s*\)\s*:\s*String\s*=\s*soraPreferences\.getString\(\s*KEY_CUR_ACCOUNT_ADDRESS\s*\)/ms,
    ) &&
    hasSingleDirectLexicalMatch(
      prefsUserDatasourceClassBlock,
      /^\s*override\s+suspend\s+fun\s+setCurAccountAddress\(\s*accountAddress\s*:\s*String\s*\)\s*=\s*soraPreferences\.putString\(\s*KEY_CUR_ACCOUNT_ADDRESS\s*,\s*accountAddress\s*\)/ms,
    ),
  "PRODUCTION_SELECTED_ACCOUNT_IDENTITY_CHANGED",
);
assert(
  androidProductionSigning.schemaVersion === 1 &&
    androidProductionSigning.platform === "android" &&
    androidProductionSigning.applicationId === "jp.co.soramitsu.sora" &&
    androidProductionSigning.releaseSigningConfig === "productionRelease" &&
    androidProductionSigning.debugSigningConfig === "cidebug" &&
    androidProductionSigning.debugFallbackAllowedForRelease === false &&
    androidProductionSigning.requiredEnvironmentVariables.join(",") ===
      "CI_KEYSTORE_PATH,CI_KEYSTORE_PASS,CI_KEYSTORE_KEY_ALIAS,CI_KEYSTORE_KEY_PASS,CI_BUILD_ID" &&
    androidProductionSigning.secretsRecordedInEvidence === false &&
    appBuild.includes('create("productionRelease")') &&
    appBuild.includes(
      'signingConfig = signingConfigs.getByName("productionRelease")',
    ) &&
    appBuild.includes("verifyProductionReleaseSigning") &&
    appBuild.includes(
      'name.contains("productionRelease", ignoreCase = true)',
    ) &&
    appBuild.includes("import java.nio.file.Files") &&
    appBuild.includes("import java.nio.file.LinkOption") &&
    appBuild.includes("import java.nio.file.Paths") &&
    appBuild.includes("import java.nio.file.attribute.PosixFilePermission") &&
    appBuild.includes("Files.isSymbolicLink") &&
    appBuild.includes('System.getenv("CI_BUILD_ID")') &&
    appBuild.includes('Regex("[1-9][0-9]{0,8}")') &&
    appBuild.includes("configuredKeystore.isAbsolute") &&
    appBuild.includes("toRealPath(LinkOption.NOFOLLOW_LINKS)") &&
    appBuild.includes("Files.getPosixFilePermissions") &&
    appBuild.includes("Production release keystore must be owner-only") &&
    gitignore.includes("*.jks") &&
    gitignore.includes("*.keystore") &&
    gitignore.includes("*.p12") &&
    gitignore.includes("*.pfx") &&
    gitignore.includes("!key/testdebug.jks") &&
    androidReleaseBuildTypeBlock !== null &&
    !hasLexicalMatch(
      androidReleaseBuildTypeBlock,
      /\bsigningConfig\s*=\s*signingConfigs\.getByName\(\s*"cidebug"\s*\)/,
    ),
  "ANDROID_PRODUCTION_SIGNING_FAIL_CLOSED_GATE_MISSING",
);
assert(
  androidReleaseBuildTypeBlock !== null &&
    hasSingleDirectLexicalMatch(
      androidReleaseBuildTypeBlock,
      /^\s*isMinifyEnabled\s*=\s*true\s*$/m,
    ) &&
    hasSingleDirectLexicalMatch(
      androidReleaseBuildTypeBlock,
      /^\s*isShrinkResources\s*=\s*true\s*$/m,
    ) &&
    /lint\s*\{[\s\S]*?abortOnError\s*=\s*true[\s\S]*?checkDependencies\s*=\s*true[\s\S]*?\}/m.test(
      appBuildSource,
    ),
  "ANDROID_PRODUCTION_LINT_OR_MINIFICATION_GATE_MISSING",
);
const androidProductionSigningQualified =
  androidProductionSigning.status === "qualified" &&
  androidProductionSigning.releaseEnabled === true &&
  /^[0-9a-f]{64}$/.test(
    androidProductionSigning.productionAppSigningCertificateSha256 ?? "",
  ) &&
  /^[0-9a-f]{64}$/.test(
    androidProductionSigning.productionUploadCertificateSha256 ?? "",
  ) &&
  androidProductionSigning.retainedProductionCertificateMatched === true &&
  androidProductionSigning.signedBundleCertificateMatched === true &&
  androidProductionSigning.playAppSigningContinuityReviewed === true;
block(
  !androidProductionSigningQualified,
  "ANDROID_PRODUCTION_SIGNING_IDENTITY_NOT_QUALIFIED",
);
assert(
  encryptionUtil.includes('KEY_ALIAS = "key_alias"') &&
    encryptionUtil.includes("hasWrappedWalletKey || hasExistingWalletMaterial()") &&
    /if \(encryptedKey\.isEmpty\(\)\) \{[\s\S]*if \(hasExistingWalletMaterial\(\)\) \{[\s\S]*throw KeyMaterialUnavailableException[\s\S]*KeyGenerator\.getInstance/.test(
      encryptionUtil,
    ),
  "EXISTING_WALLET_COULD_RECREATE_KEYSTORE_OR_WRAPPED_KEY",
);
assert(!database.includes("fallbackToDestructiveMigration"), "DESTRUCTIVE_ROOM_FALLBACK");
assert(
  walletUpgradeBackup.includes('WalletUpgradeBackupException("INVALID_DATABASE_VERSION")') &&
    walletUpgradeBackup.includes(
      'WalletUpgradeBackupException("DATABASE_DOWNGRADE_UNSUPPORTED")',
    ) &&
    walletUpgradeBackup.includes(
      'WalletUpgradeBackupException("ORPHANED_WALLET_STORAGE")',
    ) &&
    walletUpgradeBackup.includes("val retainedBackupEvidence =") &&
    /if \(!database\.existsNoFollow\(\)\) \{[\s\S]*retainedBackupEvidence[\s\S]*WalletUpgradeBackupException\("ORPHANED_WALLET_STORAGE"\)/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackup.includes("walletPreferenceInventory(context)") &&
    walletUpgradeBackup.includes("val hasWrappedWalletKeyEvidence =") &&
    walletUpgradeBackup.includes('it.key == "secret_key"') &&
    walletUpgradeBackup.includes("readWalletDatabaseInventory") &&
    walletUpgradeBackup.includes("validateCopiedWalletInventory") &&
    walletUpgradeBackup.includes("PRAGMA integrity_check") &&
    walletUpgradeBackup.includes("FAIL_CLOSED_DATABASE_ERROR_HANDLER") &&
    walletUpgradeBackup.includes('WalletUpgradeBackupException("DATABASE_CORRUPT")') &&
    walletUpgradeBackupTest.includes(
      "corruptLiveDatabaseInspectionNeverDeletesOrRewritesRecoveryBytes",
    ) &&
    walletUpgradeBackup.includes("validateMigrationJournal") &&
    walletUpgradeBackup.includes("readPreferenceDataStoreEntries") &&
    walletUpgradeBackup.includes("readSharedPreferenceEntries") &&
    walletUpgradeBackup.includes("sora_prefs.xml.bak") &&
    walletUpgradeBackup.includes("key_alias.xml.bak") &&
    walletUpgradeBackup.includes("sourceFingerprint") &&
    walletUpgradeBackup.includes("isPristineCurrentSchemaNamespace") &&
    walletUpgradeBackup.includes("publishVerifiedSnapshotBackup") &&
    walletUpgradeBackup.includes("decodePreferenceUtf8") &&
    walletUpgradeBackup.includes("if (!hasValue)") &&
    walletUpgradeBackup.includes("requiredNumericAttribute") &&
    walletUpgradeBackup.includes("WalletPreferenceIntegrity.hash") &&
    walletUpgradeBackup.includes('"INSUFFICIENT_BACKUP_STORAGE"') &&
    walletUpgradeBackup.includes("BACKUP_SPACE_MULTIPLIER = 2L") &&
    walletUpgradeBackup.includes(
      "OLDEST_RECOVERABLE_BACKUP_TARGET_VERSION = 75",
    ) &&
    walletUpgradeBackup.includes("verified-upgrade-backup") &&
    walletUpgradeBackup.includes("live-encrypted-storage") &&
    walletUpgradeBackup.includes("RECOVERY_SOURCE_CHANGED") &&
    walletUpgradeBackup.includes("recovery-manifest.json") &&
    walletUpgradeBackup.includes("publishRecoveryArchiveNoReplace") &&
    walletUpgradeBackup.includes("validateRecoveryArchive") &&
    walletUpgradeBackup.includes("ZipFile(archive)") &&
    walletUpgradeBackup.includes("actualNames.toSet() != expectedNames") &&
    walletUpgradeBackup.includes("zip.requireExactEntry") &&
    walletUpgradeBackup.includes("copyRegularNoReplace") &&
    walletUpgradeBackup.includes(
      'destinationExistsFailureCode = "RECOVERY_EXPORT_EXISTS"',
    ) &&
    walletUpgradeBackup.includes("error.errno == OsConstants.EEXIST") &&
    walletUpgradeBackup.includes("requireExactCopiedBackupTree") &&
    walletUpgradeBackup.includes("regularLinkIdentityNoFollow") &&
    walletUpgradeBackup.includes("RegularLinkIdentity") &&
    walletUpgradeBackup.includes("stat.st_nlink") &&
    /private data class RegularLinkIdentity\(\s*val inode: InodeIdentity,\s*val linkCount: Long,\s*\)/.test(
      walletUpgradeBackup,
    ) &&
    /private fun File\.regularLinkIdentityNoFollow\([\s\S]*?InodeIdentity\([\s\S]*?device = stat\.st_dev[\s\S]*?inode = stat\.st_ino[\s\S]*?size = stat\.st_size[\s\S]*?linkCount = stat\.st_nlink/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackup.includes("removeOwnedRegularLink") &&
    walletUpgradeBackup.includes("RECOVERY_EXPORT_STAGING_CLEANUP_FAILED") &&
    !walletUpgradeBackup.includes("archive.delete()") &&
    !walletUpgradeBackup.includes("staging.renameTo(archive)") &&
    !walletUpgradeBackup.includes("staging.renameTo(destination)") &&
    !walletUpgradeBackup.includes(".renameTo(") &&
    !walletUpgradeBackup.includes("deleteRecursively") &&
    !walletUpgradeBackup.includes("Os.link(") &&
    /private fun publishRecoveryArchiveNoReplace\([\s\S]*?requireRegularLinkCount\([\s\S]*?expectedLinkCount = 1L[\s\S]*?validateRecoveryArchive\([\s\S]*?expectedIdentity = stagedIdentity[\s\S]*?expectedLinkCount = 1L[\s\S]*?copyRegularNoReplace\([\s\S]*?destinationExistsFailureCode = "RECOVERY_EXPORT_EXISTS"[\s\S]*?val archiveIdentity = archive\.regularInodeIdentityNoFollow[\s\S]*?requireRegularLinkCount\([\s\S]*?file = staging[\s\S]*?expectedLinkCount = 1L[\s\S]*?validateRecoveryArchive\([\s\S]*?expectedIdentity = archiveIdentity[\s\S]*?expectedLinkCount = 1L[\s\S]*?removeOwnedRegularLink\([\s\S]*?expectedLinkCountBefore = 1L[\s\S]*?remainingLink = null[\s\S]*?expectedRemainingLinkCountAfter = null[\s\S]*?fsyncRegularFileNoFollow\([\s\S]*?validateRecoveryArchive\([\s\S]*?expectedIdentity = archiveIdentity[\s\S]*?expectedLinkCount = 1L[\s\S]*?return archive\s*\}/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackup.includes("RECOVERY_EXPORT_PUBLISH_FAILED"),
  "INVALID_LEGACY_DATABASE_NOT_FAIL_CLOSED",
);
assert(
  walletUpgradeBackup.includes("MAX_BACKUP_NAMESPACE_ENTRIES") &&
    walletUpgradeBackup.includes("strictRegularFiles") &&
    walletUpgradeBackup.includes("LinkOption.NOFOLLOW_LINKS") &&
    walletUpgradeBackup.includes("isRegularFileNoFollow") &&
    walletUpgradeBackup.includes("readBoundedBytesNoFollow") &&
    walletUpgradeBackup.includes("readBoundedUtf8NoFollow") &&
    walletUpgradeBackup.includes("openRegularNoFollow") &&
    walletUpgradeBackup.includes("openNewRegularNoFollow") &&
    walletUpgradeBackup.includes("inspectRegularNoFollow") &&
    walletUpgradeBackup.includes("OpenedRegularFile") &&
    walletUpgradeBackup.includes("OpenedRegularOutput") &&
    walletUpgradeBackup.includes("Closeable") &&
    walletUpgradeBackup.includes("private fun closeStreamAndDescriptor(") &&
    /private class OpenedRegularFile\([\s\S]*?\) : Closeable \{\s*override fun close\(\) \{\s*WalletUpgradeBackup\.closeStreamAndDescriptor\(\s*closeStream = input::close,\s*descriptor = descriptor,\s*\)\s*\}\s*\}/.test(
      walletUpgradeBackup,
    ) &&
    /private class OpenedRegularOutput\([\s\S]*?\) : Closeable \{\s*override fun close\(\) \{\s*WalletUpgradeBackup\.closeStreamAndDescriptor\(\s*closeStream = output::close,\s*descriptor = descriptor,\s*\)\s*\}\s*\}/.test(
      walletUpgradeBackup,
    ) &&
    /private fun closeStreamAndDescriptor\([\s\S]*?closeStream\(\)[\s\S]*?Os\.close\(descriptor\)[\s\S]*?failure\?\.let \{ throw it \}/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackup.includes("if (descriptor.valid())") &&
    !walletUpgradeBackup.includes("opened.input.use") &&
    !walletUpgradeBackup.includes("openedSource.input.use") &&
    walletUpgradeBackup.includes("OsConstants.O_NOFOLLOW") &&
    walletUpgradeBackup.includes("OsConstants.O_EXCL") &&
    !walletUpgradeBackup.includes("OsConstants.O_CLOEXEC") &&
    walletUpgradeBackup.includes("Os.fstat") &&
    walletUpgradeBackup.includes("directoryIdentityNoFollow") &&
    walletUpgradeBackup.includes("regularFileIdentityNoFollow") &&
    walletUpgradeBackup.includes('"DATABASE_PATH_INVALID"') &&
    walletUpgradeBackup.includes(".filter { it.existsNoFollow() }") &&
    walletUpgradeBackup.includes("BACKUP_FORMAT_VERSION = 3") &&
    walletUpgradeBackup.includes("backupGeneration") &&
    walletUpgradeBackup.includes("backupDirectoryName") &&
    walletUpgradeBackup.includes("backupGenerationFromValidatedName") &&
    walletUpgradeBackup.includes("verifiedBackupNamespace") &&
    walletUpgradeBackup.includes("generations.withIndex()") &&
    walletUpgradeBackup.includes("MAX_PUBLISHED_BACKUPS") &&
    walletUpgradeBackup.includes("VerifiedBackupCandidate") &&
    walletUpgradeBackup.includes("BACKUP_PUBLICATION_LOCK") &&
    walletUpgradeBackup.includes("RECOVERY_PUBLICATION_LOCK") &&
    walletUpgradeBackup.includes("withPublicationLock") &&
    walletUpgradeBackup.includes("opened.channel.lock()") &&
    walletUpgradeBackup.includes("pathIdentity.device != stat.st_dev") &&
    walletUpgradeBackup.includes("pathIdentity.inode != stat.st_ino") &&
    walletUpgradeBackup.includes("publishVerifiedBackupTreeNoReplace") &&
    walletUpgradeBackup.includes("createDirectoryExclusive") &&
    walletUpgradeBackup.includes("Os.mkdir(directory.absolutePath, 0x1C0)") &&
    walletUpgradeBackup.includes("copyRegularNoReplace") &&
    walletUpgradeBackup.includes("openedSourceStat.st_dev") &&
    walletUpgradeBackup.includes("destinationIdentity != openedDestinationIdentity") &&
    walletUpgradeBackup.includes("destinationIdentity == expectedSourceIdentity") &&
    walletUpgradeBackup.includes("destinationStatAfterCopy.st_nlink != 1L") &&
    walletUpgradeBackup.includes("fsyncDirectoryTreeBottomUp") &&
    walletUpgradeBackup.includes("fsyncBackupTreeFiles") &&
    walletUpgradeBackup.includes("fsyncRegularFileNoFollow") &&
    walletUpgradeBackup.includes("captureExactBackupTree") &&
    walletUpgradeBackup.includes("requireExactCopiedBackupTree") &&
    walletUpgradeBackup.includes("requireBackupTreeLinkCounts") &&
    walletUpgradeBackup.includes("removeExactOwnedBackupTree") &&
    walletUpgradeBackup.includes("removeExactValidationTree") &&
    walletUpgradeBackup.includes("BACKUP_VALIDATION_CLEANUP_FAILED") &&
    walletUpgradeBackup.includes("expectedParentIdentity") &&
    walletUpgradeBackup.includes("hasExactRegularLinkInventory") &&
    /private fun hasExactRegularLinkInventory\([\s\S]*?expectedFingerprints: Map<String, FileFingerprint>[\s\S]*?val linksBefore[\s\S]*?val fingerprints[\s\S]*?val linksAfter[\s\S]*?linksBefore == expected[\s\S]*?linksAfter == expected[\s\S]*?fingerprints == expectedFingerprints/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackup.includes("Os.remove(file.absolutePath)") &&
    walletUpgradeBackup.includes("Os.remove(directory.absolutePath)") &&
    walletUpgradeBackup.includes("requireInstalledSourceUnchanged") &&
    walletUpgradeBackup.includes("snapshotBefore != sourceSnapshot") &&
    walletUpgradeBackup.includes("snapshotAfter != sourceSnapshot") &&
    /if \(needsCurrentSchemaSnapshot\) \{[\s\S]*?\} else \{\s*val currentSchemaSourceSnapshot = createSourceSnapshot\(\s*context = context,\s*sources = knownWalletFiles\(context, database\)\s*\.filter \{ it\.existsNoFollow\(\) \},\s*legacyWalletIds = legacyWalletIds,\s*selectedWalletId = preferenceInventory\.selectedWalletId,\s*\)\s*beforeFinalSourceValidationForTest\?\.invoke\(\)\s*requireInstalledSourceUnchanged\([\s\S]*?sourceSnapshot = currentSchemaSourceSnapshot,\s*\)\s*\}\s*return/.test(
      walletUpgradeBackup,
    ) &&
    /private fun publishVerifiedBackupTreeNoReplace\([\s\S]*?val stagedTree = captureExactBackupTree\(staging\)[\s\S]*?requireBackupTreeLinkCounts\([\s\S]*?root = staging[\s\S]*?expectedLinkCount = 1L[\s\S]*?filter \{ it\.relativeName != "\.complete" \}[\s\S]*?copyRegularNoReplace\([\s\S]*?requireExactCopiedBackupTree\([\s\S]*?copyRegularNoReplace\([\s\S]*?source = File\(staging, completion\.relativeName\)[\s\S]*?requireBackupTreeLinkCounts\([\s\S]*?root = staging[\s\S]*?expectedLinkCount = 1L[\s\S]*?val publishedTree = captureExactBackupTree\(destination\)[\s\S]*?root = destination[\s\S]*?expected = publishedTree[\s\S]*?expectedLinkCount = 1L[\s\S]*?expectedRegularLinkCount = 1L[\s\S]*?removeExactOwnedBackupTree\([\s\S]*?val publishedTreeAfterCleanup = captureExactBackupTree\(destination\)[\s\S]*?publishedTreeAfterCleanup != publishedTree[\s\S]*?root = destination[\s\S]*?expected = publishedTree[\s\S]*?expectedLinkCount = 1L[\s\S]*?expectedRegularLinkCount = 1L/.test(
      walletUpgradeBackup,
    ) &&
    /private fun validateCopiedWalletInventory\([\s\S]*?createDirectoryExclusive\([\s\S]*?val validationDirectoryIdentity[\s\S]*?finally \{\s*removeExactValidationTree\([\s\S]*?expectedRootIdentity = validationDirectoryIdentity/.test(
      walletUpgradeBackup,
    ) &&
    /private fun removeExactValidationTree\([\s\S]*?expected\.directories\.size != 1[\s\S]*?expectedLinkCount = 1L[\s\S]*?removeOwnedRegularLink\([\s\S]*?expectedLinkCountBefore = 1L[\s\S]*?removeOwnedEmptyDirectory\(/.test(
      walletUpgradeBackup,
    ) &&
    /private fun removeOwnedRegularLink\([\s\S]*?parent\.directoryInodeIdentityNoFollow[\s\S]*?requireOwnedPathImmediatelyBeforeUnlink\(\)\s*requireOwnedPathImmediatelyBeforeUnlink\(\)[\s\S]*?Os\.remove\(file\.absolutePath\)[\s\S]*?parent\.directoryInodeIdentityNoFollow/.test(
      walletUpgradeBackup,
    ) &&
    /filter \{ it\.relativeName != "\.complete" \}[\s\S]*?fsyncDirectoryTreeBottomUp\(destination\)[\s\S]*?source = File\(staging, completion\.relativeName\)[\s\S]*?fsyncDirectoryTreeBottomUp\(destination\)/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackup.includes(
      "val confirmedStrictFiles = strictRegularFiles(directory)",
    ) &&
    walletUpgradeBackup.includes(
      "confirmedFileIdentities != initialFileIdentities",
    ) &&
    /manifestFile\.readBoundedUtf8NoFollow\([\s\S]*?\)\s*== manifestText\s*&&\s*completion\.readBoundedUtf8NoFollow\([\s\S]*?\)\s*== "verified"/.test(
      walletUpgradeBackup,
    ) &&
    /validatedRecords\.all \{ record ->\s*File\(directory, record\.name\)\.sha256\(\s*failureCode = "BACKUP_NAMESPACE_INVALID",\s*expectedSize = record\.size,\s*\) == record\.sha256\s*\}/.test(
      walletUpgradeBackup,
    ) &&
    !walletUpgradeBackup.includes("lastModified()") &&
    !walletUpgradeBackup.includes("walkTopDown") &&
    walletUpgradeBackupTest.includes(
      "interruptedSuccessorForcesLiveFallbackBesideVerifiedBackup",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentSchemaShortcutRejectsInterruptedSiblingNamespace",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentSchemaShortcutRejectsGenerationDirectoryMismatch",
    ) &&
    walletUpgradeBackupTest.includes(
      "recoveryExportRejectsUnexpectedEmptyBackupDirectory",
    ) &&
    walletUpgradeBackupTest.includes(
      "recoveryExportRejectsSymlinkedBackupEntryAndUsesLiveEncryptedStorage",
    ) &&
    walletUpgradeBackupTest.includes(
      "corruptHigherGenerationBackupNeverFallsBackToOlderVerifiedSnapshot",
    ) &&
    walletUpgradeBackupTest.includes(
      "recoveryExportUsesHighestVerifiedGenerationDespiteClockRollback",
    ) &&
    walletUpgradeBackupTest.includes(
      "generationManifestMustMatchImmutableBackupDirectoryName",
    ) &&
    walletUpgradeBackupTest.includes(
      "missingGenerationForcesRecoveryFallbackAndBlocksPreparation",
    ) &&
    walletUpgradeBackupTest.includes(
      "duplicateGenerationAcrossSourceVersionsFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "symlinkedEncryptedKeyAliasBackupFailsBeforeBackupPublication",
    ) &&
    walletUpgradeBackupTest.includes(
      "symlinkedAuthoritativeDataStoreFailsClosedWithoutFollowingTarget",
    ) &&
    walletUpgradeBackupTest.includes(
      "symlinkedAuthoritativeDatabaseFailsBeforeAnySQLiteOpen",
    ) &&
    walletUpgradeBackupTest.includes(
      "partialPublishedBackupWithoutCompletionMarkerBlocksRoomPreparation",
    ) &&
    walletUpgradeBackupTest.includes(
      "recoveryExportFinalNameCollisionNeverReplacesOrDeletesExistingFile",
    ) &&
    walletUpgradeBackupTest.includes(
      "sourceMutationImmediatelyBeforeActivationFailsClosedAfterBackupPublication",
    ) &&
    walletUpgradeBackupTest.includes(
      "databaseBytesMutationImmediatelyBeforeActivationFailsClosedAfterBackupPublication",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentSchemaShortcutRejectsWrappedKeyMutationAtFinalAdmission",
    ) &&
    walletUpgradeBackupTest.includes(
      "publishedBackupWithAnExtraHardLinkFailsClosedAsNonImmutable",
    ),
  "WALLET_BACKUP_NAMESPACE_NOT_FAIL_CLOSED",
);
assert(
  soraApplication.includes("override fun attachBaseContext") &&
    soraApplication.includes("WalletUpgradeBackup.prepare(this)") &&
    database.includes(".openHelperFactory(WalletUpgradeBackup.gatedOpenHelperFactory())") &&
    walletUpgradeBackup.includes("override val readableDatabase") &&
    walletUpgradeBackup.includes("override val writableDatabase"),
  "RECOVERY_DATABASE_OPEN_GATE_MISSING",
);
assert(
  /if \(WalletUpgradeBackup\.blockingFailure\(\) == null\) \{[\s\S]*selectNodeRepository\.getNodes\(\)[\s\S]*selectNodeRepository\.getSelectedNode\(\)[\s\S]*\n        \}\n    \}\n\n    override fun tryToConnect/.test(
    nodeManager,
  ) &&
    userRepository.includes("WalletUpgradeBackup.blockingFailure() == null") &&
    userRepository.includes(
      "WalletRecoveryCapabilityGate.mayResumeWalletDeletion()",
    ) &&
    userRepository.includes("resumePendingWalletDeletionLocked()") &&
    userRepository.includes("initCurSoraAccount()"),
  "RECOVERY_EAGER_DATABASE_CONSUMER_NOT_GATED",
);
assert(
  userRepositorySr25519BoundaryGate,
  "USER_REPOSITORY_SR25519_BOUNDARY_MISSING",
);
assert(
  userRepository.includes('"SELECTED_WALLET_NOT_VERIFIED"') &&
    userRepository.includes("createWalletDeletionPreview") &&
    userRepository.includes("confirmAndExecuteWalletDeletion") &&
    userRepository.includes("resumePendingWalletDeletionLocked") &&
    userRepository.includes("markDeletionDatabaseCommitted") &&
    userRepository.includes("markDeletionPreferencesCommitted") &&
    userRepository.includes("commitWalletDeletionPreferences") &&
    userRepository.includes("beforePreferencesHash") &&
    userRepository.includes("requireWalletPreferenceCoverage") &&
    userRepository.includes("WALLET_DELETION_PREVIEW_UNKNOWN") &&
    userRepository.includes("WalletDeletionIntegrity.snapshotHash") &&
    !userRepository.includes("override suspend fun fullLogout") &&
    !userRepository.includes("override suspend fun clearAccountData") &&
    userRepository.includes("keyPair?.privateKey?.fill(0)") &&
    userRepository.includes('"SORA_ADDRESS_MISMATCH"') &&
    userRepository.includes('"MNEMONIC_UNSUPPORTED"') &&
    userRepository.includes('"LEGACY_SECRET"') &&
    userRepository.includes("verifyLegacySecretSigningKey") &&
    userRepository.includes('"WALLET_SECRET_SOURCE_CONTINUITY_MISMATCH"') &&
    userRepository.includes(
      "words.size == LEGACY_SORA_MNEMONIC_WORD_COUNT",
    ) &&
    userRepositoryTest.includes(
      "deletion preview verifies retained fifteen word wallet without deleting it",
    ) &&
    userRepositoryTest.includes(
      "deletion preview rejects retained fifteen word signing mismatch before journal",
    ),
  "ACCOUNT_LIFECYCLE_WALLET_SAFETY_MISSING",
);
assert(
  walletMutationCoordinator.includes("private val mutex = Mutex()") &&
    walletMutationCoordinator.includes("currentCoroutineContext()[LockContextKey]") &&
    walletMutationCoordinatorTest.includes(
      "same coroutine may enter signing while wallet mutation is locked",
    ) &&
    userRepository.includes("private val mutex = WalletMutationCoordinator") &&
    extrinsicManager.includes("WalletMutationCoordinator.withLock") &&
    extrinsicManager.includes("requireSigningWalletAvailable") &&
    extrinsicManager.includes("hasActiveDeletionOperation") &&
    extrinsicManager.includes('"WALLET_DELETION_ACTIVE"') &&
    extrinsicManager.includes("submitGenericPreparedOnceUnlocked") &&
    !extrinsicManager.includes("stateIn(coroutineManager.applicationScope)") &&
    extrinsicManagerSafetyTest.includes(
      "bounded submission followed by unresolved finality is submission unknown",
    ) &&
    polkamarktTrader.includes("withWalletMutationLocked") &&
    walletIdentityDao.includes("countUnresolvedTransactionsForJournal") &&
    walletIdentityDao.includes("countPendingTransactionRowsForJournal") &&
    walletIdentityDao.includes('"WALLET_DELETION_ACTIVE"'),
  "SORA2_SIGNING_NOT_GATED_DURING_WALLET_DELETION",
);
assert(
  extrinsicManager.includes("interface DefinitelyNotSubmitted") &&
    extrinsicManager.includes(
      "class PreparedExtrinsicPreTransportException",
    ) &&
    extrinsicManager.includes(
      "class PreparedExtrinsicPreTransportCancellation",
    ) &&
    extrinsicManager.includes(
      "preTransportValidation: suspend () -> Unit = {}",
    ) &&
    extrinsicManager.includes("class PreparedExtrinsic internal constructor(") &&
    !extrinsicManager.includes("data class PreparedExtrinsic") &&
    extrinsicManager.includes(
      "internal val signingRuntime: Sora2MutationRuntimeContext",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "internal class ContextBoundExtrinsicBuilder internal constructor(",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "internal suspend fun createForSigning(",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "runtimeContext: Sora2MutationRuntimeContext",
    ) &&
    !/suspend fun create\(\s*from: String,\s*keypair: Sr25519Keypair/.test(
      extrinsicBuilderFactorySource,
    ) &&
    /val signingRuntime = runtimeManager\.getMutationRuntimeContext\(\)[\s\S]*?factory\.createForSigning\(from, keypair, signingRuntime\)[\s\S]*?signingRuntime = contextBoundBuilder\.runtimeContext/.test(
      extrinsicManager,
    ) &&
    /private suspend fun requirePreparedExtrinsicPreTransport\([\s\S]*?preTransportValidation\(\)[\s\S]*?requireSigningWalletAvailable\(prepared\.walletId\)[\s\S]*?val currentRuntime = runtimeManager\.getMutationRuntimeContext\(\)[\s\S]*?requireSameSora2MutationRuntimeIdentity\([\s\S]*?signing = prepared\.signingRuntime,[\s\S]*?current = currentRuntime/.test(
      extrinsicManager,
    ) &&
    /val returnedHash = try \{[\s\S]*?runtimeRpcClient\.submitExtrinsicOnce\(prepared\.encoded\)/.test(
      extrinsicManager,
    ) &&
    extrinsicManagerSafetyTest.includes(
      "pre transport validation failure is definitive and never opens RPC",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "wallet invalidated after callback is definitive and never opens RPC",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "pre transport cancellation is definitive and never opens RPC",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "generic preparation retains the exact context returned with its signing builder",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "ordinary fee keeps behavior while passing one exact runtime context",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "every prepared runtime identity drift is definitive and never opens RPC",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "fresh bounded runtime is checked only after caller pre transport validation",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "finalized head advance passes identity validation and reaches transport",
    ) &&
    sora2RuntimeContract.includes("SORA2_MUTATION_MORTAL_ERA_EXPIRED") &&
    extrinsicManagerSafetyTest.includes(
      "expired mortal era is definitive and never opens RPC",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "one shot runtime drift is definitive and does not enter SubstrateCalls",
    ) &&
    (
      extrinsicManagerSafetyTest.match(
        /coVerify\(exactly = 0\)[\s\S]*?submitExtrinsicOnce/g,
      ) ?? []
    ).length >= 2,
  "SORA2_PREPARED_EXTRINSIC_TRANSPORT_BOUNDARY_MISSING",
);
assert(
  extrinsicManager.includes("internal fun requireExactSora2TransferFee(") &&
    extrinsicManager.includes("suspend fun submitFeeQualifiedAndWatchExtrinsic(") &&
    /submitGenericPreparedOnceUnlocked\(prepared\) \{[\s\S]*?calls\.getExtrinsicFee\(prepared\.encoded\)[\s\S]*?validateBalances\(exactFee\)/.test(
      extrinsicManager,
    ) &&
    extrinsicManager.includes("SORA2_TRANSFER_FEE_CHANGED") &&
    assetsRepositoryImpl.includes(
      "extrinsicManager.submitFeeQualifiedAndWatchExtrinsic(",
    ) &&
    assetsRepositoryImpl.includes("requireExactTransferBalances(") &&
    assetsRepositoryImpl.includes(
      "substrateCalls.fetchXORBalances(from)",
    ) &&
    assetsRepositoryImpl.includes(
      "SORA2_TRANSFER_XOR_BALANCE_INSUFFICIENT",
    ) &&
    assetsRepositoryImpl.includes(
      "SORA2_TRANSFER_ASSET_BALANCE_INSUFFICIENT",
    ) &&
    transferAmountViewModel.includes(
      "feeAsset?.let { calcTransactionFee(it, amount) }",
    ) &&
    /interactor\.calcTransactionFee\(\s*recipientId\s*,\s*token\s*,\s*amount\s*,/.test(
      transferAmountViewModel,
    ) &&
    transferAmountViewModel.includes("private fun invalidateFeeReview()") &&
    /fun amountChanged\(value: BigDecimal\) \{[\s\S]*?invalidateFeeReview\(\)[\s\S]*?enteredFlow\.value = value/.test(
      transferAmountViewModel,
    ) &&
    !transferAmountViewModel.includes(
      "calcTransactionFee(feeAsset, BigDecimal.ONE)",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "requireExactSora2TransferFee(expectedFee, expectedFee)",
    ) &&
    extrinsicManagerSafetyTest.includes("SORA2_TRANSFER_FEE_CHANGED") &&
    assetsRepositoryTest.includes(
      "submitFeeQualifiedAndWatchExtrinsic(",
    ) &&
    transferAmountViewModelTest.includes(
      "TestTokens.xorToken,\n                    BigDecimal.ONE",
    ),
  "SORA2_ORDINARY_TRANSFER_EXACT_FEE_GATE_MISSING",
);
assert(
  extrinsicManager.includes("interface ExtrinsicSubmissionUnknown") &&
    extrinsicManager.includes("class ExtrinsicSubmissionUnknownException") &&
    extrinsicManager.includes("class ExtrinsicSubmissionUnknownCancellation") &&
    extrinsicManager.includes("throw submissionUnknown(expectedHash, error)") &&
    extrinsicManager.includes("awaitTerminalStatus(submitted)") &&
    extrinsicManager.includes("notifyWatchingListenerBestEffort(result)") &&
    extrinsicManager.includes("recordErrorClassBestEffort(") &&
    /val returnedHash = try \{[\s\S]*?runtimeRpcClient\.submitExtrinsicOnce\(prepared\.encoded\)[\s\S]*?throw submissionUnknown\(expectedHash, error\)[\s\S]*?returnedHash == expectedHash/.test(
      extrinsicManager,
    ) &&
    extrinsicManagerSafetyTest.includes(
      "bounded HTTP failure after handoff is submission unknown with exact local hash",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "cancellation after bounded handoff is submission unknown cancellation",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "bounded returned hash mismatch after handoff is submission unknown",
    ) &&
    boundedRuntimeRpcClientSource.includes(
      'METHOD_SUBMIT_EXTRINSIC = "author_submitExtrinsic"',
    ) &&
    boundedRuntimeRpcClientSource.includes(
      "submitExtrinsicOnce(encodedExtrinsic: String)",
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "signed submission is one bounded exact hash checked request",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "one shot RPC failure is submission unknown with exact local hash",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "one shot returned hash mismatch is submission unknown",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "one shot signing failure is definitive and never opens RPC",
    ),
  "SORA2_POST_HANDOFF_AMBIGUITY_CLASSIFICATION_MISSING",
);
assert(
  substrateCalls.includes("finalizedKey: String = FINALIZED") &&
    !substrateCalls.includes("finalizedKey: String = IN_BLOCK"),
  "SORA2_WATCH_FINALITY_DEFAULT_UNSAFE",
);
assert(
  !referralRepository.includes("SubstrateCalls.IN_BLOCK") &&
    (referralRepository.match(/untilStatus = SubstrateCalls\.FINALIZED/g) ?? [])
      .length === 3 &&
    referralRepository.includes("override suspend fun observeSetReferrer(") &&
    referralRepository.includes("override suspend fun observeUnbond(") &&
    referralRepository.includes("override suspend fun observeBond(") &&
    referralRepositoryTest.includes(
      "referral mutation waits for canonical finality",
    ) &&
    referralRepositoryTest.includes(
      "untilStatus = eq(SubstrateCalls.FINALIZED)",
    ) &&
    referralRepositoryTest.includes(
      "verify(extrinsicManager, times(3)).submitAndWaitExtrinsic(",
    ),
  "SORA2_REFERRAL_FINALITY_GATE_MISSING",
);
assert(
  assetsInteractorImpl.includes("private data class TransferSubmissionOutcome") &&
    assetsInteractorImpl.includes("error !is ExtrinsicSubmissionUnknown") &&
    assetsInteractorImpl.includes("transactionHash = error.transactionHash") &&
    assetsInteractorImpl.includes("blockHash = null") &&
    assetsInteractorImpl.includes("persistTransferHistoryBestEffort(") &&
    assetsInteractorImpl.includes("keypair.privateKey.fill(0)") &&
    assetsInteractorImpl.includes("keypair.nonce.fill(0)") &&
    /val outcome = try \{[\s\S]*?assetsRepository\.observeTransfer\([\s\S]*?\} catch \(error: Throwable\) \{[\s\S]*?error !is ExtrinsicSubmissionUnknown[\s\S]*?\} finally \{[\s\S]*?privateKey\.fill\(0\)[\s\S]*?nonce\.fill\(0\)/.test(
      assetsInteractorImpl,
    ) &&
    /private fun persistTransferHistoryBestEffort\([\s\S]*?saveTransaction\([\s\S]*?\} catch \(_: Throwable\) \{[\s\S]*?try \{[\s\S]*?recordErrorClass\(/.test(
      assetsInteractorImpl,
    ) &&
    assetsInteractorTest.includes(
      "confirmed transfer survives local history persistence failure",
    ) &&
    assetsInteractorTest.includes(
      "submission unknown transfer is persisted pending and returns exact hash",
    ) &&
    assetsInteractorTest.includes(
      "unrelated transfer failure propagates after keypair zeroization",
    ),
  "SORA2_CONFIRMED_TRANSFER_LOCAL_PERSISTENCE_SAFETY_MISSING",
);
assert(
  pinCodeViewModel.includes("handleWalletDeletionError") &&
    pinCodeViewModel.includes("walletDeletionFailureCode") &&
    pinCodeViewModel.includes("finally") &&
    pinCodeViewModel.includes("progress.hideProgress()") &&
    pinCodeViewModel.includes("publishDeletionPreview") &&
    pinCodeViewModelTest.includes(
      "journaled deletion failure hides progress and restarts into recovery",
    ) &&
    pinCodeViewModelTest.includes(
      "safe confirmation failure requires a newly verified preview",
    ),
  "WALLET_DELETION_RECOVERY_UI_MISSING",
);
assert(
  database.includes("version = 77") &&
    database.includes("Sora2PendingSubmissionLocal::class") &&
    database.includes("migration_sora2PendingSubmission_75_76") &&
    database.includes("migration_pendingNetworkTransactionChain_76_77") &&
    database.includes("WalletDeletionOperationLocal::class") &&
    database.includes("WalletDeletionTargetLocal::class") &&
    walletDeletionMigration.includes("Migration(74, 75)") &&
    walletDeletionMigration.includes("walletDeletionOperations") &&
    walletDeletionMigration.includes("walletDeletionTargets") &&
    walletDeletionMigration.includes("beforePreferencesHash") &&
    walletDeletionMigration.includes("afterPreferencesHash") &&
    walletIdentityMigration.includes("WALLET_IDENTITY_DESTINATION_ALREADY_EXISTS") &&
    walletDeletionMigration.includes("WALLET_DELETION_DESTINATION_ALREADY_EXISTS") &&
    walletIdentityMigration.includes("name COLLATE NOCASE = ?") &&
    walletDeletionMigration.includes("name COLLATE NOCASE = ?") &&
    sora2PendingSubmissionMigration.includes("Migration(75, 76)") &&
    sora2PendingSubmissionMigration.includes("name COLLATE NOCASE = ?") &&
    sora2PendingSubmissionMigration.includes("SORA2_PENDING_DESTINATION_ALREADY_EXISTS") &&
    !walletIdentityMigration.includes("INSERT OR IGNORE") &&
    walletIdentityDao.includes("and not exists (select 1 from walletDeletionOperations)") &&
    walletIdentityDao.includes("activeSlot = 1") &&
    walletIdentityDao.includes("beginDeletionOperation") &&
    walletIdentityDao.includes("countUnresolvedTransactionsForJournal(targetIds.toList()) == 0") &&
    walletIdentityDao.includes("getPendingTransactionsForJournal") &&
    walletIdentityDao.includes("transactions.forEach(::validatePendingTransaction)") &&
    walletIdentityDao.includes(
      "it.state !in TERMINAL_PENDING_STATES || !hasCurrentChainIdentity(it)",
    ) &&
    walletIdentityDao.includes("phase = 'DATABASE_COMMITTED'") &&
    walletIdentityDao.includes("phase = 'PREFERENCES_COMMITTED'") &&
    walletIdentityDao.includes("current.state in TERMINAL_PENDING_STATES") &&
    walletIdentityDao.includes("monotonicUpdatedAt = maxOf(updatedAt, current.updatedAt)") &&
    userRepository.includes("requireNoUnresolvedDeletionTransactions") &&
    userRepository.includes('"WALLET_DELETION_PENDING_JOURNAL_INVALID"') &&
    userRepositoryTest.includes(
      "deletion preview fails closed when pending journal is unreadable",
    ) &&
    retainedSchemaMigrationTest.includes(
      "A process death must not turn a malformed terminal-looking Nexus/Polkamarkt",
    ) &&
    walletPreferenceKeys.includes("encryptedCredentialPrefixes") &&
    walletPreferenceKeys.includes("scopedBooleanKeys") &&
    walletIdentityDao.includes("recordMigrationFailure"),
  "JOURNALED_WALLET_DELETION_MISSING",
);
assert(
    pendingNetworkTransactionChainMigration.includes("Migration(76, 77)") &&
    pendingNetworkTransactionChainMigration.includes("requirePendingSourceSchema(database)") &&
    pendingNetworkTransactionChainMigration.includes("requirePendingSourceIndexes(database)") &&
    pendingNetworkTransactionChainMigration.includes(
      "requirePendingSourceForeignKeys(database)",
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      "requirePendingSourceTriggersAbsent(database)",
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      'PendingSourceColumn("transactionHash", "TEXT", notNull = false',
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      'check(actual == expected) { "PENDING_CHAIN_SOURCE_SCHEMA_MISMATCH" }',
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      'check(actual == expected) { "PENDING_CHAIN_SOURCE_INDEX_MISMATCH" }',
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      'check(actual == expected) { "PENDING_CHAIN_SOURCE_FOREIGN_KEY_MISMATCH" }',
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      'type = \'trigger\' AND tbl_name = \'pendingNetworkTransactions\'',
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      "pendingNetworkTransactions_v77_copy",
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      "`localId`, `walletId`, `networkId`, NULL, `transactionHash`",
    ) &&
    (pendingNetworkTransactionChainMigration.match(/EXCEPT/g) ?? []).length >= 2 &&
    pendingNetworkTransactionChainMigration.includes(
      "WHERE `chainId` IS NOT NULL",
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      "PENDING_CHAIN_COPY_SOURCE_MISMATCH",
    ) &&
    pendingNetworkTransactionChainMigration.includes(
      "PENDING_CHAIN_COPY_DESTINATION_MISMATCH",
    ) &&
    !/delete\s+from\s+`?pendingNetworkTransactions/i.test(
      pendingNetworkTransactionChainMigration,
    ) &&
    pendingNetworkTransactionModel.includes("val chainId: String?") &&
    pendingNetworkTransactionModel.includes(
      'Index(value = ["networkId", "chainId", "transactionHash"], unique = true)',
    ) &&
    walletIdentityDao.includes("validatePendingTransactionForWrite(transaction)") &&
    walletIdentityDao.includes("hasCurrentChainIdentity(transaction)") &&
    walletIdentityDao.includes("countPendingTransactionsRequiringChainRecovery() == 0") &&
    walletIdentityDao.includes(
      "it.state !in TERMINAL_PENDING_STATES || !hasCurrentChainIdentity(it)",
    ) &&
    (walletIdentityDao.match(/\b(?:where|or) chainId is null/g) ?? []).length >= 3 &&
    walletIdentityDao.includes("chainId = :chainId") &&
    walletIdentityDao.includes("chainId is null and :chainId is null") &&
    retainedSchemaMigrationTest.includes(
      "pendingChainMigrationPreservesEveryLegacyFieldAndLeavesIdentityUnbound",
    ) &&
    retainedSchemaMigrationTest.includes(
      "pendingChainMigrationRejectsCopyNamespaceCollisionBeforeWrites",
    ) &&
    retainedSchemaMigrationTest.includes(
      "pendingChainMigrationRejectsAnUnexpectedSourceShapeBeforeWrites",
    ) &&
    retainedSchemaMigrationTest.includes(
      "pendingChainMigrationRejectsAnUnexpectedSourceIndexBeforeWrites",
    ) &&
    retainedSchemaMigrationTest.includes(
      "pendingChainMigrationRejectsAnUnexpectedSourceForeignKeyBeforeWrites",
    ) &&
    retainedSchemaMigrationTest.includes(
      "pendingChainMigrationRejectsAnUnexpectedSourceTriggerBeforeWrites",
    ) &&
    retainedSchemaMigrationTest.includes(
      "retained-historical-terminal",
    ) &&
    retainedSchemaMigrationTest.includes(
      "countUnresolvedTransactionsForJournal",
    ),
  "PENDING_TRANSACTION_CHAIN_IDENTITY_MIGRATION_MISSING",
);
assert(
  sora2PendingSubmissionModel.includes('tableName = "sora2PendingSubmissions"') &&
    sora2PendingSubmissionModel.includes("eraDeathBlockExclusive") &&
    sora2PendingSubmissionModel.includes("metadataSha256") &&
    sora2PendingSubmissionModel.includes("typesSha256") &&
    sora2PendingSubmissionCoordinator.includes("stageBeforeTransport") &&
    sora2PendingSubmissionCoordinator.includes("Sora2LegacyMigrationAdmissionGate") &&
    sora2PendingSubmissionCoordinator.includes(
      "SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED",
    ) &&
    extrinsicManager.includes("submitLegacyMigrationAndWaitExtrinsic") &&
    extrinsicManager.includes("PreparedPurpose.LEGACY_MIGRATION") &&
    extrinsicManager.includes(
      "Sora2LegacyMigrationAdmissionGate.requireNoUnresolvedWitness",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "legacy migration refuses an unresolved witness before signing",
    ) &&
    walletRepository.includes("submitLegacyMigrationAndWaitExtrinsic") &&
    sora2PendingRecoveryBatchPlannerTest.includes(
      "unresolved generic witness blocks a second legacy migration for the same wallet",
    ) &&
    sora2PendingRecoveryBatchPlannerTest.includes(
      "legacy migration gate ignores other wallets and Polkamarkt witnesses",
    ) &&
    sora2PendingSubmissionCoordinator.includes("armForTransport") &&
    sora2PendingSubmissionCoordinator.includes("recoverStatusOnly") &&
    sora2PendingSubmissionCoordinator.includes("recoveryBatchCursor") &&
    sora2PendingSubmissionCoordinator.includes("Sora2PendingRecoveryBatchPlanner.select") &&
    sora2PendingRecoveryBatchPlannerTest.includes(
      "failing first batch rotates to every later witness",
    ) &&
    sora2PendingRecoveryBatchPlannerTest.includes(
      "foreground priority does not disable bounded rotation",
    ) &&
    sora2PendingSubmissionCoordinator.includes("getCanonicalBlock(") &&
    sora2PendingSubmissionCoordinator.includes("getStorageAt(") &&
    sora2PendingSubmissionCoordinator.includes(
      "getReviewedRecoveryRuntimeSnapshotOrNull",
    ) &&
    runtimeManagerSource.includes(
      "getReviewedRecoveryRuntimeSnapshotOrNull",
    ) &&
    runtimeManagerSource.includes("fixture.metadataSha256 == metadataSha256") &&
    runtimeManagerSource.includes("fixture.typesSha256 == typesSha256") &&
    sora2PendingSubmissionCoordinator.includes("STATE_EXPIRED_NOT_INCLUDED") &&
    !sora2PendingSubmissionCoordinator.includes("submitExtrinsic(") &&
    !sora2PendingSubmissionCoordinator.includes("Sr25519Keypair") &&
    walletIdentityDao.includes("getSora2PendingSubmissionsForJournal") &&
    walletIdentityDao.includes("sora2Submissions.forEach(::validateSora2PendingSubmission)") &&
    walletIdentityDao.includes("getSora2SubmissionOverlayRows") &&
    walletIdentityDao.includes(
      "countPrunableTerminalSora2PendingSubmissionsInternal",
    ) &&
    walletIdentityDao.includes("pending.transactionHash = sora2.transactionHash") &&
    walletIdentityDao.includes("pending.state not in ('FINALIZED', 'REJECTED')") &&
    extrinsicManager.includes("stageBeforeTransport(prepared)") &&
    extrinsicManager.includes("armForTransport(staged)") &&
    transactionHistoryRepository.includes("durableHistoryMerge(") &&
    transactionHistoryRepository.includes("OPERATION_GENERIC") &&
    transactionHistoryRepository.includes("indexedHashes") &&
    transactionHistoryRepository.includes(
      "transaction.base.status = row.toTransactionStatus()",
    ) &&
    transactionHistoryRepository.includes(
      "it.base.status == TransactionStatus.PENDING",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "restores generic SORA2 pending journal after restart",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "exact PI hash suppresses generic overlay without deleting journal evidence",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "exact durable witness overrides PI execution status",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "old terminal generic witness does not crowd newer PI history",
    ) &&
    sora2PendingRecoveryWorker.includes("Sora2PendingRecoveryWorker") &&
    sora2PendingRecoveryWorker.includes("Result.retry()") &&
    soraApplication.includes("sora2PendingRecoveryScheduler.ensureOnStartup()"),
  "SORA2_GENERIC_AMBIGUITY_RECOVERY_MISSING",
);
block(
  extrinsicManager.includes("calls.submitExtrinsic(prepared.encoded)") ||
    extrinsicManager.includes("calls.submitAndWatchExtrinsic(") ||
    extrinsicManager.includes("calls.getBlock(canonicalBlockHash)") ||
    extrinsicManager.includes("calls.checkEvents(blockHash)"),
  "SORA2_SIGNED_SUBMISSION_STILL_USES_UNBOUNDED_WEBSOCKET",
);
block(
  polkamarktTrader.includes("extrinsicManager.reconcileFinalized") ||
    polkamarktTrader.includes("PolkamarktPendingReconciliationValidator") ||
    /private suspend fun recoverPendingBatch\((?:(?!\n    suspend fun quoteBuy\()[\s\S])*?calls\./.test(
      polkamarktTrader,
    ),
  "POLKAMARKT_FINALITY_RECOVERY_STILL_USES_UNBOUNDED_WEBSOCKET",
);
assert(
  room74SchemaFixture.includes("version = 74") &&
    room74SchemaFixture.includes("WalletIdentityLocal::class") &&
    room74SchemaFixture.includes("PendingNetworkTransactionV76FixtureLocal::class") &&
    !room74SchemaFixture.includes("PendingNetworkTransactionLocal::class") &&
    !room74SchemaFixture.includes("WalletDeletionOperationLocal::class") &&
    !room74SchemaFixture.includes("WalletDeletionTargetLocal::class") &&
    coreDbBuild.includes('add("kspAndroidTest", libs.roomCompilerDep)') &&
    coreDbBuild.includes("@get:InputDirectory") &&
    coreDbBuild.includes("@get:PathSensitive(PathSensitivity.RELATIVE)") &&
    coreDbBuild.includes(
      'getByName("androidTest").assets.directories.add(File(projectDir, "schemas").absolutePath)',
    ),
  "ROOM_SCHEMA_74_COMPILER_FIXTURE_MISSING",
);
assert(
  room75SchemaFixture.includes("version = 75") &&
    room75SchemaFixture.includes("version = 76") &&
    room75SchemaFixture.includes("AppDatabaseV76SchemaFixture") &&
    room75SchemaFixture.includes("WalletIdentityLocal::class") &&
    room75SchemaFixture.includes("PendingNetworkTransactionV76FixtureLocal::class") &&
    room75SchemaFixture.includes("WalletDeletionOperationLocal::class") &&
    room75SchemaFixture.includes("WalletDeletionTargetLocal::class") &&
    room75SchemaFixture.includes("Sora2PendingSubmissionLocal::class") &&
    room75SchemaFixture.includes(
      'Index(value = ["networkId", "transactionHash"], unique = true)',
    ) &&
    !room75SchemaFixture.includes("val chainId: String?"),
  "ROOM_SCHEMA_75_76_COMPILER_FIXTURE_MISSING",
);
assert(
    retainedSchemaMigrationTest.includes(
      'const val PRODUCTION_DATABASE_NAME = "app.db"',
    ) &&
    retainedSchemaMigrationTest.includes(
      'val JOURNAL_MODES = listOf("DELETE", "WAL")',
    ) &&
    retainedSchemaMigrationTest.includes(
      "RETAINED_SCHEMA_VERSIONS.flatMap { sourceVersion ->",
    ) &&
    retainedSchemaMigrationTest.includes(
      "JOURNAL_MODES.map { journalMode -> sourceVersion to journalMode }",
    ) &&
    retainedSchemaMigrationTest.includes(
      "retainedJournalCohorts.forEach { (sourceVersion, requestedJournalMode) ->",
    ) &&
    retainedSchemaMigrationTest.includes(
      "assertEquals(38, retainedJournalCohorts.size)",
    ) &&
    retainedSchemaMigrationTest.includes(
      "assertEquals(38, retainedJournalCohorts.toSet().size)",
    ) &&
    retainedSchemaMigrationTest.includes(
      "everyRetainedSchemaPreservesSingleLegacyAccountAndSelection",
    ) &&
    retainedSchemaMigrationTest.includes(
      "currentSchemaSnapshotUsesTheExactExported77Schema",
    ) &&
    retainedSchemaMigrationTest.includes(
      "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m",
    ) &&
    retainedSchemaMigrationTest.includes(
      "66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972",
    ) &&
    retainedSchemaMigrationTest.includes(
      "WalletMigrationIntegrity.snapshotHash(",
    ) &&
    retainedSchemaMigrationTest.includes(
      "WalletMigrationIds.NETWORK_ACCOUNTS_V1",
    ) &&
    retainedSchemaMigrationTest.includes("sqlite.stringRows(") &&
    retainedSchemaMigrationTest.includes("sqlite.nullableStringRows(") &&
    retainedSchemaMigrationTest.includes(
      'assertEquals(0, sqlite.count("pendingNetworkTransactions"))',
    ) &&
    retainedSchemaMigrationTest.includes(
      'assertEquals(0, sqlite.count("walletDeletionOperations"))',
    ) &&
    retainedSchemaMigrationTest.includes(
      '"PRAGMA journal_mode=$requestedJournalMode"',
    ) &&
    retainedSchemaMigrationTest.includes(
      "forceJournalModeBeforeMigration(requestedJournalMode)",
    ) &&
    retainedSchemaMigrationTest.includes(
      "RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING",
    ) &&
    retainedSchemaMigrationTest.includes(
      "RoomDatabase.JournalMode.TRUNCATE",
    ) &&
    retainedSchemaMigrationTest.includes(
      'val migratedJournalMode = sqlite.query("PRAGMA journal_mode")',
    ) &&
    retainedSchemaMigrationTest.includes(
      "migratedJournalMode.lowercase()",
    ) &&
    retainedSchemaMigrationTest.includes(
      "MIGRATION_JOURNAL_MODE_MISMATCH",
    ) &&
    retainedSchemaMigrationTest.includes(
      "sourceVersion >= WALLET_IDENTITY_SCHEMA_VERSION",
    ) &&
    retainedSchemaMigrationTest.includes(
      "These retained v74/v75/v76 cohorts have already completed the 73 -> 74 Room",
    ) &&
    retainedSchemaMigrationTest.includes(
      "INSERT INTO walletIdentities(",
    ) &&
    retainedSchemaMigrationTest.includes(
      "INSERT INTO networkAccounts(",
    ) &&
    retainedSchemaMigrationTest.includes(
      "WalletUpgradeBackup.prepare(context)",
    ) &&
    retainedSchemaMigrationTest.includes(
      "WalletUpgradeBackup.gatedOpenHelperFactory(",
    ) &&
    retainedSchemaMigrationTest.includes("installedBytesBeforeBackup") &&
    retainedSchemaMigrationTest.includes('manifest.getInt("sourceDatabaseVersion")') &&
    retainedSchemaMigrationTest.includes('manifest.getInt("targetDatabaseVersion")') &&
    walletUpgradeBackup.includes(
      "internal fun prepareWithAvailableBackupBytesForTest(",
    ) &&
    walletUpgradeBackup.includes("availableBackupBytesOverride = null") &&
    walletUpgradeBackupTest.includes(
      "reinstallWithRestoredEncryptedWalletStateAndNoDatabaseFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "insufficientBackupStorageFailsBeforeChangingInstalledWalletOrCreatingStaging",
    ) &&
    walletUpgradeBackupTest.includes(
      "appVersionRollbackFromNewerSchemaFailsWithoutChangingInstalledWallet",
    ) &&
    walletUpgradeBackupTest.includes(
      "openWalSidecarsAreCopiedByteForByteBeforeMigrationCanOpenRoom",
    ),
  "ANDROID_RETAINED_WALLET_QUALIFICATION_MATRIX_MISSING",
);
assert(
    retainedSchemaMigrationTest.includes("RETAINED_SCHEMA_VERSIONS = 58..76") &&
    retainedSchemaMigrationTest.includes("walletIdentityMigrationMatchesTheExactExported74Schema") &&
    retainedSchemaMigrationTest.includes("walletDeletionMigrationMatchesTheExactExported75Schema") &&
    retainedSchemaMigrationTest.includes("sora2PendingMigrationMatchesTheExactExported76Schema") &&
    retainedSchemaMigrationTest.includes(
      "pendingChainMigrationPreservesEveryLegacyFieldAndLeavesIdentityUnbound",
    ) &&
    retainedSchemaMigrationTest.includes("helper.runMigrationsAndValidate(") &&
    retainedSchemaMigrationTest.includes(
      "helper.createDatabase(\n                databaseName,\n                sourceVersion,",
    ) &&
    !retainedSchemaMigrationTest.includes(
      "if (sourceVersion == 74) 73 else sourceVersion",
    ) &&
    !retainedSchemaMigrationTest.includes(
      'execSQL("PRAGMA user_version = 74")',
    ) &&
    retainedSchemaMigrationTest.includes("assertEquals(2, sqlite.count(\"accounts\"))") &&
    retainedSchemaMigrationTest.includes("assertExactStagedWalletRows") &&
    retainedSchemaMigrationTest.includes(
      "SELECT walletId, displayName, secretSource, migrationState,",
    ) &&
    retainedSchemaMigrationTest.includes(
      "SELECT walletId, networkId, publicKey, address, derivationPath,",
    ) &&
    retainedSchemaMigrationTest.includes('"PENDING_VERIFICATION"') &&
    retainedSchemaMigrationTest.includes("derivationVersion, enabled") &&
    retainedSchemaMigrationTest.includes(
      "assertEquals(expectedNetworkAccountRows, actualNetworkAccountRows)",
    ) &&
    retainedSchemaMigrationTest.includes("activateMigrationJournal") &&
    retainedSchemaMigrationTest.includes("startedAt = 999") &&
    retainedSchemaMigrationTest.includes('"walletDeletionOperations"') &&
    retainedSchemaMigrationTest.includes("migration_walletDeletionJournal_74_75") &&
    retainedSchemaMigrationTest.includes("migration_sora2PendingSubmission_75_76") &&
    retainedSchemaMigrationTest.includes("migration_pendingNetworkTransactionChain_76_77") &&
    retainedSchemaMigrationTest.includes("beginDeletionOperation") &&
    retainedSchemaMigrationTest.includes("WALLET_DELETION_PENDING_TRANSACTIONS") &&
    retainedSchemaMigrationTest.includes("unexpectedDestinationNamespacesFailBeforeAnyLegacyMutation") &&
    retainedSchemaMigrationTest.includes("CREATE TABLE WalletIdentities") &&
    retainedSchemaMigrationTest.includes("CREATE TABLE WalletDeletionTargets") &&
    retainedSchemaMigrationTest.includes('localId = "terminal-pending-row"') &&
    retainedSchemaMigrationTest.includes("markDeletionDatabaseCommitted") &&
    retainedSchemaMigrationTest.includes("markDeletionPreferencesCommitted") &&
    walletUpgradeBackupTest.includes(
      "missingDatabaseWithEncryptedWalletMarkerFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "missingDatabaseWithOnlyRetainedWrappedAesKeyFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "missingDatabaseWithRetainedUpgradeBackupNeverBecomesAnEmptyWalletInstall",
    ) &&
    walletUpgradeBackupTest.includes(
      "emptyDatabaseWithEncryptedWalletMarkerFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "retainedWalletIsBackedUpWithoutChangingItsLegacyRow",
    ) &&
    walletUpgradeBackupTest.includes(
      "secondWalletSecretWithoutCommittedAccountRowFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "selectedWalletWithoutCommittedAccountRowFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "dataStoreSecondWalletSecretWithoutCommittedAccountRowFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "staleFalseWatchOnlyPreferenceDoesNotInventAnOrphanWallet",
    ) &&
    walletUpgradeBackupTest.includes(
      "emptySelectionAndInitialRegistrationDoNotInventAnOrphanWallet",
    ) &&
    walletUpgradeBackupTest.includes(
      "finishedRegistrationWithoutDatabaseFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "emptyDataStoreSelectionDoesNotInventAnOrphanWallet",
    ) &&
    walletUpgradeBackupTest.includes(
      "clearedEmptyDataStoreDoesNotInventAnOrphanWallet",
    ) &&
    walletUpgradeBackupTest.includes(
      "interruptedStagingDirectoryNeverPermitsRoomToOpen",
    ) &&
    walletUpgradeBackupTest.includes(
      "sharedPreferencesBackupIsAuthoritativeForOrphanDetection",
    ) &&
    walletUpgradeBackupTest.includes(
      "retainedSharedPreferencesBackupIsIncludedInVerifiedBackup",
    ) &&
    walletUpgradeBackupTest.includes(
      "staleVerifiedBackupIsPreservedAndNeverReusedForChangedSource",
    ) &&
    walletUpgradeBackupTest.includes(
      "verifiedBackupRecoveryExportIsCompleteAndNeverMutatesRetainedSource",
    ) &&
    walletUpgradeBackupTest.includes(
      "recoveryExportFallsBackToUnopenedEncryptedStorageWhenBackupCannotPublish",
    ) &&
    walletUpgradeBackupTest.includes(
      "recoveryExportNeverPresentsInterruptedStagingAsVerifiedBackup",
    ) &&
    walletUpgradeBackupTest.includes(
      "recoveryExportNeverIncludesUnmanifestedBackupFiles",
    ) &&
    walletUpgradeBackupTest.includes(
      "explicitRetryCompareAndSetsOnlyTheMigrationJournal",
    ) &&
    walletUpgradeBackupTest.includes(
      "retryCannotMutateJournalBeforeRecoveryHasBeenInspected",
    ) &&
    walletUpgradeBackupTest.includes(
      "restartRecoveryValidatesLegacySelectionWithoutOpeningRoom",
    ) &&
    walletUpgradeBackupTest.includes(
      "pristineCurrentSchemaNamespaceIsBackedUpBeforeActivation",
    ) &&
    walletUpgradeBackupTest.includes(
      "malformedUtf8DataStoreKeyFailsClosedWithInventoryCode",
    ) &&
    walletUpgradeBackupTest.includes(
      "dataStoreMapEntryWithoutValueFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "duplicateDataStoreMapKeyFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "duplicateDataStoreValueOneofFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "unknownDataStoreTopLevelFieldFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "walletPreferenceWithWrongWireValueTypeFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "authoritativeDataStoreDirectoryFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "nonMapSharedPreferencesRootFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "manifestWalletCountMustMatchCopiedDatabaseInventory",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentSchemaCreatesOneVerifiedSnapshotWithoutRepeatingIt",
    ) &&
    walletUpgradeBackupTest.includes(
      "createExactCurrentModernizationSchema",
    ) &&
    walletUpgradeBackupTest.includes("`transactionHash` TEXT") &&
    walletUpgradeBackupTest.includes(
      "`submissionIsAmbiguous` INTEGER NOT NULL",
    ) &&
    walletUpgradeBackupTest.includes(
      "index_pendingNetworkTransactions_networkId_chainId_transactionHash",
    ) &&
    walletUpgradeBackupTest.includes(
      "index_walletDeletionOperations_activeSlot",
    ) &&
    walletUpgradeBackupTest.includes(
      "FOREIGN KEY(`operationId`) REFERENCES `walletDeletionOperations`(`operationId`)",
    ) &&
    walletUpgradeBackupTest.includes(
      "pendingNetworkTransactionInsertSql",
    ) &&
    walletUpgradeBackup.includes("hasVerifiedCurrentSchemaSnapshot") &&
    walletUpgradeBackupTest.includes(
      "verifiedMigrationReceiptDoesNotFreezeLaterAccountNameOrSelection",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentSchemaMissingMigrationJournalTableFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "interruptedCurrentSchemaMigrationRequiresRecovery",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentSchemaPendingIdentityWithoutJournalRequiresRecovery",
    ) &&
    walletUpgradeBackupTest.includes(
      "databaseCommittedDeletionAllowsOnlyItsExactTargetOrphan",
    ) &&
    walletUpgradeBackupTest.includes(
      "databaseCommittedDeletionNeverAuthorizesUnrelatedOrphan",
    ) &&
    walletUpgradeBackupTest.includes(
      "unjournaledCurrentSchemaOrphanStillFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "preferencesCommittedDeletionRequiresTargetKeysToBeGone",
    ) &&
    walletUpgradeBackupTest.includes(
      "confirmedDeletionRequiresExactPreviewedPreferenceFingerprint",
    ) &&
    walletIdentityMigration.includes(
      "SELECT `substrateAddress`, 'sora2', '', `substrateAddress`, '', 1, 0",
    ) &&
    walletIdentityDao.includes(
      "walletIdentities.migrationState = 'VERIFIED'",
    ) &&
    walletIdentityDao.includes(
      "where migrationId = 'wallet-network-v1'",
    ) &&
    walletIdentityDao.includes(
      "state != 'VERIFIED'",
    ),
  "RETAINED_ROOM_MIGRATION_MATRIX_MISSING",
);
assert(
  !/delete\s+from\s+`?accounts`?/i.test(walletIdentityMigration) &&
    !/delete\s+from\s+`?accounts`?/i.test(migrationManager) &&
    userRepository.includes("db.accountDao().flowAccounts()") &&
    userRepository.includes("db.accountDao().getAccounts()") &&
    walletUpgradeBackup.includes(
      "OLDEST_RECOVERABLE_BACKUP_TARGET_VERSION = 75",
    ),
  "LEGACY_WALLET_DUAL_READ_OR_BACKUP_RETENTION_REMOVED",
);
assert(
  migrationSr25519ProductionBoundaryGate,
  "MIGRATION_SR25519_PRODUCTION_BOUNDARY_MISSING",
);
assert(
  migrationSr25519ManagerWiringGate,
  "MIGRATION_SR25519_MANAGER_WIRING_MISSING",
);
assert(
  migrationSr25519HostSeamGate,
  "MIGRATION_SR25519_HOST_SEAM_MISSING",
);
assert(
  migrationPinnedNativeVectorGate,
  "MIGRATION_SR25519_NATIVE_VECTOR_MISSING",
);
assert(
  migrationManager.includes(
    "val preparedWallets = legacyAccounts.map { prepareWallet(it) }",
  ) &&
    migrationManager.includes("verifyPreparedModel(preparedWallets)") &&
    migrationManager.includes("private val sora2AddressCodec: Sora2AddressCodec") &&
    sora2AddressCodec.includes("const val SORA2_PREFIX: Short = 69") &&
    sora2AddressCodec.includes("SS58Encoder.extractAddressByte(address) == SORA2_PREFIX") &&
    sora2AddressCodec.includes("publicKey.size == PUBLIC_KEY_SIZE") &&
    sora2AddressCodecTest.includes(
      "pinned production public key round trips through the existing Sora2 address",
    ) &&
    sora2AddressCodecTest.includes(
      "cross network prefix and malformed public key fail closed",
    ) &&
    migrationManager.includes('state = "VERIFYING"') &&
    migrationManager.includes("existingJournal.state != \"VERIFIED\"") &&
    migrationManager.includes("isMigrationRetryAuthorized()") &&
    migrationManager.includes('"MIGRATION_RETRY_AUTHORIZATION_MISMATCH"') &&
    migrationManager.includes("markWalletsRecoveryRequired(walletIds)") &&
    migrationManager.includes("migrationMutex.withLock") &&
    migrationManager.includes("withWalletMigrationLocked") &&
    migrationManager.includes("activateMigrationJournal") &&
    migrationManager.includes(
      'SIGNING_PARITY_CHALLENGE = "sora-wallet-migration-signing-parity-v1"',
    ) &&
    migrationSr25519BoundaryGate &&
    migrationManager.includes('"SORA_SIGNING_VECTOR_FAILED"') &&
    migrationManager.includes('"SORA_SIGNING_VECTOR_MISMATCH"') &&
    walletIdentityDao.includes("and state = 'VERIFYING'") &&
    migrationManager.includes("catch (error: CancellationException)") &&
    migrationManagerSafetyTest.includes(
      "unreadable keystore stops activation and preserves legacy account",
    ) &&
    migrationManagerSafetyTest.includes(
      "accountDao.deleteAccountsForJournal(any())",
    ) &&
    migrationManagerSafetyTest.includes(
      "credentials.saveMnemonic(any(), any())",
    ) &&
    migrationManagerSafetyTest.includes(
      "credentials.saveKeyPair(any(), any())",
    ) &&
    migrationPinnedNativeVectorGate &&
    migrationManagerSafetyTest.includes(
      "successful first migration activates every legacy account with exact selection",
    ) &&
    migrationManagerSafetyTest.includes(
      "twelve and twenty four word mnemonics preserve Sora2 identity",
    ) &&
    migrationManagerSafetyTest.includes(
      "credentials.convertPassphraseToSeed(TWELVE_WORD_MNEMONIC)",
    ) &&
    /credentials\.convertPassphraseToSeed\(\s*TWENTY_FOUR_WORD_MNEMONIC\s*\)/.test(
      migrationManagerSafetyTest,
    ) &&
    migrationManagerSafetyTest.includes(
      "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2",
    ) &&
    migrationManagerSafetyTest.includes(
      "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m",
    ) &&
    migrationManagerSafetyTest.includes(
      "8b5c2f0b1d0f27f223df9bf205e3f3cdbf2fb9c3e4da36e0351a7a6ca6d10785",
    ) &&
    migrationManagerSafetyTest.includes(
      "cnTAiyqa6dqfhfX5HhJfmPdd56sum2WXFvg1i1UgRReB336yz",
    ) &&
    migrationManagerSafetyTest.includes(
      'datasource.retrieveSeed(twentyFourVector.address) } returns ""',
    ) &&
    migrationManagerSafetyTest.includes(
      'datasource.retrieveSeed("") } returns ""',
    ) &&
    migrationManagerSafetyTest.includes('datasource.retrieveKeys("")') &&
    migrationManagerSafetyTest.includes('datasource.retrieveSeed("")') &&
    credentialsRepository.includes(
      "SubstrateSeedFactory.deriveSeed32(parsed.words, null)",
    ) &&
    !credentialsRepository.includes("EthereumSeedFactory") &&
    migrationManagerSafetyTest.includes(
      "raw seed and watch only migration never synthesize mnemonic",
    ) &&
    migrationManagerSafetyTest.includes(
      "missing and corrupt secret sources fail closed",
    ) &&
    migrationManagerSafetyTest.includes(
      "malformed verified receipt enters recovery without inspecting secrets or restarting migration",
    ) &&
    migrationManager.includes('code = "VERIFIED_RECEIPT_INVALID"') &&
    migrationManager.includes(
      "completedAt = maxOf(System.currentTimeMillis(), startedAt)",
    ) &&
    /existingJournal\?\.state == "VERIFIED" && verifiedReceipt == null[\s\S]*recordFailure\([\s\S]*return false/.test(
      migrationManager,
    ),
  "VERIFIED_WALLET_CHILDREN_NOT_REVALIDATED",
);
assert(
  migrationManager.includes(
    "private fun isStructurallyValidVerifiedReceipt(",
  ) &&
    migrationManager.includes(
      "receipt.migrationId == WalletMigrationIds.NETWORK_ACCOUNTS_V1",
    ) &&
    migrationManager.includes(
      'INTEGRITY_HASH = Regex("^[0-9a-f]{64}$")',
    ) &&
    migrationManager.includes("val receiptMatchesLiveSnapshot =") &&
    migrationManager.includes("private suspend fun refreshVerifiedReceipt(") &&
    /refreshVerifiedReceipt\([\s\S]*database\.withTransaction \{[\s\S]*finalAccounts\.size == legacyAccounts\.size[\s\S]*verifyPreparedModel\(preparedWallets\)[\s\S]*refreshVerifiedMigrationJournal\(/.test(
      migrationManager,
    ) &&
    migrationManager.includes('"VERIFIED_RECEIPT_REFRESH_CAS_MISMATCH"') &&
    migrationManager.includes(
      "private suspend fun recordActivatedModelFailureAfterSnapshotChange(",
    ) &&
    migrationManager.includes("currentAccountCount = legacyAccounts.size") &&
    walletIdentityDao.includes(
      "suspend fun refreshVerifiedMigrationJournal(",
    ) &&
    /update walletMigrationJournal[\s\S]*set legacyAccountCount = :currentAccountCount,[\s\S]*verifiedAccountCount = :currentAccountCount,[\s\S]*where migrationId = :migrationId[\s\S]*legacyAccountCount = :receiptAccountCount[\s\S]*verifiedAccountCount = :receiptVerifiedAccountCount[\s\S]*completedAt = :receiptCompletedAt/.test(
      walletIdentityDao,
    ) &&
    /set state = 'RECOVERY_REQUIRED',[\s\S]*legacyAccountCount = :currentAccountCount,[\s\S]*where migrationId = :migrationId[\s\S]*verifiedAccountCount = :receiptVerifiedAccountCount[\s\S]*completedAt = :receiptCompletedAt/.test(
      walletIdentityDao,
    ) &&
    migrationManagerSafetyTest.includes(
      "changing selected wallet refreshes the verified checkpoint after full verification",
    ) &&
    migrationManagerSafetyTest.includes(
      "renamed wallet refreshes verified checkpoint without entering recovery",
    ) &&
    migrationManagerSafetyTest.includes(
      "added wallet refreshes verified checkpoint only after every wallet is verified",
    ) &&
    migrationManagerSafetyTest.includes(
      "lifecycle drift with tampered model fails closed before checkpoint refresh",
    ) &&
    retainedSchemaMigrationTest.includes("receiptCompletedAt = 999") &&
    retainedSchemaMigrationTest.includes("receiptAccountCount = 999") &&
    retainedSchemaMigrationTest.includes('currentIntegrityHash = "refreshed-integrity"') &&
    (retainedSchemaMigrationTest.match(/refreshVerifiedMigrationJournal\(/g) ?? [])
      .length >= 4 &&
    walletUpgradeBackup.includes(
      "A VERIFIED journal is the last fully verified wallet checkpoint",
    ),
  "VERIFIED_RECEIPT_LIFECYCLE_REFRESH_MISSING",
);
assert(
    migrationManager.includes("prepareExplicitWatchOnlyWallet") &&
    migrationManager.includes('"WATCH_ONLY_SIGNING_KEY_CONFLICT"') &&
    migrationManager.includes('"WATCH_ONLY_SECRET_CONFLICT"') &&
    credentialsDatasource.includes(
      "PREFS_WATCH_ONLY = WalletPreferenceKeys.WATCH_ONLY",
    ),
  "EXPLICIT_WATCH_ONLY_MIGRATION_MISSING",
);
assert(
    migrationManager.includes('VerifiedSecret("LEGACY_SECRET", "")') &&
    migrationManager.includes("verifyLegacySecretKeyMaterial(keyPair)") &&
    activeSecretSourceContinuityGate &&
    activeSecretSourceRollbackRegression &&
    migrationManager.includes("Sr25519JNI.verify") &&
    migrationManager.includes('secret.source == "MNEMONIC"') &&
    walletUpgradeBackup.includes('"LEGACY_SECRET" ->') &&
    walletUpgradeBackup.includes("preferences.keyPairWalletIds") &&
    walletUpgradeBackup.includes(
      "preferences.incompleteKeyPairWalletIds.isNotEmpty()",
    ) &&
    migrationManagerSafetyTest.includes(
      "keypair only legacy secret is verified without synthesizing recovery material",
    ) &&
    userRepositoryTest.includes(
      "insert keypair only account preserves legacy secret as verified Sora2 only",
    ) &&
    userRepositoryTest.includes(
      "verified wallet secret source cannot be reclassified on insertion",
    ) &&
    userRepositoryTest.includes(
      "deletion preview rejects legacy secret private public mismatch before journal",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentMnemonicWithIncompleteKeypairFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentRawSeedWithMnemonicConflictFailsClosed",
    ) &&
    walletUpgradeBackupTest.includes(
      "currentWatchOnlyWithCompleteKeypairConflictFailsClosed",
    ),
  "LEGACY_SECRET_MIGRATION_MISSING",
);
assert(
  appBuild.includes('create("qualification")') &&
    appBuild.includes('applicationIdSuffix = ".qualification"') &&
    appBuild.includes(
      'testApplicationId = "jp.co.soramitsu.sora.qualification.test"',
    ) &&
    appBuild.includes('matchingFallbacks += listOf("production")') &&
    appBuild.includes(
      'selector().withFlavor("default" to "qualification")',
    ) &&
    appBuild.includes('variantBuilder.buildType != "debug"') &&
    appBuild.includes(
      'getByName("androidTest").assets.directories.add(file("../core_db/schemas").absolutePath)',
    ) &&
    appBuild.includes(
      "androidTestImplementation(libs.roomTestHelpersDep)",
    ) &&
    migrationManagerProductionPathQualificationManifest.includes(
      'android:name=".qualification.MigrationQualificationApplication"',
    ) &&
    migrationManagerProductionPathQualificationManifest.includes(
      'tools:replace="android:name"',
    ) &&
    migrationManagerProductionPathQualificationApplication.includes(
      "class MigrationQualificationApplication : Application()",
    ) &&
    !migrationManagerProductionPathQualificationApplication.includes(
      "HiltAndroidApp",
    ) &&
    !migrationManagerProductionPathQualificationApplication.includes(
      "SoraApp",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertExactIsolatedPackage()",
    ) &&
    /assertExactIsolatedPackage\(\)\s*assertSingleQualificationMethodPerProcess\(\)\s*resetIsolatedQualificationState\(\)/.test(
      migrationManagerProductionPathQualificationTest,
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      '"QUALIFICATION_REQUIRES_ONE_METHOD_PER_FRESH_PROCESS"',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'QUALIFICATION_APPLICATION_ID = "jp.co.soramitsu.sora.qualification"',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'QUALIFICATION_TEST_APPLICATION_ID =',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "Room.databaseBuilder(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "CredentialsRepositoryImpl(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "PrefsCredentialsDatasource(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "val encryptedPreferences = EncryptedPreferences(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "EncryptionUtil(context),",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "PrefsUserDatasource(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "UserRepositoryImpl(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "userDatasource.setCurAccountAddress(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "userDatasource.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "userRepository.getCurSoraAccount()",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "userRepository.getRegistrationState()",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "awaitRepositoryInitialization(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "repositoryJob.cancelAndJoin()",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "val sora2AddressCodec = Sora2AddressCodec()",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "val manager = MigrationManager(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertTrue(manager.start())",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "openQualificationDatabase()",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "val migrationHelper = MigrationTestHelper(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "AppDatabase::class.java.canonicalName",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "FrameworkSQLiteOpenHelperFactory()",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "SOURCE_DATABASE_VERSION = 76",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "TARGET_DATABASE_VERSION = 77",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      ".addMigrations(migration_sora2PendingSubmission_75_76)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      ".addMigrations(migration_pendingNetworkTransactionChain_76_77)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "INSERT INTO walletIdentities(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "VALUES(?, ?, 'UNKNOWN', 'PENDING_VERIFICATION', 1)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "INSERT INTO networkAccounts(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "VALUES(?, 'sora2', '', ?, '', 1, 0)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'fixtureDatabase.query("SELECT COUNT(*) FROM walletIdentities")',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'fixtureDatabase.query("SELECT COUNT(*) FROM networkAccounts")',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      ".openHelperFactory(WalletUpgradeBackup.gatedOpenHelperFactory())",
    ) &&
    !migrationManagerProductionPathQualificationTest.includes(
      "openUngatedFixtureDatabase",
    ) &&
    /migrationHelper\.createDatabase\(\s*DATABASE_NAME,\s*SOURCE_DATABASE_VERSION,\s*\)\.use \{ fixtureDatabase ->[\s\S]*expectedAccounts\.forEach[\s\S]*assertEquals\(SOURCE_DATABASE_VERSION, fixtureDatabase\.version\)[\s\S]*val sourceBeforeBackup = walletStorageSnapshot\(\)[\s\S]*WalletUpgradeBackup\.prepare\(context\)[\s\S]*assertVerifiedSourceBackup\([\s\S]*val database = openQualificationDatabase\(\)[\s\S]*assertEquals\(TARGET_DATABASE_VERSION, database\.openHelper\.writableDatabase\.version\)[\s\S]*assertTrue\(manager\.start\(\)\)/.test(
      migrationManagerProductionPathQualifyBody,
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'assertEquals("VERIFIED", receipt.state)',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertRecoveredSigningParity",
    ) &&
    migrationManagerProductionPathQualificationTest.includes("SignWrapper.sign(") &&
    migrationManagerProductionPathQualificationTest.includes(
      "Sr25519JNI.verify(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertTrue(loadedKeyStore().containsAlias(KEY_ALIAS))",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertOpaqueStringEquals(expectedWrappedAesKey, wrappedAesKey())",
    ) &&
    !migrationManagerProductionPathQualificationTest.includes(
      "mockk<AppDatabase>",
    ) &&
    !migrationManagerProductionPathQualificationTest.includes(
      "mockk<CredentialsRepository>",
    ) &&
    !migrationManagerProductionPathQualificationTest.includes(
      "mockk<Sora2AddressCodec>",
    ) &&
    !migrationManagerProductionPathQualificationTest.includes(
      "mockk<UserRepository>",
    ) &&
    !migrationManagerProductionPathQualificationTest.includes(
      "migrationBoundary(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "RETAINED_SORA_MNEMONIC_WORD_COUNT = 15",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertFalse(credentialsRepository.isMnemonicValid(secret.words))",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "credentialsRepository.convertRetainedSoraPassphraseToSeed(secret.words)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'secretSource = if (secret.nexusCapable)',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      '"MNEMONIC_UNSUPPORTED"',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "nexusCapable = false",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "SecretFixture.LegacySecret",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'secretSource = "LEGACY_SECRET"',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'assertEquals("", credentialsDatasource.retrieveMnemonic(suffix))',
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      'assertEquals("", credentialsDatasource.retrieveSeed(suffix))',
    ) &&
    /networkAccounts = if \(secret\.nexusCapable\) \{[\s\S]*?expectedMnemonicNetworks\([\s\S]*?\} else \{[\s\S]*?listOf\(expectedSora2Network\(address, keyPair\.publicKey\)\)/.test(
      migrationManagerProductionPathQualificationTest,
    ) &&
    [
      "qualifyTwelveWordMnemonicThroughProductionPath",
      "qualifyTwentyFourWordMnemonicThroughProductionPath",
      "qualifyRetainedFifteenWordMnemonicThroughProductionPath",
      "qualifyRawSeedThroughProductionPath",
      "qualifyLegacySecretThroughProductionPath",
      "qualifyExplicitWatchOnlyThroughProductionPath",
      "qualifyTwoAccountExactSelectionThroughProductionPath",
    ].every((method) =>
      migrationManagerProductionPathQualificationTest.includes(method) &&
      migrationManagerProductionPathQualificationScript.includes(method),
    ) &&
    (migrationManagerProductionPathQualificationScript.match(
      /\nrun_qualification_method /g,
    ) ?? []).length === 7 &&
    migrationManagerProductionPathQualificationScript.includes(
      ':app:assembleQualificationDebug',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      ':app:assembleQualificationDebugAndroidTest',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      'manifest application-id "${qualification_target_apk}"',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      'manifest application-id "${qualification_test_apk}"',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      'manifest print "${qualification_target_apk}"',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      'manifest print "${qualification_test_apk}"',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      "grep -Fq 'android:sharedUserId'",
    ) &&
    /if \[ "\$\{built_target_package\}" != "\$\{qualification_target_package\}" \][\s\S]*if \[ "\$\{built_test_package\}" != "\$\{qualification_test_package\}" \][\s\S]*adb install -r -t "\$\{qualification_target_apk\}"[\s\S]*adb install -r -t "\$\{qualification_test_apk\}"/.test(
      migrationManagerProductionPathQualificationScript,
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      'qualification_target_package="jp.co.soramitsu.sora.qualification"',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      'qualification_test_package="jp.co.soramitsu.sora.qualification.test"',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      'if [ "${qualification_target}" != "${qualification_target_package}" ]',
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      "adb shell am force-stop",
    ) &&
    migrationManagerProductionPathQualificationScript.includes(
      "^OK \\(1 test\\)$",
    ) &&
    credentialsRepository.includes(
      "sora2AddressCodec.toSoraAddressOrNull(legacy.publicKey)",
    ),
  "MIGRATION_MANAGER_PRODUCTION_PATH_QUALIFICATION_MISSING",
);
assert(
  credentialsRepository.includes(
    '"MNEMONIC_NOT_AVAILABLE_FOR_SEED_DERIVATION"',
  ) &&
    credentialsRepository.includes(
      '"MNEMONIC_NOT_AVAILABLE_FOR_IROHA_DERIVATION"',
    ) &&
    /if \(seed\.isEmpty\(\)\) \{[\s\S]*hasRetainedSoraWordCount\(retainedWordCount\)[\s\S]*MnemonicCreator\.fromWords\(mnemonic\)[\s\S]*seed = convertRetainedSoraPassphraseToSeed\(mnemonic\)/.test(
      credentialsRepository,
    ) &&
    credentialsRepositoryTest.includes(
      "retained fifteen word wallet derives Sora2 seed but public recovery rejects it",
    ),
  "MISSING_MNEMONIC_CAN_SYNTHESIZE_SIGNING_MATERIAL",
);
assert(
  /override suspend fun migrate\(\): Boolean \{[\s\S]*?walletRepository\.needsMigration\(irohaData\.address\)[\s\S]*?saveNeedsMigration\(false, soraAccount\)[\s\S]*?MIGRATION_SELECTED_WALLET_CHANGED[\s\S]*?credentialsRepository\.retrieveKeyPair\(soraAccount\)[\s\S]*?keypair\.privateKey\.fill\(0\)[\s\S]*?keypair\.nonce\.fill\(0\)/.test(
    walletInteractor,
  ) &&
    walletInteractorTest.includes(
      "migrate rechecks authoritative account state before loading the signing key",
    ) &&
    walletInteractorTest.includes(
      "verify(credentialsRepository, never()).retrieveKeyPair(soraAccount)",
    ),
  "LEGACY_MIGRATION_ACCOUNT_REVALIDATION_MISSING",
);
assert(
  credentialsRepository.includes(
    "val localGenesis = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH",
  ) &&
    !credentialsRepository.includes("SoraConfigManager") &&
    (credentialsRepository.match(/seed = seed\.fromHex\(\)/g) ?? []).length === 2 &&
    credentialsRepositoryTest.includes(
      "multiaccount recovery export decodes every stored hex seed to raw bytes",
    ) &&
    credentialsRepositoryTest.includes(
      "eq(Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH)",
    ),
  "RECOVERY_JSON_EXPORT_CAN_DRIFT_OR_ENCODE_HEX_TEXT_AS_SEED",
);
assert(
  encryptedWalletMigrationStorageTest.includes(
    "phase01SeedRetainedSharedPreferencesAndVerifiedBackup",
  ) &&
    encryptedWalletMigrationStorageTest.includes(
      "phase02ImportLegacyCiphertextAfterProcessRestart",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "phase03ReadRetainedDataStoreAfterSecondProcessRestart",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "phase04AuthenticatedEnvelopeAndWrappedKeyCorruptionFailClosed",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "phase05MissingKeystoreAliasNeverCreatesReplacement",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "WalletUpgradeBackup.prepare(context)",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      'const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"',
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      'const val WRAPPED_AES_KEY = "secret_key"',
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "assertLegacyCiphertextValuesUnchanged",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "generateLegacySharedPreferenceValues",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "derivePinnedMnemonicKeyPair",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "assertRecoveredKeySigningParity",
    ) &&
    encryptedWalletMigrationStorageTest.includes("SignWrapper.sign(") &&
    encryptedWalletMigrationStorageTest.includes("Sr25519JNI.verify(") &&
    encryptedWalletMigrationStorageTest.includes(
      "legacyCiphertextSha256Key",
    ) &&
    !encryptedWalletMigrationStorageTest.includes(
      '"1111111111111111111111111111111111111111111111111111111111111111"',
    ) &&
    !encryptedWalletMigrationStorageTest.includes(
      '"2222222222222222222222222222222222222222222222222222222222222222"',
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "assertArrayEquals(dataStoreBytesBeforeRead, dataStoreFile().readBytes())",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "KeyMaterialUnavailableException",
    ) &&
    encryptedWalletMigrationStorageTest.includes("WalletDecryptionException") &&
    encryptedWalletMigrationStorageTest.includes(
      'assertEquals("", datasource.retrieveSeed(""))',
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      'const val PINNED_SORA2_SEED =',
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2",
    ) &&
    encryptedWalletMigrationStorageTest.includes(
      'const val PINNED_SORA2_ADDRESS =',
    ) &&
    encryptedWalletQualificationScript.includes(
      ":feature_account_impl:installProductionDebugAndroidTest",
    ) &&
    encryptedWalletQualificationScript.includes("qualification_target") &&
    encryptedWalletQualificationScript.includes("(target=") &&
    encryptedWalletQualificationScript.includes("adb shell am force-stop") &&
    encryptedWalletQualificationScript.includes("^OK \\(1 test\\)$") &&
    [
      "phase01SeedRetainedSharedPreferencesAndVerifiedBackup",
      "phase02ImportLegacyCiphertextAfterProcessRestart",
      "phase03ReadRetainedDataStoreAfterSecondProcessRestart",
      "phase04AuthenticatedEnvelopeAndWrappedKeyCorruptionFailClosed",
      "phase05MissingKeystoreAliasNeverCreatesReplacement",
    ].every(
      (phase) => encryptedWalletQualificationScript.includes(`"${phase}"`),
    ),
  "ENCRYPTED_MIGRATION_STORAGE_QUALIFICATION_MISSING",
);
assert(
  splashInteractor.includes("catch (error: CancellationException)") &&
    splashInteractor.includes("throw error") &&
    splashInteractor.includes("WalletUpgradeBackup.noteUnexpectedMigrationFailure()") &&
    walletUpgradeBackup.includes("fun noteUnexpectedMigrationFailure()") &&
    walletUpgradeBackupTest.includes(
      "unexpectedMigrationFailureCannotDemoteExistingBackupBlocker",
    ) &&
    walletUpgradeBackupTest.includes(
      "assertFalse(WalletUpgradeBackup.canAuthorizeMigrationRetry())",
    ) &&
    splashInteractorTest.includes(
      "migration exception completes recovery decision instead of hanging splash",
    ) &&
    splashInteractorTest.includes(
      "WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED",
    ),
  "MIGRATION_CANCELLATION_CONVERTED_TO_RECOVERY",
);
assert(
  (migrationManager.match(/WalletUpgradeBackup\.noteMigrationRecoveryRequired\(\)/g) ?? [])
    .length >= 5 &&
    migrationManager.includes("A cancelled Room write can have committed") &&
    migrationManagerSafetyTest.includes(
      "post journal cancellation closes the live process mutation gate",
    ) &&
    migrationManagerSafetyTest.includes(
      "failure journal write exception still closes the live process mutation gate",
    ) &&
    migrationManagerSafetyTest.includes(
      "WalletRecoveryCapabilityGate.requireUserMutationAllowed()",
    ),
  "MIGRATION_LIVE_PROCESS_RECOVERY_GATE_MISSING",
);
assert(
  splashInteractor.includes("canContinueLegacyWallet") &&
    splashInteractor.includes("userRepository.getCurSoraAccount()") &&
    splashActivity.includes("recovery.canContinueLegacyWallet") &&
    splashActivity.includes("splashViewModel.continueLegacyWallet()") &&
    splashActivity.includes("authorizeLegacyReadOnly") &&
    splashActivity.includes("authorizeMigrationRetry") &&
    splashViewModel.includes("WalletMigrationRecoveryUiState") &&
    splashViewModel.includes("interactor.retryMigration()") &&
    splashViewModel.includes("interactor.canContinueLegacyWallet()") &&
    splashViewModelTest.includes(
      "migration recovery never evaluates onboarding or implicit wallet creation",
    ) &&
    splashViewModelTest.includes(
      "legacy continuation revalidates exact selection immediately before navigation",
    ) &&
    splashViewModelTest.includes(
      "legacy continuation selection race stays on recovery screen",
    ) &&
    splashInteractorTest.includes(
      "missing legacy selection hides continuation instead of selecting a wallet",
    ),
  "RECOVERY_COULD_ROUTE_TO_ONBOARDING_OR_UNVERIFIED_LEGACY_SELECTION",
);
assert(
  walletRecoveryGate.includes("LEGACY_READ_ONLY") &&
    walletRecoveryGate.includes("MIGRATION_RETRY_AUTHORIZED") &&
    walletRecoveryGate.includes("requireUserMutationAllowed") &&
    walletUpgradeBackup.includes("applyRecoveryWriteGuards") &&
    walletUpgradeBackup.includes("CREATE TEMP TRIGGER IF NOT EXISTS") &&
    /private fun applyRecoveryWriteGuards\([\s\S]*?val tables = listOf\([\s\S]*?"pendingNetworkTransactions",\s*"sora2PendingSubmissions",[\s\S]*?\)\s*val operations = listOf/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackup.includes("override fun close()") &&
    /fun gatedOpenHelperFactory\([\s\S]*?beforeGuardAdmissionForTest = null,[\s\S]*?internal fun gatedOpenHelperFactoryWithGuardAdmissionForTest\([\s\S]*?beforeGuardAdmissionForTest = beforeGuardAdmission/.test(
      walletUpgradeBackup,
    ) &&
    /private fun configured\([\s\S]*?\): SupportSQLiteDatabase = synchronized\(this\) \{\s*beforeGuardAdmissionForTest\?\.invoke\(\)\s*val required =\s*WalletRecoveryCapabilityGate\.requiresWalletWriteGuards\(\)[\s\S]*?applyRecoveryWriteGuards\(database, enabled = true\)[\s\S]*?database\s*\}/.test(
      walletUpgradeBackup,
    ) &&
    walletUpgradeBackupTest.includes(
      "legacyReadOnlyGuardsEveryPendingNetworkTransactionMutation",
    ) &&
    walletUpgradeBackupTest.includes("forbidden-after-reopen") &&
    /fun legacyReadOnlyGuardsEverySora2PendingSubmissionMutation\(\) \{[\s\S]*?sora2PendingSubmissionInsertSql\([\s\S]*?UPDATE sora2PendingSubmissions[\s\S]*?DELETE FROM sora2PendingSubmissions[\s\S]*?helper\.close\(\)[\s\S]*?sora2PendingSubmissionInsertSql\([\s\S]*?UPDATE sora2PendingSubmissions[\s\S]*?DELETE FROM sora2PendingSubmissions[\s\S]*?SELECT localId FROM sora2PendingSubmissions/.test(
      walletUpgradeBackupTest,
    ) &&
    /fun normalToRecoveryTransitionDuringHandleAdmissionCannotEscapeUnguarded\(\) \{[\s\S]*?gatedOpenHelperFactoryWithGuardAdmissionForTest\([\s\S]*?WalletUpgradeBackup\.noteMigrationRecoveryRequired\(\)[\s\S]*?val guarded = helper\.writableDatabase[\s\S]*?WalletRecoveryCapabilityGate\.Mode\.RECOVERY_INSPECTED[\s\S]*?assertRecoveryWriteRejected[\s\S]*?UPDATE accounts[\s\S]*?assertEquals\("Retained", cursor\.getString\(0\)\)/.test(
      walletUpgradeBackupTest,
    ) &&
    walletUpgradeBackupTest.includes("WALLET_RECOVERY_READ_ONLY") &&
    userRepository.includes(
      "WalletRecoveryCapabilityGate.requireUserMutationAllowed()",
    ) &&
    credentialsRepository.includes(
      "WalletRecoveryCapabilityGate.requireUserMutationAllowed()",
    ) &&
    extrinsicManager.includes(
      "WalletRecoveryCapabilityGate.requireUserMutationAllowed()",
    ) &&
    nexusRecoveryCoordinator.includes(
      "WalletRecoveryCapabilityGate.requireUserMutationAllowed()",
    ) &&
    userRepositoryTest.includes(
      "legacy recovery blocks account insertion before secret or database access",
    ) &&
    walletRecoveryGateTest.includes(
      "only explicit authorization enables migration writes",
    ),
  "RECOVERY_READ_ONLY_CAPABILITY_GATE_MISSING",
);
assert(!settings.includes("mavenLocal()"), "MAVEN_LOCAL_ENABLED");
assert(rootBuild.includes("failOnDynamicVersions()"), "DYNAMIC_VERSION_GATE_MISSING");
assert(rootBuild.includes("failOnChangingVersions()"), "CHANGING_VERSION_GATE_MISSING");
assert(
  rootBuild.includes("LockMode.STRICT") &&
    rootBuild.includes("activateDependencyLocking()") &&
    rootBuild.includes(
      'configurationName.contains("productionRelease", ignoreCase = true)',
    ),
  "PRODUCTION_RELEASE_STRICT_DEPENDENCY_LOCKING_MISSING",
);
assert(
  gradleDependencyProvenance.schemaVersion === 3 &&
    gradleDependencyProvenance.platform === "android" &&
    gradleDependencyProvenance.wrapper.distributionVersion === "9.5.1" &&
    gradleDependencyProvenance.wrapper.distributionUrl ===
      "https://services.gradle.org/distributions/gradle-9.5.1-bin.zip" &&
    gradleDependencyProvenance.wrapper.distributionSha256 ===
      "bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f" &&
    gradleDependencyProvenance.wrapper.expectedWrapperJarSha256 ===
      "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7" &&
    gradleDependencyProvenance.wrapper.observedWrapperJarSha256 ===
      gradleWrapperJarSha256 &&
    gradleDependencyProvenance.wrapper.checksumSource ===
      "https://gradle.org/release-checksums/",
  "GRADLE_WRAPPER_PROVENANCE_EVIDENCE_INVALID",
);
assert(
  gradleWrapperProperties.includes(
    "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip",
  ) &&
    gradleWrapperProperties.includes(
      "distributionSha256Sum=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f",
    ) &&
    gradleWrapperProperties.includes("networkTimeout=10000") &&
    gradleWrapperProperties.includes("validateDistributionUrl=true"),
  "GRADLE_DISTRIBUTION_NOT_IMMUTABLY_PINNED",
);
for (const dependencyFailure of androidDependencyPreflight.failures) {
  assert(false, dependencyFailure);
}
for (const dependencyBlocker of androidDependencyPreflight.releaseBlockers) {
  block(true, dependencyBlocker);
}
assert(
  gradleDependencyProvenance.repositoryPolicy.reviewedStagedIrohaRepository
    .path === "build/iroha-mobile-sdk/maven" &&
    gradleDependencyProvenance.repositoryPolicy.reviewedStagedIrohaRepository
      .gradleRepositoryName === "ReviewedIrohaMobileSdk" &&
    gradleDependencyProvenance.repositoryPolicy.reviewedStagedIrohaRepository
      .exclusiveGroup === "org.hyperledger.iroha.sdk" &&
    gradleDependencyProvenance.repositoryPolicy.reviewedStagedIrohaRepository
      .manifestPath ===
      "build/iroha-mobile-sdk/maven/REVIEWED_CONTENTS.sha256" &&
    gradleDependencyProvenance.repositoryPolicy.reviewedStagedIrohaRepository
      .manifestFormat === "sha256-two-space-relative-path" &&
    ["present", "qualified", "symlinkFree", "wholeTreeManifestReviewed"].every(
      (field) =>
        typeof gradleDependencyProvenance.repositoryPolicy
          .reviewedStagedIrohaRepository[field] === "boolean",
    ),
  "GRADLE_STAGED_IROHA_REPOSITORY_EVIDENCE_INVALID",
);
const dependencyPreflightIndex = productionReleaseWorkflow.indexOf(
  "run: node scripts/verify-production-modernization.mjs --dependency-preflight",
);
const dependencySigningReviewAdmissionIndex =
  productionReleaseWorkflow.indexOf(
    "Admit independently signed dependency and signing review",
  );
const runnerScopedPathInitializationIndex = productionReleaseWorkflow.indexOf(
  "Initialize runner-scoped qualification paths",
);
const staticSourceAuditIndex = productionReleaseWorkflow.indexOf(
  "run: node scripts/verify-production-modernization.mjs\n",
);
const downloadedPackageModeNormalizationIndex =
  productionReleaseWorkflow.indexOf(
    "Normalize downloaded package to owner-only inputs",
  );
const downloadedPackageValidationIndex = productionReleaseWorkflow.indexOf(
  "Validate complete downloaded reproducibility package",
);
const exactReleaseGateIndex = productionReleaseWorkflow.indexOf(
  "node scripts/verify-production-modernization.mjs --release",
);
const firstProductionGradleIndex =
  productionReleaseWorkflow.indexOf("./gradlew");
const productionBundleIndex = productionReleaseWorkflow.indexOf(
  ":app:bundleProductionRelease",
);
const releaseEvaluationIndex = productionReleaseWorkflow.indexOf(
  "Record explicit release evaluation epoch",
);
const refreshedPiProbeIndex = productionReleaseWorkflow.indexOf(
  "Refresh live PI contract after funded canaries",
);
const preCanaryGateIndex = productionReleaseWorkflow.indexOf(
  "node scripts/verify-production-modernization.mjs --pre-canary",
);
const fundedCanaryControllerIndex = productionReleaseWorkflow.indexOf(
  "Run protected candidate-bound funded canaries",
);
const productionWorkflowActions = [
  ...productionReleaseWorkflow.matchAll(
    /^\s*uses:\s*([^@\s]+)@([^\s#]+)(?:\s+#.*)?\s*$/gm,
  ),
];
assert(
    productionReleaseWorkflow.includes('CI: "true"') &&
    productionReleaseWorkflowJobEnvironment.length > 0 &&
    !productionReleaseWorkflowJobEnvironment.includes("${{ runner.temp }}") &&
    runnerScopedPathInitializationIndex >= 0 &&
    runnerScopedPathInitializationIndex < dependencySigningReviewAdmissionIndex &&
    productionReleaseWorkflow.includes(
      '[[ "$RUNNER_TEMP" = /* && -d "$RUNNER_TEMP" && ! -L "$RUNNER_TEMP" ]] || exit 1',
    ) &&
    productionReleaseWorkflow.includes(
      '[[ "$GITHUB_ENV" = /* && -f "$GITHUB_ENV" && ! -L "$GITHUB_ENV" ]] || exit 1',
    ) &&
    productionRunnerTempPathBindings.every(([name, relativePath]) =>
      productionReleaseWorkflow.includes(
        `append_runner_temp_path "${name}" "${relativePath}"`,
      ),
    ) &&
    sourceMatchCount(
      productionReleaseWorkflow,
      /^\s*append_runner_temp_path\s+"[A-Z][A-Z0-9_]*"\s+"[A-Za-z0-9._/-]+"\s*$/gm,
    ) === productionRunnerTempPathBindings.length &&
    dependencySigningReviewAdmissionIndex >= 0 &&
    dependencyPreflightIndex >= 0 &&
    dependencySigningReviewAdmissionIndex < dependencyPreflightIndex &&
    downloadedPackageModeNormalizationIndex >= 0 &&
    downloadedPackageValidationIndex > downloadedPackageModeNormalizationIndex &&
    productionReleaseWorkflow.includes(
      '[[ ${#package_files[@]} -eq 30 ]] || exit 1',
    ) &&
    productionReleaseWorkflow.includes('chmod 600 "${package_files[@]}"') &&
    staticSourceAuditIndex >= 0 &&
    staticSourceAuditIndex > dependencyPreflightIndex &&
    firstProductionGradleIndex > staticSourceAuditIndex &&
    productionBundleIndex > firstProductionGradleIndex &&
    preCanaryGateIndex > productionBundleIndex &&
    fundedCanaryControllerIndex > preCanaryGateIndex &&
    refreshedPiProbeIndex > productionBundleIndex &&
    refreshedPiProbeIndex > fundedCanaryControllerIndex &&
    releaseEvaluationIndex > refreshedPiProbeIndex &&
    exactReleaseGateIndex > releaseEvaluationIndex &&
    productionReleaseWorkflow.includes("permissions:\n  contents: read") &&
    productionReleaseWorkflow.includes("persist-credentials: false") &&
    [
      ":app:testProductionDebugUnitTest",
      ":core_db:connectedProductionDebugAndroidTest",
      ":common:connectedProductionDebugAndroidTest",
      ":sorasubstrate:connectedProductionDebugAndroidTest",
      ":common:testProductionDebugUnitTest",
      ":common_wallet:testProductionDebugUnitTest",
      ":core_db:testProductionDebugUnitTest",
      ":demeter:testProductionDebugUnitTest",
      ":feature_account_api:testProductionDebugUnitTest",
      ":feature_account_impl:testProductionDebugUnitTest",
      ":feature_assets_api:testProductionDebugUnitTest",
      ":feature_assets_impl:testProductionDebugUnitTest",
      ":feature_blockexplorer_api:testProductionDebugUnitTest",
      ":feature_blockexplorer_impl:testProductionDebugUnitTest",
      ":feature_ecosystem_impl:testProductionDebugUnitTest",
      ":feature_ethereum_api:testProductionDebugUnitTest",
      ":feature_main_api:testProductionDebugUnitTest",
      ":feature_main_impl:testProductionDebugUnitTest",
      ":feature_multiaccount_api:testProductionDebugUnitTest",
      ":feature_multiaccount_impl:testProductionDebugUnitTest",
      ":feature_polkaswap_api:testProductionDebugUnitTest",
      ":feature_polkaswap_impl:testProductionDebugUnitTest",
      ":feature_referral_api:testProductionDebugUnitTest",
      ":feature_referral_impl:testProductionDebugUnitTest",
      ":feature_select_node_api:testProductionDebugUnitTest",
      ":feature_select_node_impl:testProductionDebugUnitTest",
      ":feature_sora_card_api:testProductionDebugUnitTest",
      ":feature_sora_card_impl:testProductionDebugUnitTest",
      ":feature_wallet_api:testProductionDebugUnitTest",
      ":feature_wallet_impl:testProductionDebugUnitTest",
      ":network:testProductionDebugUnitTest",
      ":sorasubstrate:testProductionDebugUnitTest",
      ":test_data:testProductionDebugUnitTest",
      ":app:verifyProductionReleaseSigning",
      ":app:testProductionReleaseUnitTest",
      ":app:lintProductionRelease",
      ":app:assembleProductionRelease",
      ":app:bundleProductionRelease",
    ].every((task) => productionReleaseWorkflow.includes(task)) &&
    productionReleaseWorkflow.includes(
      "reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
    ) &&
    productionReleaseWorkflow.includes(
      "jp.co.soramitsu.core_db.WalletIdentityMigration75Test,jp.co.soramitsu.core_db.WalletUpgradeBackupTest",
    ) &&
    productionReleaseWorkflow.includes(
      "bash scripts/run-encrypted-wallet-upgrade-qualification.sh",
    ) &&
    productionReleaseWorkflow.includes(
      "bash scripts/run-migration-manager-production-path-qualification.sh",
    ) &&
    productionReleaseWorkflow.includes(
      "Minified signed production APK device smoke",
    ) &&
    productionReleaseWorkflow.includes(
      'sdkmanager "platforms;android-36" "build-tools;36.0.0"',
    ) &&
    productionReleaseWorkflow.includes(
      '"$ANDROID_HOME/build-tools/36.0.0/apksigner"',
    ) &&
    productionReleaseWorkflow.includes(
      "app/build/outputs/mapping/productionRelease/mapping.txt",
    ) &&
    productionReleaseWorkflow.includes(
      "app/build/outputs/apk/production/release/app-production-release.apk",
    ) &&
    productionReleaseWorkflow.includes(
      "adb shell am start -W",
    ) &&
    encryptedWalletQualificationScript.includes(
      ":feature_account_impl:installProductionDebugAndroidTest",
    ) &&
    productionReleaseWorkflow.includes(":core_db:kspProductionDebugKotlin") &&
    productionReleaseWorkflow.includes(
      ":core_db:kspProductionDebugAndroidTestKotlin",
    ) &&
    productionReleaseWorkflow.includes(
      "jp.co.soramitsu.core_db.AppDatabaseV74SchemaFixture/74.json",
    ) &&
    productionReleaseWorkflow.includes(
      "jp.co.soramitsu.core_db.AppDatabaseV75SchemaFixture/75.json",
    ) &&
    productionReleaseWorkflow.includes(
      "jp.co.soramitsu.core_db.AppDatabaseV76SchemaFixture/76.json",
    ) &&
    productionReleaseWorkflow.includes("AppDatabase-74.reviewed.json") &&
    productionReleaseWorkflow.includes("AppDatabase-75.reviewed.json") &&
    productionReleaseWorkflow.includes("AppDatabase-76.reviewed.json") &&
    productionReleaseWorkflow.includes("AppDatabase-77.reviewed.json") &&
    productionReleaseWorkflow.includes("cmp \\") &&
    productionReleaseWorkflow.includes("--no-parallel") &&
    [
      "CI_KEYSTORE_PATH",
      "CI_KEYSTORE_PASS",
      "CI_KEYSTORE_KEY_ALIAS",
      "CI_KEYSTORE_KEY_PASS",
    ].every((name) =>
      productionReleaseWorkflow.includes(
        `${name}: \${{ secrets.${name} }}`,
      ),
    ) &&
    !/--dependency-verification(?:=|\s+)(?:off|lenient)|--write-verification-metadata|--write-locks|mavenLocal|includeBuild|dependencySubstitution|PAY_WINGS_REPOSITORY_URL/i.test(
      productionReleaseWorkflow,
    ),
  "PRODUCTION_WORKFLOW_PROVENANCE_GATE_ORDER_INVALID",
);
assert(
  productionReleaseWorkflow.includes("timeout-minutes: 240") &&
    productionReleaseWorkflow.includes(
      "CI_BUILD_ID: ${{ vars.PRODUCTION_VERSION_CODE }}",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/test-android-candidate-signing-verification-v1.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/test-android-qualified-candidate-package-v1.mjs",
    ) &&
    sourceMatchCount(
      productionReleaseWorkflow,
      /node scripts\/verify-production-modernization\.mjs --dependency-preflight\n\s+\.\/gradlew \\/g,
    ) === sourceMatchCount(productionReleaseWorkflow, /\.\/gradlew \\/g) &&
    sourceMatchCount(
      productionReleaseWorkflow,
      /node scripts\/verify-production-modernization\.mjs --dependency-preflight/g,
    ) === sourceMatchCount(productionReleaseWorkflow, /\.\/gradlew \\/g) + 1 &&
    productionReleaseWorkflow.includes(
      "node scripts/test-funded-canary-controller-bundle-v1.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      'git clone --no-local --no-checkout "$GITHUB_WORKSPACE" "$repro_root"',
    ) &&
    productionReleaseWorkflow.includes(
      'export GRADLE_USER_HOME="$RUNNER_TEMP/sora-android-reproduction-gradle-home"',
    ) &&
    sourceMatchCount(
      productionReleaseWorkflow,
      /:app:bundleProductionRelease/g,
    ) === 2 &&
    productionReleaseWorkflow.includes(
      'cmp "$PRODUCTION_CANDIDATE_AAB_PATH" "$PRODUCTION_REPRODUCED_AAB_PATH"',
    ) &&
    productionReleaseWorkflow.includes(
      'cmp "$PRODUCTION_CANDIDATE_APK_PATH" "$PRODUCTION_REPRODUCED_APK_PATH"',
    ) &&
    productionReleaseWorkflow.includes(
      'cmp "$PRODUCTION_CANDIDATE_MAPPING_PATH" "$PRODUCTION_REPRODUCED_MAPPING_PATH"',
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/verify-android-candidate-signing.mjs",
    ) &&
    sourceMatchCount(
      productionReleaseWorkflow,
      /PRODUCTION_KEYTOOL_PATH="\$\(realpath "\$JAVA_HOME\/bin\/keytool"\)"/g,
    ) === 2 &&
    productionReleaseWorkflow.includes(
      "node scripts/create-android-qualified-candidate-package.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/verify-downloaded-android-qualified-candidate-package.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/extract-funded-canary-controller-bundle.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "X-SORA-Contract: sora-android-funded-canary-controller-v1",
    ) &&
    productionReleaseWorkflow.includes(
      'Accept: application/x-ustar',
    ) &&
    productionReleaseWorkflow.includes(
      '--pinnedpubkey "sha256//$FUNDED_CANARY_CONTROLLER_TLS_SPKI_SHA256_BASE64"',
    ) &&
    productionReleaseWorkflow.includes(
      '"$FUNDED_CANARY_CONTROLLER_ORIGIN/v1/android/funded-canary/evidence-bundle"',
    ) &&
    [
      "candidate.apk",
      "r8-mapping.txt",
      "reproduced-candidate.aab",
      "reproduced-candidate.apk",
      "reproduced-r8-mapping.txt",
      "primary-build.log",
      "reproduction-build.log",
      "primary-signing-verification.json",
      "reproduction-signing-verification.json",
      "android-migration-controller-envelope-v1.json",
      "android-migration-controller-extraction-v1.json",
      "taira-deployment-manifest.json",
      "taira-deployment-operator.sig",
      "taira-deployment-reviewer.sig",
      "taira-deployment-operator.pem",
      "taira-deployment-reviewer.pem",
      "taira-deployment-admission.json",
      "android-dependency-signing-review-manifest.json",
      "android-dependency-signing-review-producer.sig",
      "android-dependency-signing-review-reviewer.sig",
      "android-dependency-signing-review-producer.pem",
      "android-dependency-signing-review-reviewer.pem",
      "android-dependency-signing-review-admission.json",
      "funded-canary-controller-bundle-v1.tar",
      "funded-canary-controller-extraction-v1.json",
      "candidate-package-manifest.json",
    ].every((name) => productionReleaseWorkflow.includes(name)) &&
    productionReleaseWorkflow.includes(
      "node scripts/test-android-dependency-signing-review-v1.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/verify-android-dependency-signing-review.mjs",
    ) &&
    androidDependencySigningReviewSource.includes(
      'contractId: "sora-android-dependency-signing-review-v1"',
    ) &&
    androidDependencySigningReviewSource.includes(
      'namedCurve !== "prime256v1"',
    ) &&
    androidDependencySigningReviewSource.includes(
      'dsaEncoding: "ieee-p1363"',
    ) &&
    androidDependencySigningReviewSource.includes(
      "canonicalP256Signature(producerSignature.bytes)",
    ) &&
    androidDependencySigningReviewSource.includes(
      "ANDROID_DEPENDENCY_SIGNING_REVIEW_AUTHORITIES_INVALID",
    ) &&
    androidDependencySigningReviewSource.includes(
      "ANDROID_DEPENDENCY_SIGNING_REVIEW_EXTERNAL_EVIDENCE_INVALID",
    ) &&
    androidDependencySigningReviewSource.includes(
      "authorizesDependencySigningQualification: true",
    ) &&
    androidDependencySigningReviewSource.includes(
      "authorizesRelease: false",
    ) &&
    androidDependencySigningReviewVerifierSource.includes(
      "ANDROID_DEPENDENCY_SIGNING_REVIEW_CONTRACT_DIVERGED",
    ) &&
    androidDependencySigningReviewVerifierSource.includes(
      "PRODUCTION_CANDIDATE_SOURCE_REVISION",
    ) &&
    androidDependencySigningReviewTestSource.includes(
      "P-256 positive admission",
    ) &&
    androidDependencySigningReviewTestSource.includes("sequence drift") &&
    androidDependencySigningReviewTestSource.includes(
      "external evidence absent",
    ) &&
    androidDependencySigningReviewTestSource.includes("wrongSignature") &&
    androidDependencySigningReviewTestSource.includes("hardlinked") &&
    androidCandidateSigningVerificationSource.includes(
      'contractId: "sora-android-candidate-signing-verification-v1"',
    ) &&
    androidCandidateSigningVerificationSource.includes(
      "parseKeytoolJarSignerCertificate",
    ) &&
    androidCandidateSigningVerificationSource.includes(
      '"-printcert",\n        "-rfc",\n        "-jarfile",\n        aabPath',
    ) &&
    androidCandidateSigningVerificationSource.includes(
      '"-verify",\n        "-strict",\n        "-keystore"',
    ) &&
    androidCandidateSigningVerificationSource.includes(
      '["verify", "--verbose", "--print-certs", apkPath]',
    ) &&
    androidCandidateSigningVerificationSource.includes(
      "ANDROID_CANDIDATE_AAB_UPLOAD_CERTIFICATE_MISMATCH",
    ) &&
    androidCandidateSigningVerificationSource.includes(
      "ANDROID_CANDIDATE_UPLOAD_CERTIFICATE_MISMATCH",
    ) &&
    androidCandidateSigningVerificationSource.includes(
      "ANDROID_CANDIDATE_SIGNING_INPUT_CHANGED_DURING_VERIFICATION",
    ) &&
    androidCandidateSigningVerificationSource.includes(
      "authorizesProductionMutation: false",
    ) &&
    androidCandidateSigningVerifierSource.includes(
      "validateAndroidCandidateSigningVerificationV1",
    ) &&
    androidCandidateSigningVerificationTestSource.includes(
      "multiple-signers",
    ) &&
    androidCandidateSigningVerificationTestSource.includes(
      "multiple-aab-signers",
    ) &&
    androidCandidateSigningVerificationTestSource.includes(
      "aab-certificate-mismatch",
    ) &&
    androidCandidateSigningVerificationTestSource.includes(
      "hardlinked-aab",
    ) &&
    androidCandidateSigningVerificationTestSource.includes(
      "aab-changed-during-verification",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      'contractId: "sora-android-qualified-candidate-package-v1"',
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "CANDIDATE_PACKAGE_AAB_NOT_REPRODUCIBLE",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "CANDIDATE_PACKAGE_APK_NOT_REPRODUCIBLE",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "CANDIDATE_PACKAGE_R8_MAPPING_NOT_REPRODUCIBLE",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "observedAabUploadCertificateSha256",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "CANDIDATE_PACKAGE_DEPENDENCY_INVENTORY_NOT_QUALIFIED",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "CANDIDATE_PACKAGE_MIGRATION_CONTROLLER_EXTRACTION_DIVERGED",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "DOWNLOADED_CANDIDATE_MIGRATION_CONTROLLER_ENVELOPE_DIVERGED",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "CANDIDATE_PACKAGE_TAIRA_DEPLOYMENT_VERIFICATION_FAILED",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "DOWNLOADED_CANDIDATE_TAIRA_DEPLOYMENT_VERIFICATION_FAILED",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "tairaDeploymentManifestSha256",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "dependencySigningReviewManifestSha256",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "CANDIDATE_PACKAGE_DEPENDENCY_SIGNING_REVIEW_VERIFICATION_FAILED",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "DOWNLOADED_CANDIDATE_DEPENDENCY_SIGNING_REVIEW_VERIFICATION_FAILED",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "authorizesRelease: false",
    ) &&
    androidQualifiedCandidatePackageSource.includes(
      "authorizesProductionMutation: false",
    ) &&
    androidQualifiedCandidatePackageCreatorSource.includes(
      "validateAndroidQualifiedCandidatePackageV1",
    ) &&
    androidDownloadedCandidatePackageVerifierSource.includes(
      "validateDownloadedAndroidQualifiedCandidatePackageV1",
    ) &&
    androidQualifiedCandidatePackageTestSource.includes(
      "signing-verification-drift",
    ) &&
    androidQualifiedCandidatePackageTestSource.includes("hardlink-alias") &&
    androidQualifiedCandidatePackageTestSource.includes(
      "taira-deployment-signature-drift",
    ) &&
    androidQualifiedCandidatePackageTestSource.includes(
      "downloaded-taira-manifest-drift",
    ) &&
    androidQualifiedCandidatePackageTestSource.includes(
      "dependency-review-signature-drift",
    ) &&
    androidQualifiedCandidatePackageTestSource.includes(
      "downloaded-dependency-review-manifest-drift",
    ),
  "ANDROID_REPRODUCIBLE_CANDIDATE_SEAL_MISSING",
);
assert(
  fundedCanaryControllerBundleSource.includes(
      'contractId: "sora-android-funded-canary-controller-bundle-v1"',
    ) &&
      fundedCanaryControllerBundleSource.includes(
        '"candidate.apks": GIB',
      ) &&
      fundedCanaryControllerBundleSource.includes(
        '"taira-evidence-bundle.json": JSON_MAXIMUM_BYTES',
      ) &&
      fundedCanaryControllerBundleSource.includes(
        '"minamoto-evidence-bundle.json": JSON_MAXIMUM_BYTES',
      ) &&
      fundedCanaryControllerBundleSource.includes(
        "FUNDED_CANARY_BUNDLE_TAR_CHECKSUM_INVALID",
      ) &&
      fundedCanaryControllerBundleSource.includes(
        "FUNDED_CANARY_BUNDLE_TAR_DUPLICATE_ENTRY",
      ) &&
      fundedCanaryControllerBundleSource.includes(
        "FUNDED_CANARY_BUNDLE_JSON_INVALID",
      ) &&
      fundedCanaryControllerBundleSource.includes(
        "authorizesProductionMutation: false",
      ) &&
      fundedCanaryControllerBundleExtractorSource.includes(
        "extractFundedCanaryControllerBundleV1",
      ) &&
      fundedCanaryControllerBundleTestSource.includes("symbolic-entry") &&
      fundedCanaryControllerBundleTestSource.includes("duplicate-json-key") &&
      fundedCanaryControllerBundleTestSource.includes("nonzero-padding") &&
      /const deferredPreCanaryBlockerSet = new Set\(\[\s*"TAIRA_FUNDED_CANARY_NOT_QUALIFIED",\s*"MINAMOTO_FUNDED_CANARY_NOT_QUALIFIED",\s*"FUNDED_CANARY_CROSS_NETWORK_PAIR_NOT_QUALIFIED",\s*\]\);/m.test(
        read("scripts/verify-production-modernization.mjs"),
      ),
  "FUNDED_CANARY_CONTROLLER_HANDOFF_CONTRACT_MISSING",
);
assert(
  androidMigrationControllerEnvelopeSource.includes(
    'contractId: "sora-android-migration-controller-envelope-v1"',
  ) &&
    [
      "android-migration-matrix.json",
      "android-migration-evidence.json",
      "android-migration-trust.json",
      "android-migration-retained-snapshot-manifest.json",
      "android-migration-test-results.json",
      "android-migration-encrypted-storage-evidence.json",
      "android-migration-device-execution-evidence.json",
      "android-migration-raw-execution-evidence.json",
      "android-migration-receipt-signature.der",
      "android-migration-evidence-producer-signature.der",
      "android-migration-evidence-reviewer-signature.der",
      "device-producer-public-key.pem",
      "reviewer-public-key.pem",
    ].every((name) =>
      androidMigrationControllerEnvelopeSource.includes(`"${name}"`),
    ) &&
    androidMigrationControllerEnvelopeSource.includes(
      "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_INVENTORY_INVALID",
    ) &&
    androidMigrationControllerEnvelopeSource.includes(
      "ANDROID_MIGRATION_CONTROLLER_ENVELOPE_ARTIFACTS_DIVERGED",
    ) &&
    androidMigrationControllerEnvelopeSource.includes(
      "authorizesProductionMutation: false",
    ) &&
    androidMigrationControllerEnvelopeExtractorSource.includes(
      "extractAndroidMigrationControllerEnvelopeV1",
    ) &&
    androidMigrationControllerEnvelopeTestSource.includes(
      "duplicate-outer-key",
    ) &&
    androidMigrationControllerEnvelopeTestSource.includes(
      "duplicate-payload-json-key",
    ) &&
    androidMigrationControllerEnvelopeTestSource.includes(
      "artifact-drift",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/test-android-migration-controller-envelope-v1.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/extract-android-migration-controller-envelope.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      '--pinnedpubkey "sha256//$ANDROID_MIGRATION_CONTROLLER_TLS_SPKI_SHA256_BASE64"',
    ) &&
    productionReleaseWorkflow.includes(
      'Accept: application/vnd.sora.android-migration-envelope-v1+json',
    ) &&
    productionReleaseWorkflow.includes(
      '"$ANDROID_MIGRATION_CONTROLLER_ORIGIN/v1/android/migration-qualification/evidence-envelope"',
    ) &&
    !productionReleaseWorkflow.includes(
      "ANDROID_MIGRATION_QUALIFICATION_RECEIPT_SIGNATURE_PATH: ${{ vars.",
    ) &&
    !productionReleaseWorkflow.includes(
      "ANDROID_MIGRATION_QUALIFICATION_DEVICE_PRODUCER_PUBLIC_KEY_PATH: ${{ vars.",
    ),
  "ANDROID_MIGRATION_CONTROLLER_HANDOFF_CONTRACT_MISSING",
);
block(
  productionWorkflowActions.length === 0 ||
    productionWorkflowActions.some(([, , revision]) =>
      !/^[0-9a-f]{40}$/.test(revision),
    ),
  "PRODUCTION_WORKFLOW_ACTIONS_NOT_IMMUTABLY_PINNED",
);
block(
  workflowActions.length === 0 ||
    workflowActions.some(({ revision }) => !/^[0-9a-f]{40}$/.test(revision)) ||
    githubWorkflows.some(({ source }) =>
      !source.includes("permissions:\n  contents: read"),
    ) ||
    githubWorkflows.some(
      ({ source }) =>
        source.includes("actions/checkout@") &&
        !source.includes("persist-credentials: false"),
    ) ||
    gradleDependencyProvenance.workflowPolicy?.status !== "source-qualified" ||
    gradleDependencyProvenance.workflowPolicy?.permissions !== "contents:read" ||
    gradleDependencyProvenance.workflowPolicy?.checkoutPersistCredentials !==
      false ||
    gradleDependencyProvenance.releaseCriteria
      .githubActionsPinnedToReviewedCommits !== true ||
    Object.keys(reviewedWorkflowActions).length !==
      new Set(workflowActions.map(({ action }) => action)).size ||
    workflowActions.some(
      ({ action, revision }) =>
        reviewedWorkflowActions[action]?.revision !== revision,
    ) ||
    Object.entries(reviewedWorkflowActions).some(
      ([action, evidence]) =>
        !/^[0-9a-f]{40}$/.test(evidence.revision) ||
        !workflowActions.some(
          (reference) =>
            reference.action === action &&
            reference.revision === evidence.revision,
        ),
    ),
  "GITHUB_WORKFLOW_PROVENANCE_NOT_IMMUTABLE",
);
block(
  !jenkinsLibraryRevision ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary?.status !==
      "qualified" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.productionAllowed !== true ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary?.revision !==
      jenkinsLibraryRevision ||
    jenkinsLibraryRevision !==
      "65079bbe356bca4a3d5a1964e360498735afa1f0" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.repository !== "https://github.com/soramitsu/jenkins-library.git" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.selector !==
      "jenkins-library@65079bbe356bca4a3d5a1964e360498735afa1f0" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.defaultBranch !== "master" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.defaultBranchProtected !== false ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.treeObject !== "d48928bf7706be3fd7d6bb37aae43593c3ba5100" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.commitVerification !== "github-valid" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.sourceReviewStatus !== "source-qualified" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidAppPipeline?.path !== "src/org/android/AppPipeline.groovy" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidAppPipeline?.blob !==
      "efb74759096cc53df123fed9d519c4ce77e0fa68" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidAppPipeline?.sha256 !==
      "59202e27bd9715d5f3b19cab86db6eb8eb962f1a4479ee01cb3a622745efa8ac" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidMainPipeline?.path !== "src/org/android/MainPipeline.groovy" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidMainPipeline?.blob !==
      "b7122116557157bda97bd1e22e59a269975c3078" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidMainPipeline?.sha256 !==
      "c98d14e29fc95f395971b20d6cc5949b7ad32a6a63f45c5e8ef500eb11b47a57" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidParams?.path !== "src/org/android/Params.groovy" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidParams?.blob !== "9e28009d53ca4338369ebf3ffb7dae0d5d8a874c" ||
    gradleDependencyProvenance.workflowPolicy?.jenkinsSharedLibrary
      ?.androidParams?.sha256 !==
      "6c580e4054b238a5c68a6b878df0e608db0cad156c7fb355fec117ddf8cfad1a" ||
    gradleDependencyProvenance.releaseCriteria.jenkinsSharedLibraryPinned !==
      true,
  "JENKINS_SHARED_LIBRARY_NOT_IMMUTABLY_PINNED",
);

const gradleDependencyProvenanceQualified =
  gradleDependencyProvenance.status === "qualified" &&
  gradleDependencyProvenance.releaseEnabled === true &&
  gradleDependencyProvenance.wrapper.wrapperJarMatchesDistribution === true &&
  gradleDependencyProvenance.repositoryPolicy.sourceQualifiedVendorRepository
    ?.productionAllowed === true &&
  gradleDependencyProvenance.dependencyVerification.status === "qualified" &&
  gradleDependencyProvenance.dependencyVerification.independentlyReviewed ===
    true &&
  gradleDependencyProvenance.dependencyLocking.status === "qualified" &&
  gradleDependencyProvenance.dependencyLocking.independentlyReviewed === true &&
  Object.values(gradleDependencyProvenance.releaseCriteria).every(
    (criterion) => criterion === true,
  ) &&
  androidDependencyPreflight.failures.length === 0 &&
  androidDependencyPreflight.releaseBlockers.length === 0;
block(
  !gradleDependencyProvenanceQualified,
  "GRADLE_DEPENDENCY_PROVENANCE_NOT_QUALIFIED",
);
assert(
  [
    "nexusAvailable",
    "nexusSendsAvailable",
    "polkamarktVisible",
    "polkamarktMutationsAvailable",
    "tairaDefaultVisible",
  ].every((field) => piIndexerClient.includes(field)) &&
    /suspend fun requireMobileConfig\(\): PiMobileConfig \{[\s\S]*requireHealthy\(\)[\s\S]*val config = getMobileConfig\(\)/.test(
      piIndexerClient,
    ) &&
    piIndexerClient.includes("if (query == HEALTH_QUERY)") &&
    piIndexerClient.includes("requireRecentHealthyIdentity()") &&
    piIndexerClient.includes("enforceResponseCheckpointPostflight = true") &&
    piIndexerClient.includes("requireResponsePreflight()") &&
    piIndexerClient.includes("requireStableResponseCheckpoint(qualification)") &&
    piIndexerClient.includes("PI_INDEXER_RESPONSE_CHECKPOINT_CHANGED") &&
    piIndexerClientTest.includes(
      "production read is bracketed by one stable indexer checkpoint",
    ) &&
    piIndexerClientTest.includes(
      "production read rejects checkpoint drift across its response",
    ) &&
    productionFeatureManager.includes("indexerClient.requireMobileConfig()") &&
    productionFeatureManager.includes("PI_FLAGS_CACHED") &&
    productionFeatureManager.includes(
      ".getBooleanSnapshot(TAIRA_EXPLICIT_PREFERENCE_KEYS)[TAIRA_VISIBLE]",
    ) &&
    productionFeatureManager.includes(
      "val tairaVisibility = TairaVisibilityPolicy.resolve(",
    ) &&
    productionFeatureManager.includes(
      "explicitPreference = explicitTairaPreference",
    ) &&
    productionFeatureManager.includes(
      "preferences.putBoolean(TAIRA_VISIBLE, visible)",
    ) &&
    soraPreferences.includes(
      "fun getBooleanSnapshotFlow(fields: Set<String>): Flow<Map<String, Boolean>>",
    ) &&
    soraPreferences.includes(
      "suspend fun getBooleanSnapshot(fields: Set<String>): Map<String, Boolean>",
    ) &&
    soraPreferences.includes("val keys = fields.associateWith(::booleanPreferencesKey)") &&
    productionFeatureManager.includes(
      "preferences.getBooleanSnapshotFlow(TAIRA_VISIBILITY_KEYS)",
    ) &&
    productionFeatureManager.includes(
      "TairaVisibilityPolicy.resolveCachedSnapshot(",
    ) &&
    productionFeatureManager.includes(
      "PI_TAIRA_DEFAULT_VISIBLE to flags.tairaDefaultVisible",
    ) &&
    productionFeatureManager.includes("preferences.putBooleans(") &&
    productionFeatureManager.includes("PI_FLAGS_CACHED to true") &&
    productionFeatureManager.includes("return INCOMPLETE_CACHE_FLAGS") &&
    productionFeatureManager.includes(
      "val snapshot = preferences.getBooleanSnapshot(FEATURE_CACHE_KEYS)",
    ),
  "PI_MOBILE_EMERGENCY_CAPABILITIES_MISSING",
);
assert(
  strictJsonDocumentAdmission.includes("reader.strictness = Strictness.STRICT") &&
    strictJsonDocumentAdmission.includes("requireWellFormedUtf16(name)") &&
    strictJsonDocumentAdmission.includes("if (!names.add(name)) invalidJson()") &&
    strictJsonDocumentAdmission.includes(
      "JsonToken.STRING -> requireWellFormedUtf16(reader.nextString())",
    ) &&
    strictJsonDocumentAdmission.includes("MAXIMUM_STRICT_JSON_DEPTH = 64") &&
    strictJsonDocumentAdmission.includes("MAXIMUM_STRICT_JSON_TOKENS = 500_000") &&
    strictJsonDocumentAdmissionTest.includes(
      "strict admission rejects duplicate decoded names at every object depth",
    ) &&
    strictJsonDocumentAdmissionTest.includes(
      "strict admission rejects non RFC syntax and trailing documents",
    ) &&
    strictJsonDocumentAdmissionTest.includes(
      "strict admission rejects isolated escaped surrogates",
    ) &&
    strictJsonDocumentAdmissionTest.includes(
      "strict admission independently bounds depth and token work",
    ) &&
    piCanonicalIntegerLexemeSerializer.includes("KSerializer<String?>") &&
    piCanonicalIntegerLexemeSerializer.includes(
      "All seven PI health",
    ) &&
    piCanonicalIntegerLexemeSerializer.includes(
      'Regex("^(?:0|[1-9][0-9]*)$")',
    ) &&
    piCanonicalIntegerLexemeSerializer.includes("decodeJsonElement()") &&
    piCanonicalIntegerLexemeSerializer.includes(
      "JsonPrimitive(requireCanonical(it))",
    ) &&
    piCanonicalIntegerLexemeSerializer.includes(
      'ERROR_CODE = "PI_INDEXER_INTEGER_LEXEME_INVALID"',
    ) &&
    piCanonicalIntegerLexemeSerializer.includes(
      "MAXIMUM_WIRE_BYTES = 4_096",
    ) &&
    (piIndexerModels.match(
      /@Serializable\(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class\)/g,
    ) ?? []).length === 7 &&
    (piIndexerClient.match(
      /@Serializable\(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class\)/g,
    ) ?? []).length === 7 &&
    piHealthIntegerFields.every((field) => {
      const marker = new RegExp(
        `@Serializable\\(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class\\)\\s+val ${field}: String\\?`,
      );
      return marker.test(piIndexerModels) && marker.test(piIndexerClient);
    }) &&
    !piIndexerClient.includes("private fun JsonPrimitive?.wireNumber()") &&
    piCanonicalIntegerLexemeSerializerTest.includes(
      "strict PI integer serializer preserves arbitrary precision number and string lexemes",
    ) &&
    piCanonicalIntegerLexemeSerializerTest.includes(
      "strict PI integer serializer cache encoding is deterministic and precision safe",
    ) &&
    piCanonicalIntegerLexemeSerializerTest.includes(
      "strict PI integer serializer rejects booleans exponents signs and leading zero",
    ) &&
    piCanonicalIntegerLexemeSerializerTest.includes(
      "strict PI integer serializer bounds exact integer work without fixed width conversion",
    ) &&
    piIndexerClientTest.includes(
      "health accepts deployed numeric and canonical quoted worker integer tokens",
    ) &&
    /@Inject\s+constructor\(\s*boundedHttpTextClient: BoundedHttpTextClient,[\s\S]*?graphQlTransport = BoundedGraphQlTransport\(boundedHttpTextClient, json\)/.test(
      piIndexerClient,
    ) &&
    /private val wireJson = Json\(json\) \{\s*isLenient = false/.test(
      piIndexerClient,
    ) &&
    piIndexerClient.includes("private val cachedResponseJson = Json(json)") &&
    (piIndexerClient.match(/coerceInputValues = false/g) ?? []).length >= 2 &&
    piIndexerClient.includes("cachedResponseJson.decodeFromString(") &&
    piIndexerClientTest.includes(
      "offline cache keeps live string only quantity admission",
    ) &&
    piIndexerClient.includes("client.postJsonUtf8(") &&
    piIndexerClient.includes("MAXIMUM_LIVE_RESPONSE_BYTES = 8 * 1_024 * 1_024") &&
    (piIndexerClient.match(/requireStrictJsonDocumentWithoutDuplicateKeys\(/g) ?? [])
      .length >= 2 &&
    piIndexerResponseCache.includes(
      "bytes.decodeToString(throwOnInvalidSequence = true)",
    ) &&
    (piIndexerResponseCache.match(
      /requireStrictJsonDocumentWithoutDuplicateKeys\(/g,
    ) ?? []).length >= 2 &&
    piIndexerResponseCacheTest.includes(
      "ambiguous cache envelope is deleted before materialization",
    ) &&
    piIndexerResponseCacheTest.includes(
      "ambiguous cached response is deleted before publication",
    ) &&
    piIndexerResponseCacheTest.includes(
      "cache writes all seven health integer fields as strings and reads numeric tokens",
    ) &&
    piIndexerOfflineFallbackPolicy.includes(
      "if (!boundedTransportAllowsFallback(current.safeCode)) return false",
    ) &&
    piIndexerOfflineFallbackPolicy.includes(
      "current is RestClientException.WhileSerialization",
    ) &&
    piIndexerOfflineFallbackPolicy.includes(
      "current is RuntimeException && !isRestClientError",
    ) &&
    piIndexerOfflineFallbackPolicyTest.includes(
      "authoritative bounded rejection wins over a nested transient error",
    ) &&
    piIndexerOfflineFallbackPolicyTest.includes(
      "RestClientException.WhileSerialization(",
    ) &&
    soraConfigManagerSource.includes("requireUnambiguousSoraConfigPayload(") &&
    (soraConfigManagerSource.match(/requireUnambiguousSoraConfigPayload\(/g) ?? [])
      .length >= 3 &&
    soraConfigManagerSource.includes(
      "requireSoraConfigCacheFallbackEligible(error)",
    ) &&
    soraConfigPayloadAdmissionTest.includes(
      "config admission rejects duplicate decoded JSON names",
    ) &&
    soraConfigPayloadAdmissionTest.includes(
      "config cache fallback is limited to transient transport failures",
    ),
  "PI_OR_REMOTE_CONFIG_BOUNDED_STRICT_JSON_ADMISSION_MISSING",
);
assert(
  piIndexerClient.includes("getAllTransactionHistory") &&
    piIndexerClient.includes("private suspend fun getAllHistoryElementsQualified(") &&
    piIndexerClient.includes("pageSize = TRANSACTION_PEER_PAGE_SIZE") &&
    piIndexerClient.includes("maxPages = MAX_CURSOR_PAGES") &&
    piIndexerClientTest.includes(
      "transaction peer search walks bounded cursors without truncating contacts",
    ) &&
    piIndexerClient.includes("this API promises the newest `count` rows") &&
    piIndexerClient.includes("page.items.size <= pageSize") &&
    piIndexerClient.includes("result.size == page.totalCount") &&
    piIndexerClient.includes("seenPages") &&
    piIndexerClient.includes("seenCursors") &&
    piIndexerClient.includes('put("after", after?.let(::JsonPrimitive) ?: JsonNull)') &&
    piIndexerClient.includes('"PI_INDEXER_REPEATED_PAGE"') &&
    piIndexerClient.includes("canonicalTransactionHash") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_CHECKPOINT_CHANGED") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_PROVENANCE_CHANGED") &&
    piIndexerClient.includes("PI_INDEXER_PAGE_CHECKPOINT_CHANGED") &&
    piIndexerClient.includes("PI_INDEXER_PAGE_PROVENANCE_CHANGED") &&
    piIndexerClient.includes("seenTransactionIds") &&
    piIndexerClient.includes("consumed <= historyPage.totalCount.toLong()") &&
    piIndexerClient.includes("consumed == historyPage.totalCount.toLong()") &&
    piIndexerClient.includes("PI_INDEXER_TRANSACTION_LOOKUP_AMBIGUOUS") &&
    /responseIdentityValidator = \{ page ->[\s\S]{0,500}?requireTransactionHashes\([\s\S]{0,300}?requireUnambiguousTransactionLookup\(page\)/.test(
      piIndexerClient,
    ) &&
    piIndexerClient.includes("MAX_HISTORY_CALLS_PER_TRANSACTION = 100") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_CALLS_TRUNCATED") &&
    piIndexerClient.includes("calls(first: $MAX_HISTORY_CALLS_PER_TRANSACTION)") &&
    piIndexerClientTest.includes("PI_INDEXER_TRANSACTION_LOOKUP_AMBIGUOUS") &&
    piIndexerClientTest.includes("PI_INDEXER_HISTORY_CALLS_TRUNCATED") &&
    piIndexerClient.includes("id = canonicalHistoryIdentifier(id)") &&
    piIndexerClientTest.includes(
      "general history accepts synthetic bridge ids but exact lookup remains hash only",
    ) &&
    piHistoryCheckpointValidator.includes("isValidHistoryIdentifier") &&
    piHistoryCheckpointValidatorTest.includes(
      "history accepts bounded synthetic event identifiers",
    ) &&
    piIndexerClient.includes("seenItemIds") &&
    piIndexerClient.includes("PI_INDEXER_DUPLICATE_ITEM") &&
    (piIndexerClient.match(/requireUniqueRuntimeMarketIds/g) ?? []).length >= 3 &&
    piIndexerClient.includes("PI_INDEXER_DUPLICATE_RUNTIME_MARKET_ID") &&
    piIndexerClient.includes(
      "runtimeMarketIds.distinct().size == runtimeMarketIds.size",
    ) &&
    piIndexerClientTest.includes("duplicateRuntimeMarketError") &&
    piIndexerClientTest.includes(
      'isEqualTo("PI_INDEXER_DUPLICATE_RUNTIME_MARKET_ID")',
    ) &&
    piIndexerClient.includes("internal object PiResponseCheckpointValidator") &&
    piIndexerClient.includes(
      "internal object PiHistoryResponseIdentityValidator",
    ) &&
    piIndexerClient.includes("PI_INDEXER_RESPONSE_BLOCK_AFTER_CHECKPOINT") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_CHAIN_COORDINATES_INVALID") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_ACCOUNT_MISMATCH") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_TRANSACTION_MISMATCH") &&
    piIndexerClient.includes("PI_INDEXER_MARKET_IDENTITY_MISMATCH") &&
    piIndexerClient.includes("PI_INDEXER_MARKET_STATUS_MISMATCH") &&
    piIndexerClient.includes(
      "PI_INDEXER_INVALID_MARKET_SNAPSHOT_SEMANTICS",
    ) &&
    piIndexerClient.includes('fieldFilter("type", "equalTo", "DEFAULT")') &&
    piIndexerClient.includes('type == "DEFAULT"') &&
    !piIndexerClient.includes('type == "BLOCK"') &&
    piIndexerClient.includes("PI_INDEXER_MARKET_SNAPSHOT_ORDER_INVALID") &&
    piIndexerClient.includes("coalescedNonNegativeExactQuantity") &&
    piIndexerClient.includes("canonicalBlockHash") &&
    piIndexerClient.includes("isAsciiHexDigit") &&
    piIndexerClient.includes("PI_INDEXER_INVALID_SIGNALS") &&
    piIndexerClient.includes("PI_INDEXER_ASSET_IDENTITY_MISMATCH") &&
    piIndexerClient.includes("PI_INDEXER_REFERRER_IDENTITY_MISMATCH") &&
    piIndexerClient.includes("PI_INDEXER_INVALID_POOL_APY") &&
    piIndexerClientTest.includes(
      "response block heights cannot exceed attributed PI checkpoint",
    ) &&
    piIndexerClientTest.includes(
      "qualified history rejects a row after its attributed PI checkpoint",
    ) &&
    piIndexerClientTest.includes(
      "row after attributed checkpoint is rejected before offline cache write",
    ) &&
    piIndexerClientTest.includes(
      "history without canonical block coordinates is rejected before cache write",
    ) &&
    piIndexerClientTest.includes(
      "qualified account history rejects a row for another account before cache write",
    ) &&
    piIndexerClientTest.includes(
      "qualified transaction lookup rejects a different returned hash",
    ) &&
    piIndexerClientTest.includes(
      "qualified market lookup rejects another market before cache write",
    ) &&
    piIndexerClientTest.includes(
      "qualified market catalog rejects a mismatched requested status",
    ) &&
    piIndexerClientTest.includes(
      "invalid market quantities are rejected before offline cache write",
    ) &&
    piIndexerClientTest.includes(
      "invalid dpm enrichment is rejected before offline cache write",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt rows require checkpoint semantics and consistent aliases before cache write",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt trade block hashes are canonical ASCII and nonzero before cache write",
    ) &&
    piIndexerClientTest.includes(
      "market snapshots reject ordering reversals across cursor pages",
    ) &&
    piIndexerClientTest.includes(
      "chart values derive only from canonical exact PI decimals",
    ) &&
    piIndexerClient.includes("validatedChartPercentage") &&
    piIndexerClientTest.includes(
      "chart percentage and unit prices preserve canonical web semantics",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt signals reject negative counts and duplicate labels",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt signals reject negative volume quantities",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt signals reject out of range accuracy",
    ) &&
    piIndexerClientTest.includes(
      "asset reads reject returned ids outside the request and inexact prices",
    ) &&
    piIndexerClientTest.includes(
      "referral rewards reject another returned referrer",
    ) &&
    piIndexerClientTest.includes("pool apys reject negative values") &&
    piIndexerClientTest.includes(
      "polkamarkt cursor pagination rejects cached and live page mixing",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt cursor pagination rejects cached pages from different checkpoints",
    ) &&
    piIndexerClientTest.includes(
      "snapshot order failures remain rejected when cursor pages replay from cache",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt cursor pagination rejects overlapping item identities",
    ) &&
    piIndexerClientTest.includes(
      "cursor history pagination rejects overlapping transaction identities",
    ) &&
    piIndexerClientTest.includes(
      "cursor history pagination rejects a non advancing cursor",
    ),
  "PI_BOUNDED_STRICT_PAGINATION_MISSING",
);
assert(
  !piIndexerClient.includes('put("offset"') &&
    !piIndexerClient.includes("offset: Int?") &&
    !piIndexerClient.includes("historyElements(first: ${'$'}first, offset:") &&
    piIndexerClientTest.includes(
      "numbered history compatibility walks bounded cursors without offsets",
    ) &&
    piIndexerClientTest.includes('doesNotContain("offset")'),
  "PI_HISTORY_OFFSET_PAGINATION_REMAINS",
);
assert(
  piIndexerClient.includes("private suspend fun collectAssetsQualified(") &&
    piIndexerClient.includes("expectedTokenIds: Set<String>? = null") &&
    piIndexerClient.includes("page.items.all { it.id in expectedTokenIds }") &&
    piIndexerClient.includes("collectCursorPagesQualified(") &&
    piIndexerClient.includes("orderBy: [ID_ASC]") &&
    piIndexerClient.includes("pageInfo { hasNextPage endCursor }") &&
    piIndexerClient.includes("data.referrerRewards.toPage(first)") &&
    piIndexerClient.includes("data.poolXYKs.toPage(first)") &&
    piIndexerClient.includes("variables = cursorVariables(first, after)") &&
    piIndexerClient.includes("PI_INDEXER_REWARD_AMOUNT_MISSING") &&
    piIndexerClient.includes("PI_INDEXER_REWARD_BLOCK_HEIGHT_MISSING") &&
    piIndexerClientTest.includes(
      "fiat query walks bounded cursors without truncating prices",
    ) &&
    piIndexerClientTest.includes(
      "filtered asset identity is rejected before offline cache write",
    ) &&
    piIndexerClientTest.includes(
      "referral rewards reject missing values and walk bounded cursors",
    ) &&
    piIndexerClientTest.includes(
      "pool apys allow null values while walking bounded cursors",
    ) &&
    piIndexerClientTest.includes('doesNotContain("\\\"filter\\\"")') &&
    piIndexerClientTest.includes(
      "cursor connections reject missing pagination metadata instead of defaulting empty",
    ) &&
    /data class HistoryConnection\([\s\S]{0,180}?val edges: List<HistoryEdge>,[\s\S]{0,180}?val pageInfo: PageInfo,[\s\S]{0,180}?val totalCount: Int,/.test(
      piIndexerClient,
    ) &&
    /data class CursorConnection<T>\([\s\S]{0,180}?val edges: List<CursorEdge<T>>,[\s\S]{0,180}?val pageInfo: PageInfo,[\s\S]{0,180}?val totalCount: Int,/.test(
      piIndexerClient,
    ) &&
    !piIndexerClientTest.includes(
      "referral rewards coerce missing referral and amount to empty strings",
    ),
  "PI_PRICE_OR_REFERRAL_CURSOR_QUALIFICATION_MISSING",
);
assert(
  piIndexerModels.includes(
    "val typedAccountBalancesAvailable: Boolean = false",
  ) &&
    piIndexerClient.includes("requireTypedAccountBalancesCapability") &&
    piIndexerClient.includes(
      "PI_INDEXER_TYPED_ACCOUNT_BALANCES_UNAVAILABLE",
    ) &&
    piIndexerClientTest.includes(
      "typed account balances stay fail closed for PI schema v1",
    ),
  "PI_TYPED_ACCOUNT_BALANCE_SCHEMA_GATE_MISSING",
);
assert(
  piIndexerModels.includes("val metadataUri: String? = null") &&
    piIndexerModels.includes("val cancellationEvidenceUri: String? = null") &&
    piIndexerModels.includes("val displayYesProbabilityBps: Int? = null") &&
    piIndexerModels.includes("val displayNoProbabilityBps: Int? = null") &&
    piIndexerClient.includes("resolutionSource.validatedMarketReference()") &&
    piIndexerClient.includes("metadataUri.validatedMarketReference()") &&
    piIndexerClient.includes(
      "cancellationEvidenceUri.validatedMarketReference()",
    ) &&
    polkamarktScreen.includes("catalogMarket.metadataUri") &&
    polkamarktScreen.includes("catalogMarket.cancellationEvidenceUri") &&
    polkamarktScreen.includes("catalogMarket.resolutionSource"),
  "POLKAMARKT_CATALOG_EVIDENCE_OR_PRICE_FALLBACK_MISSING",
);
assert(
  piIndexerModels.includes("val totalVolumeUsd: String") &&
    piIndexerModels.includes("val liquidityUsd: String") &&
    piIndexerModels.includes("val volumeUsd: String") &&
    !/data class PolkamarktSignals\([\s\S]*?val totalVolumeUsd: Double/.test(
      piIndexerModels,
    ) &&
    piIndexerClient.includes("MAX_EXACT_QUANTITY_LENGTH = 4_096") &&
    piIndexerClient.includes("EXACT_DECIMAL.matches(this)") &&
    piIndexerClient.includes("val totalVolumeUsd: JsonPrimitive") &&
    piIndexerClient.includes("val probability: JsonPrimitive?") &&
    piIndexerModels.includes("val virtualDepth: String?") &&
    piIndexerModels.includes("val dpmCollateral: String?") &&
    piIndexerModels.includes("val realYesShares: String?") &&
    piIndexerModels.includes("val realNoShares: String?") &&
    piIndexerClient.includes("PI_INDEXER_INVALID_DPM_STATE") &&
    piIndexerClient.includes("val priceChangeDay: JsonPrimitive?") &&
    piIndexerClient.includes('@SerialName("polkamarktSignals")') &&
    piIndexerClient.includes("validatedChartBigDecimal") &&
    piIndexerClient.includes("BigDecimal(exact)") &&
    piIndexerClient.includes("check(isString) { code }") &&
    piIndexerClientTest.includes(
      "polkamarkt signal quantity strings preserve exact decimals",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt signal quantities reject numeric json tokens",
    ) &&
    piIndexerClientTest.includes('"polkamarktSignals": {') &&
    !piIndexerClientTest.includes('"signals": {') &&
    piIndexerClientTest.includes(
      "polkamarkt quantities reject non canonical exponent notation",
    ),
  "PI_POLKAMARKT_EXACT_QUANTITY_CONTRACT_MISSING",
);
assert(
  piIndexerClient.includes("MAX_EXACT_LONG_LENGTH = 19") &&
    piIndexerClient.includes(
      'val UNSIGNED_INTEGER = Regex("^(?:0|[1-9][0-9]*)$")',
    ) &&
    !piIndexerClient.includes("decimal.toBigIntegerExact()") &&
    piIndexerClientTest.includes(
      "health rejects non canonical or oversized checkpoint integers",
    ),
  "PI_HEALTH_CANONICAL_INTEGER_CONTRACT_MISSING",
);
assert(
    piIndexerClient.includes("val blockHeight: Long? = null") &&
    piIndexerClient.includes("blockHeight = blockHeight") &&
    piIndexerClient.includes("response.edges.size <= first") &&
    piIndexerClient.includes("consumed <= historyPage.totalCount.toLong()") &&
    piIndexerClient.includes("PI_INDEXER_DUPLICATE_TRANSACTION") &&
    piIndexerClient.includes("id = canonicalHistoryIdentifier(id)") &&
    piIndexerClient.includes(
      "blockHash = blockHash?.let(::canonicalBlockHash)",
    ) &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_EXECUTION_INVALID") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_FEE_INVALID") &&
    piIndexerClient.includes("PI_INDEXER_HISTORY_TIMESTAMP_INVALID") &&
    piIndexerClient.includes("validatedNonNegativeExactInteger") &&
    piHistoryCheckpointValidator.includes("element.executionKnown") &&
    piHistoryCheckpointValidator.includes("unsignedInteger.matches(element.networkFee)") &&
    transactionMapper.includes("toNonNegativeBigInteger") &&
    transactionMapper.includes("toNonNegativeBigDecimal") &&
    !transactionMapper.includes("toBigDecimalOrDefault") &&
    !transactionMapper.includes("toBigIntegerOrDefault") &&
    /val HISTORY_QUERY = """[\s\S]*?totalCount[\s\S]*?pageInfo \{ hasNextPage endCursor \}[\s\S]*?blockHash[\s\S]*?blockHeight/.test(
      piIndexerClient,
    ) &&
    piIndexerClientTest.includes(
      "transaction history rejects malformed transaction and block identities",
    ) &&
    piIndexerClientTest.includes("PI_INDEXER_INVALID_PAGE") &&
    piIndexerClientTest.includes(
      "transaction history rejects missing or non boolean execution result",
    ) &&
    piIndexerClientTest.includes(
      "transaction history rejects missing malformed negative or decimal fee",
    ) &&
    piIndexerClientTest.includes(
      "transaction history rejects missing or negative timestamp",
    ) &&
    transactionMapperAdversarialTest.includes(
      "transfer with malformed amount is rejected instead of mapping zero",
    ) &&
    transactionMapperAdversarialTest.includes(
      "history with malformed timestamp is rejected instead of mapping epoch",
    ) &&
    piIndexerClientTest.includes('contains("blockHeight")') &&
    piLiveContractTest.includes("historyElements") &&
    piLiveContractTest.includes("totalCount") &&
    piLiveContractTest.includes("blockHeight"),
  "PI_HISTORY_WIRE_IDENTITY_OR_PAGE_CONTRACT_MISSING",
);
assert(
  piHistoryCheckpointValidator.includes(
    "object PiHistoryCheckpointValidator",
  ) &&
    piHistoryCheckpointValidator.includes(
      "element.address == expectedAddress",
    ) &&
    piHistoryCheckpointValidator.includes(
      "element.dataFrom == expectedAddress",
    ) &&
    piHistoryCheckpointValidator.includes(
      "element.dataTo == expectedAddress",
    ) &&
    piHistoryCheckpointValidator.includes(
      "PI_INDEXER_HISTORY_TRANSACTION_INVALID",
    ) &&
    piHistoryCheckpointValidator.includes(
      "PI_INDEXER_HISTORY_BLOCK_MISSING",
    ) &&
    piHistoryCheckpointValidator.includes(
      "PI_INDEXER_HISTORY_ABOVE_CHECKPOINT",
    ) &&
    piHistoryCheckpointValidator.includes(
      "PI_INDEXER_HISTORY_BLOCK_CONFLICT",
    ) &&
    piHistoryCheckpointValidator.includes(
      "PI_INDEXER_HISTORY_CHECKPOINT_FORK",
    ) &&
    piHistoryCheckpointValidator.includes(
      "health.latestIndexedBlockHash?.let",
    ) &&
    piHistoryCheckpointValidator.includes(
      "MAXIMUM_CONCURRENT_RPC_CHECKS = 8",
    ) &&
    piHistoryCheckpointValidator.includes(
      ".chunked(MAXIMUM_CONCURRENT_RPC_CHECKS)",
    ) &&
    piHistoryCheckpointValidatorTest.includes(
      "history binds exact account and canonical hashes at indexed checkpoint",
    ) &&
    piHistoryCheckpointValidatorTest.includes(
      "history rejects a record from another account before rpc",
    ) &&
    piHistoryCheckpointValidatorTest.includes(
      "history rejects a canonical rpc hash mismatch",
    ) &&
    piHistoryCheckpointValidatorTest.includes(
      "history binds optional indexed checkpoint hash even for an empty page",
    ) &&
    piHistoryCheckpointValidatorTest.includes(
      "history rejects conflicting hashes at the same block height before rpc",
    ),
  "PI_HISTORY_CANONICAL_CHECKPOINT_VALIDATION_MISSING",
);
assert(
  transactionHistoryRepository.includes(
    "private val substrateCalls: SubstrateCalls",
  ) &&
    transactionHistoryRepository.includes(
      "private val validatedHistoryCache: PiValidatedHistoryCache",
    ) &&
    (
      transactionHistoryRepository.match(
        /PiHistoryCheckpointValidator\.validate\(/g,
      ) ?? []
    ).length >= 3 &&
    (
      transactionHistoryRepository.match(
        /canonicalBlockHash = substrateCalls::getBlockHash/g,
      ) ?? []
    ).length >= 3 &&
    transactionHistoryRepositoryTest.includes(
      "getLastTransactions rejects a PI record for another account",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "getLastTransactions keeps local pending transactions when indexer fails",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "getTransaction falls back to local pending transaction when indexer fails",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "getLastTransactions uses only previously canonical validated cache when fully offline",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "raw PI cache is replaced by separately canonical validated history without RPC",
    ),
  "PI_HISTORY_REPOSITORY_CHECKPOINT_OR_OFFLINE_OVERLAY_MISSING",
);
assert(
  transactionHistoryRepository.includes(
    "val canonicalHash = requireCanonicalLocalPendingHash(transfer.base.txHash)",
  ) &&
    transactionHistoryRepository.includes(
      "LocalPendingTransactionKey(walletId, canonicalHash)",
    ) &&
    !transactionHistoryRepository.includes(
      "LocalPendingTransactionKey(walletId, transfer.base.txHash)",
    ) &&
    transactionHistoryRepository.includes(
      "val canonicalHash = canonicalLocalPendingHashOrNull(txHash) ?: return",
    ) &&
    !transactionHistoryRepository.includes("key.transactionHash == txHash") &&
    transactionHistoryRepository.includes(
      "value.canonicalExtrinsicHash().takeIf { it == value }",
    ) &&
    transactionHistoryRepository.includes(
      'Regex("^0x[0-9a-f]{64}$")',
    ) &&
    !transactionHistoryRepository.includes("localOverlay[hash ?: rawHash]") &&
    transactionHistoryRepository.includes(
      '"SORA2_PENDING_OVERLAY_HASH_INVALID"',
    ) &&
    extrinsicManager.includes(
      "transactionHash = extrinsic.extrinsicHash().canonicalExtrinsicHash()",
    ) &&
    fearlessLibExt.includes('return "0x${payload.lowercase()}"') &&
    fearlessLibExt.includes("payload.any { it != '0' }") &&
    transactionHistoryRepositoryTest.includes(
      "local pending overlay rejects malformed and noncanonical hashes",
    ) &&
    transactionHistoryRepositoryTest.includes(
      "local pending overlay matches status only by canonical hash",
    ) &&
    transactionHistoryRepositoryTest.includes(
      '"0x" + "AB".repeat(32)',
    ) &&
    transactionHistoryRepositoryTest.includes(
      '"0x" + "\\uFF11".repeat(64)',
    ),
  "SORA2_LOCAL_PENDING_CANONICAL_HASH_ADMISSION_MISSING",
);
assert(
  firebaseWrapper.includes("enum class PrivacySafeErrorClass") &&
    firebaseWrapper.includes(
      "if (blackList.any { it.isInstance(t) })",
    ) &&
    firebaseWrapper.includes("fun recordErrorClass(") &&
    firebaseWrapper.includes(
      "recordErrorClass(privacySafeErrorClass(t))",
    ) &&
    firebaseWrapper.includes(
      "else -> PrivacySafeErrorClass.UNCLASSIFIED_FAILURE",
    ) &&
    !firebaseWrapper.includes('Timber.e(t, "ERROR")') &&
    !firebaseWrapper.includes("recordException(t)") &&
    firebaseWrapper.includes("PrivacySafeTelemetryException(errorClass.name)") &&
    !firebaseWrapper.includes("PrivacySafeTelemetryException(errorClass.name, t)") &&
    transactionHistoryRepository.includes(
      "PrivacySafeErrorClass.PI_TRANSACTION_HISTORY",
    ) &&
    transactionHistoryRepository.includes(
      "PrivacySafeErrorClass.PI_TRANSACTION_DETAIL",
    ) &&
    !transactionHistoryRepository.includes(
      "FirebaseWrapper.recordException(it)",
    ) &&
    extrinsicManager.includes(
      "PrivacySafeErrorClass.EXTRINSIC_SUBMISSION",
    ) &&
    polkamarktCalls.includes(
      "PrivacySafeErrorClass.FINALIZED_EVENT_READ",
    ) &&
    firebaseWrapperTest.includes(
      "PrivacySafeErrorClass.EXTRINSIC_SUBMISSION",
    ) &&
    firebaseWrapperTest.includes(
      "phrase seed private key cnSensitiveWalletAddress raw signed payload",
    ) &&
    firebaseWrapperTest.includes("PrivacySafeErrorClass.STATE_FAILURE") &&
    transactionHistoryRepositoryTest.includes(
      "PrivacySafeErrorClass.PI_TRANSACTION_PEERS",
    ),
  "PRIVACY_SAFE_TRANSACTION_TELEMETRY_MISSING",
);
assert(
  piValidatedHistoryCache.includes("context.noBackupFilesDir") &&
    piValidatedHistoryCache.includes("PiCanonicalHistoryValidationReceipt") &&
    piValidatedHistoryCache.includes("PiHistoryCheckpointValidator.validatePersisted") &&
    piValidatedHistoryCache.includes("payloadSha256") &&
    piValidatedHistoryCache.includes("MessageDigest.isEqual") &&
    piValidatedHistoryCache.includes("StandardCopyOption.ATOMIC_MOVE") &&
    piValidatedHistoryCache.includes("MAXIMUM_ENTRY_COUNT = 64") &&
    piValidatedHistoryCache.includes("MAXIMUM_TOTAL_BYTES = 32L * 1_024 * 1_024") &&
    piValidatedHistoryCache.includes(
      "health.publicBaseUrl == OptionsProvider.polkaswapIndexerEndpoint",
    ) &&
    piValidatedHistoryCacheTest.includes(
      "canonical validated history survives restart but not account change or expiry",
    ) &&
    piValidatedHistoryCacheTest.includes(
      "canonical validation receipt cannot be reused for another account",
    ),
  "PI_CANONICAL_VALIDATED_OFFLINE_HISTORY_CACHE_MISSING",
);
assert(
  polkamarktCatalogCache.includes(
    "PI_INDEXER_CATALOG_CACHE_REQUIRES_LIVE_READ",
  ) &&
    polkamarktCatalogCache.includes(
      "health.publicBaseUrl == OptionsProvider.polkaswapIndexerEndpoint",
    ) &&
    polkamarktCatalogCache.includes(
      "PI_INDEXER_CATALOG_CACHE_CHECKPOINT_INVALID",
    ) &&
    polkamarktCatalogCache.includes("MAX_CACHE_AGE_SECONDS = 24 * 60 * 60L") &&
    (polkamarktCatalogCache.match(/hasCanonicalUniqueMarketIdentity/g) ?? [])
      .length >= 3 &&
    polkamarktCatalogCache.includes(
      "PI_INDEXER_CATALOG_CACHE_MARKET_IDENTITY_INVALID",
    ) &&
    polkamarktCatalogCache.includes(
      "runtimeMarketIds.distinct().size == markets.size",
    ) &&
    polkamarktCatalogCacheTest.includes(
      "legacy catalog cache rejects duplicate runtime market ids across distinct rows",
    ) &&
    polkamarktCatalogCacheTest.includes("duplicateRuntimeMarketPayload") &&
    polkamarktCatalogCacheTest.includes(
      "PolkamarktCatalogCache.decodeValidatedCatalogPayload(",
    ),
  "PI_POLKAMARKT_CATALOG_QUALIFICATION_CACHE_MISSING",
);
assert(
  walletIdentityDao.includes("MAX_ACTIVE_PENDING_TRANSACTIONS = 500") &&
    walletIdentityDao.includes("MAX_RETAINED_TERMINAL_TRANSACTIONS = 500") &&
    walletIdentityDao.includes("pruneAuthoritativelyTerminalTransactions()") &&
    walletIdentityDao.includes(
      "getOldestAuthoritativelyTerminalTransactionsInternal(",
    ) &&
    walletIdentityDao.includes(
      "val tairaBinding = TairaDeployment.binding",
    ) &&
    walletIdentityDao.includes(
      "val tairaChainId = tairaBinding?.currentChainId",
    ) &&
    walletIdentityDao.includes(
      "val tairaPendingJournalPrefix = tairaBinding?.pendingJournalPrefix.orEmpty()",
    ) &&
    walletIdentityDao.includes(
      "it.state in TERMINAL_PENDING_STATES && !it.submissionIsAmbiguous",
    ) &&
    walletIdentityDao.includes(
      "deleteAuthoritativelyTerminalTransactionsInternal(",
    ) &&
    retainedSchemaMigrationTest.includes(
      "terminalPendingRetentionNeverPrunesUnresolvedRecoveryRows",
    ) &&
    retainedSchemaMigrationTest.includes(
      '"COMMITTED_PENDING_RECONCILIATION" to true',
    ) &&
    retainedSchemaMigrationTest.includes(
      'getPendingTransaction("retained-terminal-0")',
    ) &&
    retainedSchemaMigrationTest.includes(
      'getPendingTransaction("retained-terminal-500")',
    ) &&
    walletIdentityDao.includes("validatePendingTransaction") &&
    walletIdentityDao.includes("PENDING_TRANSACTION_UPDATE_MISMATCH") &&
    walletIdentityDao.includes("PENDING_TRANSACTION_HASH_INVALID") &&
    walletIdentityDao.includes("private fun isCanonicalPendingHash(") &&
    walletIdentityDao.includes(
      "PolkamarktPendingAssetId.parse(transaction.assetId) != null",
    ) &&
    walletIdentityDao.includes('transaction.networkId == "sora2"') &&
    walletIdentityDao.includes(
      'transaction.networkId in setOf("minamoto", "taira")',
    ) &&
    walletIdentityDao.includes(
      "NexusAssetDefinitionIdentity.hasCanonicalWireShape(",
    ) &&
    walletIdentityDao.includes('hash.startsWith("0x")') &&
    walletIdentityDao.includes('normalized == hash.removePrefix("0x")') &&
    walletIdentityDao.includes("normalized == hash") &&
    retainedSchemaMigrationTest.includes('"0".repeat(64)') &&
    retainedSchemaMigrationTest.includes('"0x" + "a".repeat(64)') &&
    retainedSchemaMigrationTest.includes('"A".repeat(64)') &&
    retainedSchemaMigrationTest.includes(
      'val canonicalPolkamarktHash = "0x" + "b".repeat(64)',
    ) &&
    retainedSchemaMigrationTest.includes(
      'networkId = "sora2"',
    ) &&
    retainedSchemaMigrationTest.includes(
      'assetId = "polkamarkt:1:BUY"',
    ) &&
    retainedSchemaMigrationTest.includes('"0x" + "0".repeat(64)') &&
    retainedSchemaMigrationTest.includes('"0X" + "b".repeat(64)') &&
    retainedSchemaMigrationTest.includes(
      'localId = "pending-polkamarkt-wrong-network"',
    ) &&
    retainedSchemaMigrationTest.includes(
      'localId = "pending-polkamarkt-invalid-identity-$index"',
    ) &&
    retainedSchemaMigrationTest.includes(
      'localId = "pending-nexus-wrong-network"',
    ) &&
    retainedSchemaMigrationTest.includes('networkId = "minamoto"') &&
    polkamarktRuntimeDtos.includes("object PolkamarktPendingAssetId") &&
    polkamarktRuntimeDtos.includes(
      'return "polkamarkt:$marketId:$operation"',
    ) &&
    polkamarktRuntimeDtos.includes("encode(it.marketId, it.operation) == value") &&
    polkamarktMarketIdTest.includes(
      "pending journal asset identity is exact and operation scoped",
    ) &&
    polkamarktMarketIdTest.includes('"polkamarkt:1:buy"') &&
    polkamarktTrader.includes("transactionHash.canonicalExtrinsicHash()") &&
    polkamarktTrader.includes(
      "records.forEach { WalletIdentityDao.validatePendingTransaction(it) }",
    ) &&
    polkamarktTrader.includes('networkId = "sora2"') &&
    polkamarktTrader.includes(
      "assetId = PolkamarktPendingAssetId.encode(marketId, operation)",
    ) &&
    polkamarktTrader.includes(
      "PolkamarktPendingAssetId.parse(record.assetId)",
    ) &&
    polkamarktViewModel.includes("val pendingRecoveryRequired: Boolean = false") &&
    polkamarktViewModel.includes(".retryPolkamarktPendingObservation(") &&
    polkamarktViewModel.includes("retryWhen { error, attempt ->") &&
    polkamarktPendingObservationPolicyTest.includes(
      "pending observer failure resubscribes and receives repaired state",
    ) &&
    polkamarktPendingObservationPolicyTest.includes(
      "authoritative empty pending state clears only the recovery error",
    ) &&
    polkamarktPendingObservationPolicyTest.includes(
      "PI lag retries exact reconciliation until Room becomes terminal",
    ) &&
    polkamarktPendingObservationPolicyTest.includes(
      "partial reconciliation resets backoff while unresolved rows remain",
    ) &&
    polkamarktPendingObservationPolicyTest.includes(
      "account switch stops PI lag recovery without another pass",
    ) &&
    polkamarktViewModel.includes(
      "internal suspend fun reconcilePolkamarktPendingUntilTerminal(",
    ) &&
    polkamarktViewModel.includes(
      "trader.observePending(accountAddress).first().isNotEmpty()",
    ) &&
    polkamarktViewModel.includes(
      "if (pendingRecoveryJob?.isActive != true)",
    ) &&
    polkamarktViewModel.includes("pendingRecoveryJob?.cancel()") &&
    polkamarktViewModel.includes("POLKAMARKT_PENDING_RECOVERY_REQUIRED") &&
    polkamarktViewModel.includes(
      "!_state.value.mutationsEnabled || _state.value.pendingRecoveryRequired",
    ) &&
    /state\.mutationsEnabled\s*&&\s*!state\.pendingRecoveryRequired\s*&&\s*!state\.mutationLoading/.test(
      polkamarktScreen,
    ) &&
    fearlessLibExt.includes("payload.any { it != '0' }") &&
    polkamarktPendingRecoveryTest.includes(
      '"0x${"0".repeat(64)}".canonicalExtrinsicHash()',
    ),
  "PENDING_TRANSACTION_JOURNAL_VALIDATION_MISSING",
);

const irohaPin = json("config/iroha-mobile-sdk-pin.json");
assert(
  irohaPin.schemaVersion === 4 &&
    irohaPin.platform === "android" &&
    /^\d{4}-\d{2}-\d{2}$/.test(irohaPin.assessedAt ?? ""),
  "IROHA_ANDROID_READINESS_SCHEMA_MISMATCH",
);
assert(
  irohaPin.upstream.tag === "v2.0.0-rc.2.1-fearless-mobile-sdk.3",
  "IROHA_ANDROID_TAG_MISMATCH",
);
assert(
  irohaPin.upstream.commit === "4f8cfbdd17aa6a3b049e619f23ec02501e5297b6",
  "IROHA_ANDROID_COMMIT_MISMATCH",
);
assert(
  irohaPin.artifact.sha256 ===
    "24bb47552977cc2610512f59b8bccfd0b047b179302ed972600a66ddc1a01de6",
  "IROHA_ANDROID_ARCHIVE_HASH_MISMATCH",
);
assert(irohaPin.artifact.bytes === 116992564, "IROHA_ANDROID_ARCHIVE_SIZE_MISMATCH");
assert(
  irohaPin.artifact.githubReleaseImmutable === false &&
    typeof irohaPin.artifact.freshArtifactReviewRequired === "boolean" &&
    irohaPin.artifact.previouslyReviewedArchiveSha256 ===
      "50e37369e3b08e4435f15ae31d1baca28dd55eae82686000df8f39b85feee7e6" &&
    irohaPin.artifact.previouslyReviewedArchiveBytes === 116231129,
  "IROHA_ANDROID_MUTABLE_ARCHIVE_PROVENANCE_MISSING",
);
assert(
  irohaPin.reviewedCore.coordinate ===
    "org.hyperledger.iroha.sdk:core-jvm:2.0.0-rc.2.1-fearless-mobile-sdk.3" &&
    /^[0-9a-f]{64}$/.test(irohaPin.reviewedCore.jarSha256 ?? "") &&
    irohaPin.reviewedCore.reviewedAgainstArchiveSha256 ===
      (irohaPin.reviewedCore.currentArchiveCoreJarIdentityVerified
        ? irohaPin.artifact.sha256
        : irohaPin.artifact.previouslyReviewedArchiveSha256),
  "IROHA_ANDROID_CORE_HASH_MISMATCH",
);
const productionSignerBinding = irohaPin.productionSignerBinding;
const productionFinalityBinding = irohaPin.productionFinalityBinding;
const finalityTrustContract = irohaPin.finalityTrustContract;
const nativeFinalityCanaryContract = irohaPin.nativeFinalityCanaryContract;
const nativeCanaryContract = irohaPin.nativeCanaryContract;
const nativeCanaryQualificationEvidence =
  nativeCanaryContract?.qualificationEvidence;
const requiredNativeCanaryQualificationFields = [
  "reviewedPlatformArtifactPresent",
  "requiredExportInventoryQualified",
  "transactionBytesParityQualified",
  "signingPrehashParityQualified",
  "signedEnvelopeParityQualified",
  "decodeProjectionParityQualified",
];
assert(
  productionSignerBinding &&
    ["blocked", "qualified"].includes(productionSignerBinding.status) &&
    productionSignerBinding.consumerModule === "feature_wallet_impl" &&
    productionSignerBinding.dependencyConfiguration === "implementation" &&
    ["adapterReviewed", "artifactIdentityReviewed", "bindingReviewed"].every(
      (field) => typeof productionSignerBinding[field] === "boolean",
    ) &&
    [
      "adapterType",
      "adapterSourcePath",
      "adapterSourceSha256",
      "artifactCoordinate",
      "artifactSha256",
    ].every(
      (field) =>
        productionSignerBinding[field] === null ||
        typeof productionSignerBinding[field] === "string",
    ),
  "IROHA_ANDROID_SIGNER_BINDING_SCHEMA_INVALID",
);
assert(
  hasExactKeys(productionFinalityBinding, [
    "status",
    "consumerModule",
    "adapterType",
    "adapterSourcePath",
    "adapterSourceSha256",
    "verifierSourceRevision",
    "verifierArtifactSha256",
    "nativeFinalityCanaryReceiptSha256",
    "finalityTrustManifestReceiptSha256",
    "adapterReviewed",
    "verifierArtifactIdentityReviewed",
    "bindingReviewed",
  ]) &&
    ["blocked", "qualified"].includes(productionFinalityBinding.status) &&
    productionFinalityBinding.consumerModule === "feature_wallet_impl" &&
    [
      "adapterType",
      "adapterSourcePath",
      "adapterSourceSha256",
      "verifierSourceRevision",
      "verifierArtifactSha256",
      "nativeFinalityCanaryReceiptSha256",
      "finalityTrustManifestReceiptSha256",
    ].every(
      (field) =>
        productionFinalityBinding[field] === null ||
        typeof productionFinalityBinding[field] === "string",
    ) &&
    [
      "adapterReviewed",
      "verifierArtifactIdentityReviewed",
      "bindingReviewed",
    ].every((field) => typeof productionFinalityBinding[field] === "boolean"),
  "IROHA_ANDROID_FINALITY_BINDING_SCHEMA_INVALID",
);
const requiredFinalityTrustBooleans = [
  "requiresUnpredictableNonzeroChallenge",
  "requiresExactChallengeBinding",
  "requiresCanonicalNoritoRoundTrip",
  "requiresExpectedChainId",
  "requiresExpectedNodeAndBuildIdentity",
  "requiresExpectedValidatorRosterAndQuorum",
  "requiresCanonicalSignedGenesis",
  "requiresTrustedFirstHeightContextId",
  "requiresStatefulSuccessorVerification",
  "requiresFinalizedBlockHashBinding",
  "requiresNodeAndAggregateSignatures",
  "requiresSingleStateView",
];
assert(
  hasExactKeys(finalityTrustContract, [
    "schemaVersion",
    "status",
    "attestationRoute",
    "bundleRoute",
    "serverContractSourceRevision",
    "serverOpenApiSha256",
    "serverRouteSourceSha256",
    "minamotoTrustedContextReceiptSha256",
    "tairaTrustedContextReceiptSha256",
    ...requiredFinalityTrustBooleans,
    "acceptsStatusBlocksScalar",
    "acceptsSelfDeclaredNodeTrust",
  ]) &&
    finalityTrustContract.schemaVersion === 1 &&
    ["blocked", "qualified"].includes(finalityTrustContract.status) &&
    finalityTrustContract.attestationRoute ===
      FUNDED_CANARY_ATTESTATION_ROUTE &&
    finalityTrustContract.bundleRoute === FUNDED_CANARY_BUNDLE_ROUTE &&
    requiredFinalityTrustBooleans.every(
      (field) => finalityTrustContract[field] === true,
    ) &&
    finalityTrustContract.acceptsStatusBlocksScalar === false &&
    finalityTrustContract.acceptsSelfDeclaredNodeTrust === false,
  "IROHA_ANDROID_FINALITY_TRUST_CONTRACT_INVALID",
);
assert(
  hasExactKeys(nativeFinalityCanaryContract, [
    "schemaVersion",
    "status",
    "requiredNativeAbi",
    "receiptPath",
    "receiptSha256",
    "attestationRoute",
    "bundleRoute",
    "attestationResponseType",
    "bundleResponseType",
    "reviewedPlatformArtifactPresent",
    "canonicalNoritoRoundTripQualified",
    "attestationDecodeProjectionQualified",
    "bundleDecodeProjectionQualified",
    "trustContextBindingQualified",
    "dirtyLocalDebugArtifactAccepted",
  ]) &&
    nativeFinalityCanaryContract.schemaVersion === 1 &&
    ["missing", "qualified"].includes(nativeFinalityCanaryContract.status) &&
    nativeFinalityCanaryContract.requiredNativeAbi === 21 &&
    nativeFinalityCanaryContract.receiptPath ===
      "docs/modernization/qualification/android-iroha-finality-native-canary-v1.json" &&
    nativeFinalityCanaryContract.attestationRoute ===
      FUNDED_CANARY_ATTESTATION_ROUTE &&
    nativeFinalityCanaryContract.bundleRoute === FUNDED_CANARY_BUNDLE_ROUTE &&
    nativeFinalityCanaryContract.attestationResponseType ===
      "BridgeFinalityAttestationV1" &&
    nativeFinalityCanaryContract.bundleResponseType ===
      "BridgeFinalityBundle" &&
    nativeFinalityCanaryContract.dirtyLocalDebugArtifactAccepted === false &&
    (nativeFinalityCanaryContract.status === "qualified" ||
      [
        "reviewedPlatformArtifactPresent",
        "canonicalNoritoRoundTripQualified",
        "attestationDecodeProjectionQualified",
        "bundleDecodeProjectionQualified",
        "trustContextBindingQualified",
      ].every((field) => nativeFinalityCanaryContract[field] === false)),
  "IROHA_ANDROID_FINALITY_NATIVE_CANARY_CONTRACT_INVALID",
);
assert(
  nativeCanaryContract?.schemaVersion === 1 &&
    nativeCanaryContract.handoffObservedAt === "2026-08-05" &&
    nativeCanaryContract.referenceOnly === true &&
    nativeCanaryContract.requiredNativeAbi === 21 &&
    nativeCanaryContract.vectorSchema ===
      "iroha.js.validation-fee-release-vector.v1" &&
    nativeCanaryContract.vectorSha256 ===
      "5a6ecca9a97d21acf979215e54df00fdd71544900a291270539e8fd374b078ce" &&
    nativeCanaryContract.canonicalTypedSbdCbsiId ===
      "7ZepsJTHCVLKsrFFNZGSRGZgvBhv" &&
    nativeCanaryContract.policyFingerprint ===
      "4ec93301681c32cc0d44c55939f6020a0c1a1aaa5ad24ff2723a7561873fbbf7" &&
    nativeCanaryContract.payoutFingerprint ===
      "c04b52fb6684b555ac69dd3aad026dacf3b325b5e9fa4fcc2b3bd70ca59a31d9" &&
    nativeCanaryContract.dirtyLocalDebugArtifactAccepted === false &&
    hasExactKeys(nativeCanaryQualificationEvidence, [
      "status",
      "receiptPath",
      "receiptSha256",
    ]) &&
    ["missing", "qualified"].includes(
      nativeCanaryQualificationEvidence?.status,
    ) &&
    nativeCanaryQualificationEvidence?.receiptPath ===
      "docs/modernization/qualification/android-iroha-native-canary-v1.json" &&
    ((nativeCanaryQualificationEvidence.status === "missing" &&
      nativeCanaryQualificationEvidence.receiptSha256 === null) ||
      (nativeCanaryQualificationEvidence.status === "qualified" &&
        /^[0-9a-f]{64}$/.test(
          nativeCanaryQualificationEvidence.receiptSha256 ?? "",
        ))) &&
    (nativeCanaryQualificationEvidence.status === "qualified" ||
      requiredNativeCanaryQualificationFields.every(
        (field) => nativeCanaryContract[field] === false,
      )) &&
    requiredNativeCanaryQualificationFields.every(
      (field) => typeof nativeCanaryContract[field] === "boolean",
    ),
  "IROHA_ANDROID_NATIVE_CANARY_CONTRACT_INVALID",
);
assert(
  irohaPin.artifact.githubReleaseImmutable === false &&
    irohaPin.artifact.ordinaryBuildFetch === false &&
    typeof irohaPin.provenance.cryptographicTagSignaturePresent === "boolean",
  "IROHA_ANDROID_MUTABLE_DEPENDENCY_FETCH_ENABLED",
);
const requiredIrohaAndroidReleaseCriteria = [
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
];
const externallyEvaluatedIrohaCanaryCriteria = [
  "fundedTairaCanaryQualified",
  "fundedMinamotoCanaryQualified",
];
assert(
  [
    ...requiredIrohaAndroidReleaseCriteria,
    ...externallyEvaluatedIrohaCanaryCriteria,
  ].every((criterion) =>
    Object.hasOwn(irohaPin.releaseCriteria ?? {}, criterion),
  ) &&
    externallyEvaluatedIrohaCanaryCriteria.every(
      (criterion) => irohaPin.releaseCriteria[criterion] === false,
    ),
  "IROHA_ANDROID_RELEASE_CRITERIA_SCHEMA_INCOMPLETE",
);
const stagedIrohaRepository =
  gradleDependencyProvenance.repositoryPolicy.reviewedStagedIrohaRepository;
const irohaQualificationClaimed =
  irohaPin.status === "qualified" ||
  irohaPin.releaseEnabled === true ||
  irohaPin.artifact.applicationRuntimeDependency === true ||
  irohaPin.reviewedCore.currentArchiveCoreJarIdentityVerified === true ||
  irohaPin.releaseCriteria?.productionRuntimeDependencyPinned === true ||
  irohaPin.releaseCriteria?.unavailableSignerReplaced === true ||
  productionSignerBinding?.status === "qualified" ||
  productionSignerBinding?.adapterReviewed === true ||
  productionSignerBinding?.artifactIdentityReviewed === true ||
  productionSignerBinding?.bindingReviewed === true ||
  productionFinalityBinding?.status === "qualified" ||
  productionFinalityBinding?.adapterReviewed === true ||
  productionFinalityBinding?.verifierArtifactIdentityReviewed === true ||
  productionFinalityBinding?.bindingReviewed === true ||
  finalityTrustContract?.status === "qualified" ||
  nativeFinalityCanaryContract?.status === "qualified" ||
  nativeFinalityCanaryContract?.receiptSha256 != null ||
  nativeCanaryQualificationEvidence?.status === "qualified" ||
  nativeCanaryQualificationEvidence?.receiptSha256 != null;
const irohaAndroidQualified =
  irohaPin.status === "qualified" &&
  irohaPin.releaseEnabled === true &&
  irohaPin.artifact.freshArtifactReviewRequired === false &&
  irohaPin.artifact.applicationRuntimeDependency === true &&
  irohaPin.reviewedCore.currentArchiveCoreJarIdentityVerified === true &&
  !String(irohaPin.reviewedCore.knownHasherDefect ?? "").includes(
    "do-not-enable",
  ) &&
  irohaPin.provenance.licenseOrNoticeBundled === true &&
  irohaPin.provenance.sbomBundled === true &&
  irohaPin.provenance.buildProvenanceBundled === true &&
  irohaPin.provenance.artifactAttestationBundled === true &&
  irohaPin.provenance.reproducibleSourceToBinaryIdentity === "proven" &&
  gradleDependencyProvenanceQualified &&
  stagedIrohaRepository.present === true &&
  stagedIrohaRepository.qualified === true &&
  stagedIrohaRepository.symlinkFree === true &&
  stagedIrohaRepository.wholeTreeManifestReviewed === true &&
  /^[0-9a-f]{64}$/.test(stagedIrohaRepository.manifestSha256 ?? "") &&
  productionSignerBinding?.status === "qualified" &&
  productionSignerBinding?.adapterReviewed === true &&
  productionSignerBinding?.artifactIdentityReviewed === true &&
  productionSignerBinding?.bindingReviewed === true &&
  productionFinalityBinding?.status === "qualified" &&
  productionFinalityBinding?.adapterReviewed === true &&
  productionFinalityBinding?.verifierArtifactIdentityReviewed === true &&
  productionFinalityBinding?.bindingReviewed === true &&
  finalityTrustContract?.status === "qualified" &&
  nativeFinalityCanaryContract?.status === "qualified" &&
  /^[0-9a-f]{64}$/.test(nativeFinalityCanaryContract?.receiptSha256 ?? "") &&
  nativeCanaryQualificationEvidence?.status === "qualified" &&
  /^[0-9a-f]{64}$/.test(
    nativeCanaryQualificationEvidence?.receiptSha256 ?? "",
  ) &&
  requiredNativeCanaryQualificationFields.every(
    (field) => nativeCanaryContract?.[field] === true,
  ) &&
  requiredIrohaAndroidReleaseCriteria.every(
    (criterion) => irohaPin.releaseCriteria?.[criterion] === true,
  );
block(
  !irohaAndroidQualified,
  "IROHA_ANDROID_CURRENT_ARTIFACT_NOT_REVIEWED",
);
block(!irohaAndroidQualified, "IROHA_ANDROID_SDK_NOT_SEND_QUALIFIED");

const manifests = [
  "common/src/production/assets/sora2_metadata.manifest.json",
  "feature_wallet_impl/src/test/resources/sora2_metadata.manifest.json",
];
const metadataFiles = [
  "common/src/production/assets/sora2_metadata",
  "feature_wallet_impl/src/test/resources/sora2_metadata",
];
manifests.forEach((path, index) => {
  const manifest = json(path);
  assert(
    manifest.sourceRevision === "411dcdb70c5c00b21482a44d02334840d5f338c6",
    `SORA2_SOURCE_REF_MISMATCH:${path}`,
  );
  assert(manifest.specVersion === 130, `SORA2_SPEC_VERSION_MISMATCH:${path}`);
  assert(
    manifest.transactionVersion === 130,
    `SORA2_TRANSACTION_VERSION_MISMATCH:${path}`,
  );
  assert(
    manifest.rpcEndpoint === "https://ws.mof.sora.org",
    `SORA2_RUNTIME_RPC_ENDPOINT_MISMATCH:${path}`,
  );
  assert(
    sha256(metadataFiles[index]) === manifest.fileSha256,
    `SORA2_METADATA_HASH_MISMATCH:${path}`,
  );
});
const runtimeTypesPath =
  "common/src/production/assets/types_scalecodec_mobile.json";
const runtimeTypesManifestPath =
  "common/src/production/assets/types_scalecodec_mobile.manifest.json";
const runtimeTypesManifest = json(runtimeTypesManifestPath);
assert(
  runtimeTypesManifest.contract ===
    "sora2-runtime-130-mobile-type-definitions-v1" &&
    runtimeTypesManifest.reviewedForSourceRevision ===
      "411dcdb70c5c00b21482a44d02334840d5f338c6" &&
    runtimeTypesManifest.specVersion === 130 &&
    runtimeTypesManifest.transactionVersion === 130 &&
    runtimeTypesManifest.fileSha256 ===
      "e87760d7a566d1b1b3d21a1e76ad70990fd54e14e6af3ba27dd4440461063601" &&
    sha256(runtimeTypesPath) === runtimeTypesManifest.fileSha256,
  "SORA2_RUNTIME_TYPES_HASH_MISMATCH",
);
assert(
  sora2RuntimeContract.includes(
    'const val METADATA_FILE_SHA256 =\n            "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf"',
  ) &&
    sora2RuntimeContract.includes("runtimeManager.getMutationRuntimeContext()") &&
    !sora2RuntimeContract.includes("calls.getMetadataHex()") &&
    !sora2RuntimeContract.includes("calls.getRuntimeVersion()") &&
    sora2RuntimeContract.includes(
      'const val RUNTIME_RPC_ENDPOINT = "https://ws.mof.sora.org"',
    ) &&
    sora2RuntimeContract.includes(
      'const val RUNTIME_WS_ENDPOINT = "wss://ws.mof.sora.org"',
    ) &&
    sora2RuntimeContract.includes("requireReviewedSora2WebSocketEndpoint(") &&
    sora2RuntimeContract.includes("SORA2_MUTATION_TRANSPORT_UNREVIEWED") &&
    sora2RuntimeContract.includes(
      'const val SORA_MAINNET_GENESIS_HASH =\n            "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"',
    ) &&
    sora2RuntimeContract.includes("SORA2_GENESIS_HASH_MISMATCH") &&
    sora2RuntimeContract.includes("context.metadataSha256") &&
    sora2RuntimeContract.includes("context.typesSha256") &&
    sora2RuntimeContract.includes("context.snapshot.metadata.module") &&
    sora2RuntimeContract.includes("SORA2_RUNTIME_SNAPSHOT_IDENTITY_MISMATCH") &&
    sora2RuntimeContract.includes("SORA2_RUNTIME_TYPES_IDENTITY_MISMATCH") &&
    sora2RuntimeContract.includes("SORA2_RUNTIME_SNAPSHOT_VERSION_MISMATCH") &&
    sora2RuntimeContract.includes(
      'const val TYPES_FILE_SHA256 =\n            "e87760d7a566d1b1b3d21a1e76ad70990fd54e14e6af3ba27dd4440461063601"',
    ) &&
    sora2RuntimeContractTest.includes(
      "wrong live genesis fails before runtime metadata can authorize a mutation",
    ) &&
    runtimeManagerSource.includes("SORA2_SS58_PREFIX: Short = 69") &&
    runtimeManagerSource.includes("SORA2_SS58_PREFIX_MISMATCH") &&
    runtimeManagerSource.includes("class Sora2RuntimeIdentityException") &&
    runtimeManagerSource.includes(
      "observed != BigInteger.valueOf(SORA2_SS58_PREFIX.toLong())",
    ) &&
    runtimeManagerSource.includes(
      "val observedPrefix = valueConstant?.type?.fromByteArrayOrNull(",
    ) &&
    !/fromByteArrayOrNull\([\s\S]*?\) as\? BigInteger[\s\S]*?\?\: SORA2_SS58_PREFIX/.test(
      runtimeManagerSource,
    ) &&
    runtimeManagerSource.includes("getMutationRuntimeContext()") &&
    runtimeManagerSource.includes("sora2RuntimeRpcClient.getFinalizedIdentity()") &&
    runtimeManagerSource.includes(
      "sora2RuntimeRpcClient.getMetadataAtFinalized(finalizedHash)",
    ) &&
    runtimeManagerSource.includes(
      "sora2RuntimeRpcClient.getMetadataAtFinalized(",
    ) &&
    runtimeManagerSource.includes(
      "connectionManager.requireReviewedSora2MutationTransport()",
    ) &&
    !runtimeManagerSource.includes("SocketService") &&
    !runtimeManagerSource.includes("executeAsyncMapped") &&
    !runtimeManagerSource.includes("RuntimeVersionAtRequest") &&
    !runtimeManagerSource.includes("RuntimeMetadataAtRequest") &&
    runtimeManagerSource.includes("SORA2_RUNTIME_REFRESH_FAILED") &&
    runtimeManagerSource.includes("enforceReviewedProductionRuntime") &&
    runtimeManagerSource.includes('"SORA2_SPEC_VERSION_MISMATCH"') &&
    runtimeManagerSource.includes('"SORA2_TRANSACTION_VERSION_MISMATCH"') &&
    runtimeManagerSource.includes(
      "runtimeVersion.specVersion != candidate.runtimeVersion",
    ) &&
    runtimeManagerSource.includes("liveGenesisHash != candidate.genesisHash") &&
    runtimeManagerSource.includes("requireSora2Ss58Prefix(") &&
    /val metadataSha256 = admittedMetadata\.runtimeMetadataSha256\(\)[\s\S]*val typesSha256 = sora2TypesRaw\.rawSha256\(\)[\s\S]*val validatedPrefix = requireSora2Ss58Prefix\([\s\S]*return RuntimeCandidate\([\s\S]*prefix = validatedPrefix[\s\S]*metadataSha256 = metadataSha256[\s\S]*typesSha256 = typesSha256/.test(
      runtimeManagerSource,
    ) &&
    runtimeManagerSource.includes(
      'RUNTIME_CACHE_MANIFEST_FILE = "sora2_runtime_cache_manifest_v2.json"',
    ) &&
    runtimeManagerSource.includes("data class RuntimeCacheManifest") &&
    runtimeManagerSource.includes('@SerializedName("genesisHash")') &&
    runtimeManagerSource.includes("createRuntimeCacheManifest(") &&
    runtimeManagerSource.includes("requirePublishedRuntimeCache(") &&
    runtimeManagerSource.includes("legacyRuntimeCacheMatchesBundled(") &&
    runtimeManagerSource.includes("SORA2_RUNTIME_CACHE_METADATA_HASH_MISMATCH") &&
    runtimeManagerSource.includes("SORA2_RUNTIME_CACHE_TYPES_HASH_MISMATCH") &&
    runtimeManagerSource.includes("SORA2_RUNTIME_CACHE_GENERATION_MISMATCH") &&
    runtimeManagerSource.includes("MAX_RUNTIME_CACHE_MANIFEST_BYTES = 16 * 1024") &&
    runtimeManagerSource.includes("MAX_RUNTIME_METADATA_BYTES = 16 * 1024 * 1024") &&
    runtimeManagerSource.includes("MAX_RUNTIME_TYPES_BYTES = 8 * 1024 * 1024") &&
    runtimeManagerSource.includes("MAX_RUNTIME_GENERATION_SCAN = 32") &&
    runtimeManagerSource.includes("MAX_RUNTIME_GENERATION_PRUNE = 4") &&
    runtimeManagerSource.includes("requireRuntimePayloadWithinLimit(") &&
    runtimeManagerSource.includes("mayAdmitLegacyRuntimeCache(") &&
    runtimeManagerSource.includes("selectUnreachableRuntimeGenerationFiles(") &&
    /getMetadataAtFinalized\(finalizedHash\)[\s\S]*requireRuntimePayloadWithinLimit\([\s\S]*MAX_RUNTIME_METADATA_BYTES/.test(
      runtimeManagerSource,
    ) &&
    /boundedHttpTextClient\.getUtf8\([\s\S]*maximumBytes = MAX_RUNTIME_TYPES_BYTES[\s\S]*requireRuntimePayloadWithinLimit\([\s\S]*MAX_RUNTIME_TYPES_BYTES/.test(
      runtimeManagerSource,
    ) &&
    !runtimeManagerSource.includes("restClient.getAsString") &&
    /val metadataSha256 = admittedMetadata\.runtimeMetadataSha256\(\)[\s\S]*if \(requireReviewedIdentityBeforeParsing\)[\s\S]*Sora2RuntimeContract\.METADATA_FILE_SHA256[\s\S]*val sora2TypesRaw =[\s\S]*val typesSha256 = sora2TypesRaw\.rawSha256\(\)[\s\S]*requireReviewedSora2SnapshotIdentity\([\s\S]*RuntimeMetadataReader\.read\(admittedMetadata\)/.test(
      runtimeManagerSource,
    ) &&
    /metadataSource = MetadataSource\.SoraNet,[\s\S]*runtimeVersion = liveRuntimeVersion\.specVersion,[\s\S]*requireReviewedIdentityBeforeParsing =\s*enforceReviewedProductionRuntime/.test(
      runtimeManagerSource,
    ) &&
    /readInternalCacheFile\(fileName, maxBytes\)[\s\S]*RuntimeCacheReadState\.UNREADABLE/.test(
      runtimeManagerSource,
    ) &&
    /fileManager\.writeInternalCacheFileAtomically\(\s*coordinates\.metadataFile,\s*metadata,\s*MAX_RUNTIME_METADATA_BYTES,[\s\S]*currentCoroutineContext\(\)\.ensureActive\(\)[\s\S]*fileManager\.writeInternalCacheFileAtomically\(\s*coordinates\.typesFile,\s*sora2Types,\s*MAX_RUNTIME_TYPES_BYTES,[\s\S]*currentCoroutineContext\(\)\.ensureActive\(\)[\s\S]*requirePublishedRuntimeCache\([\s\S]*fileManager\.writeInternalCacheFileAtomically\(\s*RUNTIME_CACHE_MANIFEST_FILE,\s*manifestRaw,\s*MAX_RUNTIME_CACHE_MANIFEST_BYTES,/.test(
      runtimeManagerSource,
    ) &&
    /readInternalCacheFileAtomically\(\s*RUNTIME_CACHE_MANIFEST_FILE,\s*MAX_RUNTIME_CACHE_MANIFEST_BYTES,[\s\S]*loadPublishedRuntimeCache\(manifestRaw\)[\s\S]*MetadataSource\.Cache\(published\.sora2Types\)/.test(
      runtimeManagerSource,
    ) &&
    /val published = requirePublishedRuntimeCache\([\s\S]*pruneUnreachableRuntimeCacheFiles\(coordinates\)[\s\S]*return published/.test(
      runtimeManagerSource,
    ) &&
    /val bundledMetadataSha256 = bundledMetadata\.runtimeMetadataSha256\(\)[\s\S]*legacyMetadata\.runtimeMetadataSha256\(\) == bundledMetadataSha256[\s\S]*catch \(_: Sora2RuntimeIdentityException\) \{[\s\S]*false/.test(
      runtimeManagerSource,
    ) &&
    /fun publishRuntimeCandidate\(candidate: RuntimeCandidate\)[\s\S]*prefix = candidate\.prefix[\s\S]*runtimeMetadataSha256 = candidate\.metadataSha256[\s\S]*runtimeTypesSha256 = candidate\.typesSha256[\s\S]*runtimeSpecVersion = candidate\.runtimeVersion[\s\S]*runtimeGenesisHash = candidate\.genesisHash[\s\S]*runtimeSnapshot = candidate\.snapshot/.test(
      runtimeManagerSource,
    ) &&
    boundedRuntimeRpcClientSource.includes(
      "client.postJsonUtf8(\n        rawUrl = Sora2RuntimeContract.RUNTIME_RPC_ENDPOINT",
    ) &&
    boundedRuntimeRpcClientSource.includes(
      'METHOD_BLOCK_HASH = "chain_getBlockHash"',
    ) &&
    boundedRuntimeRpcClientSource.includes(
      'METHOD_FINALIZED_HEAD = "chain_getFinalizedHead"',
    ) &&
    boundedRuntimeRpcClientSource.includes(
      'METHOD_RUNTIME_VERSION = "state_getRuntimeVersion"',
    ) &&
    boundedRuntimeRpcClientSource.includes(
      'METHOD_METADATA = "state_getMetadata"',
    ) &&
    boundedRuntimeRpcClientSource.includes(
      "MAXIMUM_METADATA_RESPONSE_BYTES = 2 * 1024 * 1024",
    ) &&
    boundedRuntimeRpcClientSource.includes("JsonReader(StringReader(rawResponse))") &&
    boundedRuntimeRpcClientSource.includes("reader.isLenient = false") &&
    !boundedRuntimeRpcClientSource.includes("gson.fromJson(rawResponse") &&
    boundedRuntimeRpcClientSource.includes("MAXIMUM_STRUCTURED_JSON_DEPTH = 32") &&
    boundedRuntimeRpcClientSource.includes("MAXIMUM_STRUCTURED_JSON_TOKENS = 4_096") &&
    boundedRuntimeRpcClientSource.includes("RESULT_ENVELOPE_FIELDS") &&
    boundedRuntimeRpcClientSource.includes("ERROR_ENVELOPE_FIELDS") &&
    boundedRuntimeRpcClientSource.includes("SORA2_RUNTIME_RPC_ID_MISMATCH") &&
    boundedRuntimeRpcClientSource.includes("SORA2_RUNTIME_RPC_REMOTE_ERROR") &&
    boundedRuntimeRpcClientSource.includes("requireCanonicalJsonInteger(") &&
    boundedRuntimeRpcClientTest.includes(
      "finalized identity uses canonical checkpoint requests and method-specific limits",
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "strict envelope rejects version id extra fields null and result error ambiguity",
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "runtime version rejects duplicate security fields before Gson materialization",
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "runtime version structured depth and token work are independently bounded",
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "mutation transport accepts only exact reviewed websocket counterpart",
    ) &&
    connectionManagerSource.includes("acquireReviewedSora2MutationTransport()") &&
    wsConnectionManagerSource.includes(
      "private val mutationTransport = Sora2MutationTransportCoordinator()",
    ) &&
    wsConnectionManagerSource.includes(
      "mutationTransport.observeConnectedAddress(",
    ) &&
    wsConnectionManagerSource.includes(
      "lease.closeAndRunDeferredSwitch(socket::switchUrl)",
    ) &&
    mutationTransportCoordinatorSource.includes(
      "private var admittedConnectedAddress: String? = null",
    ) &&
    mutationTransportCoordinatorSource.includes(
      "private var activeLeases: Int = 0",
    ) &&
    mutationTransportCoordinatorSource.includes(
      "private var deferredSwitchAddress: String? = null",
    ) &&
    /private fun release\(\): String\? = synchronized\(lock\)[\s\S]*?requestedAddress = next[\s\S]*?admittedConnectedAddress = null/.test(
      mutationTransportCoordinatorSource,
    ) &&
    mutationTransportCoordinatorTest.includes(
      "requested and unreviewed sockets fail while exact connected socket admits",
    ) &&
    mutationTransportCoordinatorTest.includes(
      "switch during lease is deferred and release revokes admission before switch",
    ) &&
    mutationTransportCoordinatorTest.includes(
      "lease close is idempotent and cannot invoke deferred switch twice",
    ) &&
    nodeManager.includes("WalletMutationCoordinator.withLock") &&
    nodeManager.includes("switchUrlWithWalletMutationBarrier") &&
    (nodeManager.match(/connectionManager\.switchUrl\(/g) ?? []).length === 1 &&
    /suspend fun submitExtrinsic\([\s\S]*acquireReviewedSora2MutationTransport\(\)[\s\S]*SubmitExtrinsicRequest\(extrinsic\)[\s\S]*transportLease\.close\(\)/.test(
      polkamarktCalls,
    ) &&
    /fun submitAndWatchExtrinsic\([\s\S]*flow \{[\s\S]*acquireReviewedSora2MutationTransport\(\)[\s\S]*SubmitAndWatchExtrinsicRequest\(extrinsic\)[\s\S]*transportLease\.close\(\)/.test(
      polkamarktCalls,
    ) &&
    !extrinsicBuilderFactorySource.includes("RuntimeManager") &&
    extrinsicBuilderFactorySource.includes("internal suspend fun createForFee(") &&
    /suspend fun calcFee\([\s\S]*?val runtimeContext = runtimeManager\.getMutationRuntimeContext\(\)[\s\S]*?factory\.createForFee\(from, runtimeContext\)/.test(
      extrinsicManager,
    ) &&
    extrinsicBuilderFactorySource.includes(
      "runtime = runtimeContext.snapshot",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "runtimeVersion = runtimeContext.runtimeVersion",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "genesisHash = runtimeContext.genesisHash.removeHexPrefix().fromHex()",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "blockHash = runtimeContext.finalizedHash.removeHexPrefix().fromHex()",
    ) &&
    !extrinsicBuilderFactorySource.includes("calls.getRuntimeVersion()") &&
    !extrinsicBuilderFactorySource.includes("runtimeManager.getRuntimeSnapshot()") &&
    fileManagerContract.includes("readInternalCacheFileAtomically") &&
    fileManagerContract.includes("writeInternalCacheFileAtomically") &&
    fileManagerContract.includes("listInternalCacheFileNames") &&
    fileManagerContract.includes("deleteInternalCacheFile") &&
    fileManagerImplementation.includes("readBoundedUtf8") &&
    fileManagerImplementation.includes("CodingErrorAction.REPORT") &&
    fileManagerImplementation.includes("Files.newDirectoryStream") &&
    /for \(entry in entries\) \{\s*if \(inspected >= maxEntries\) break\s*inspected \+= 1\s*\/\/ Bound directory work itself/.test(
      fileManagerImplementation,
    ) &&
    fileManagerImplementation.includes("LinkOption.NOFOLLOW_LINKS") &&
    fileManagerImplementation.includes("isSafeAtomicCacheEntryForWrite(") &&
    fileManagerImplementation.includes(
      'validateAtomicEntryIfPresent(File("${target.path}.bak"), fileName)',
    ) &&
    fileManagerImplementation.includes(
      'validateAtomicEntryIfPresent(File("${target.path}.new"), fileName)',
    ) &&
    fileManagerImplementation.includes("AtomicFile(internalCacheFile(fileName))") &&
    fileManagerImplementation.includes("atomicFile.finishWrite(output)") &&
    fileManagerImplementation.includes("atomicFile.failWrite(output)") &&
    fileManagerAtomicEntryPolicyTest.includes(
      "atomic cache write accepts only absent or regular non symlink entries",
    ) &&
    fileManagerAtomicEntryPolicyTest.includes(
      "isSymbolicLinkNoFollow = true",
    ) &&
    fileManagerAtomicRecoveryTest.includes(
      "interruptedAtomicFileWriteRecoversTheLastCompleteValueAfterReopen",
    ) &&
    fileManagerAtomicRecoveryTest.includes(
      "failedAtomicFileWriteNeverPublishesItsPartialValue",
    ) &&
    fileManagerAtomicRecoveryTest.includes(
      "atomicWriterRejectsBaseBackupAndNewSymlinksWithoutFollowingThem",
    ) &&
    boundedHttpTextClient.includes('setRequestProperty("Accept-Encoding", "identity")') &&
    boundedHttpTextClient.includes("useCaches = false") &&
    boundedHttpTextClient.includes(
      'setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")',
    ) &&
    boundedHttpTextClient.includes('setRequestProperty("Pragma", "no-cache")') &&
    boundedHttpTextClient.includes(
      'BoundedHttpTextException("BOUNDED_HTTP_RUNTIME")',
    ) &&
    boundedHttpTextClient.includes("status == HttpURLConnection.HTTP_OK") &&
    boundedHttpTextClient.includes("instanceFollowRedirects = false") &&
    boundedHttpTextClient.includes("maximumBytes - total + 1") &&
    boundedHttpTextClient.includes("CodingErrorAction.REPORT") &&
    boundedHttpTextClient.includes("MAX_REDIRECTS = 3") &&
    boundedHttpTextClient.includes("runInterruptible(Dispatchers.IO)") &&
    /suspend fun getUtf8\([\s\S]*\): String = readBoundedHttpsUtf8\([\s\S]*connectionOpener = BoundedHttpConnectionOpener/.test(
      boundedHttpTextClient,
    ) &&
    /internal suspend fun readBoundedHttpsUtf8\([\s\S]*deadlineWatchdog[\s\S]*CoroutineStart\.UNDISPATCHED[\s\S]*disconnectActiveConnection\(\)[\s\S]*runInterruptible\(Dispatchers\.IO\)[\s\S]*readBoundedStrictUtf8\(input, maximumBytes\)/.test(
      boundedHttpTextClient,
    ) &&
    /private fun requireBoundedHttpsUrl\([\s\S]*url\.protocol\.lowercase\(\) != "https"[\s\S]*url\.userInfo != null/.test(
      boundedHttpTextClient,
    ) &&
    boundedHttpTextClientTest.includes(
      "streaming UTF8 reader stops after exactly one byte beyond its bound",
    ) &&
    boundedHttpTextClientTest.includes(
      "streaming UTF8 reader rejects malformed bytes",
    ) &&
    boundedHttpTextClientTest.includes(
      "client admits three HTTPS redirects and disables transport redirects",
    ) &&
    boundedHttpTextClientTest.includes("assertFalse(connection.useCaches)") &&
    boundedHttpTextClientTest.includes(
      'connection.requestHeaders["Cache-Control"]',
    ) &&
    boundedHttpTextClientTest.includes(
      "client classifies transport runtime defects as cache ineligible",
    ) &&
    boundedHttpTextClientTest.includes(
      "JSON POST requires exact HTTP 200 before reading the response",
    ) &&
    boundedHttpTextClientTest.includes(
      "client rejects a fourth redirect without opening its target",
    ) &&
    boundedHttpTextClientTest.includes(
      "client rejects declared and streamed oversize bodies",
    ) &&
    boundedHttpTextClientTest.includes(
      "client rejects encoded bodies and non success statuses without reading them",
    ) &&
    boundedHttpTextClientTest.includes(
      "client cancellation interrupts a cooperative blocking response and publishes no body",
    ) &&
    boundedHttpTextClientTest.includes(
      "client cancellation disconnects a response that ignores thread interruption",
    ) &&
    boundedHttpTextClientTest.includes(
      "absolute deadline closes a slow drip response and rejects its partial body",
    ) &&
    !substrateModuleSource.includes("setMaxPayloadSize") &&
    !substrateModuleSource.includes("setFrameQueueSize") &&
    sora2RuntimeContractTest.includes(
      "runtime metadata cannot replace an installed Sora2 wallet prefix",
    ) &&
    sora2RuntimeContractTest.includes(
      "generation manifest rejects missing or mixed runtime cache files",
    ) &&
    sora2RuntimeContractTest.includes(
      "malformed or partial legacy runtime cache falls back only to bundled pair",
    ) &&
    sora2RuntimeContractTest.includes(
      "runtime payload admission rejects oversize and malformed UTF8 before caching",
    ) &&
    sora2RuntimeContractTest.includes(
      "unreadable legacy runtime cache permits only bundled fallback",
    ) &&
    sora2RuntimeContractTest.includes(
      "runtime generation pruning is bounded and protects active or unrelated files",
    ) &&
    sora2RuntimeContractTest.includes('it.endsWith(".bak")') &&
    sora2RuntimeContractTest.includes('it.endsWith(".new")') &&
    sora2RuntimeContractTest.includes("it.startsWith(active)") &&
    sora2RuntimeContractTest.includes(
      "mutation runtime identity rejects upgrades and downgrades symmetrically",
    ) &&
    sora2RuntimeContractTest.includes(
      "production runtime cache and mutations are bound to canonical genesis",
    ) &&
    sora2RuntimeContractTest.includes(
      "runtime metadata identity canonicalizes asset newline without weakening type bytes",
    ) &&
    sora2RuntimeContractTest.includes(
      "assertThrows(Sora2RuntimeIdentityException::class.java)",
    ) &&
    sora2RuntimeContractTest.includes("requireSora2Ss58Prefix(null)") &&
    sora2RuntimeContractTest.includes("BigInteger.valueOf(65_605L)") &&
    sora2RuntimeContractTest.includes(
      "coVerify(exactly = 1) { runtimeManager.getMutationRuntimeContext() }",
    ) &&
    runtimeCacheAtomicRecoveryTest.includes(
      "interruptionAfterNewMetadataWriteRetainsTheOldCompletePointer",
    ) &&
    runtimeCacheAtomicRecoveryTest.includes(
      "interruptedManifestReplacementRecoversTheOldCompletePointer",
    ) &&
    runtimeCacheAtomicRecoveryTest.includes(
      "aPointerWhoseGenerationIsIncompleteFailsWithoutMixingOldAndNewFiles",
    ),
  "SORA2_LIVE_METADATA_IDENTITY_GATE_MISSING",
);
assert(
  boundedRuntimeRpcClientSource.includes(
    'METHOD_ACCOUNT_NEXT_INDEX = "system_accountNextIndex"',
  ) &&
    boundedRuntimeRpcClientSource.includes(
      "suspend fun getAccountNextIndex(accountAddress: String): BigInteger",
    ) &&
    boundedRuntimeRpcClientSource.includes(
      "MAXIMUM_ACCOUNT_NONCE_RESPONSE_BYTES = 4 * 1024",
    ) &&
    boundedRuntimeRpcClientSource.includes(
      "resultKind = Sora2RuntimeRpcResultKind.UNSIGNED_INTEGER",
    ) &&
    boundedRuntimeRpcClientSource.includes(
      "requireCanonicalAccountNonceRaw(reader.nextString())",
    ) &&
    boundedRuntimeRpcClientSource.includes(
      'ACCOUNT_NONCE_MAX = BigInteger("4294967295")',
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "account nonce preserves the exact canonical u32 wire integer",
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "account nonce rejects normalized signed quoted and overflowing values",
    ) &&
    boundedRuntimeRpcClientTest.includes(
      "account nonce rejects a noncanonical SORA2 account before transport",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "private val runtimeRpcClient: Sora2BoundedRuntimeRpcClient",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "val nonce = runtimeRpcClient.getAccountNextIndex(from)",
    ) &&
    extrinsicBuilderFactorySource.includes("nonce = nonce") &&
    extrinsicBuilderFactoryTest.includes(
      "signing builder retains the exact bounded RPC nonce as BigInteger",
    ) &&
    extrinsicBuilderFactoryTest.includes(
      'val exactNonce = BigInteger("4294967295")',
    ) &&
    extrinsicBuilderFactoryTest.includes(
      "assertEquals(exactNonce, retainedNonce(result.builder))",
    ) &&
    !polkamarktCalls.includes("suspend fun getNonce(") &&
    !polkamarktCalls.includes("pojo<Double>()") &&
    !extrinsicBuilderFactorySource.includes("calls.getNonce(from)"),
  "SORA2_ACCOUNT_NONCE_PRECISION_GATE_MISSING",
);
assert(
  sora2RuntimeContract.includes(
    "class QualifiedSora2MutationRuntime internal constructor",
  ) &&
    sora2RuntimeContract.includes(
      "internal val context: Sora2MutationRuntimeContext",
    ) &&
    sora2RuntimeContract.includes(
      "suspend fun requirePolkamarktMutationRuntimeContext()",
    ) &&
    sora2RuntimeContract.includes(
      "suspend fun requirePolkamarktMutationRuntimeUnchanged(",
    ) &&
    sora2RuntimeContract.includes(
      "requireSameSora2MutationRuntimeIdentity(",
    ) &&
    sora2RuntimeContract.includes("SORA2_MUTATION_GENESIS_DRIFT") &&
    sora2RuntimeContract.includes("SORA2_MUTATION_SPEC_VERSION_DRIFT") &&
    sora2RuntimeContract.includes(
      "SORA2_MUTATION_TRANSACTION_VERSION_DRIFT",
    ) &&
    sora2RuntimeContract.includes("SORA2_MUTATION_METADATA_DRIFT") &&
    sora2RuntimeContract.includes("SORA2_MUTATION_TYPES_DRIFT") &&
    !sora2RuntimeContract.includes(
      "signing.finalizedHash == current.finalizedHash",
    ) &&
    sora2RuntimeContractTest.includes(
      "qualified signing context accepts finalized advance but rejects every identity drift",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "internal suspend fun createForPolkamarkt(",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "buildExtrinsic(from, keypair, runtime.context)",
    ) &&
    extrinsicBuilderFactorySource.includes(
      "buildExtrinsic(from, generateFakeKeyPair(), runtime.context)",
    ) &&
    extrinsicManager.includes("suspend fun calcPolkamarktFee(") &&
    extrinsicManager.includes("factory.createForPolkamarkt(from, runtime)") &&
    extrinsicManager.includes("suspend fun preparePolkamarktExtrinsic(") &&
    extrinsicManager.includes(
      "factory.createForPolkamarkt(from, keypair, qualifiedPolkamarktRuntime)",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "Polkamarkt preparation passes the qualified context into the exact builder",
    ) &&
    extrinsicManagerSafetyTest.includes(
      "final Polkamarkt fee uses the same qualified builder context",
    ) &&
    !polkamarktTrader.includes("extrinsicManager.prepareExtrinsic(") &&
    (polkamarktTrader.match(/calcPolkamarktFee\(/g) ?? []).length === 2 &&
    /private suspend fun calculateTradeFee\([\s\S]*?signingRuntime: QualifiedSora2MutationRuntime,[\s\S]*?calcPolkamarktFee\([\s\S]*?runtime = signingRuntime,/.test(
      polkamarktTrader,
    ) &&
    /val signingRuntime =\s*runtimeContract\.requirePolkamarktMutationRuntimeContext\(\)\s*val exactNetworkFee = calculateTradeFee\([\s\S]*?signingRuntime = signingRuntime,[\s\S]*?preparePolkamarktExtrinsic\([\s\S]*?runtime = signingRuntime,/.test(
      polkamarktTrader,
    ) &&
    /val signingRuntime =\s*runtimeContract\.requirePolkamarktMutationRuntimeContext\(\)\s*val beforeSignFee = checkNotNull\(\s*extrinsicManager\.calcPolkamarktFee\([\s\S]*?runtime = signingRuntime,[\s\S]*?preparePolkamarktExtrinsic\([\s\S]*?runtime = signingRuntime,/.test(
      polkamarktTrader,
    ) &&
    (polkamarktTrader.match(/preparePolkamarktExtrinsic\(/g) ?? []).length === 2 &&
    (
      polkamarktTrader.match(
        /val signingRuntime =\s*runtimeContract\.requirePolkamarktMutationRuntimeContext\(\)[\s\S]*?preparePolkamarktExtrinsic\([\s\S]*?runtime = signingRuntime,[\s\S]*?Triple\(signed, pendingId, signingRuntime\)/g,
      ) ?? []
    ).length === 2 &&
    (
      polkamarktTrader.match(
        /requireMutationPreTransport\([\s\S]*?signingRuntime = signingRuntime,[\s\S]*?allowedCurrentLocalId = localId,/g,
      ) ?? []
    ).length === 2 &&
    /internal suspend fun requireMutationPreTransport\([\s\S]*?signingRuntime: QualifiedSora2MutationRuntime,[\s\S]*?requirePolkamarktMutationRuntimeUnchanged\(signingRuntime\)/.test(
      polkamarktTrader,
    ) &&
    polkamarktPreTransportGateTest.includes(
      "runtime drift immediately before transport fails closed",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "requirePolkamarktMutationRuntimeUnchanged",
    ),
  "POLKAMARKT_QUALIFIED_RUNTIME_CONTEXT_THREADING_MISSING",
);
assert(
  sha256(metadataFiles[0]) === sha256(metadataFiles[1]),
  "SORA2_RUNTIME_FIXTURES_DIVERGED",
);

const walletDerivationFixture =
  "common/src/test/resources/wallet-derivation-v1.json";
const walletDerivationFixtureSha256 =
  "1dde0fd8131d630cb4eb44da4cd9e85b965fce310b773eb98a5502d995734bfb";
assert(
  sha256(walletDerivationFixture) === walletDerivationFixtureSha256,
  "WALLET_DERIVATION_FIXTURE_HASH_MISMATCH",
);
const walletDerivationContract = json(walletDerivationFixture);
const twelveWordSora2Vector = walletDerivationContract.vectors.find(
  (vector) => vector.name === "bip39-12-abandon",
);
const twentyFourWordSora2Vector = walletDerivationContract.vectors.find(
  (vector) => vector.name === "fearless-default-24",
);
assert(
  walletDerivationContract.sora2?.curve === "sr25519" &&
    walletDerivationContract.sora2?.ss58Prefix === 69 &&
    walletDerivationContract.provenance?.sora2PublicKeySourceSha256 ===
      "3b6ea60c00eae541c6dc05617b295acf8fc3ad1e097274a6c0dad5c15fbfdbc6" &&
    twelveWordSora2Vector?.sora2?.directBip39Seed32Hex ===
      "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc1" &&
    twelveWordSora2Vector?.sora2?.legacySora2MiniSeedHex ===
      "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2" &&
    twelveWordSora2Vector?.sora2?.publicKeyHex ===
      "66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972" &&
    twelveWordSora2Vector?.sora2?.address ===
      "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m" &&
    twentyFourWordSora2Vector?.sora2?.directBip39Seed32Hex ===
      "0a6d060f6242aece4b074e48e7d8166f792a9b2bb7b295fa5ac289eda7647290" &&
    twentyFourWordSora2Vector?.sora2?.legacySora2MiniSeedHex ===
      "8b5c2f0b1d0f27f223df9bf205e3f3cdbf2fb9c3e4da36e0351a7a6ca6d10785" &&
    twentyFourWordSora2Vector?.sora2?.publicKeyHex ===
      "4a50a9606f3b0c47e0582f9a2dce9da3ec59819c6e15468f6f03f07e7fcfed23" &&
    twentyFourWordSora2Vector?.sora2?.address ===
      "cnTAiyqa6dqfhfX5HhJfmPdd56sum2WXFvg1i1UgRReB336yz",
  "SORA2_WALLET_DERIVATION_VECTORS_MISSING",
);
assert(
  irohaKeyDerivationTest.includes(
    "shared twelve and twenty four word vectors preserve Sora2 seed inputs",
  ) &&
    irohaKeyDerivationTest.includes('it.has("sora2")') &&
    irohaKeyDerivationTest.includes('expected["directBip39Seed32Hex"]') &&
    irohaKeyDerivationTest.includes('expected["legacySora2MiniSeedHex"]') &&
    irohaKeyDerivationTest.includes(
      "deriveRetainedSora2MiniSeed",
    ) &&
    irohaKeyDerivationTest.includes("EthereumSeedFactory.deriveSeed(") &&
    irohaKeyDerivationTest.includes("SubstrateSeedFactory.deriveSeed(") &&
    irohaKeyDerivationTest.includes(
      "bip39Seed.copyOfRange(0, SORA2_SEED_BYTES)",
    ) &&
    !irohaKeyDerivationTest.includes("SubstrateKeypairFactory") &&
    !irohaKeyDerivationTest.includes("Sr25519JNI") &&
    migrationManagerProductionPathQualificationTest.includes(
      "private fun pinnedMnemonicVector(mnemonic: String)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "SubstrateKeypairFactory.generate(",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertEquals(it.publicKey, keyPair.publicKey.toHexString())",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "assertEquals(it.address, address)",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "PINNED_TWELVE_WORD_PUBLIC_KEY",
    ) &&
    migrationManagerProductionPathQualificationTest.includes(
      "PINNED_TWENTY_FOUR_WORD_PUBLIC_KEY",
    ),
  "SHARED_SORA2_WALLET_DERIVATION_TEST_MISSING",
);
const iosWalletDerivationFixture = resolve(
  root,
  "../sora-ios/Fixtures/Modernization/wallet-derivation-v1.json",
);
if (existsSync(iosWalletDerivationFixture)) {
  assert(
    createHash("sha256")
      .update(readFileSync(iosWalletDerivationFixture))
      .digest("hex") === walletDerivationFixtureSha256,
    "ANDROID_IOS_WALLET_DERIVATION_FIXTURES_DIVERGED",
  );
}

const polkamarktContractRelativePath =
  "feature_polkaswap_impl/src/test/resources/polkamarkt_web_contract.json";
const polkamarktContractRecord = readStrictJsonFile(
  join(root, polkamarktContractRelativePath),
  16 * 1024 * 1024,
);
assert(polkamarktContractRecord !== null, "POLKAMARKT_CONTRACT_UNREADABLE");
const webContract = polkamarktContractRecord?.value ?? Object.create(null);
assert(
  webContract.webReference?.inspectedRevision ===
    "893783ba6a19c33043eb5dabe42d949c14d0f257" &&
    webContract.webReference?.branch === "ui-updates" &&
    webContract.webReference?.commitTreeObject ===
      "e391982c0921dea5278e919a7558b7a6a2afc0d4" &&
    webContract.webReference?.polkamarktTreeObject ===
      "57f0fe7623f2b93b34faecfc66d6c5da96d54e1b" &&
    webContract.webReference?.commitSignatureStatus === "verified",
  "POLKAMARKT_WEB_REF_MISMATCH",
);
assert(
  webContract.mobileClaimConfirmation?.requiresExplicitConfirmation === true &&
    JSON.stringify(
      webContract.mobileClaimConfirmation?.requiredReviewedFields,
    ) ===
      JSON.stringify([
        "accountId",
        "source",
        "marketIds",
        "finalizedBlockHash",
        "claims",
      ]) &&
    JSON.stringify(
      webContract.mobileClaimConfirmation?.freshChecksBeforeSigning,
    ) ===
      JSON.stringify([
        "account",
        "featureFlags",
        "runtimeMetadata",
        "claimValues",
        "xorFee",
        "xorBalance",
      ]),
  "POLKAMARKT_MOBILE_CLAIM_CONFIRMATION_FIXTURE_INVALID",
);
assert(
  webContract.runtime?.marketIdScaleType === "u32" &&
    webContract.runtime?.marketIdMaximum === 4_294_967_295 &&
    webContract.runtime?.closeBlockScaleType === "u32" &&
    webContract.runtime?.closeBlockMaximum === 4_294_967_295,
  "POLKAMARKT_WEB_RUNTIME_MARKET_ID_MISMATCH",
);
const webSourceFiles = webContract.webReference?.sourceFiles;
const webSourceBlobs = webContract.webReference?.sourceBlobObjects;
assert(
  Array.isArray(webSourceFiles) &&
    webSourceFiles.length === 22 &&
    webSourceBlobs &&
    Object.keys(webSourceBlobs).length === webSourceFiles.length &&
    webSourceFiles.every(
      (path) =>
        Object.hasOwn(webSourceBlobs, path) &&
        /^[0-9a-f]{40}$/.test(webSourceBlobs[path]?.gitSha1 ?? "") &&
        Number.isSafeInteger(webSourceBlobs[path]?.bytes) &&
        webSourceBlobs[path].bytes > 0,
    ),
  "POLKAMARKT_WEB_SOURCE_TREE_RECEIPT_INVALID",
);
const polkamarktCanonicalVectors = webContract.canonicalVectors;
const polkamarktMinimumOutputVectors =
  polkamarktCanonicalVectors?.minimumOutput;
const polkamarktRuntimeCallVectors = polkamarktCanonicalVectors?.runtimeCalls;
const polkamarktFullExtrinsicQualification =
  polkamarktCanonicalVectors?.fullExtrinsicQualification;
const polkamarktGenerationContract =
  polkamarktFullExtrinsicQualification?.generationContract;
const polkamarktPlatformSourceManifests =
  polkamarktGenerationContract?.platformSourceManifests;
const polkamarktPlatformRepositories = {
  reference: "polkaswap-exchange-web",
  android: "sora-wallet/sora-android",
  ios: "sora-wallet/sora-ios",
};
const polkamarktBlockedPlatformSourceManifests = {
  reference: {
    repository: "polkaswap-exchange-web",
    revision: "893783ba6a19c33043eb5dabe42d949c14d0f257",
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
const polkamarktBlockedQualificationAccountIdHex = null;
const polkamarktAndroidSourceRevisionPin =
  process.env.PRODUCTION_CANDIDATE_SOURCE_REVISION ?? "";
const polkamarktIosSourceRevisionPin =
  process.env.POLKAMARKT_IOS_SOURCE_REVISION ?? "";
const polkamarktIosSourceRoot =
  process.env.POLKAMARKT_IOS_SOURCE_ROOT ?? "";
const polkamarktAndroidImplementationPathspecs = [
  ":(glob)**/src/main/**",
  ":(glob)**/src/production/**",
  ":(glob)**/src/release/**",
  ":(glob)**/*.gradle",
  ":(glob)**/*.gradle.kts",
  "buildSrc/**",
  "gradle/**",
  "gradle.properties",
  "settings.gradle",
  "settings.gradle.kts",
];
const polkamarktIosImplementationPathspecs = [
  ":(glob)SoraPassport/**/*.swift",
  ":(glob)SoraPassport/**/*.[chm]",
  ":(glob)SoraPassport/**/*.[ch]pp",
  ":(glob)SoraPassport/**/*.mm",
  ":(glob)VendorPackages/**/Sources/**",
  "Vendor/IrohaSwift/**",
  "Vendor/NoritoBridge.xcframework/**",
  "SoraPassport.xcodeproj/project.pbxproj",
  "SoraPassport.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved",
  "SoraPassport/Configs/**",
];
const polkamarktImplementationMatchesReviewedRevision = ({
  repositoryRoot,
  reviewedRevision,
  currentRevision,
  pathspecs,
}) => {
  if (
    !isAbsolute(repositoryRoot) ||
    resolve(repositoryRoot) !== repositoryRoot ||
    !/^[0-9a-f]{40}$/.test(reviewedRevision ?? "") ||
    /^0+$/.test(reviewedRevision ?? "") ||
    !/^[0-9a-f]{40}$/.test(currentRevision ?? "") ||
    /^0+$/.test(currentRevision ?? "")
  ) {
    return false;
  }
  try {
    const status = lstatSync(repositoryRoot);
    if (
      !status.isDirectory() ||
      status.isSymbolicLink() ||
      realpathSync(repositoryRoot) !== repositoryRoot
    ) {
      return false;
    }
  } catch {
    return false;
  }
  const runGit = (arguments_) =>
    spawnSync("git", ["-C", repositoryRoot, ...arguments_], {
      cwd: repositoryRoot,
      encoding: "utf8",
      env: process.env,
      maxBuffer: 64 * 1024,
      timeout: 30_000,
      windowsHide: true,
    });
  const head = runGit(["rev-parse", "--verify", "HEAD"]);
  const reviewed = runGit([
    "rev-parse",
    "--verify",
    `${reviewedRevision}^{commit}`,
  ]);
  if (
    head.error !== undefined ||
    head.signal !== null ||
    head.status !== 0 ||
    head.stderr !== "" ||
    head.stdout !== `${currentRevision}\n` ||
    reviewed.error !== undefined ||
    reviewed.signal !== null ||
    reviewed.status !== 0 ||
    reviewed.stderr !== "" ||
    reviewed.stdout !== `${reviewedRevision}\n`
  ) {
    return false;
  }
  const diff = runGit([
    "diff",
    "--quiet",
    reviewedRevision,
    currentRevision,
    "--",
    ...pathspecs,
  ]);
  return (
    diff.error === undefined &&
    diff.signal === null &&
    diff.status === 0 &&
    diff.stdout === "" &&
    diff.stderr === ""
  );
};
const polkamarktSourceManifestKeys = [
  "repository",
  "revision",
  "sourceTreeSha256",
  "generatorSha256",
  "proofVerifierRevision",
  "proofVerifierBinarySha256",
  "proofVerifierPublicKeySha256",
];
assert(
  polkamarktCanonicalVectors?.derivationSources?.minimumOutput ===
    "lib/amounts.ts" &&
    polkamarktCanonicalVectors?.derivationSources?.runtimeCalls ===
      "services/runtimeMarkets.ts" &&
    polkamarktCanonicalVectors?.derivationSources?.scaleArguments ===
      "../sora2-network/pallets/polkamarkt/src/lib.rs" &&
    polkamarktCanonicalVectors?.derivationSources?.runtimeRevision ===
      "411dcdb70c5c00b21482a44d02334840d5f338c6" &&
    Array.isArray(polkamarktMinimumOutputVectors) &&
    polkamarktMinimumOutputVectors.length >= 4 &&
    polkamarktMinimumOutputVectors.every(
      (vector) =>
        /^(?:0|[1-9][0-9]*)$/.test(vector?.quotedOutput ?? "") &&
        Number.isInteger(vector?.slippageBps) &&
        vector.slippageBps >= 1 &&
        vector.slippageBps <= 1_000 &&
        /^(?:0|[1-9][0-9]*)$/.test(vector?.expectedMinimum ?? "") &&
        (BigInt(vector.quotedOutput) * BigInt(10_000 - vector.slippageBps)) /
          10_000n ===
          BigInt(vector.expectedMinimum),
    ) &&
    Array.isArray(polkamarktRuntimeCallVectors) &&
    polkamarktRuntimeCallVectors.map((vector) => vector?.call).join("\0") ===
      [
        "buy",
        "sell",
        "claim_market",
        "claim_markets",
        "claim_creator_fees",
      ].join("\0") &&
    polkamarktRuntimeCallVectors.every(
      (vector) => /^(?:[0-9a-f]{2})+$/.test(vector?.scaleArgumentsHex ?? ""),
    ) &&
    polkamarktFullExtrinsicQualification?.receiptSchema ===
      "sora-mobile-polkamarkt-full-extrinsic-receipt-v1" &&
    polkamarktFullExtrinsicQualification?.requiredMetadataSha256 ===
      "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf" &&
    polkamarktFullExtrinsicQualification?.requiredGenesisHash ===
      "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5" &&
    polkamarktFullExtrinsicQualification?.requiredSpecVersion === 130 &&
    polkamarktFullExtrinsicQualification?.requiredTransactionVersion ===
      130 &&
    polkamarktFullExtrinsicQualification
      ?.metadataIndicesMustBeResolvedDynamically === true &&
    JSON.stringify(
      polkamarktFullExtrinsicQualification?.requiredVectorOrder,
    ) ===
      JSON.stringify([
        "buy",
        "sell",
        "claim_market",
        "claim_markets",
        "claim_creator_fees",
      ]) &&
    polkamarktFullExtrinsicQualification?.generationContract
      ?.candidateReceiptSchema ===
      "sora-mobile-polkamarkt-platform-extrinsic-receipt-v1" &&
    polkamarktFullExtrinsicQualification?.generationContract
      ?.independentReviewSchema ===
      "sora-mobile-polkamarkt-extrinsic-review-v1" &&
    polkamarktFullExtrinsicQualification?.generationContract?.merger ===
      "scripts/qualify-polkamarkt-extrinsic-receipts.mjs" &&
    polkamarktFullExtrinsicQualification?.generationContract
      ?.runtimeMetadata?.sha256 ===
      "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf" &&
    polkamarktFullExtrinsicQualification?.generationContract
      ?.runtimeMetadata?.palletAndCallIndices ===
      "resolve-from-this-metadata-never-hardcode" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.cryptoType === "sr25519" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.accountIdSource === "reviewed-receipt" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.nonceSource === "reviewed-receipt" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.mortalEraSource === "reviewed-receipt" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.finalizedBlockHashSource === "reviewed-receipt" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.tipSource === "reviewed-receipt" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.signaturePayloadHashingThresholdBytes === 256 &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.signaturePayloadHashingRule ===
      "blake2b-256-only-when-raw-payload-length-is-greater-than-256" &&
    polkamarktFullExtrinsicQualification?.generationContract?.signingContext
      ?.privateSigningMaterialPermittedInFixture === false &&
    (polkamarktGenerationContract?.signingContext
      ?.qualificationAccountIdHex === null ||
      /^[0-9a-f]{64}$/.test(
        polkamarktGenerationContract?.signingContext
          ?.qualificationAccountIdHex ?? "",
      )) &&
    polkamarktGenerationContract?.cryptographicProofSchema ===
      "sora-mobile-polkamarkt-native-cryptographic-proof-v1" &&
    JSON.stringify(
      polkamarktGenerationContract?.requiredCryptographicProofFields,
    ) ===
      JSON.stringify([
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
      ]) &&
    JSON.stringify(
      polkamarktGenerationContract?.requiredCryptographicProofClaims,
    ) ===
      JSON.stringify([
        "candidate-identity-bound",
        "sr25519-signatures-verified-over-reconstructed-signing-prehashes",
        "signed-extrinsics-decoded-through-pinned-live-metadata",
        "decoded-projections-match-canonical-runtime-call-vectors",
        "signed-extrinsic-hashes-recomputed",
        "non-production-qualification-account-used",
        "private-signing-material-absent-from-receipts",
      ]) &&
    hasExactKeys(polkamarktPlatformSourceManifests, [
      "reference",
      "android",
      "ios",
    ]) &&
    ["reference", "android", "ios"].every(
      (platform) =>
        hasExactKeys(
          polkamarktPlatformSourceManifests?.[platform],
          polkamarktSourceManifestKeys,
        ) &&
        polkamarktPlatformSourceManifests[platform].repository ===
          polkamarktPlatformRepositories[platform] &&
        (polkamarktPlatformSourceManifests[platform].revision === null ||
          /^[0-9a-f]{40}$/.test(
            polkamarktPlatformSourceManifests[platform].revision,
          )) &&
        [
          "sourceTreeSha256",
          "generatorSha256",
          "proofVerifierBinarySha256",
          "proofVerifierPublicKeySha256",
        ].every(
          (field) =>
            polkamarktPlatformSourceManifests[platform][field] === null ||
            /^[0-9a-f]{64}$/.test(
              polkamarktPlatformSourceManifests[platform][field],
            ),
        ) &&
        (polkamarktPlatformSourceManifests[platform]
          .proofVerifierRevision === null ||
          /^[0-9a-f]{40}$/.test(
            polkamarktPlatformSourceManifests[platform]
              .proofVerifierRevision,
          )),
    ) &&
    JSON.stringify(
      polkamarktFullExtrinsicQualification?.generationContract
        ?.requiredSharedVectorFields,
    ) ===
      JSON.stringify([
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
      ]) &&
    JSON.stringify(
      polkamarktFullExtrinsicQualification?.generationContract
        ?.requiredPlatformVectorFields,
    ) ===
      JSON.stringify([
        "id",
        "signerPublicKeyHex",
        "signatureHex",
        "signedExtrinsicHex",
        "extrinsicHashHex",
        "signingPrehashHex",
        "decodedProjection",
        "decodedProjectionSha256",
      ]) &&
    JSON.stringify(
      polkamarktFullExtrinsicQualification?.generationContract?.parityRules,
    ) ===
      JSON.stringify([
        "reference-android-ios-full-call-bytes-equal",
        "reference-android-ios-signing-prehash-equal",
        "reference-android-ios-decoded-projection-sha256-equal",
        "each-platform-reviewed-cryptographic-proof-signature-verifies",
        "each-proof-binds-source-metadata-context-vectors-and-signed-extrinsics",
        "each-proof-attests-sr25519-verification-and-live-metadata-round-trip",
        "sr25519-signature-bytes-may-differ-but-must-verify",
      ]) &&
    (polkamarktFullExtrinsicQualification
      ?.reviewedWebAndRuntimeReceiptQualified === true
      ? polkamarktFullExtrinsicQualification?.reviewedReceipt?.format ===
          polkamarktFullExtrinsicQualification.receiptSchema &&
        polkamarktFullExtrinsicQualification.reviewedReceipt
          ?.parityQualified === true &&
        /^[0-9a-f]{40}$/.test(polkamarktAndroidSourceRevisionPin) &&
        !/^0+$/.test(polkamarktAndroidSourceRevisionPin) &&
        /^[0-9a-f]{40}$/.test(polkamarktIosSourceRevisionPin) &&
        !/^0+$/.test(polkamarktIosSourceRevisionPin) &&
        polkamarktPlatformSourceManifests.reference.revision ===
          "893783ba6a19c33043eb5dabe42d949c14d0f257" &&
        polkamarktImplementationMatchesReviewedRevision({
          repositoryRoot: root,
          reviewedRevision:
            polkamarktPlatformSourceManifests.android.revision,
          currentRevision: polkamarktAndroidSourceRevisionPin,
          pathspecs: polkamarktAndroidImplementationPathspecs,
        }) &&
        polkamarktImplementationMatchesReviewedRevision({
          repositoryRoot: polkamarktIosSourceRoot,
          reviewedRevision: polkamarktPlatformSourceManifests.ios.revision,
          currentRevision: polkamarktIosSourceRevisionPin,
          pathspecs: polkamarktIosImplementationPathspecs,
        }) &&
        /^[0-9a-f]{64}$/.test(
          polkamarktGenerationContract?.signingContext
            ?.qualificationAccountIdHex ?? "",
        ) &&
        !/^0+$/.test(
          polkamarktGenerationContract?.signingContext
            ?.qualificationAccountIdHex ?? "",
        ) &&
        ["reference", "android", "ios"].every((platform) => {
          const manifest = polkamarktPlatformSourceManifests[platform];
          return (
            /^[0-9a-f]{40}$/.test(manifest.revision ?? "") &&
            !/^0+$/.test(manifest.revision ?? "") &&
            /^[0-9a-f]{40}$/.test(
              manifest.proofVerifierRevision ?? "",
            ) &&
            !/^0+$/.test(manifest.proofVerifierRevision ?? "") &&
            [
              "sourceTreeSha256",
              "generatorSha256",
              "proofVerifierBinarySha256",
              "proofVerifierPublicKeySha256",
            ].every(
              (field) =>
                /^[0-9a-f]{64}$/.test(manifest[field] ?? "") &&
                !/^0+$/.test(manifest[field] ?? ""),
            )
          );
        }) &&
        /^[0-9a-f]{64}$/.test(
          polkamarktFullExtrinsicQualification.canonicalContractSha256 ?? "",
        ) &&
        polkamarktFullExtrinsicQualification.blocker === null
      : polkamarktFullExtrinsicQualification?.reviewedReceipt === null &&
        polkamarktFullExtrinsicQualification
          ?.reviewedWebAndRuntimeReceiptQualified === false &&
        JSON.stringify(polkamarktPlatformSourceManifests) ===
          JSON.stringify(polkamarktBlockedPlatformSourceManifests) &&
        polkamarktGenerationContract?.signingContext
          ?.qualificationAccountIdHex ===
          polkamarktBlockedQualificationAccountIdHex &&
        polkamarktFullExtrinsicQualification?.canonicalContractSha256 ===
          null &&
        polkamarktFullExtrinsicQualification?.blocker ===
          "Generate independent reference, Android, and iOS receipts from the pinned web revision and exact runtime metadata; independently sign the composite receipt; then prove full call bytes, signing prehashes, signatures, signed extrinsics, and decoded projections before enabling mutations."),
  "POLKAMARKT_CANONICAL_VECTOR_FIXTURE_INVALID",
);
const polkamarktReviewKeySha256 =
  process.env.POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256 ?? "";
const polkamarktQualifiedFixtureSha256 =
  process.env.POLKAMARKT_EXTRINSIC_QUALIFIED_FIXTURE_SHA256 ?? "";
const polkamarktReviewedReceipt =
  polkamarktFullExtrinsicQualification?.reviewedReceipt;
assert(
  productionReleaseWorkflow.includes(
    "POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256: ${{ vars.POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256 }}",
  ) &&
    productionReleaseWorkflow.includes(
      "POLKAMARKT_EXTRINSIC_QUALIFIED_FIXTURE_SHA256: ${{ vars.POLKAMARKT_EXTRINSIC_QUALIFIED_FIXTURE_SHA256 }}",
    ) &&
    productionReleaseWorkflow.includes(
      "POLKAMARKT_IOS_REPOSITORY: ${{ vars.POLKAMARKT_IOS_REPOSITORY }}",
    ) &&
    productionReleaseWorkflow.includes(
      "POLKAMARKT_IOS_SOURCE_REVISION: ${{ vars.POLKAMARKT_IOS_SOURCE_REVISION }}",
    ) &&
    productionReleaseWorkflow.includes(
      "POLKAMARKT_IOS_SOURCE_ROOT: ${{ github.workspace }}/.cross-platform/sora-ios",
    ) &&
    productionReleaseWorkflow.includes(
      "POLKAMARKT_IOS_FIXTURE_PATH: ${{ github.workspace }}/.cross-platform/sora-ios/Fixtures/Modernization/polkamarkt-runtime-v130.json",
    ) &&
    productionReleaseWorkflow.includes(
      "repository: ${{ vars.POLKAMARKT_IOS_REPOSITORY }}",
    ) &&
    productionReleaseWorkflow.includes(
      "ref: ${{ vars.POLKAMARKT_IOS_SOURCE_REVISION }}",
    ) &&
    productionReleaseWorkflow.includes("fetch-depth: 0") &&
    productionReleaseWorkflow.includes(
      "token: ${{ secrets.POLKAMARKT_IOS_READ_TOKEN }}",
    ) &&
    productionReleaseWorkflow.includes(
      'observed_revision="$(git -C "$GITHUB_WORKSPACE/.cross-platform/sora-ios" rev-parse --verify HEAD)"',
    ),
  "POLKAMARKT_PROTECTED_QUALIFICATION_PINS_NOT_WIRED",
);
assert(
  polkamarktFullExtrinsicQualification
    ?.reviewedWebAndRuntimeReceiptQualified !== true ||
    (/^[0-9a-f]{64}$/.test(polkamarktReviewKeySha256) &&
      !/^0+$/.test(polkamarktReviewKeySha256) &&
      /^[0-9a-f]{64}$/.test(polkamarktQualifiedFixtureSha256) &&
      !/^0+$/.test(polkamarktQualifiedFixtureSha256) &&
      polkamarktQualifiedFixtureSha256 ===
        polkamarktContractRecord?.sha256 &&
      polkamarktReviewedReceipt?.review?.publicKeySha256 ===
        polkamarktReviewKeySha256 &&
      /^[0-9a-f]{64}$/.test(
        polkamarktReviewedReceipt?.review?.receiptSha256 ?? "",
      ) &&
      /^[0-9a-f]{64}$/.test(
        polkamarktReviewedReceipt?.review?.signatureReceiptSha256 ?? "",
      ) &&
      ["reference", "android", "ios"].every(
        (platform) =>
          /^[0-9a-f]{64}$/.test(
            polkamarktReviewedReceipt?.platforms?.[platform]
              ?.candidateReceiptSha256 ?? "",
          ) &&
          /^(?:[0-9a-f]{2})+$/.test(
            polkamarktReviewedReceipt?.platforms?.[platform]
              ?.candidateReceiptHex ?? "",
          ) &&
          polkamarktReviewedReceipt?.platforms?.[platform]?.vectors?.length ===
            5,
      )),
  "POLKAMARKT_REVIEWED_EXTRINSIC_RECEIPT_INVALID",
);
let polkamarktInstalledQualificationValidation = null;
if (
  polkamarktFullExtrinsicQualification
    ?.reviewedWebAndRuntimeReceiptQualified === true
) {
  polkamarktInstalledQualificationValidation = spawnSync(
    process.execPath,
    [
      join(root, "scripts/qualify-polkamarkt-extrinsic-receipts.mjs"),
      "--validate-qualified",
      join(root, polkamarktContractRelativePath),
    ],
    {
      cwd: root,
      encoding: "utf8",
      env: process.env,
      maxBuffer: 2 * 1024 * 1024,
      timeout: 30_000,
      windowsHide: true,
    },
  );
}
assert(
  polkamarktInstalledQualificationValidation === null ||
    (polkamarktInstalledQualificationValidation.error === undefined &&
      polkamarktInstalledQualificationValidation.signal === null &&
      polkamarktInstalledQualificationValidation.status === 0 &&
      polkamarktInstalledQualificationValidation.stdout ===
        "POLKAMARKT_QUALIFIED_FIXTURE_VALID\n" &&
      polkamarktInstalledQualificationValidation.stderr === ""),
  "POLKAMARKT_INSTALLED_QUALIFIED_FIXTURE_REVALIDATION_FAILED",
);
block(
  polkamarktFullExtrinsicQualification
    ?.reviewedWebAndRuntimeReceiptQualified !== true,
  "POLKAMARKT_WEB_RUNTIME_EXTRINSIC_PARITY_UNQUALIFIED",
);
const polkamarktExtrinsicQualifier = read(
  "scripts/qualify-polkamarkt-extrinsic-receipts.mjs",
);
const polkamarktExtrinsicQualifierHarness = read(
  "scripts/test-polkamarkt-extrinsic-qualification.mjs",
);
const polkamarktExtrinsicQualificationGuide = read(
  "docs/modernization/polkamarkt-full-extrinsic-qualification.md",
);
assert(
  polkamarktExtrinsicQualifier.includes(
    'const RECEIPT_SCHEMA =\n  "sora-mobile-polkamarkt-full-extrinsic-receipt-v1"',
  ) &&
    polkamarktExtrinsicQualifier.includes(
      "POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "POLKAMARKT_BLAKE2B256_SELF_TEST_FAILED",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "metadataResolvedDynamically !== true",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "requireCrossPlatformParity(candidates)",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "vector.signingPrehashHex !== expectedPrehash",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "stableJson(candidate.vectors.map(sharedVector))",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      '"sora-mobile-polkamarkt-native-cryptographic-proof-v1"',
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "requireCryptographicProof(",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "POLKAMARKT_PLATFORM_SOURCE_IDENTITY_UNQUALIFIED",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "EXPECTED_PLATFORM_SOURCE_MANIFESTS",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "POLKAMARKT_CHECKED_IN_SOURCE_MANIFEST_MISMATCH",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "requireExactImmutableContract(",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      'args.mode === "validate-qualified"',
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "requireInstalledQualifiedFixture(qualifiedFixtureRecord)",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "actual.length !== expected.length",
    ) &&
    polkamarktExtrinsicQualifier.includes("prohibitSecretFields") &&
    polkamarktExtrinsicQualifier.includes(
      "prohibitSecretFields(fixtureRecord.value)",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "prohibitSecretFields(reviewedReceipt)",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "fixture.webReference.sourceBlobObjects",
    ) &&
    polkamarktExtrinsicQualifier.includes("const runtimeArgumentKeys = {") &&
    polkamarktExtrinsicQualifier.includes(
      "fixture.mobileClaimConfirmation.copy,",
    ) &&
    polkamarktExtrinsicQualifier.includes("verifyEd25519") &&
    polkamarktExtrinsicQualifier.includes("O_EXCL") &&
    polkamarktExtrinsicQualifier.includes("O_NOFOLLOW") &&
    polkamarktExtrinsicQualifier.includes(
      "POLKAMARKT_QUALIFICATION_OUTPUT_EXISTS",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "POLKAMARKT_QUALIFICATION_OUTPUT_CLOSE_FAILED",
    ) &&
    polkamarktExtrinsicQualifier.includes(
      "pathStatus.ino === createdIdentity.ino",
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      "Hermetic regression and mutation harness for the Polkamarkt receipt merger.",
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      "private blocked input so this regression remains valid after a reviewed",
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      "readStrictJsonFile(\n  FIXTURE_PATH",
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "duplicate-json-key"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "wrong-prehash"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "wrong-extrinsic-hash"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "fixture-private-field"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "fixture-deep-private-field"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "fixture-unknown-runtime-argument-field"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "fixture-object-smuggled-through-primitive-array"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "raw-payload-missing-genesis-hash"',
    ) &&
    /expectFailure\(\s*"missing-reviewed-native-cryptographic-proof"/.test(
      polkamarktExtrinsicQualifierHarness,
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "nul-delimited-exact-key-collision"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "candidate-source-manifest-mismatch"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      '"checked-in-source-manifests-unqualified"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'passed.push("qualified-fixture-transition")',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      "exactPolkamarktErrorCodes(diagnostic)",
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      "must not produce a qualified fixture until reviewed platform proofs exist",
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "qualified-fixture-rejected-as-input"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n    "replaced-review-signature"',
    ) &&
    polkamarktExtrinsicQualifierHarness.includes(
      'expectFailure(\n      "symlink-output-parent"',
    ) &&
    productionReleaseWorkflow.includes(
      "run: node scripts/test-polkamarkt-extrinsic-qualification.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "run: node scripts/verify-production-modernization.mjs",
    ) &&
    productionReleaseWorkflow.indexOf(
      "run: node scripts/verify-production-modernization.mjs",
    ) <
      productionReleaseWorkflow.indexOf(
        "run: node scripts/test-polkamarkt-extrinsic-qualification.mjs",
      ) &&
    /Sr25519 signatures may legitimately\s+differ/.test(
      polkamarktExtrinsicQualificationGuide,
    ) &&
    /does not\s+supply or fabricate/.test(
      polkamarktExtrinsicQualificationGuide,
    ) &&
    polkamarktExtrinsicQualificationGuide.includes(
      "signed native-proof binding",
    ) &&
    polkamarktExtrinsicQualificationGuide.includes(
      "installed-qualified replay validation",
    ),
  "POLKAMARKT_FULL_EXTRINSIC_QUALIFIER_INVALID",
);
block(
  webContract.webReference?.polkamarktContractPresentAtInspectedRevision !==
    true,
  "POLKAMARKT_CANONICAL_WEB_IMPLEMENTATION_NOT_PRESENT_AT_PIN",
);
const iosPolkamarktContract = resolve(
  root,
  "../sora-ios/Fixtures/Modernization/polkamarkt-runtime-v130.json",
);
const configuredIosPolkamarktContract =
  process.env.POLKAMARKT_IOS_FIXTURE_PATH ?? "";
const configuredIosPolkamarktSourceRoot =
  process.env.POLKAMARKT_IOS_SOURCE_ROOT ?? "";
const configuredIosPolkamarktRepository =
  process.env.POLKAMARKT_IOS_REPOSITORY ?? "";
const configuredIosPolkamarktRevision =
  process.env.POLKAMARKT_IOS_SOURCE_REVISION ?? "";
assert(
  configuredIosPolkamarktContract.length === 0 ||
    (/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(
      configuredIosPolkamarktRepository,
    ) &&
      !configuredIosPolkamarktRepository
        .split("/")
        .some((component) => component === "." || component === "..") &&
      /^[0-9a-f]{40}$/.test(configuredIosPolkamarktRevision) &&
      !/^0+$/.test(configuredIosPolkamarktRevision) &&
      isAbsolute(configuredIosPolkamarktSourceRoot) &&
      resolve(configuredIosPolkamarktSourceRoot) ===
        configuredIosPolkamarktSourceRoot &&
      existsSync(configuredIosPolkamarktSourceRoot) &&
      lstatSync(configuredIosPolkamarktSourceRoot).isDirectory() &&
      !lstatSync(configuredIosPolkamarktSourceRoot).isSymbolicLink() &&
      realpathSync(configuredIosPolkamarktSourceRoot) ===
        configuredIosPolkamarktSourceRoot &&
      configuredIosPolkamarktContract ===
        join(
          configuredIosPolkamarktSourceRoot,
          "Fixtures/Modernization/polkamarkt-runtime-v130.json",
        )),
  "POLKAMARKT_IOS_PARITY_SOURCE_IDENTITY_INVALID",
);
const requiredIosPolkamarktContract =
  configuredIosPolkamarktContract.length > 0
    ? configuredIosPolkamarktContract
    : iosPolkamarktContract;
const iosPolkamarktContractRecord =
  isAbsolute(requiredIosPolkamarktContract) &&
  resolve(requiredIosPolkamarktContract) === requiredIosPolkamarktContract
    ? readStrictJsonFile(requiredIosPolkamarktContract, 16 * 1024 * 1024)
    : null;
assert(
  iosPolkamarktContractRecord !== null &&
    iosPolkamarktContractRecord.sha256 === polkamarktContractRecord?.sha256,
  "ANDROID_IOS_POLKAMARKT_FIXTURES_DIVERGED",
);

const probe = json("docs/modernization/release-probe-evidence-2026-08-02.json");
const piProbeObservedAtMs = Date.parse(
  probe.piIndexer?.lastReprobedAtUtc ?? "",
);
const piProbeNowMs = Date.now();
const piLiveReceiptPath =
  process.env.PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH ?? "";
const piLiveRecord =
  piLiveReceiptPath.length > 0 && isAbsolute(piLiveReceiptPath)
    ? readStrictJsonFile(piLiveReceiptPath, 64 * 1024)
    : null;
const piLiveReceipt = piLiveRecord?.value ?? null;
const piLiveNow = Math.floor(piProbeNowMs / 1000);
assert(
  probe.schemaVersion === 1 &&
    probe.privacy?.accountIdentifiersIncluded === false &&
    probe.privacy?.secretsIncluded === false &&
    probe.privacy?.signedPayloadsIncluded === false &&
    probe.privacy?.mutationsAttempted === false,
  "PRODUCTION_RELEASE_EVIDENCE_PRIVACY_OR_SCHEMA_INVALID",
);
block(
  !Number.isFinite(piProbeObservedAtMs) ||
    piProbeObservedAtMs > piProbeNowMs + 5 * 60 * 1000 ||
    piProbeObservedAtMs < piProbeNowMs - 24 * 60 * 60 * 1000,
  "PI_PRODUCTION_PROBE_EVIDENCE_STALE_OR_INVALID",
);
block(
  validateProductionPiRawLiveReceipt(piLiveReceipt, piLiveNow) === null,
  "LIVE_PI_PRODUCTION_PROBE_RECEIPT_MISSING_OR_INVALID",
);
assert(
  probe.piIndexer?.endpoint === "https://pi.soramitsu.io/graphql" &&
    probe.piIndexer.observedContract?.serviceId === "pi.soramitsu.io" &&
    probe.piIndexer.observedContract?.ecosystem === "sora2" &&
    probe.piIndexer.observedContract?.chainId === "sora:mainnet" &&
    probe.piIndexer.observedContract?.network === "mainnet" &&
    probe.piIndexer.observedContract?.readOnly === true &&
    probe.piIndexer.observedContract?.workerReady === true &&
    Number.isSafeInteger(
      probe.piIndexer.observedContract?.workerLatestFinalizedBlock,
    ) &&
    Number.isSafeInteger(
      probe.piIndexer.observedContract?.workerLatestIndexedBlock,
    ) &&
    probe.piIndexer.observedContract.workerLatestIndexedBlock <=
      probe.piIndexer.observedContract.workerLatestFinalizedBlock,
  "PI_INDEXER_RELEASE_EVIDENCE_IDENTITY_INVALID",
);
assert(
  piProductionProbe.includes(
    'const ENDPOINT = "https://pi.soramitsu.io/graphql"',
  ) &&
    piProductionProbe.includes('redirect: "error"') &&
    piProductionProbe.includes("PI_PROBE_GRAPHQL_ERROR") &&
    piProductionProbe.includes("parseStrictJsonBytes") &&
    piProductionProbe.includes("SORA2_RPC_ENDPOINT") &&
    piProductionProbe.includes("chain_getBlockHash") &&
    piProductionProbe.includes("finalizedCheckpointBlockHash") &&
    piProductionProbe.includes("capabilityFinalizedCheckpointBlockHash") &&
    piProductionProbe.includes("workerLatestFinalizedBlock") &&
    piProductionProbe.includes("MAXIMUM_CHECKPOINT_AGE_SECONDS") &&
    piProductionProbe.includes("MAXIMUM_CHECKPOINT_LAG_BLOCKS = 32") &&
    piProductionProbe.includes("MobileConfigWithHealth") &&
    piProductionProbe.includes("PI_MOBILE_CONFIG_HEALTH_REGRESSED") &&
    piProductionProbe.includes("mobileConfigHealthBound: true") &&
    piProductionProbe.includes("nexusSendsAvailable") &&
    piProductionProbe.includes("polkamarktMutationsAvailable") &&
    piProductionProbe.includes("tairaDefaultVisible") &&
    piProductionProbe.includes("blockHeight") &&
    piProductionProbe.includes("SYNTHETIC_HISTORY_ACCOUNT") &&
    piProductionProbe.includes("mutationsAttempted: false") &&
    productionReleaseWorkflow.includes(
      'node scripts/probe-pi-production-contract.mjs > "$PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH"',
    ) &&
    productionReleaseWorkflow.includes(
      'append_runner_temp_path "PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH" "sora-pi-production-raw-live-v1.json"',
    ),
  "LIVE_PI_PRODUCTION_CONTRACT_GATE_MISSING",
);
assert(
  probe.androidSdk.upstreamCommit === irohaPin.upstream.commit &&
    probe.androidSdk.currentArchiveSha256 === irohaPin.artifact.sha256 &&
    probe.androidSdk.currentArchiveBytes === irohaPin.artifact.bytes &&
    probe.androidSdk.previouslyReviewedArchiveSha256 ===
      irohaPin.artifact.previouslyReviewedArchiveSha256 &&
    probe.androidSdk.githubReleaseImmutable ===
      irohaPin.artifact.githubReleaseImmutable &&
    probe.androidSdk.freshArtifactReviewRequired ===
      irohaPin.artifact.freshArtifactReviewRequired &&
    probe.androidSdk.currentArchiveRuntimeIdentityVerified ===
      irohaPin.reviewedCore.currentArchiveCoreJarIdentityVerified &&
    probe.androidSdk.knownHasherDefect ===
      irohaPin.reviewedCore.knownHasherDefect &&
    probe.androidSdk.applicationRuntimeDependency ===
      irohaPin.artifact.applicationRuntimeDependency &&
    probe.androidSdk.qualification === irohaPin.status,
  "IROHA_ANDROID_RELEASE_EVIDENCE_DIVERGED",
);
assert(
  probe.gradleDependencyProvenance.dependencyInventoryMaterializedAt ===
    gradleDependencyProvenance.assessedAt &&
    probe.gradleDependencyProvenance.distributionVersion ===
    gradleDependencyProvenance.wrapper.distributionVersion &&
    probe.gradleDependencyProvenance.distributionSha256Pinned === true &&
    probe.gradleDependencyProvenance.expectedWrapperJarSha256 ===
      gradleDependencyProvenance.wrapper.expectedWrapperJarSha256 &&
    probe.gradleDependencyProvenance.observedWrapperJarSha256 ===
      gradleWrapperJarSha256 &&
    probe.gradleDependencyProvenance.wrapperJarMatchesDistribution ===
      gradleDependencyProvenance.wrapper.wrapperJarMatchesDistribution &&
    probe.gradleDependencyProvenance.sourceQualifiedVendorRepositoryPresent ===
      true &&
    probe.gradleDependencyProvenance.sourceQualifiedVendorRepositoryStatus ===
      gradleDependencyProvenance.repositoryPolicy.sourceQualifiedVendorRepository
        .status &&
    probe.gradleDependencyProvenance.exactRepositoryModuleFilterCount ===
      gradleDependencyProvenance.repositoryPolicy.sourceQualifiedVendorRepository
        .exactModules.length &&
    probe.gradleDependencyProvenance.materializedVendorModuleCount ===
      gradleDependencyProvenance.repositoryPolicy.sourceQualifiedVendorRepository
        .materializedCoordinates.length &&
    probe.gradleDependencyProvenance.sourceProvenanceSha256 ===
      gradleDependencyProvenance.repositoryPolicy.sourceQualifiedVendorRepository
        .sourceProvenanceSha256 &&
    probe.gradleDependencyProvenance.vendorContentsManifestSha256 ===
      gradleDependencyProvenance.repositoryPolicy.sourceQualifiedVendorRepository
        .contentsManifestSha256 &&
    probe.gradleDependencyProvenance.sourceToBinaryReviewComplete ===
      gradleDependencyProvenance.releaseCriteria
        .vendoredSourceToBinaryReviewComplete &&
    probe.gradleDependencyProvenance.strictVerificationMetadataPresent ===
      gradleDependencyProvenance.dependencyVerification.metadataPresent &&
    probe.gradleDependencyProvenance.verificationMetadataSha256 ===
      gradleDependencyProvenance.dependencyVerification.metadataSha256 &&
    probe.gradleDependencyProvenance.verificationMetadataDigestSha256 ===
      gradleDependencyProvenance.dependencyVerification.metadataDigestSha256 &&
    probe.gradleDependencyProvenance.verificationMetadataReviewed ===
      gradleDependencyProvenance.dependencyVerification.independentlyReviewed &&
    probe.gradleDependencyProvenance.productionReleaseLocksPresent ===
      gradleDependencyProvenance.dependencyLocking
        .allConfiguredProductionReleaseConfigurationsMaterialized &&
    probe.gradleDependencyProvenance.lockFileCount ===
      gradleDependencyProvenance.dependencyLocking.materializedInventory
        .lockFilePaths.length &&
    probe.gradleDependencyProvenance.lockFileSetSha256 ===
      gradleDependencyProvenance.dependencyLocking.materializedInventory
        .lockFileSetSha256 &&
    probe.gradleDependencyProvenance.lockConfigurationCount ===
      gradleDependencyProvenance.dependencyLocking.materializedInventory
        .configurationCount &&
    probe.gradleDependencyProvenance.productionReleaseLockConfigurationCount ===
      gradleDependencyProvenance.dependencyLocking.materializedInventory
        .productionReleaseConfigurationCount &&
    probe.gradleDependencyProvenance.lockConfigurationInventorySha256 ===
      gradleDependencyProvenance.dependencyLocking.materializedInventory
        .configurationInventorySha256 &&
    probe.gradleDependencyProvenance.productionReleaseLocksReviewed ===
      gradleDependencyProvenance.dependencyLocking.independentlyReviewed &&
    probe.gradleDependencyProvenance
      .idensicTensorflowNamespaceWorkaroundStatus ===
      gradleDependencyProvenance.repositoryPolicy
        .idensicTensorflowNamespaceCompatibility.status &&
    probe.gradleDependencyProvenance
      .idensicTensorflowNamespaceWorkaroundReviewed ===
      gradleDependencyProvenance.repositoryPolicy
        .idensicTensorflowNamespaceCompatibility.independentlyReviewed &&
    probe.gradleDependencyProvenance.broadNamespaceQualificationGranted ===
      gradleDependencyProvenance.repositoryPolicy
        .idensicTensorflowNamespaceCompatibility.broadQualificationGranted &&
    probe.gradleDependencyProvenance.arbitraryPayWingsRepositoryRemoved ===
      gradleDependencyProvenance.repositoryPolicy
        .arbitraryPayWingsRepositoryRemoved &&
    probe.gradleDependencyProvenance.exactSoramitsuAndJitpackModuleFiltersPresent ===
      true &&
    probe.gradleDependencyProvenance.trackedAuthTokenRemoved ===
      gradleDependencyProvenance.credentialIncident
        .trackedProjectAuthTokenRemoved &&
    probe.gradleDependencyProvenance.priorTokenRevocationConfirmed ===
      gradleDependencyProvenance.credentialIncident.priorTokenRevoked &&
    probe.gradleDependencyProvenance
      .replacementCredentialRotationConfirmed ===
      gradleDependencyProvenance.credentialIncident
        .replacementCredentialRotated &&
    probe.gradleDependencyProvenance.secretsRecordedInEvidence ===
      gradleDependencyProvenance.credentialIncident
        .secretValueRetainedInEvidence &&
    probe.gradleDependencyProvenance.qualification ===
      gradleDependencyProvenance.status,
  "GRADLE_DEPENDENCY_RELEASE_EVIDENCE_DIVERGED",
);
assert(
  probe.androidSigningIdentity.applicationId ===
    androidProductionSigning.applicationId &&
    probe.androidSigningIdentity.dedicatedProductionReleaseConfig === true &&
    probe.androidSigningIdentity.debugFallbackAllowedForRelease === false &&
    probe.androidSigningIdentity
      .productionAppSigningCertificateSha256Recorded ===
      /^[0-9a-f]{64}$/.test(
        androidProductionSigning.productionAppSigningCertificateSha256 ?? "",
      ) &&
    probe.androidSigningIdentity
      .productionUploadCertificateSha256Recorded ===
      /^[0-9a-f]{64}$/.test(
        androidProductionSigning.productionUploadCertificateSha256 ?? "",
      ) &&
    probe.androidSigningIdentity.signedBundleCertificateMatched ===
      androidProductionSigning.signedBundleCertificateMatched &&
    probe.androidSigningIdentity.playAppSigningContinuityReviewed ===
      androidProductionSigning.playAppSigningContinuityReviewed &&
    probe.androidSigningIdentity.privateKeyOrPasswordRecorded === false,
  "ANDROID_SIGNING_RELEASE_EVIDENCE_DIVERGED",
);
block(
  probe.androidSdk.qualification !== "qualified",
  "IROHA_ANDROID_RELEASE_EVIDENCE_NOT_QUALIFIED",
);
block(
  probe.gradleDependencyProvenance.qualification !== "qualified",
  "GRADLE_DEPENDENCY_RELEASE_EVIDENCE_NOT_QUALIFIED",
);
block(
  probe.androidSigningIdentity.qualification !== "qualified",
  "ANDROID_SIGNING_RELEASE_EVIDENCE_NOT_QUALIFIED",
);
block(
  probe.piIndexer.status !== "qualified" ||
    probe.piIndexer.readContractStatus !== "qualified",
  "PI_INDEXER_CHAIN_IDENTITY_NOT_QUALIFIED",
);
block(
  probe.piIndexer.historyCheckpointContractStatus !== "qualified" ||
    !Object.values(probe.piIndexer.historyQualification ?? {}).length ||
    !Object.values(probe.piIndexer.historyQualification ?? {}).every(
      (value) => value === true,
    ),
  "PI_HISTORY_CHECKPOINT_CONTRACT_NOT_QUALIFIED",
);
block(
  probe.piIndexer.mutationCapabilitiesStatus !== "qualified" ||
    (probe.piIndexer.missingRequiredMobileConfigFields ?? []).length !== 0 ||
    !Object.values(probe.piIndexer.mobileConfigQualification ?? {}).length ||
    !Object.values(probe.piIndexer.mobileConfigQualification ?? {}).every(
      (value) => value === true,
    ),
  "PI_MOBILE_CONFIG_CAPABILITIES_NOT_DEPLOYED",
);
block(
  !Object.values(probe.piIndexer.accountBalancesQualification ?? {}).length ||
    !Object.values(probe.piIndexer.accountBalancesQualification ?? {}).every(
      (value) => value === true,
    ),
  "PI_TYPED_ACCOUNT_BALANCES_NOT_QUALIFIED",
);

const tairaDeploymentInput = {
  manifestPath: process.env.TAIRA_DEPLOYMENT_MANIFEST_PATH ?? "",
  operatorSignaturePath:
    process.env.TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_PATH ?? "",
  reviewerSignaturePath:
    process.env.TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_PATH ?? "",
  operatorPublicKeyPath:
    process.env.TAIRA_DEPLOYMENT_OPERATOR_PUBLIC_KEY_PATH ?? "",
  reviewerPublicKeyPath:
    process.env.TAIRA_DEPLOYMENT_REVIEWER_PUBLIC_KEY_PATH ?? "",
  expectedOperatorKeySha256:
    process.env.TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256 ?? "",
  expectedReviewerKeySha256:
    process.env.TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256 ?? "",
};
const tairaDeploymentAdmissionPath =
  process.env.TAIRA_DEPLOYMENT_ADMISSION_RECEIPT_PATH ?? "";
const tairaDeploymentAdmissionRecord = isAbsolute(
  tairaDeploymentAdmissionPath,
)
  ? readStrictJsonFile(tairaDeploymentAdmissionPath, 64 * 1024)
  : null;
let tairaDeploymentAdmission = null;
try {
  const evaluationEpochSeconds =
    tairaDeploymentAdmissionRecord?.value?.evaluationEpochSeconds;
  const currentEpochSeconds = Math.floor(Date.now() / 1000);
  if (
    !Number.isSafeInteger(evaluationEpochSeconds) ||
    evaluationEpochSeconds > currentEpochSeconds ||
    currentEpochSeconds - evaluationEpochSeconds > 6 * 60 * 60
  ) {
    throw new Error("TAIRA_DEPLOYMENT_ADMISSION_STALE");
  }
  const verified = verifyTairaDeploymentManifestV1({
    ...tairaDeploymentInput,
    evaluationEpochSeconds,
  });
  if (
    JSON.stringify(verified) !==
    JSON.stringify(tairaDeploymentAdmissionRecord.value)
  ) {
    throw new Error("TAIRA_DEPLOYMENT_ADMISSION_DIVERGED");
  }
  tairaDeploymentAdmission = verified;
} catch {
  tairaDeploymentAdmission = null;
}
block(
  tairaDeploymentAdmission === null,
  "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED",
);
block(
  probe.nexus.taira.httpStatus !== 200 ||
    probe.nexus.taira.endpoint !==
      tairaDeploymentAdmission?.current?.publicNodeMcpEndpoint ||
    probe.nexus.taira.observedChainId !==
      tairaDeploymentAdmission?.current?.chainId ||
    probe.nexus.taira.observedI105Discriminant !==
      tairaDeploymentAdmission?.current?.i105Discriminant ||
    probe.nexus.taira.rolloutValidation?.configuredEndpointClass !==
      "canonical-public-node" ||
    probe.nexus.taira.rolloutValidation?.canonicalPublicNodeEndpointProvided !==
      true ||
    probe.nexus.taira.rolloutValidation?.qualification !== "qualified" ||
    probe.nexus.taira.sumeragiTelemetry?.chainIdentityDecoded !== true,
  "TAIRA_TORII_NOT_HEALTHY",
);
block(
  probe.nexus.minamoto.httpStatus !== 200 ||
    probe.nexus.minamoto.endpoint !== "https://minamoto.sora.org/health" ||
    probe.nexus.minamoto.observedChainId !==
      "00000000-0000-0000-0000-000000000753" ||
    probe.nexus.minamoto.observedI105Discriminant !== 753 ||
    probe.nexus.minamoto.qualification !== "qualified",
  "MINAMOTO_TORII_NOT_HEALTHY",
);

const signerBinding = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/di/WalletFeatureModule.kt",
);
const nexusTransactionSignerSource = stripGradleComments(
  read(
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusTransactionSigner.kt",
  ),
);
const nexusSendQualificationSource = stripGradleComments(
  read(
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusSendQualification.kt",
  ),
);
const nexusSendQualificationTest = read(
  "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusSendQualificationTest.kt",
);
const nexusSendQualificationProviderSource = stripGradleComments(signerBinding);
const nexusSendQualificationProviderMatches = [
  ...nexusSendQualificationProviderSource.matchAll(
    /@Provides\s+@Singleton\s+fun\s+provideNexusSendQualification\s*\(\s*signer\s*:\s*NexusTransactionSigner\s*,\s*finalityReader\s*:\s*NexusFinalityReader\s*,?\s*\)\s*:\s*NexusSendQualification\s*=\s*DefaultNexusSendQualification\s*\(\s*signer\s*,\s*finalityReader\s*,?\s*\)/g,
  ),
];
const unavailableSignerFailureOccurrences =
  nexusTransactionSignerSource.match(
    /throw\s+IllegalStateException\("NEXUS_SIGNER_NOT_QUALIFIED"\)/g,
  ) ?? [];
const unavailableFinalityReaderFailureOccurrences =
  nexusTransactionSignerSource.match(
    /throw\s+IllegalStateException\("NEXUS_FINALITY_READER_NOT_QUALIFIED"\)/g,
  ) ?? [];
assert(
  /class\s+UnavailableNexusTransactionSigner\s*:\s*NexusTransactionSigner\s*\{/.test(
    nexusTransactionSignerSource,
  ) &&
    /override\s+fun\s+isQualifiedFor\s*\(\s*network\s*:\s*NexusNetwork\s*\)\s*:\s*Boolean\s*=\s*false/.test(
      nexusTransactionSignerSource,
    ) &&
    /override\s+suspend\s+fun\s+quote\s*\(\s*request\s*:\s*NexusTransferSigningRequest\s*\)\s*:\s*NexusTransferFeeQuote\s*=\s*throw\s+IllegalStateException\("NEXUS_SIGNER_NOT_QUALIFIED"\)/.test(
      nexusTransactionSignerSource,
    ) &&
    /interface\s+NexusFinalityReader\s*\{[\s\S]*?suspend\s+fun\s+finalizedCheckpoint\s*\(\s*network\s*:\s*NexusNetwork\s*\)\s*:\s*NexusFinalityCheckpoint[\s\S]*?\}/.test(
      nexusTransactionSignerSource,
    ) &&
    /override\s+suspend\s+fun\s+sign\s*\(\s*request\s*:\s*NexusTransferSigningRequest\s*,\s*quote\s*:\s*NexusTransferFeeQuote\s*,?\s*\)\s*:\s*NexusSignedTransaction\s*=\s*throw\s+IllegalStateException\("NEXUS_SIGNER_NOT_QUALIFIED"\)/.test(
      nexusTransactionSignerSource,
    ) &&
    /class\s+UnavailableNexusFinalityReader\s*:\s*NexusFinalityReader\s*\{[\s\S]*?override\s+fun\s+isQualifiedFor\s*\(\s*network\s*:\s*NexusNetwork\s*\)\s*:\s*Boolean\s*=\s*false[\s\S]*?override\s+suspend\s+fun\s+finalizedCheckpoint\s*\(\s*network\s*:\s*NexusNetwork\s*\)\s*:\s*NexusFinalityCheckpoint\s*=\s*throw\s+IllegalStateException\("NEXUS_FINALITY_READER_NOT_QUALIFIED"\)/.test(
      nexusTransactionSignerSource,
    ) &&
    unavailableSignerFailureOccurrences.length === 2 &&
    unavailableFinalityReaderFailureOccurrences.length === 1 &&
    /fun\s+provideNexusFinalityReader\s*\(\s*\)\s*:\s*NexusFinalityReader\s*=\s*UnavailableNexusFinalityReader\s*\(\s*\)/.test(
      signerBinding,
    ),
  "IROHA_UNAVAILABLE_SIGNER_IMPLEMENTATION_NOT_FAIL_CLOSED",
);
const nexusSendQualificationInterface =
  nexusSendQualificationSource.match(
    /interface\s+NexusSendQualification\s*\{[\s\S]*?\}/,
  )?.[0] ?? "";
const nexusSendQualificationImplementationDeclaration =
  nexusSendQualificationSource.match(
    /class\s+DefaultNexusSendQualification[\s\S]*?\{\s*$/m,
  )?.[0] ?? "";
assert(
  /interface\s+NexusSendQualification\s*\{\s*fun\s+isQualifiedFor\s*\(\s*network\s*:\s*NexusNetwork\s*\)\s*:\s*Boolean\s*\}/.test(
    nexusSendQualificationInterface,
  ) &&
    !/\b(?:quote|sign|finalizedCheckpoint)\s*\(/.test(
      nexusSendQualificationInterface,
    ) &&
    /class\s+DefaultNexusSendQualification\s*\(\s*private\s+val\s+signer\s*:\s*NexusTransactionSigner\s*,\s*private\s+val\s+finalityReader\s*:\s*NexusFinalityReader\s*,?\s*\)\s*:\s*NexusSendQualification\s*\{/.test(
      nexusSendQualificationImplementationDeclaration,
    ) &&
    !/\)\s*:\s*[^\{]*(?:NexusTransactionSigner|NexusFinalityReader)/.test(
      nexusSendQualificationImplementationDeclaration,
    ) &&
    /override\s+fun\s+isQualifiedFor\s*\(\s*network\s*:\s*NexusNetwork\s*\)\s*:\s*Boolean\s*=\s*try\s*\{[\s\S]*?signer\.isQualifiedFor\(network\)\s*&&\s*finalityReader\.isQualifiedFor\(network\)[\s\S]*?\}\s*catch\s*\(\s*_\s*:\s*Exception\s*\)\s*\{\s*false\s*\}\s*catch\s*\(\s*_\s*:\s*LinkageError\s*\)\s*\{\s*false\s*\}/.test(
      nexusSendQualificationSource,
    ) &&
    nexusSendQualificationProviderMatches.length === 1 &&
    nexusSendQualificationTest.includes(
      "send qualification requires both capabilities without exercising either capability",
    ) &&
    nexusSendQualificationTest.includes(
      "qualification adapter failures disable sends without invoking sensitive operations",
    ) &&
    nexusSendQualificationTest.includes(
      "native linkage failures disable sends without invoking sensitive operations",
    ) &&
    nexusSendQualificationTest.includes(
      'throws UnsatisfiedLinkError("signer native artifact unavailable")',
    ) &&
    nexusSendQualificationTest.includes(
      'throws UnsatisfiedLinkError("finality native artifact unavailable")',
    ) &&
    nexusSendQualificationTest.includes("Triple(false, false, false)") &&
    nexusSendQualificationTest.includes("Triple(false, true, false)") &&
    nexusSendQualificationTest.includes("Triple(true, false, false)") &&
    nexusSendQualificationTest.includes("Triple(true, true, true)") &&
    nexusSendQualificationTest.includes(
      "coVerify(exactly = 0) { signer.quote(any()) }",
    ) &&
    nexusSendQualificationTest.includes(
      "coVerify(exactly = 0) { signer.sign(any(), any()) }",
    ) &&
    nexusSendQualificationTest.includes(
      "coVerify(exactly = 0) { finalityReader.finalizedCheckpoint(any()) }",
    ),
  "NEXUS_SEND_QUALIFICATION_CAPABILITY_PROJECTION_MISSING",
);
const isSafeRelativeEvidencePath = (value) =>
  typeof value === "string" &&
  /^[A-Za-z0-9._/-]+$/.test(value) &&
  !value.startsWith("/") &&
  value
    .split("/")
    .every((segment) => segment && segment !== "." && segment !== "..");
const isSymlinkFreeRegularFile = (relativePath) => {
  if (!isSafeRelativeEvidencePath(relativePath)) return false;
  try {
    let currentPath = root;
    const segments = relativePath.split("/");
    for (const [index, segment] of segments.entries()) {
      currentPath = join(currentPath, segment);
      const stat = lstatSync(currentPath);
      if (stat.isSymbolicLink()) return false;
      if (index < segments.length - 1 && !stat.isDirectory()) return false;
      if (index === segments.length - 1 && !stat.isFile()) return false;
    }
    return true;
  } catch {
    return false;
  }
};
const inspectSymlinkFreeDirectory = (relativeDirectory) => {
  const inspection = { valid: false, files: [] };
  if (!isSafeRelativeEvidencePath(relativeDirectory)) return inspection;
  try {
    let absoluteDirectory = root;
    const segments = relativeDirectory.split("/");
    for (const segment of segments) {
      absoluteDirectory = join(absoluteDirectory, segment);
      const stat = lstatSync(absoluteDirectory);
      if (stat.isSymbolicLink() || !stat.isDirectory()) return inspection;
    }
    let valid = true;
    const visit = (directory, relativePrefix) => {
      for (const name of readdirSync(directory).sort()) {
        const absolutePath = join(directory, name);
        const relativePath = relativePrefix
          ? `${relativePrefix}/${name}`
          : name;
        const stat = lstatSync(absolutePath);
        if (stat.isSymbolicLink()) {
          valid = false;
        } else if (stat.isDirectory()) {
          visit(absolutePath, relativePath);
        } else if (stat.isFile()) {
          inspection.files.push(relativePath);
        } else {
          valid = false;
        }
      }
    };
    visit(absoluteDirectory, "");
    inspection.files.sort();
    inspection.valid = valid;
  } catch {
    inspection.valid = false;
  }
  return inspection;
};

const stagedIrohaRepositoryQualificationClaimed =
  irohaQualificationClaimed ||
  stagedIrohaRepository.present === true ||
  stagedIrohaRepository.qualified === true ||
  stagedIrohaRepository.symlinkFree === true ||
  stagedIrohaRepository.wholeTreeManifestReviewed === true ||
  stagedIrohaRepository.manifestSha256 !== null;
let stagedIrohaRepositoryQualified = false;
let stagedIrohaManifestEntries = new Map();
if (stagedIrohaRepositoryQualificationClaimed) {
  const repositoryInspection = inspectSymlinkFreeDirectory(
    stagedIrohaRepository.path,
  );
  const manifestRelativePath = relative(
    join(root, stagedIrohaRepository.path),
    join(root, stagedIrohaRepository.manifestPath),
  );
  let manifestQualified = false;
  if (
    repositoryInspection.valid &&
    isSafeRelativeEvidencePath(manifestRelativePath) &&
    repositoryInspection.files.includes(manifestRelativePath)
  ) {
    try {
      const manifestSource = read(stagedIrohaRepository.manifestPath);
      const manifestLines = manifestSource.endsWith("\n")
        ? manifestSource.slice(0, -1).split("\n")
        : [];
      const manifestEntries = manifestLines.map((line) => {
        const match = line.match(
          /^([0-9a-f]{64})  ([A-Za-z0-9._~+-]+(?:\/[A-Za-z0-9._~+-]+)*)$/,
        );
        return match ? { sha256: match[1], path: match[2] } : null;
      });
      const entryPaths = manifestEntries.map((entry) => entry?.path);
      const expectedHashedPaths = repositoryInspection.files.filter(
        (path) => path !== manifestRelativePath,
      );
      const entriesAreCanonical =
        manifestEntries.length > 0 &&
        manifestEntries.every(Boolean) &&
        entryPaths.length === new Set(entryPaths).size &&
        entryPaths.join("\n") === [...entryPaths].sort().join("\n") &&
        entryPaths.join("\n") === expectedHashedPaths.join("\n") &&
        !entryPaths.includes(manifestRelativePath);
      const everyManifestHashMatches =
        entriesAreCanonical &&
        manifestEntries.every(
          (entry) =>
            sha256(`${stagedIrohaRepository.path}/${entry.path}`) ===
            entry.sha256,
        );
      if (entriesAreCanonical) {
        stagedIrohaManifestEntries = new Map(
          manifestEntries.map((entry) => [entry.path, entry.sha256]),
        );
      }
      manifestQualified =
        manifestSource.endsWith("\n") &&
        !manifestSource.endsWith("\n\n") &&
        /^[0-9a-f]{64}$/.test(stagedIrohaRepository.manifestSha256 ?? "") &&
        sha256(stagedIrohaRepository.manifestPath) ===
          stagedIrohaRepository.manifestSha256 &&
        everyManifestHashMatches;
    } catch {
      manifestQualified = false;
    }
  }
  stagedIrohaRepositoryQualified =
    stagedIrohaRepository.present === true &&
    stagedIrohaRepository.qualified === true &&
    stagedIrohaRepository.symlinkFree === true &&
    stagedIrohaRepository.wholeTreeManifestReviewed === true &&
    repositoryInspection.valid &&
    manifestQualified;
  assert(
    stagedIrohaRepositoryQualified,
    "IROHA_STAGED_MAVEN_REPOSITORY_NOT_COMPLETELY_REVIEWED",
  );
}

const [reviewedIrohaGroup, reviewedIrohaArtifact, reviewedIrohaVersion] =
  irohaPin.reviewedCore.coordinate.split(":");
const strippedWalletFeatureBuild = stripGradleComments(walletFeatureBuild);
const exactIrohaDependencyPattern = new RegExp(
  `^\\s*${escapeRegExp(productionSignerBinding.dependencyConfiguration)}` +
    `\\s*\\(\\s*["']${escapeRegExp(irohaPin.reviewedCore.coordinate)}` +
    `["']\\s*\\)\\s*$`,
  "gm",
);
const exactIrohaDependencyDeclarations =
  strippedWalletFeatureBuild.match(exactIrohaDependencyPattern) ?? [];
const reviewedIrohaModulePattern = new RegExp(
  escapeRegExp(`${reviewedIrohaGroup}:${reviewedIrohaArtifact}`),
  "g",
);
const reviewedIrohaModuleReferences = gradleBuildSources.flatMap(
  ({ path, source }) =>
    (source.match(reviewedIrohaModulePattern) ?? []).map(() => path),
);
const forbiddenGradleSubstitutionPattern =
  /\bmavenLocal\s*\(|\bflatDir\s*\{|\bincludeBuild\s*\(|\bdependencySubstitution\b/;
const localIrohaDependencyPattern =
  /\b(?:implementation|api|compileOnly|runtimeOnly)\s*\(\s*(?:(?:project|files|fileTree)\s*\([\s\S]{0,240}?(?:iroha|core-jvm)|libs(?:\.[A-Za-z0-9_]+)*\.(?:[A-Za-z0-9_.]*(?:iroha|coreJvm|core\.jvm)[A-Za-z0-9_.]*))/i;
const strippedSettings = stripGradleComments(settings);
const stagedIrohaGradleBindingQualified =
  exactIrohaDependencyDeclarations.length === 1 &&
  reviewedIrohaModuleReferences.length === 1 &&
  reviewedIrohaModuleReferences[0] ===
    "feature_wallet_impl/build.gradle.kts" &&
  new RegExp(
    `^\\s*name\\s*=\\s*"${escapeRegExp(stagedIrohaRepository.gradleRepositoryName)}"\\s*$`,
    "m",
  ).test(strippedSettings) &&
  new RegExp(
    `^\\s*url\\s*=\\s*uri\\(rootDir\\.resolve\\("${escapeRegExp(stagedIrohaRepository.path)}"\\)\\)\\s*$`,
    "m",
  ).test(strippedSettings) &&
  new RegExp(
    `^\\s*includeGroup\\("${escapeRegExp(stagedIrohaRepository.exclusiveGroup)}"\\)\\s*$`,
    "m",
  ).test(strippedSettings) &&
  gradleBuildSources.every(
    ({ source }) =>
      !forbiddenGradleSubstitutionPattern.test(source) &&
      !localIrohaDependencyPattern.test(source),
  );
if (irohaQualificationClaimed) {
  assert(
    stagedIrohaGradleBindingQualified,
    "IROHA_REVIEWED_COORDINATE_NOT_DIRECTLY_AND_EXCLUSIVELY_WIRED",
  );
}

const strippedSignerBinding = stripGradleComments(signerBinding);
const unavailableSignerOccurrences =
  strippedSignerBinding.match(/UnavailableNexusTransactionSigner/g) ?? [];
const unavailableSignerIsExactlyBound =
  unavailableSignerOccurrences.length === 2 &&
  /^\s*import\s+jp\.co\.soramitsu\.feature_wallet_impl\.data\.nexus\.UnavailableNexusTransactionSigner\s*$/m.test(
    strippedSignerBinding,
  ) &&
    /fun\s+provideNexusTransactionSigner\s*\(\s*\)\s*:\s*NexusTransactionSigner\s*=\s*UnavailableNexusTransactionSigner\s*\(\s*\)/.test(
      strippedSignerBinding,
    );
const unavailableFinalityReaderOccurrences =
  strippedSignerBinding.match(/UnavailableNexusFinalityReader/g) ?? [];
const unavailableFinalityReaderIsExactlyBound =
  unavailableFinalityReaderOccurrences.length === 2 &&
  /^\s*import\s+jp\.co\.soramitsu\.feature_wallet_impl\.data\.nexus\.UnavailableNexusFinalityReader\s*$/m.test(
    strippedSignerBinding,
  ) &&
  /fun\s+provideNexusFinalityReader\s*\(\s*\)\s*:\s*NexusFinalityReader\s*=\s*UnavailableNexusFinalityReader\s*\(\s*\)/.test(
    strippedSignerBinding,
  );
const localQualificationValue = (name) => {
  const matches = [
    ...productionFeatureManager.matchAll(
      new RegExp(
        `^\\s*(?:private\\s+)?const val ${escapeRegExp(name)}` +
          `\\s*=\\s*(true|false)\\s*$`,
        "gm",
      ),
    ),
  ];
  return matches.length === 1 ? matches[0][1] : null;
};
const nativeMutationFlagsRemainDisabled =
  localQualificationValue("LOCAL_NEXUS_SENDS_QUALIFIED") === "false" &&
  localQualificationValue("LOCAL_POLKAMARKT_MUTATIONS_QUALIFIED") === "false";
const signerPromotionClaimed =
  irohaQualificationClaimed ||
  productionSignerBinding?.status === "qualified" ||
  productionSignerBinding?.adapterReviewed === true ||
  productionSignerBinding?.artifactIdentityReviewed === true ||
  productionSignerBinding?.bindingReviewed === true ||
  [
    "adapterType",
    "adapterSourcePath",
    "adapterSourceSha256",
    "artifactCoordinate",
    "artifactSha256",
  ].some((field) => productionSignerBinding?.[field] !== null) ||
  !unavailableSignerIsExactlyBound ||
  !unavailableFinalityReaderIsExactlyBound;
let reviewedSignerAdapterSourceQualified = false;
let exactReviewedSignerProviderBound = false;
const reviewedAdapterType = productionSignerBinding?.adapterType;
const reviewedAdapterTypeParts =
  typeof reviewedAdapterType === "string" ? reviewedAdapterType.split(".") : [];
const reviewedAdapterSimpleName =
  reviewedAdapterTypeParts[reviewedAdapterTypeParts.length - 1];
const reviewedAdapterPackage = reviewedAdapterTypeParts.slice(0, -1).join(".");
const reviewedAdapterTypeIsSafe =
  typeof reviewedAdapterType === "string" &&
  /^(?:[a-z_][A-Za-z0-9_]*\.)+[A-Z][A-Za-z0-9_]*$/.test(
    reviewedAdapterType,
  );
const expectedReviewedAdapterSourcePath = reviewedAdapterTypeIsSafe
  ? `feature_wallet_impl/src/main/java/${reviewedAdapterType.split(".").join("/")}.kt`
  : null;
if (
  reviewedAdapterTypeIsSafe &&
  productionSignerBinding.adapterSourcePath ===
    expectedReviewedAdapterSourcePath &&
  isSymlinkFreeRegularFile(productionSignerBinding.adapterSourcePath)
) {
  const reviewedAdapterSource = read(productionSignerBinding.adapterSourcePath);
  const reviewedAdapterContractSource = stripGradleComments(
    reviewedAdapterSource,
  );
  const reviewedAdapterDeclaration =
    reviewedAdapterContractSource.match(
      new RegExp(
        `\\b(?:class\\s+${escapeRegExp(reviewedAdapterSimpleName)}\\b[^{}]*` +
          `|object\\s+${escapeRegExp(reviewedAdapterSimpleName)}\\b[^{}]*)\\{`,
      ),
    )?.[0] ?? "";
  reviewedSignerAdapterSourceQualified =
    /^[0-9a-f]{64}$/.test(
      productionSignerBinding.adapterSourceSha256 ?? "",
    ) &&
    sha256(productionSignerBinding.adapterSourcePath) ===
      productionSignerBinding.adapterSourceSha256 &&
    new RegExp(
      `^\\s*package\\s+${escapeRegExp(reviewedAdapterPackage)}\\s*$`,
      "m",
    ).test(reviewedAdapterContractSource) &&
    new RegExp(
      `^\\s*import\\s+${escapeRegExp(reviewedIrohaGroup)}\\.[A-Za-z0-9_.*]+\\s*$`,
      "m",
    ).test(reviewedAdapterContractSource) &&
    reviewedAdapterDeclaration.includes("NexusTransactionSigner") &&
    reviewedAdapterDeclaration.includes("NexusFinalityReader");
  exactReviewedSignerProviderBound =
    new RegExp(
      `^\\s*import\\s+${escapeRegExp(reviewedAdapterType)}\\s*$`,
      "m",
    ).test(strippedSignerBinding) &&
    new RegExp(
      `\\bfun\\s+provideNexusTransactionSigner\\s*\\(\\s*` +
        `adapter\\s*:\\s*${escapeRegExp(reviewedAdapterSimpleName)}\\s*,?\\s*\\)` +
        `\\s*:\\s*NexusTransactionSigner\\s*=\\s*adapter\\b`,
    ).test(strippedSignerBinding) &&
    new RegExp(
      `\\bfun\\s+provideNexusFinalityReader\\s*\\(\\s*` +
        `adapter\\s*:\\s*${escapeRegExp(reviewedAdapterSimpleName)}\\s*,?\\s*\\)` +
        `\\s*:\\s*NexusFinalityReader\\s*=\\s*adapter\\b`,
    ).test(strippedSignerBinding) &&
    !strippedSignerBinding.includes("UnavailableNexusTransactionSigner") &&
    !strippedSignerBinding.includes("UnavailableNexusFinalityReader");
}
const reviewedIrohaJarManifestPath =
  `${reviewedIrohaGroup.split(".").join("/")}/` +
  `${reviewedIrohaArtifact}/${reviewedIrohaVersion}/` +
  `${reviewedIrohaArtifact}-${reviewedIrohaVersion}.jar`;
const reviewedSignerArtifactIdentityQualified =
  productionSignerBinding?.artifactCoordinate ===
    irohaPin.reviewedCore.coordinate &&
  productionSignerBinding?.artifactSha256 === irohaPin.reviewedCore.jarSha256 &&
  irohaPin.reviewedCore.currentArchiveCoreJarIdentityVerified === true &&
  stagedIrohaRepositoryQualified &&
  stagedIrohaManifestEntries.get(reviewedIrohaJarManifestPath) ===
    productionSignerBinding?.artifactSha256;
const requiredNativeCanaryParityStages = [
  "requiredExportInventory",
  "transactionBytes",
  "signingPrehash",
  "signedEnvelope",
  "decodeProjection",
];
const requiredNativeCanaryTransactionParityStages =
  requiredNativeCanaryParityStages.slice(1);
const nativeCanaryQualificationReceiptClaimed =
  nativeCanaryQualificationEvidence?.status === "qualified" ||
  nativeCanaryQualificationEvidence?.receiptSha256 != null ||
  requiredNativeCanaryQualificationFields.some(
    (field) => nativeCanaryContract?.[field] === true,
  );
let nativeCanaryQualificationReceiptQualified = false;
if (nativeCanaryQualificationReceiptClaimed) {
  try {
    const receiptPath = nativeCanaryQualificationEvidence?.receiptPath;
    const receiptStat = isSymlinkFreeRegularFile(receiptPath)
      ? lstatSync(join(root, receiptPath))
      : null;
    const receipt = receiptStat ? json(receiptPath) : null;
    const parityEvidence = receipt?.parityEvidence;
    const exportInventoryEvidence =
      parityEvidence?.requiredExportInventory;
    const receiptNativeContract = receipt?.nativeContract;
    const artifactBinding = receipt?.artifactBinding;
    nativeCanaryQualificationReceiptQualified =
      receiptStat !== null &&
      receiptStat.size > 0 &&
      receiptStat.size <= 64 * 1024 &&
      nativeCanaryQualificationEvidence.status === "qualified" &&
      /^[0-9a-f]{64}$/.test(
        nativeCanaryQualificationEvidence.receiptSha256 ?? "",
      ) &&
      sha256(receiptPath) ===
        nativeCanaryQualificationEvidence.receiptSha256 &&
      hasExactKeys(receipt, [
        "schemaVersion",
        "platform",
        "status",
        "privacy",
        "artifactBinding",
        "nativeContract",
        "nativeAbi",
        "parityEvidence",
      ]) &&
      receipt.schemaVersion === 1 &&
      receipt.platform === "android" &&
      receipt.status === "qualified" &&
      hasExactKeys(receipt.privacy, [
        "containsSecrets",
        "containsPrivateKeys",
        "containsRawSignedPayloads",
      ]) &&
      receipt.privacy.containsSecrets === false &&
      receipt.privacy.containsPrivateKeys === false &&
      receipt.privacy.containsRawSignedPayloads === false &&
      hasExactKeys(artifactBinding, [
        "artifactClass",
        "sourceTreeClean",
        "upstreamRevision",
        "reviewedArchiveSha256",
        "artifactCoordinate",
        "artifactSha256",
        "stagedRepositoryManifestSha256",
        "adapterSourceSha256",
      ]) &&
      artifactBinding.artifactClass === "reviewed-platform-release" &&
      artifactBinding.sourceTreeClean === true &&
      artifactBinding.upstreamRevision === irohaPin.upstream.commit &&
      artifactBinding.reviewedArchiveSha256 === irohaPin.artifact.sha256 &&
      artifactBinding.artifactCoordinate ===
        productionSignerBinding.artifactCoordinate &&
      artifactBinding.artifactSha256 ===
        productionSignerBinding.artifactSha256 &&
      artifactBinding.stagedRepositoryManifestSha256 ===
        stagedIrohaRepository.manifestSha256 &&
      artifactBinding.adapterSourceSha256 ===
        productionSignerBinding.adapterSourceSha256 &&
      hasExactKeys(receiptNativeContract, [
        "requiredNativeAbi",
        "vectorSchema",
        "vectorSha256",
        "canonicalTypedSbdCbsiId",
        "policyFingerprint",
        "payoutFingerprint",
      ]) &&
      receiptNativeContract.requiredNativeAbi ===
        nativeCanaryContract.requiredNativeAbi &&
      receiptNativeContract.vectorSchema === nativeCanaryContract.vectorSchema &&
      receiptNativeContract.vectorSha256 === nativeCanaryContract.vectorSha256 &&
      receiptNativeContract.canonicalTypedSbdCbsiId ===
        nativeCanaryContract.canonicalTypedSbdCbsiId &&
      receiptNativeContract.policyFingerprint ===
        nativeCanaryContract.policyFingerprint &&
      receiptNativeContract.payoutFingerprint ===
        nativeCanaryContract.payoutFingerprint &&
      hasExactKeys(receipt.nativeAbi, ["required", "observed", "qualified"]) &&
      receipt.nativeAbi.required === nativeCanaryContract.requiredNativeAbi &&
      receipt.nativeAbi.observed === nativeCanaryContract.requiredNativeAbi &&
      receipt.nativeAbi.qualified === true &&
      hasExactKeys(parityEvidence, requiredNativeCanaryParityStages) &&
      hasExactKeys(exportInventoryEvidence, [
        "referenceSha256",
        "platformSha256",
        "referenceCount",
        "platformCount",
        "qualified",
      ]) &&
      /^[0-9a-f]{64}$/.test(
        exportInventoryEvidence.referenceSha256 ?? "",
      ) &&
      exportInventoryEvidence.platformSha256 ===
        exportInventoryEvidence.referenceSha256 &&
      Number.isSafeInteger(exportInventoryEvidence.referenceCount) &&
      exportInventoryEvidence.referenceCount > 0 &&
      exportInventoryEvidence.platformCount ===
        exportInventoryEvidence.referenceCount &&
      exportInventoryEvidence.qualified === true &&
      requiredNativeCanaryTransactionParityStages.every((stage) => {
        const evidence = parityEvidence[stage];
        return (
          hasExactKeys(evidence, [
            "referenceSha256",
            "platformSha256",
            "qualified",
          ]) &&
          /^[0-9a-f]{64}$/.test(evidence.referenceSha256 ?? "") &&
          evidence.platformSha256 === evidence.referenceSha256 &&
          evidence.qualified === true
        );
      });
  } catch {
    nativeCanaryQualificationReceiptQualified = false;
  }
  assert(
    nativeCanaryQualificationReceiptQualified,
    "IROHA_ANDROID_NATIVE_CANARY_QUALIFICATION_RECEIPT_INVALID",
  );
}
const productionSignerBindingQualified =
  productionSignerBinding?.status === "qualified" &&
  productionSignerBinding?.adapterReviewed === true &&
  productionSignerBinding?.artifactIdentityReviewed === true &&
  productionSignerBinding?.bindingReviewed === true &&
  reviewedSignerAdapterSourceQualified &&
  exactReviewedSignerProviderBound &&
  reviewedSignerArtifactIdentityQualified &&
  nativeCanaryQualificationReceiptQualified &&
  stagedIrohaGradleBindingQualified;
if (signerPromotionClaimed) {
  assert(
    productionSignerBindingQualified,
    "IROHA_PRODUCTION_SIGNER_NOT_EXACTLY_BOUND_TO_REVIEWED_ARTIFACT",
  );
} else {
  assert(
    unavailableSignerIsExactlyBound && unavailableFinalityReaderIsExactlyBound,
    "IROHA_UNAVAILABLE_SIGNER_BLOCKED_DEFAULT_CHANGED",
  );
}
assert(
  (irohaAndroidQualified && productionSignerBindingQualified) ||
    nativeMutationFlagsRemainDisabled,
  "ANDROID_NATIVE_MUTATION_FLAGS_ENABLED_BEFORE_SIGNER_PROVENANCE_QUALIFICATION",
);
const nexusNetwork = read(
  "common/src/main/java/jp/co/soramitsu/common/nexus/NexusNetwork.kt",
);
const nexusToriiRoutes = read(
  "common/src/main/java/jp/co/soramitsu/common/nexus/NexusToriiRoutes.kt",
);
const nexusTransactionHash = read(
  "common/src/main/java/jp/co/soramitsu/common/nexus/NexusTransactionHash.kt",
);
const nexusHistory = read(
  "common/src/main/java/jp/co/soramitsu/common/nexus/NexusTransferHistory.kt",
);
const nexusHistoryTest = read(
  "common/src/test/java/jp/co/soramitsu/common/nexus/NexusTransferHistoryParserTest.kt",
);
const nexusTorii = read(
  "common/src/main/java/jp/co/soramitsu/common/nexus/NexusToriiClient.kt",
);
const nexusToriiRoutesTest = read(
  "common/src/test/java/jp/co/soramitsu/common/nexus/NexusToriiRoutesTest.kt",
);
const tairaDeploymentBindingTest = read(
  "common/src/test/java/jp/co/soramitsu/common/nexus/TairaDeploymentBindingTest.kt",
);
const commonBuildForTairaDeployment = read("common/build.gradle.kts");
const tairaDeploymentManifestLibrary = read(
  "scripts/lib/taira-deployment-manifest-v1.mjs",
);
const tairaDeploymentManifestVerifier = read(
  "scripts/verify-taira-deployment-manifest.mjs",
);
const tairaDeploymentManifestTest = read(
  "scripts/test-taira-deployment-manifest-v1.mjs",
);
const tairaDeploymentAdmissionWorkflowIndex =
  productionReleaseWorkflow.indexOf(
    "- name: Admit operator-signed Taira epoch mapping",
  );
assert(
  tairaDeploymentAdmissionWorkflowIndex >= 0 &&
    tairaDeploymentAdmissionWorkflowIndex <
      productionReleaseWorkflow.indexOf("./gradlew") &&
    productionReleaseWorkflow.includes(
      "node scripts/verify-taira-deployment-manifest.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/test-taira-deployment-manifest-v1.mjs",
    ) &&
    productionReleaseWorkflow.includes(
      "secrets.TAIRA_DEPLOYMENT_MANIFEST_BASE64",
    ) &&
    productionReleaseWorkflow.includes(
      "secrets.TAIRA_DEPLOYMENT_OPERATOR_SIGNATURE_BASE64",
    ) &&
    productionReleaseWorkflow.includes(
      "secrets.TAIRA_DEPLOYMENT_REVIEWER_SIGNATURE_BASE64",
    ) &&
    productionReleaseWorkflow.includes(
      "vars.TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256",
    ) &&
    productionReleaseWorkflow.includes(
      "vars.TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256",
    ) &&
    commonBuildForTairaDeployment.includes(
      'protectedBuildConfigString("TAIRA_CURRENT_CHAIN_ID")',
    ) &&
    commonBuildForTairaDeployment.includes(
      'protectedBuildConfigString("TAIRA_RETIRED_CHAIN_ID")',
    ) &&
    commonBuildForTairaDeployment.includes(
      'protectedBuildConfigString("TAIRA_DEPLOYMENT_MANIFEST_SHA256")',
    ) &&
    nexusNetwork.includes("class TairaDeploymentBinding private constructor") &&
    nexusNetwork.includes("setOf(currentChainId, retiredChainId) != knownChains") &&
    !nexusNetwork.includes(
      "currentChainId != WalletNetworkChainIdentity.TAIRA_EPOCH_B",
    ) &&
    !nexusNetwork.includes(
      "retiredChainId != WalletNetworkChainIdentity.TAIRA_EPOCH_A",
    ) &&
    nexusNetwork.includes('currentToriiBaseUrl == "https://taira.sora.org"') &&
    nexusNetwork.includes("TairaDeployment.binding?.currentChainId") &&
    nexusNetwork.includes("UNQUALIFIED_TAIRA_CHAIN_ID") &&
    nexusNetwork.includes("00000000-0000-0000-0000-000000000000") &&
    !nexusNetwork.includes(
      "admittedTaira?.currentChainId ?: WalletNetworkChainIdentity.TAIRA_EPOCH_B",
    ) &&
    nexusNetwork.includes("TAIRA_EPOCH_A") &&
    nexusNetwork.includes("TAIRA_EPOCH_B") &&
    nexusNetwork.includes('val pendingJournalPrefix: String = "taira:$manifestSha256:"') &&
    nexusNetwork.includes("fun ownsPendingJournal(localId: String)") &&
    walletIdentityDao.includes("chainId = :tairaChainId") &&
    walletIdentityDao.includes("tairaPendingJournalPrefix") &&
    walletIdentityDao.includes(
      "substr(localId, 1, length(:tairaPendingJournalPrefix))",
    ) &&
    walletIdentityDao.includes("hasCurrentChainIdentityForBinding") &&
    walletIdentityDao.includes("transaction.localId.startsWith(tairaPendingJournalPrefix)") &&
    !walletIdentityDao.includes(
      "or (networkId = 'taira' and chainId =\n                'fc56984b-2be7-431d-840e-21514d1883f0')",
    ) &&
    !nexusNetwork.includes(
      'const val TAIRA = "fc56984b-2be7-431d-840e-21514d1883f0"',
    ) &&
    nexusToriiRoutes.includes("TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED") &&
    nexusToriiRoutes.includes("val binding = TairaDeployment.binding") &&
    nexusToriiRoutesTest.includes(
      "Taira routes fail before transport when deployment manifest is absent",
    ) &&
    tairaDeploymentBindingTest.includes(
      "accepts either signed current epoch mapping",
    ) &&
    tairaDeploymentBindingTest.includes(
      "partial drifted or self reviewed build projections stay unqualified",
    ) &&
    tairaDeploymentBindingTest.includes(
      "production variant contains the exact admitted deployment projection",
    ) &&
    tairaDeploymentBindingTest.includes('BuildConfig.FLAVOR != "production"') &&
    tairaDeploymentBindingTest.includes(
      'requireNotNull(TairaDeployment.binding)',
    ) &&
    tairaDeploymentManifestLibrary.includes(
      "sora-taira-deployment-epoch-manifest-v1",
    ) &&
    tairaDeploymentManifestLibrary.includes(
      'parsed.hostname !== "taira.sora.org"',
    ) &&
    tairaDeploymentManifestLibrary.includes(
      "verifySignature(null, manifestRecord.bytes",
    ) &&
    tairaDeploymentManifestVerifier.includes(
      "RELEASE_EVALUATION_EPOCH_SECONDS",
    ) &&
    tairaDeploymentManifestTest.includes(
      "2 signed operator-selected mappings and ${mutations} fail-closed mutations passed",
    ),
  "TAIRA_DEPLOYMENT_MANIFEST_GATE_MISSING_OR_FAIL_OPEN",
);
const nexusBalanceValidatorTest = read(
  "common/src/test/java/jp/co/soramitsu/common/nexus/NexusBalanceValidatorTest.kt",
);
const nexusAccountTransactionProofTest = read(
  "common/src/test/java/jp/co/soramitsu/common/nexus/NexusAccountTransactionProofTest.kt",
);
const nexusCoordinator = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusTransactionCoordinator.kt",
);
const nexusPendingReconciler = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPendingReconciler.kt",
);
const nexusCoordinatorTest = read(
  "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusTransactionCoordinatorTest.kt",
);
const nexusFinalityCheckpointTest = read(
  "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusFinalityCheckpointTest.kt",
);
const nexusPendingOverlayValidator = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPendingOverlayValidator.kt",
);
const nexusPendingOverlayValidatorTest = read(
  "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPendingOverlayValidatorTest.kt",
);
const nexusPendingRecoveryWorker = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPendingRecoveryWorker.kt",
);
const nexusPortfolioRepository = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPortfolioRepository.kt",
);
const strippedNexusPortfolioRepository = stripGradleComments(
  nexusPortfolioRepository,
);
const cardsHubViewModel = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/presentation/cardshub/CardsHubViewModel.kt",
);
const cardsHubScreen = read(
  "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/presentation/cardshub/CardsHubFragment.kt",
);
const cardsHubViewModelTest = read(
  "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/presentation/wallet/CardsHubViewModelTest.kt",
);
const tairaVisibilityPolicyTest = read(
  "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/TairaVisibilityPolicyTest.kt",
);
const nexusPortfolioTairaVisibilityTest = read(
  "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPortfolioTairaVisibilityTest.kt",
);
const assetsInteractor = read(
  "feature_assets_api/src/main/java/jp/co/soramitsu/feature_assets_api/domain/AssetsInteractor.kt",
);
const irohaAddressCodec = read(
  "common/src/main/java/jp/co/soramitsu/common/nexus/IrohaAddressCodec.kt",
);
const polkamarktFragment = read(
  "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktFragment.kt",
);
const polkamarktWebContractSource = read(
  "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktWebContract.kt",
);
const commonStrings = read("common/src/main/res/values/strings.xml");
const androidCommonLocalizationRoot = join(root, "common/src/main/res");
const androidCommonLocalizationSources = readdirSync(
  androidCommonLocalizationRoot,
  { withFileTypes: true },
)
  .filter((entry) => entry.isDirectory() && entry.name.startsWith("values"))
  .map((entry) => join(androidCommonLocalizationRoot, entry.name, "strings.xml"))
  .filter((path) => existsSync(path))
  .map((path) => ({ path, source: readFileSync(path, "utf8") }));
const androidLocalizedStringValue = (source, key) =>
  new RegExp(`<string\\s+name="${escapeRegExp(key)}">([^<]*)<\\/string>`).exec(
    source,
  )?.[1] ?? null;
const androidMnemonicLocalizationQualified =
  androidCommonLocalizationSources.length >= 20 &&
  androidCommonLocalizationSources.every(({ source }) => {
    const invalid = androidLocalizedStringValue(
      source,
      "common_error_mnemonic_is_not_valid",
    );
    const length = androidLocalizedStringValue(
      source,
      "common_error_mnemonic_length_error",
    );
    const exportDescription = androidLocalizedStringValue(
      source,
      "export_protection_passphrase_description",
    );
    return (
      (invalid === null ||
        (invalid.includes("12") &&
          invalid.includes("24") &&
          !invalid.includes("15"))) &&
      (length === null ||
        (length.includes("12") &&
          length.includes("24") &&
          !length.includes("15"))) &&
      (exportDescription === null ||
        (exportDescription.includes("24") && !exportDescription.includes("12")))
    );
  });
assert(
  androidMnemonicLocalizationQualified,
  "ANDROID_12_24_WORD_WALLET_COPY_INVALID",
);
const androidWalletMnemonicPolicySources = [
  credentialsRepository,
  migrationManager,
  migrationManagerSafetyTest,
].join("\n");
const compactPolicySource = (source) => source.replace(/\s+/g, " ");
const containsExactWordCountLiteral = (source, values) => {
  const compact = compactPolicySource(source);
  const body = values.join(", ");
  return [
    `setOf(${body})`,
    `listOf(${body})`,
    `[${body}]`,
  ].some((literal) => compact.includes(literal));
};
assert(
  containsExactWordCountLiteral(credentialsRepository, [12, 24]) &&
    credentialsRepository.includes("const val LEGACY_SORA_WORD_COUNT = 15") &&
    /wordCount\s+in\s+setOf\(\s*12\s*,\s*LEGACY_SORA_WORD_COUNT\s*,\s*24\s*\)/.test(
      credentialsRepository,
    ) &&
    migrationManager.includes("convertRetainedSoraPassphraseToSeed") &&
    /if \(words\.size == 12 \|\| words\.size == 24\) \{\s*"MNEMONIC"\s*\} else \{\s*"MNEMONIC_UNSUPPORTED"\s*\}/.test(
      migrationManager,
    ) &&
    migrationManager.includes('if (secret.source == "MNEMONIC")') &&
    migrationManagerSafetyTest.includes(
      "retained fifteen word mnemonic remains verified Sora2 only without rewrite",
    ) &&
    migrationManagerSafetyTest.includes('"MNEMONIC_UNSUPPORTED"') &&
    migrationManagerSafetyTest.includes('listOf("sora2")'),
  "ANDROID_RETAINED_15_WORD_WALLET_POLICY_MISSING",
);
const collectIosSwiftSources = (directory, records = []) => {
  if (!existsSync(directory)) return records;
  for (const name of readdirSync(directory).sort()) {
    if (
      [
        ".git",
        ".build",
        "build",
        "DerivedData",
        "Carthage",
        "Pods",
      ].includes(name)
    ) {
      continue;
    }
    const path = join(directory, name);
    const status = lstatSync(path);
    if (status.isSymbolicLink()) continue;
    if (status.isDirectory()) {
      collectIosSwiftSources(path, records);
    } else if (status.isFile() && path.endsWith(".swift")) {
      records.push({ path, source: readFileSync(path, "utf8") });
    }
  }
  return records;
};
const iosSwiftSources = collectIosSwiftSources(resolve(root, "../sora-ios"));
const iosWalletMnemonicPolicySource =
  iosSwiftSources.find(({ source }) =>
    source.includes("enum WalletMnemonicWordPolicy"),
  )?.source ?? "";
const iosLegacyMnemonicEntropySources = iosSwiftSources
  .filter(({ source }) => source.includes("legacyMnemonicEntropy"))
  .map(({ source }) => source);
assert(
  containsExactWordCountLiteral(iosWalletMnemonicPolicySource, [12, 24]) &&
    containsExactWordCountLiteral(iosWalletMnemonicPolicySource, [
      12,
      15,
      24,
    ]) &&
    /case\s+15\s*:[\s\S]{0,320}return\s+\.legacyMnemonicEntropy/.test(
      iosWalletMnemonicPolicySource,
    ) &&
    iosLegacyMnemonicEntropySources.length > 0 &&
    iosLegacyMnemonicEntropySources.some((source) =>
      /expectedSource\s*==\s*\.legacyMnemonicEntropy[\s\S]{0,160}\?\s*\[\s*\.sora2\s*\]/.test(
        source,
      ),
    ) &&
    iosLegacyMnemonicEntropySources.every(
      (source) =>
        !/(?:deriveNexus|deriveIroha)[A-Za-z0-9_]*(?:\([^)]*legacyMnemonicEntropy|[\s\S]{0,160}legacyMnemonicEntropy)/.test(
          source,
        ),
    ),
  "IOS_RETAINED_15_WORD_WALLET_POLICY_MISSING",
);
const nexusAuthoritativeTransactionProof =
  nexusTorii.match(
    /override suspend fun hasAuthoritativeCommittedTransaction\([\s\S]*?\n    }\n\n    suspend fun mcp/,
  )?.[0] ?? "";
const nexusAccountTransactionProofImplementation =
  nexusTorii.match(
    /internal class NexusAccountTransactionProof\([\s\S]*?\n}\n\n@Serializable\ndata class NexusMcpRequest/,
  )?.[0] ?? "";
const nexusMcpImplementation =
  nexusTorii.match(
    /suspend fun mcp\([\s\S]*?\n    }\n\n    override suspend fun committedXorTransfers/,
  )?.[0] ?? "";
const nexusCommittedHistoryImplementation =
  nexusTorii.match(
    /override suspend fun committedXorTransfers\([\s\S]*?\n    }\n\n    private suspend fun getAllAccountAssets/,
  )?.[0] ?? "";
const nexusBalanceValidatorImplementation =
  nexusTorii.match(
    /object NexusBalanceValidator \{[\s\S]*?\n}\n\n\/\*\*/,
  )?.[0] ?? "";
const nexusXorBalanceImplementation =
  nexusTorii.match(
    /override suspend fun getXorBalance\([\s\S]*?(?=\n    \/\*\*[\s\S]*?Reads the exact definition journaled)/,
  )?.[0] ?? "";
const nexusExactAssetBalanceImplementation =
  nexusTorii.match(
    /override suspend fun getAssetBalanceByDefinition\([\s\S]*?(?=\n    suspend fun resolveXorDefinition)/,
  )?.[0] ?? "";
const nexusReconcileImplementation =
  nexusPendingReconciler.match(
    /suspend fun reconcile\(localId: String\): NexusSendResult \{[\s\S]*?\n    }\n\n    \/\*\* Polls the exact hash only/,
  )?.[0] ?? "";
const nexusToriiReadInterface =
  nexusTorii.match(
    /interface NexusToriiReadClient \{[\s\S]*?\n}\n\n\/\*\*/,
  )?.[0] ?? "";
const directAliasAsAssetIdentityFixture =
  /(?:\b(?:id|asset|assetId|assetDefinitionId)\s*=|\b(?:XOR_DEFINITION|XOR_DEFINITION_ID|XOR_ASSET_ID)\s*=)\s*"(?:xor|xor#universal)"/;
const nexusPendingValidFixture =
  nexusPendingOverlayValidatorTest.match(
    /private fun pending\(\) = PendingNetworkTransactionLocal\([\s\S]*?\n    \)\n\n    private companion object/,
  )?.[0] ?? "";
assert(
  nexusTransactionHash.includes("object NexusTransactionHash") &&
    nexusTransactionHash.includes("value != value.trim()") &&
    nexusTransactionHash.includes("wireHash.matches(it)") &&
    nexusTransactionHash.includes("character != '0'") &&
    nexusToriiRoutes.includes("NexusTransactionHash.normalized(value)") &&
    nexusAccountTransactionProofImplementation.includes(
      "NexusTransactionHash.normalized(value)",
    ) &&
    nexusHistory.includes(".let(NexusTransactionHash::normalized)") &&
    nexusCoordinator.includes("NexusTransactionHash.normalized(value)") &&
    nexusPendingOverlayValidator.includes(
      "NexusTransactionHash.normalized(hash) == hash",
    ) &&
    nexusToriiRoutesTest.includes("transaction status rejects malformed and all-zero hashes") &&
    nexusAccountTransactionProofTest.includes('transactionHash = "0".repeat(64)') &&
    nexusPendingOverlayValidatorTest.includes(
      'pending.copy(transactionHash = "0".repeat(64))',
    ),
  "NEXUS_TRANSACTION_HASH_PARITY_GUARD_MISSING",
);
assert(
  nexusToriiRoutes.includes('const val HEALTH_RESPONSE_MEDIA_TYPE = "text/plain"') &&
    nexusToriiRoutes.includes(
      'const val NORITO_TRANSACTION_REQUEST_MEDIA_TYPE = "application/x-norito"',
    ) &&
    nexusToriiRoutes.includes("fun responseAccept(url: String): String") &&
    nexusToriiRoutes.includes("fun requestContentType(method: String, url: String): String?") &&
    nexusToriiRoutes.includes("fun isExpectedResponseContentType(url: String, value: String?): Boolean") &&
    nexusToriiRoutes.includes("fun isExpectedContentType(value: String?, expected: String): Boolean") &&
    nexusToriiRoutes.includes('if (URI(url).path.endsWith("/health"))') &&
    nexusToriiRoutes.includes("HEALTH_RESPONSE_MEDIA_TYPE") &&
    nexusToriiRoutes.includes("JSON_RESPONSE_MEDIA_TYPE") &&
    nexusToriiRoutes.includes('method == "POST" && path.endsWith("/v1/mcp")') &&
    nexusToriiRoutes.includes(
      'method == "POST" && path.endsWith("/v1/pipeline/transactions")',
    ) &&
    nexusToriiRoutes.includes('parts[1].trim().equals("charset=utf-8", ignoreCase = true)') &&
    nexusToriiRoutes.includes(
      'fun isHealthyResponse(payload: String): Boolean = payload == "Healthy"',
    ) &&
    nexusTorii.includes(
      'setRequestProperty("Accept", NexusToriiRoutes.responseAccept(url))',
    ) &&
    nexusTorii.includes(
      "val expectedRequestContentType = NexusToriiRoutes.requestContentType(method, url)",
    ) &&
    nexusTorii.includes("requestContentType == expectedRequestContentType") &&
    nexusTorii.includes(
      "NexusToriiRoutes.isExpectedResponseContentType(",
    ) &&
    nexusTorii.includes(
      "!NexusToriiRoutes.isExpectedContentType(\n                contentType,\n                NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE,",
    ) &&
    nexusTorii.includes('connection.getHeaderField("Content-Type")') &&
    nexusTorii.includes('safeCode = "NEXUS_RESPONSE_CONTENT_TYPE_INVALID"') &&
    nexusTorii.includes(
      "if (!NexusToriiRoutes.isHealthyResponse(payload))",
    ) &&
    nexusTorii.includes('throw NexusToriiException("NEXUS_HEALTH_INVALID")') &&
    nexusToriiRoutes.includes('const val XOR_ASSET_ALIAS = "xor#universal"') &&
    nexusToriiRoutes.includes("fun assetDefinition(network: NexusNetwork, selector: String)") &&
    nexusTorii.includes("data class NexusAssetAliasBinding(") &&
    nexusTorii.includes("object NexusAssetDefinitionIdentity") &&
    nexusTorii.includes("fun hasCanonicalWireShape(value: String): Boolean") &&
    nexusTorii.includes("fun isQualifiedXorDefinition(definition: NexusAssetDefinition)") &&
    nexusTorii.includes("NexusAssetDefinitionIdentity.isQualifiedXorDefinition(definition)") &&
    nexusTorii.includes("NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)") &&
    !nexusTorii.includes("getAllAssetDefinitions") &&
    nexusTorii.includes("object NexusToriiResponseContract") &&
    nexusTorii.includes('private const val MAX_FANOUT_ROUTES = 1_024') &&
    nexusTorii.includes('private const val ROUTED_BY = "x-iroha-routed-by"') &&
    nexusTorii.includes('private const val ROUTE_LANE_ID = "x-iroha-route-lane-id"') &&
    nexusTorii.includes(
      'private const val ROUTE_DATASPACE_ID = "x-iroha-route-dataspace-id"',
    ) &&
    nexusTorii.includes('private val MAX_LANE_ID = BigInteger("4294967295")') &&
    nexusTorii.includes(
      'private val MAX_DATASPACE_ID = BigInteger("18446744073709551615")',
    ) &&
    nexusTorii.includes("!isCanonicalRouteId(routeLaneId, MAX_LANE_ID)") &&
    nexusTorii.includes(
      "!isCanonicalRouteId(routeDataspaceId, MAX_DATASPACE_ID)",
    ) &&
    nexusTorii.includes(
      'routedBy != null && routedBy !in setOf("local", "proxy")',
    ) &&
    nexusTorii.includes(
      'private val COUNT = Regex("^(?:0|[1-9][0-9]{0,3})$")',
    ) &&
    nexusTorii.includes("requireFanout: Boolean = false") &&
    nexusTorii.includes("firstFailure != null || requireFanout") &&
    nexusTorii.includes("firstFailure != null || rawCounts.any { it == null }") &&
    nexusTorii.includes("if (routedBy == null)") &&
    nexusTorii.includes("routeLaneId != null || routeDataspaceId != null") &&
    nexusTorii.includes("attempted <= 0") &&
    nexusTorii.includes("it <= MAX_FANOUT_ROUTES") &&
    nexusTorii.includes("succeeded != attempted") &&
    nexusTorii.includes("counts.drop(2).any { it != 0 }") &&
    nexusTorii.includes(
      "NexusToriiResponseContract.validateFanout(\n                connection::getHeaderField,\n                requireFanout,",
    ) &&
    (nexusTorii.match(/requireFanout = true/g) ?? []).length >= 3 &&
    nexusToriiRoutesTest.includes(
      "qualified network uses exact outer response and typed request media",
    ) &&
    nexusToriiRoutesTest.includes("listOf(NexusNetworks.minamoto)") &&
    nexusToriiRoutesTest.includes(
      "Taira routes fail before transport when deployment manifest is absent",
    ) &&
    nexusToriiRoutesTest.includes('"text/plain; charset=UTF-8"') &&
    nexusToriiRoutesTest.includes('"application/json; profile=unexpected"') &&
    nexusToriiRoutesTest.includes('"application/json, text/plain"') &&
    nexusHistoryTest.includes(
      'mcpResult(contentType = JsonPrimitive("application/json; profile=unexpected"))',
    ) &&
    /mcpResult\(\s*contentType = JsonPrimitive\("application\/json; charset=UTF-8"\)/.test(
      nexusHistoryTest,
    ) &&
    nexusHistoryTest.includes(
      'mcpResult(contentType = JsonPrimitive("application/json;"))',
    ) &&
    nexusToriiRoutesTest.includes("NORITO_TRANSACTION_REQUEST_MEDIA_TYPE") &&
    nexusToriiRoutesTest.includes(
      'https://minamoto.sora.org/v1/assets/definitions/xor%23universal',
    ) &&
    nexusToriiRoutesTest.includes("6TEAJqbb8oEPmLncoNiMRbLEK6tw") &&
    nexusToriiRoutesTest.includes('status = "leased_grace"') &&
    nexusToriiRoutesTest.includes(
      "partial Torii fanout cannot masquerade as an authoritative success",
    ) &&
    nexusToriiRoutesTest.includes('mapOf("x-iroha-routed-by" to "proxy")') &&
    nexusToriiRoutesTest.includes('mapOf("x-iroha-routed-by" to "local")') &&
    nexusToriiRoutesTest.includes('mapOf("x-iroha-routed-by" to "Proxy")') &&
    nexusToriiRoutesTest.includes('"x-iroha-route-lane-id" to "4294967295"') &&
    nexusToriiRoutesTest.includes(
      '"x-iroha-route-dataspace-id" to "18446744073709551615"',
    ) &&
    nexusToriiRoutesTest.includes('"x-iroha-route-lane-id" to "4294967296"') &&
    nexusToriiRoutesTest.includes(
      '"x-iroha-route-dataspace-id" to "18446744073709551616"',
    ) &&
    nexusToriiRoutesTest.includes('(complete - "x-iroha-routed-by")') &&
    nexusToriiRoutesTest.includes(
      'headerValue = mapOf("x-iroha-routed-by" to "proxy")::get',
    ) &&
    nexusToriiRoutesTest.includes('"x-iroha-route-lane-id" to "1"') &&
    nexusToriiRoutesTest.includes('"x-iroha-route-dataspace-id" to "2"') &&
    nexusToriiRoutesTest.includes("requireFanout = true") &&
    [
      "x-iroha-fanout-routes-attempted",
      "x-iroha-fanout-routes-succeeded",
      "x-iroha-fanout-routes-failed",
      "x-iroha-fanout-routes-unavailable",
      "x-iroha-fanout-routes-denied",
      "x-iroha-fanout-routes-not-found",
    ].every((header) => nexusTorii.includes(header)),
  "NEXUS_TORII_HEALTH_CONTENT_NEGOTIATION_MISSING",
);
assert(
  nexusBalanceValidatorImplementation.includes("object NexusBalanceValidator") &&
    nexusBalanceValidatorImplementation.includes(
      "NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)",
    ) &&
    nexusBalanceValidatorImplementation.includes(
      "expectedAssetName = NexusAssetDefinitionIdentity.XOR_NAME",
    ) &&
    nexusBalanceValidatorImplementation.includes(
      "expectedAssetAlias = NexusToriiRoutes.XOR_ASSET_ALIAS",
    ) &&
    nexusBalanceValidatorImplementation.includes(
      "expectedAssetName == null ||",
    ) &&
    nexusBalanceValidatorImplementation.includes(
      "balance.assetName == expectedAssetName",
    ) &&
    nexusBalanceValidatorImplementation.includes(
      "expectedAssetAlias == null ||",
    ) &&
    nexusBalanceValidatorImplementation.includes(
      "balance.assetAlias == expectedAssetAlias",
    ) &&
    nexusBalanceValidatorImplementation.includes(
      '"NEXUS_BALANCE_IDENTITY_MISMATCH"',
    ) &&
    nexusXorBalanceImplementation.includes("resolveXorDefinition(network)") &&
    nexusXorBalanceImplementation.includes(
      "assetDefinitionId = xorDefinition.id",
    ) &&
    nexusXorBalanceImplementation.includes(
      "expectedAssetName = NexusAssetDefinitionIdentity.XOR_NAME",
    ) &&
    nexusXorBalanceImplementation.includes(
      "expectedAssetAlias = NexusToriiRoutes.XOR_ASSET_ALIAS",
    ) &&
    nexusExactAssetBalanceImplementation.includes(
      "NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)",
    ) &&
    nexusExactAssetBalanceImplementation.includes("getAllAccountAssets(") &&
    nexusExactAssetBalanceImplementation.includes(
      "assetDefinitionId = assetDefinitionId",
    ) &&
    nexusExactAssetBalanceImplementation.includes(
      "NexusBalanceValidator.exactAssetBalance(",
    ) &&
    nexusExactAssetBalanceImplementation.includes("expectedAssetName = null") &&
    nexusExactAssetBalanceImplementation.includes("expectedAssetAlias = null") &&
    !nexusExactAssetBalanceImplementation.includes("resolveXorDefinition") &&
    nexusBalanceValidatorTest.includes("6TEAJqbb8oEPmLncoNiMRbLEK6tw") &&
    nexusBalanceValidatorTest.includes("assetName") &&
    nexusBalanceValidatorTest.includes("assetAlias"),
  "NEXUS_XOR_BALANCE_CANONICAL_IDENTITY_GUARD_MISSING",
);
assert(
    nexusToriiRoutes.includes("fun accountTransactions(") &&
    nexusToriiRoutes.includes(
      "NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)",
    ) &&
    nexusToriiRoutes.includes(
      '"&asset_id=${queryValue(canonicalAssetSelector(assetDefinitionId))}"',
    ) &&
    nexusToriiRoutes.includes('"&count_mode=exact"') &&
    nexusAuthoritativeTransactionProof.includes(
      "NexusAssetDefinitionIdentity.hasCanonicalWireShape(assetDefinitionId)",
    ) &&
    nexusAuthoritativeTransactionProof.includes(
      "val proof = NexusAccountTransactionProof(",
    ) &&
    nexusAuthoritativeTransactionProof.includes(
      "pageSize = ACCOUNT_TRANSACTION_PAGE_SIZE",
    ) &&
    nexusAuthoritativeTransactionProof.includes("maxPages = MAX_PAGES") &&
    nexusAuthoritativeTransactionProof.includes("while (true)") &&
    nexusAuthoritativeTransactionProof.includes(
      "assetDefinitionId = assetDefinitionId",
    ) &&
    nexusAuthoritativeTransactionProof.includes("offset = proof.nextOffset") &&
    nexusAuthoritativeTransactionProof.includes("requireFanout = true") &&
    nexusAuthoritativeTransactionProof.includes(
      "when (proof.accept(page))",
    ) &&
    nexusAuthoritativeTransactionProof.includes(
      "NexusAccountTransactionProofResult.FOUND -> return true",
    ) &&
    nexusAuthoritativeTransactionProof.includes(
      "NexusAccountTransactionProofResult.ABSENT -> return false",
    ) &&
    nexusAccountTransactionProofImplementation.includes("page.total >= 0") &&
    nexusTorii.includes("val items: List<NexusAccountTransactionItem>,") &&
    nexusAccountTransactionProofImplementation.includes(
      'page.countMode == "exact"',
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "page.hasMore == (nextOffset + page.items.size.toLong() < page.total)",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "page.items.size <= pageSize",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "expectedTotal == page.total",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "nextOffset <= page.total",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "page.items.size.toLong() <= page.total - nextOffset",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "check(pagesAccepted < maxPages)",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "pagesAccepted += 1",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "nextOffset += page.items.size",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "if (nextOffset == page.total)",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "seenHashes.add(normalized)",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      "pageFingerprints.add(normalizedItems.joinToString",
    ) &&
    nexusAccountTransactionProofImplementation.includes("item.succeeded") &&
    nexusAccountTransactionProofImplementation.includes(
      "NexusToriiRoutes.canonicalAccount(network, authority) == account",
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      '"NEXUS_TRANSACTION_HISTORY_DUPLICATE_HASH"',
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      '"NEXUS_TRANSACTION_HISTORY_REPEATED_PAGE"',
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      '"NEXUS_TRANSACTION_HISTORY_EMPTY_PAGE"',
    ) &&
    nexusAccountTransactionProofImplementation.includes(
      '"NEXUS_TRANSACTION_HISTORY_PAGE_LIMIT_EXCEEDED"',
    ) &&
    nexusAccountTransactionProofTest.includes(
      "proof accepts only an exact successful transaction from the account authority",
    ) &&
    nexusAccountTransactionProofTest.includes(
      "proof rejects exact-count drift and impossible page boundaries",
    ) &&
    nexusAccountTransactionProofTest.includes(
      '"""{"total":0,"has_more":false,"count_mode":"exact"}"""',
    ) &&
    nexusAccountTransactionProofTest.includes('countMode = "bounded"') &&
    nexusAccountTransactionProofTest.includes("hasMore = true") &&
    nexusAccountTransactionProofTest.includes(
      "proof distinguishes repeated pages from duplicate hashes across different pages",
    ) &&
    nexusAccountTransactionProofTest.includes(
      "proof rejects empty continuation and fails closed at its page bound",
    ) &&
    nexusToriiRoutesTest.includes(
      "assetDefinitionId = NexusToriiRoutes.XOR_ASSET_ALIAS",
    ),
  "NEXUS_AUTHORITATIVE_ACCOUNT_TRANSACTION_PROOF_MISSING",
);
assert(
  nexusToriiRoutes.includes('"count_mode=exact"') &&
    nexusToriiRoutesTest.includes(
      "?limit=25&offset=50&count_mode=exact&scope=global",
    ) &&
    nexusTorii.includes("internal data class NexusAssetBalancePage(") &&
    nexusTorii.includes("val asset: String,") &&
    nexusTorii.includes("val items: List<NexusAssetBalance>,") &&
    nexusTorii.includes('val countMode: String') &&
    nexusTorii.includes('val total: Long') &&
    nexusTorii.includes("internal class NexusExactAssetPageProof(") &&
    nexusTorii.includes('page.countMode == "exact"') &&
    nexusTorii.includes("expectedTotal == page.total") &&
    nexusTorii.includes("page.hasMore == (nextOffset < page.total)") &&
    nexusTorii.includes("if (proof.accept(page)) return proof.items") &&
    nexusBalanceValidatorTest.includes(
      "asset pages require explicit stable exact counts and bounded termination",
    ) &&
    nexusBalanceValidatorTest.includes(
      '"""{"has_more":false,"count_mode":"exact","total":0}"""',
    ) &&
    nexusBalanceValidatorTest.includes(
      '"asset_id":"$XOR_DEFINITION","quantity":"1"',
    ) &&
    nexusBalanceValidatorTest.includes('countMode = "bounded"') &&
    nexusBalanceValidatorTest.includes('"NEXUS_PAGE_LIMIT_EXCEEDED"'),
  "NEXUS_EXACT_ASSET_PAGE_PROOF_MISSING",
);
assert(
  nexusTorii.includes('"iroha.instructions.list"') &&
    nexusTorii.includes("internal object NexusMcpResultContract") &&
    nexusMcpImplementation.includes("requestContentType = \"application/json\"") &&
    !nexusMcpImplementation.includes("requireFanout") &&
    /val result = response\.result[\s\S]*?val structuredResult = NexusMcpResultContract\.validateEmbeddedRoute\([\s\S]*?result = result,[\s\S]*?requireFanout = true,[\s\S]*?\)[\s\S]*?NexusTransferHistoryParser\.page\([\s\S]*?result = structuredResult/.test(
      nexusCommittedHistoryImplementation,
    ) &&
    nexusTorii.includes('direct["body"] != null || direct["items"] != null') &&
    nexusTorii.includes('structured["body"] !is JsonObject') &&
    nexusTorii.includes('structured["items"] != null') &&
    nexusTorii.includes("requireFanout: Boolean = false") &&
    nexusTorii.includes("requireFanout = requireFanout") &&
    nexusHistory.includes("BigDecimal") &&
    nexusHistory.includes("NexusQuantityContract::isWireQuantity") &&
    nexusHistory.includes("unsignedInteger") &&
    nexusTorii.includes("NEXUS_HISTORY_REPEATED_PAGE") &&
    nexusTorii.includes("val page = pageIndex + 1") &&
    nexusTorii.includes("parsedPage.requireBoundedSourceCount(HISTORY_PAGE_SIZE)") &&
    nexusHistory.includes("NEXUS_HISTORY_PAGE_SIZE_EXCEEDED") &&
    nexusHistory.includes("NEXUS_HISTORY_INVALID_BATCH_ASSET") &&
    nexusHistory.includes('"asset_definition"') &&
    nexusHistoryTest.includes(
      "batch history requires the exact XOR definition on every leg",
    ) &&
    nexusHistoryTest.includes(
      "MCP history requires a complete embedded route fanout proof",
    ) &&
    nexusHistoryTest.includes("includesStructuredShadowItems = true") &&
    nexusHistoryTest.includes("NexusTransferHistoryPage(emptyList(), 101)") &&
    nexusHistoryTest.includes('"x-iroha-routed-by" to JsonPrimitive("Proxy")'),
  "NEXUS_BOUNDED_EXACT_HISTORY_MISSING",
);
assert(
  nexusPendingOverlayValidator.includes("NEXUS_PENDING_SCOPE_INVALID") &&
    nexusPendingOverlayValidator.includes("NEXUS_PENDING_AMBIGUITY_INVALID") &&
    nexusPendingOverlayValidator.includes("NEXUS_PENDING_HASH_INVALID") &&
    nexusPendingOverlayValidator.includes("NEXUS_PENDING_TIMESTAMP_INVALID") &&
    nexusPendingOverlayValidator.includes("NEXUS_PENDING_ASSET_INVALID") &&
    nexusPendingOverlayValidator.includes(
      "NexusAssetDefinitionIdentity.hasCanonicalWireShape(transaction.assetId)",
    ) &&
    nexusPendingOverlayValidator.includes("sealed interface Projection") &&
    nexusPendingOverlayValidator.includes("fun projectForPortfolio(") &&
    nexusPendingOverlayValidator.includes("Projection.RecoveryRequired") &&
    nexusPendingOverlayValidator.includes(
      "internal object NexusPendingJournalPresentationContract",
    ) &&
    nexusPendingOverlayValidator.includes(
      "WalletIdentityDao.validatePendingTransaction(transaction)",
    ) &&
    nexusPendingOverlayValidator.includes(
      "(transaction.walletId to transaction.networkId) !in accountRoutes",
    ) &&
    /data class NexusPendingTransaction\([\s\S]{0,500}?val assetDefinitionId: String/.test(
      nexusPortfolioRepository,
    ) &&
    /assetDefinitionId\s*=\s*transaction\.assetId/.test(
      nexusPendingOverlayValidator,
    ) &&
    /val canonicalAssetDefinitionId\s*=\s*balance\.assetId \?: balance\.asset/.test(
      nexusPortfolioRepository,
    ) &&
    /\.filter\s*\{\s*it\.assetDefinitionId\s*==\s*canonicalAssetDefinitionId\s*}/.test(
      nexusPortfolioRepository,
    ) &&
    /\.filterNot\s*\{\s*it\.assetDefinitionId\s*==\s*canonicalAssetDefinitionId\s*}/.test(
      nexusPortfolioRepository,
    ) &&
    nexusPortfolioRepository.includes(
      "pendingTransactions = if (pendingRecoveryRequired)",
    ) &&
    /recoveryPendingTransactions\s*=\s*if \(pendingRecoveryRequired\)/.test(
      nexusPortfolioRepository,
    ) &&
    /assetDefinitionId\s*=\s*null[\s\S]{0,500}?pendingTransactions\s*=\s*emptyList\(\)/.test(
      nexusPortfolioRepository,
    ) &&
    /pendingTransactions\s*=\s*emptyList\(\)[\s\S]{0,500}?recoveryPendingTransactions\s*=\s*pending/.test(
      nexusPortfolioRepository,
    ) &&
    cardsHubScreen.includes("balance.recoveryPendingTransactions.forEach") &&
    cardsHubScreen.includes('append(" · pending asset recovery")') &&
    nexusPortfolioRepository.includes("errorCode = if (pendingRecoveryRequired)") &&
    nexusPortfolioRepository.includes('"NEXUS_PENDING_RECOVERY_REQUIRED"') &&
    nexusPortfolioRepository.includes(
      "NexusPendingJournalPresentationContract.requiresRecovery(",
    ) &&
    nexusPortfolioRepository.includes("networkAccounts = accounts") &&
    nexusPortfolioRepository.includes(
      "pending = if (pendingRecoveryRequired) emptyList() else pending",
    ) &&
    nexusPendingOverlayValidator.includes("fun portfolioAccess(recoveryRequired: Boolean)") &&
    nexusPendingOverlayValidator.includes("allowsQualifiedReads = true") &&
    nexusPendingOverlayValidator.includes("projectsPending = !recoveryRequired") &&
    nexusPendingOverlayValidator.includes("allowsSends = !recoveryRequired") &&
    nexusPortfolioRepository.includes(
      "val portfolioAccess =",
    ) &&
    nexusPortfolioRepository.includes(
      "val pending = if (!portfolioAccess.projectsPending)",
    ) &&
    nexusPortfolioRepository.includes("portfolioAccess.allowsSends &&") &&
    /check\(portfolioAccess\.allowsQualifiedReads\)[\s\S]{0,300}?val balance = torii\.getXorBalance\(/.test(
      nexusPortfolioRepository,
    ) &&
    nexusPortfolioRepository.includes("confirmedTransfers = history.items") &&
    /val pendingRecoveryRequired\s*=[\s\S]{0,3000}?val balance = torii\.getXorBalance\(/.test(
      nexusPortfolioRepository,
    ) &&
    !nexusPortfolioRepository.includes("pendingRecoveryBalance(") &&
    nexusPendingOverlayValidatorTest.includes(
      "recoveryAccess.projectsPending",
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      "recoveryAccess.allowsQualifiedReads",
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      "normalAccess.allowsQualifiedReads",
    ) &&
    nexusPendingOverlayValidatorTest.includes("recoveryAccess.allowsSends") &&
    cardsHubScreen.includes(
      'balance.errorCode == "NEXUS_PENDING_RECOVERY_REQUIRED"',
    ) &&
    cardsHubScreen.includes("pending_transaction_recovery_required") &&
    nexusPendingOverlayValidatorTest.includes(
      "pending overlay is bound to exact wallet network and durable state",
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      "ambiguous overlay is valid only in unknown state",
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      "corrupt durable pending batch becomes recovery evidence without unsafe projection",
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      "shared pending journal never hides an invalid domain or overflow row",
    ) &&
    nexusPendingOverlayValidatorTest.includes('pending.copy(amount = "0")') &&
    nexusPendingOverlayValidatorTest.includes('networkId = "unknown"') &&
    nexusPendingOverlayValidatorTest.includes("networkAccounts = emptyList()") &&
    nexusPendingOverlayValidatorTest.includes("Projection.RecoveryRequired") &&
    nexusPendingOverlayValidatorTest.includes("6TEAJqbb8oEPmLncoNiMRbLEK6tw"),
  "NEXUS_PENDING_OVERLAY_SCOPE_GUARD_MISSING",
);
assert(
  nexusNetwork.includes("WalletNetworkChainIdentity") &&
    nexusNetwork.includes("fc56984b-2be7-431d-840e-21514d1883f0") &&
    nexusPendingReconciler.indexOf(
      "check(WalletIdentityDao.hasCurrentChainIdentity(pending))",
    ) >= 0 &&
    nexusPendingReconciler.indexOf(
      "check(WalletIdentityDao.hasCurrentChainIdentity(pending))",
    ) <
      nexusPendingReconciler.indexOf("torii.transactionStatus(network, hash)") &&
    nexusPendingOverlayValidator.includes("expectedChainId: String") &&
    nexusPendingOverlayValidator.includes("transaction.chainId == expectedChainId") &&
    nexusPendingOverlayValidatorTest.includes(
      "unbound and retired chain rows project only as recovery evidence",
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      "Taira current identity requires exact manifest namespace for either mapping",
    ) &&
    nexusCoordinatorTest.includes(
      "unbound and retired Taira journals remain immutable without Torii calls",
    ) &&
    nexusCoordinatorTest.includes(
      "Taira cannot sign or journal without an admitted deployment manifest",
    ) &&
    nexusCoordinatorTest.includes(
      "coVerify(exactly = 0) { torii.transactionStatus(any(), any()) }",
    ) &&
    nexusCoordinator.includes(
      "private suspend fun requirePendingChainRecoveryClear()",
    ) &&
    nexusCoordinator.includes("val localId = durablePendingLocalId(network)") &&
    nexusCoordinator.includes('return "${binding.pendingJournalPrefix}$nonce"') &&
    nexusPendingReconciler.includes(
      "check(WalletIdentityDao.hasCurrentChainIdentity(current))",
    ) &&
    (nexusCoordinator.match(/requirePendingChainRecoveryClear\(\)/g) ?? [])
      .length >= 7 &&
    nexusCoordinator.indexOf("requirePendingChainRecoveryClear()") <
      nexusCoordinator.indexOf("val account = requireActiveAccount(") &&
    /WalletRecoveryCapabilityGate\.requireUserMutationAllowed\(\)\s+requirePendingChainRecoveryClear\(\)\s+val localId = durablePendingLocalId\(network\)\s+val signed = signer\.sign/.test(
      nexusCoordinator,
    ) &&
    /requirePendingChainRecoveryClear\(\)\s+transportStarted = true\s+torii\.submit/.test(
      nexusCoordinator,
    ) &&
    nexusCoordinatorTest.includes(
      "historical chain evidence blocks prepare before Torii quote or signing",
    ) &&
    nexusCoordinatorTest.includes(
      "coEvery { dao.countPendingTransactionsRequiringChainRecovery() } returns 1",
    ) &&
    nexusCoordinatorTest.includes(
      "coVerify(exactly = 0) { signer.sign(any(), any()) }",
    ) &&
    nexusCoordinatorTest.includes(
      "coVerify(exactly = 0) { torii.submit(any(), any()) }",
    ),
  "NEXUS_PENDING_CHAIN_IDENTITY_GUARD_MISSING",
);
assert(
  [
    nexusBalanceValidatorTest,
    nexusCoordinatorTest,
    cardsHubViewModelTest,
  ].every(
    (source) =>
      source.includes("6TEAJqbb8oEPmLncoNiMRbLEK6tw") &&
      !directAliasAsAssetIdentityFixture.test(source),
  ) &&
    nexusBalanceValidatorTest.includes(
      'const val XOR_DEFINITION = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"',
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      'const val XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"',
    ) &&
    nexusCoordinatorTest.includes(
      'const val XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"',
    ) &&
    cardsHubViewModelTest.includes(
      'const val CANONICAL_XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"',
    ) &&
    nexusPendingValidFixture.includes("assetId = XOR_DEFINITION_ID") &&
    !directAliasAsAssetIdentityFixture.test(nexusPendingValidFixture) &&
    nexusBalanceValidatorTest.includes(
      "assetDefinitionId = NexusToriiRoutes.XOR_ASSET_ALIAS",
    ) &&
    nexusPendingOverlayValidatorTest.includes(
      'pending.copy(assetId = "xor#universal")',
    ),
  "NEXUS_ANDROID_CANONICAL_ASSET_TEST_FIXTURES_MISSING",
);
assert(
  assetsInteractor.includes("subscribeAssetOfAccount") &&
    cardsHubViewModel.includes("Sora2PortfolioBalance") &&
    cardsHubViewModel.includes("subscribeAssetOfAccount(") &&
    cardsHubViewModel.includes("SubstrateOptionsProvider.feeAssetId") &&
    cardsHubViewModel.includes(
      "Replace the previous wallet row before waiting for",
    ) &&
    cardsHubViewModel.includes("error is CancellationException") &&
    cardsHubViewModel.includes(".stripTrailingZeros()") &&
    cardsHubViewModel.includes(".toPlainString()") &&
    cardsHubScreen.includes(
      "sora2Portfolio != null || nexusPortfolio.isNotEmpty()",
    ) &&
    cardsHubScreen.includes('text = "SORA2"') &&
    cardsHubScreen.includes("R.string.network_badge_mainnet") &&
    cardsHubScreen.includes("onSora2Receive") &&
    cardsHubScreen.includes("onSora2Open") &&
    cardsHubViewModelTest.includes(
      "SORA2 portfolio keeps XOR row when XOR is not favorite",
    ) &&
    cardsHubViewModelTest.includes(
      "SORA2 account switch never pairs address with another wallet balance",
    ),
  "UNIFIED_SORA2_PORTFOLIO_ROW_MISSING",
);
assert(
  /private\s+val\s+sendQualification\s*:\s*NexusSendQualification/.test(
    strippedNexusPortfolioRepository,
  ) &&
    strippedNexusPortfolioRepository.includes(
      "sendQualification.isQualifiedFor(network)",
    ) &&
    !/\bNexusTransactionSigner\b/.test(strippedNexusPortfolioRepository) &&
    !/\bNexusFinalityReader\b/.test(strippedNexusPortfolioRepository) &&
    !/\b(?:quote|sign|finalizedCheckpoint)\s*\(/.test(
      strippedNexusPortfolioRepository,
    ) &&
    nexusCoordinator.includes("private val signer: NexusTransactionSigner") &&
    nexusCoordinator.includes("private val finalityReader: NexusFinalityReader") &&
    nexusPendingReconciler.includes(
      "private val finalityReader: NexusFinalityReader",
    ) &&
    !/private\s+val\s+signer\s*:\s*NexusTransactionSigner/.test(
      nexusPendingReconciler,
    ),
  "NEXUS_PORTFOLIO_SIGNING_CAPABILITY_LEAK",
);
assert(
  nexusNetwork.includes('displayName = "Minamoto"') &&
    nexusNetwork.includes('displayName = "Taira Testnet"') &&
    nexusNetwork.includes("isTestnet = true") &&
    nexusNetwork.includes("enabledByDefault = true") &&
    nexusPortfolioRepository.includes(
      "observeWallet(wallet.substrateAddress).onStart",
    ) &&
    nexusPortfolioRepository.includes(
      "Account selection is a privacy boundary",
    ) &&
    nexusPortfolioRepository.includes("walletId = walletId") &&
    nexusPortfolioRepository.includes(
      "it.networkId == account.networkId",
    ) &&
    nexusPortfolioRepository.includes(
      "val parsedAccount = IrohaAddressCodec.parse(",
    ) &&
    nexusPortfolioRepository.includes(
      "network.chainDiscriminant",
    ) &&
    nexusPortfolioRepository.includes(
      "sendQualification.isQualifiedFor(network)",
    ) &&
    cardsHubViewModel.includes("_nexusSend.value = NexusSendUiState()") &&
    cardsHubViewModel.includes(
      "val walletId = network.walletId",
    ) &&
    cardsHubViewModel.includes(
      "selectedPortfolioWalletId = account.substrateAddress",
    ) &&
    cardsHubViewModel.includes(
      "expected.walletId != selectedPortfolioWalletId",
    ) &&
    cardsHubViewModel.includes(
      "currentNexusBalance(boundNetwork)?.sendAvailable != true",
    ) &&
    cardsHubViewModel.includes("nexusSendOperation?.cancel()") &&
    cardsHubViewModel.includes(
      "prepared.request.walletId != network.walletId",
    ) &&
    cardsHubViewModel.includes(
      "if (isCurrentSora2Wallet(walletId)) router.openQrCodeFlow()",
    ) &&
    cardsHubScreen.includes("remember(sora2?.address)") &&
    cardsHubScreen.includes(
      "val scopedBalances = balances.filter { it.walletId == sora2?.address }",
    ) &&
    cardsHubScreen.includes("LaunchedEffect(scopedBalances)") &&
    cardsHubScreen.includes(
      "receiveBalance?.takeIf(isNexusBalanceCurrent)",
    ) &&
    cardsHubScreen.includes(
      "if (isNexusBalanceCurrent(balance))",
    ) &&
    cardsHubScreen.includes(
      "if (isSora2WalletCurrent(balance.address))",
    ) &&
    cardsHubScreen.includes("R.string.network_badge_testnet") &&
    cardsHubScreen.includes("clipboard.setText(AnnotatedString(balance.address))") &&
    cardsHubScreen.includes("uriHandler.openUri(balance.explorerBaseUrl)") &&
    cardsHubScreen.includes("balance.pendingTransactions.forEach") &&
    cardsHubScreen.includes("balance.confirmedTransfers.take(5).forEach") &&
    cardsHubScreen.includes("var historyBalance by remember(sora2?.address)") &&
    cardsHubScreen.includes(
      "key(balance.walletId, balance.networkId, balance.address)",
    ) &&
    cardsHubScreen.includes("LazyColumn(") &&
    cardsHubScreen.includes(".heightIn(max = 480.dp)") &&
    cardsHubScreen.includes("items = balance.confirmedTransfers") &&
    cardsHubScreen.includes("historyBalance?.takeIf(isNexusBalanceCurrent)") &&
    cardsHubScreen.includes("R.string.network_view_full_history") &&
    cardsHubScreen.includes("balance.historyErrorCode == null") &&
    cardsHubScreen.includes("expanded = true") &&
    cardsHubScreen.includes("val counterparty = if (outgoing)") &&
    cardsHubScreen.includes("transfer.transactionHash,") &&
    cardsHubScreen.includes("transfer.timestampMillis,") &&
    commonStrings.includes('name="network_view_full_history"') &&
    commonStrings.includes('name="network_history_counterparty"') &&
    commonStrings.includes('name="network_history_transaction_hash"') &&
    commonStrings.includes('name="network_history_timestamp_millis"') &&
    cardsHubViewModelTest.includes(
      "account switch dismisses previous wallet Nexus confirmation",
    ) &&
    cardsHubViewModelTest.includes(
      "Nexus send entry rejects a row from another wallet",
    ) &&
    cardsHubViewModelTest.includes(
      "Nexus preparation uses immutable row wallet while cards state lags",
    ) &&
    cardsHubViewModelTest.includes(
      "account switch cancels in-flight Nexus preparation without stale UI write",
    ) &&
    cardsHubViewModelTest.includes(
      "removing Taira row invalidates receive and send actions",
    ) &&
    cardsHubViewModelTest.includes(
      "SORA2 actions reject a stale wallet row at tap time",
    ) &&
    tairaVisibilityPolicyTest.includes(
      "remote value is only a default before an explicit choice",
    ) &&
    tairaVisibilityPolicyTest.includes(
      "explicit Taira choice overrides later remote defaults",
    ) &&
    tairaVisibilityPolicyTest.includes(
      "Nexus kill switch hides Taira without erasing explicit choice",
    ) &&
    tairaVisibilityPolicyTest.includes(
      "active never-chosen visibility follows each complete remote snapshot",
    ) &&
    tairaVisibilityPolicyTest.includes(
      "explicit choice survives later complete remote snapshots",
    ) &&
    tairaVisibilityPolicyTest.includes(
      "missing cache keeps tester default but interrupted cache hides Taira",
    ) &&
    nexusPortfolioRepository.includes(
      "featureManager.observeTairaVisible()",
    ) &&
    !nexusPortfolioRepository.includes(
      "featureManager.observeTairaVisible(featureState.tairaVisible)",
    ) &&
    nexusPortfolioTairaVisibilityTest.includes(
      "hidden Taira is removed before any balance history or send qualification call",
    ) &&
    nexusPortfolioTairaVisibilityTest.includes(
      "coVerify(exactly = 0) { torii.getXorBalance(any(), any()) }",
    ) &&
    nexusPortfolioTairaVisibilityTest.includes(
      "coVerify(exactly = 0) { torii.committedXorTransfers(any(), any(), any()) }",
    ),
  "UNIFIED_NEXUS_NETWORK_SCOPE_OR_ACCOUNT_SWITCH_CLEAR_MISSING",
);
assert(
  nexusPendingReconciler.includes('"COMMITTED_PENDING_RECONCILIATION"') &&
    nexusPendingReconciler.includes("committedXorTransfers") &&
    nexusPendingReconciler.includes("transfer.transactionHash") &&
    nexusPendingReconciler.includes("transfer.receiver") &&
    nexusPendingReconciler.includes("exactHistoryMatches != 1") &&
    nexusPendingReconciler.includes("NEXUS_COMMITTED_HEIGHT_MISSING") &&
    nexusPendingReconciler.includes("committedAt > 0L") &&
    nexusPendingReconciler.includes(
      "finalityReader.finalizedCheckpoint(network).requireFor(network)",
    ) &&
    nexusCoordinator.includes("entrypointHash != staged.transactionHash") &&
    nexusPendingReconciler.includes("status.hasAuthoritativeGlobalResolution") &&
    nexusPendingReconciler.includes("NEXUS_STATUS_RESOLUTION_INVALID") &&
    nexusTorii.includes("val hasAuthoritativeGlobalResolution") &&
    nexusToriiRoutesTest.includes(
      "terminal pipeline status requires authoritative global state",
    ) &&
    nexusToriiRoutesTest.includes(
      "applied pipeline status requires state and a positive block",
    ) &&
    nexusToriiRoutesTest.includes(
      "committed pipeline status requires state positive block and no rejection",
    ) &&
    nexusFinalityCheckpointTest.includes(
      "checkpoint is bound to exact wallet network and chain uuid",
    ) &&
    nexusFinalityCheckpointTest.includes(
      "zero finalized checkpoint is never authoritative",
    ) &&
    nexusFinalityCheckpointTest.includes(
      "height without a canonical nonzero finalized block hash is never authoritative",
    ) &&
    nexusTransactionSignerSource.includes("val finalizedBlockHash: String") &&
    nexusTransactionSignerSource.includes(
      "NexusTransactionHash.normalized(finalizedBlockHash) == finalizedBlockHash",
    ) &&
    !nexusTransactionSignerSource.includes("FINALIZED_BLOCK_HASH") &&
    nexusFinalityCheckpointTest.includes(
      "archived pre v2 Taira chain cannot satisfy current Taira finality",
    ) &&
    nexusReconcileImplementation.includes(
      "NexusAssetDefinitionIdentity.hasCanonicalWireShape(pending.assetId)",
    ) &&
    !nexusReconcileImplementation.includes("resolveXorDefinition") &&
    !nexusReconcileImplementation.includes("getXorBalance") &&
    /hasAuthoritativeCommittedTransaction\([\s\S]*?assetDefinitionId\s*=\s*pending\.assetId[\s\S]*?transactionHash\s*=\s*hash[\s\S]*?NEXUS_TRANSACTION_HISTORY_PROOF_MISSING[\s\S]*?getAssetBalanceByDefinition\([\s\S]*?assetDefinitionId\s*=\s*pending\.assetId[\s\S]*?committedXorTransfers\([\s\S]*?assetDefinitionId\s*=\s*pending\.assetId/.test(
      nexusReconcileImplementation,
    ) &&
    nexusCoordinatorTest.includes("entrypointHash = hash") &&
    nexusCoordinatorTest.includes(
      "duplicate exact transfer history cannot finalize a committed send",
    ) &&
    nexusCoordinatorTest.includes(
      "finality checkpoint from another chain cannot finalize a committed send",
    ) &&
    nexusCoordinator.includes("signedTransactionHash"),
  "NEXUS_FINALITY_HISTORY_RECONCILIATION_MISSING",
);
assert(
  nexusPendingRecoveryWorker.includes("@HiltWorker") &&
    nexusPendingRecoveryWorker.includes("private val recovery: NexusPendingRecovery") &&
    nexusPendingRecoveryWorker.includes("MAX_TRANSACTIONS_PER_RUN = 10") &&
    nexusPendingRecoveryWorker.includes("NetworkType.CONNECTED") &&
    nexusPendingRecoveryWorker.includes("BackoffPolicy.EXPONENTIAL") &&
    nexusPendingRecoveryWorker.includes("ensureOnStartup()") &&
    nexusPendingRecoveryWorker.includes("ExistingWorkPolicy.KEEP") &&
    nexusPendingRecoveryWorker.includes("kickAfterJournal()") &&
    nexusPendingRecoveryWorker.includes("ExistingWorkPolicy.APPEND_OR_REPLACE") &&
    nexusPendingRecoveryWorker.includes("pass.failures > 0 || pass.unresolved > 0") &&
    nexusPendingRecoveryWorker.includes("scheduler.continueAfterBoundedBatch()") &&
    nexusPendingRecoveryWorker.includes("runAttemptCount.toLong() * MAX_TRANSACTIONS_PER_RUN") &&
    !nexusPendingRecoveryWorker.includes("torii.submit") &&
    !nexusPendingRecoveryWorker.includes("signer.sign") &&
    !nexusPendingReconciler.includes("torii.submit") &&
    !nexusPendingReconciler.includes("signer.sign") &&
    nexusToriiReadInterface.includes("interface NexusToriiReadClient") &&
    nexusToriiReadInterface.includes("suspend fun getXorBalance(") &&
    nexusToriiReadInterface.includes("suspend fun transactionStatus(") &&
    nexusToriiReadInterface.includes(
      "suspend fun hasAuthoritativeCommittedTransaction(",
    ) &&
    nexusToriiReadInterface.includes(
      "suspend fun getAssetBalanceByDefinition(",
    ) &&
    nexusToriiReadInterface.includes("suspend fun committedXorTransfers(") &&
    !/\bsubmit\s*\(/.test(nexusToriiReadInterface) &&
    !/\bsign\s*\(/.test(nexusToriiReadInterface) &&
    !nexusToriiReadInterface.includes("NexusSignedTransaction") &&
    nexusPortfolioRepository.includes(
      "private val torii: NexusToriiReadClient",
    ) &&
    !nexusPortfolioRepository.includes(
      "private val torii: NexusToriiClient",
    ) &&
    nexusPendingReconciler.includes(
      "private val torii: NexusToriiReadClient",
    ) &&
    !nexusPendingReconciler.includes(
      "private val torii: NexusToriiClient",
    ) &&
    signerBinding.includes("fun provideNexusToriiReadClient(") &&
    signerBinding.includes("): NexusToriiReadClient = client") &&
    nexusCoordinator.includes("private val torii: NexusToriiClient") &&
    nexusCoordinator.includes(
      "torii.submit(network, staged.signed.noritoBytes)",
    ) &&
    soraApplication.includes("nexusPendingRecoveryScheduler.ensureOnStartup()") &&
    nexusCoordinator.includes("pendingRecoveryScheduler.kickAfterJournal()") &&
    nexusPendingReconciler.includes(
      "remainingUnattempted = candidates.size - attempted.size",
    ) &&
    nexusCoordinatorTest.includes(
      "restart recovery rotates bounded passes so failed exact hashes cannot starve later rows",
    ) &&
    nexusCoordinatorTest.includes(
      "empty restart recovery pass terminates durable work",
    ) &&
    nexusPendingReconciler.includes("ConcurrentHashMap.newKeySet<String>()") &&
    nexusPendingReconciler.includes("activeLocalIds.add(localId)") &&
    nexusPendingReconciler.includes("submissionActivity.isActive(localId)") &&
    nexusCoordinator.includes("submissionActivity.release(staged.localId)") &&
    /private suspend fun update\([\s\S]*?WalletMutationCoordinator\.withLock \{[\s\S]*?WalletRecoveryCapabilityGate\.requireUserMutationAllowed\(\)/.test(
      nexusPendingReconciler,
    ) &&
    nexusCoordinatorTest.includes(
      "live signed journal cannot be reconciled before submit handoff completes",
    ) &&
    nexusCoordinatorTest.includes(
      "coVerify(exactly = 0) { torii.transactionStatus(any(), any()) }",
    ),
  "NEXUS_DURABLE_STATUS_ONLY_RECOVERY_MISSING",
);
assert(
  nexusCoordinator.includes("WalletMutationCoordinator.withLock") &&
    (nexusCoordinator.match(/requireSendEnabled\([^)]*request\.networkId\)/g) ?? [])
      .length >= 4 &&
    nexusCoordinator.includes(
      "networkId != WalletNetworkId.TAIRA || state.tairaVisible",
    ) &&
    nexusCoordinator.includes("val finalQuote = signer.quote") &&
    nexusCoordinator.includes("canonicalPositiveFee(quote.fee)") &&
    nexusCoordinator.includes("canonicalPositiveFee(finalQuote.fee)") &&
    nexusCoordinator.includes("val finalBalance = canonicalNonNegativeQuantity") &&
    nexusCoordinator.includes("signer.sign(prepared.signingRequest, finalQuote)") &&
    nexusCoordinator.includes("insertPendingTransaction") &&
    nexusCoordinator.includes("prepared.reserveSubmission()") &&
    nexusCoordinator.includes("NEXUS_CONFIRMATION_ALREADY_SUBMITTED") &&
    nexusCoordinatorTest.includes(
      "signing and pending journal are locked and confirmation is one shot",
    ) &&
    nexusCoordinatorTest.includes(
      "prepare rejects a zero fee before signing or submission",
    ) &&
    nexusCoordinatorTest.includes("competingMutationEntered") &&
    nexusCoordinatorTest.includes("ambiguous submission keeps exact hash and cannot be retried") &&
    nexusCoordinator.includes("var transportStarted = false") &&
    nexusCoordinator.includes("val transportXorDefinition = torii.resolveXorDefinition(") &&
    nexusCoordinator.includes("transportXorDefinition.id == prepared.assetDefinitionId") &&
    /val receipt = WalletMutationCoordinator\.withLock \{[\s\S]*?featureManager\.getState\(\)[\s\S]*?requireSendEnabled\(transportState, request\.networkId\)[\s\S]*?signer\.isQualifiedFor\(network\)[\s\S]*?validatePreparedContract\(prepared, network\)[\s\S]*?requireActiveAccount\([\s\S]*?NEXUS_ACCOUNT_CHANGED[\s\S]*?WALLET_DELETION_ACTIVE[\s\S]*?transportStarted = true[\s\S]*?torii\.submit/.test(
      nexusCoordinator,
    ) &&
    nexusCoordinator.includes(
      'state = if (transportStarted) "UNKNOWN" else "REJECTED"',
    ) &&
    nexusCoordinator.includes("ambiguous = transportStarted") &&
    nexusCoordinatorTest.includes(
      "emergency disablement before Torii handoff is definitively not submitted",
    ) &&
    nexusCoordinatorTest.includes(
      "Taira send is rejected when test networks are hidden",
    ) &&
    nexusCoordinatorTest.includes(
      "coVerify(exactly = 0) { torii.submit(any(), any()) }",
    ),
  "NEXUS_SIGNING_PENDING_DELETION_RACE_GUARD_MISSING",
);
assert(
  nexusPendingReconciler.includes("val ALLOWED_TRANSITIONS") &&
    nexusPendingReconciler.includes("requireSelected = false") &&
    nexusPendingReconciler.includes("NEXUS_NETWORK_ACCOUNT_KEY_MISMATCH") &&
    nexusPendingReconciler.includes(
      "NEXUS_NETWORK_ACCOUNT_DERIVATION_MISMATCH",
    ) &&
    nexusPendingReconciler.includes("NEXUS_MASTER_PHRASE_REQUIRED") &&
    nexusPendingReconciler.includes('pending.state == "SIGNED"') &&
    nexusCoordinatorTest.includes("stale status cannot regress finality") &&
    nexusCoordinatorTest.includes("recovery does not require wallet selection") &&
    nexusCoordinatorTest.includes("signed restart record becomes ambiguous") &&
    nexusCoordinatorTest.includes("stored I105 address and public key mismatch") &&
    nexusTorii.includes("submissionMayHaveReachedTorii = true") &&
    nexusTorii.includes("A malformed or truncated receipt"),
  "NEXUS_ONE_SHOT_MONOTONIC_RECOVERY_GUARDS_MISSING",
);
assert(
  irohaAddressCodec.includes("MAX_ADDRESS_BYTES = 512") &&
    irohaAddressCodec.includes("MAX_ADDRESS_CHARACTERS = 160") &&
    irohaKeyDerivationTest.includes(
      "address validation rejects unbounded input before base conversion",
    ),
  "IROHA_I105_UNBOUNDED_INPUT_GUARD_MISSING",
);
assert(
  sora2PendingSubmissionCoordinator.includes(
    "SORA2_RECOVERY_DUPLICATE_INCLUSION",
  ) &&
    sora2PendingSubmissionCoordinator.includes(
      "SORA2_RECOVERY_SYSTEM_EVENT_AMBIGUOUS",
    ) &&
    sora2PendingSubmissionCoordinator.includes("STATE_FINALIZED_SUCCESS") &&
    sora2PendingSubmissionCoordinator.includes("STATE_FINALIZED_FAILURE") &&
    polkamarktTrader.includes("getSora2PendingSubmission(") &&
    polkamarktTrader.includes("POLKAMARKT_RECOVERY_WITNESS_MISMATCH") &&
    polkamarktTrader.includes("authoritativeMechanism") &&
    polkamarktTrader.includes("authoritativeCloseBlock"),
  "POLKAMARKT_AUTHORITATIVE_FINALITY_OR_QUOTE_BINDING_MISSING",
);
assert(
  polkamarktRuntimeDtos.includes("const val MAX_VALUE: Long = 4_294_967_295L") &&
    polkamarktRuntimeDtos.includes("fun toScale(value: Long): BigInteger") &&
    (
      polkamarktRuntimeDtos.match(/val marketId: Long/g) ?? []
    ).length >= 5 &&
    /suspend fun quoteBuy\([\s\S]*?requirePendingRecoveryClear\(expectedAccountId\)[\s\S]*?quoteBuyForAccount/.test(
      polkamarktTrader,
    ) &&
    /suspend fun quoteSell\([\s\S]*?requirePendingRecoveryClear\(expectedAccountId\)[\s\S]*?quoteSellForAccount/.test(
      polkamarktTrader,
    ) &&
    !polkamarktRuntimeDtos.includes("val marketId: Int") &&
    polkamarktExtrinsics.includes("marketIds: List<Long>") &&
    (
      polkamarktExtrinsics.match(/marketId: Long/g) ?? []
    ).length >= 4 &&
    polkamarktExtrinsics.includes("PolkamarktMarketId.toScale") &&
    !polkamarktExtrinsics.includes("marketId.toBigInteger()") &&
    (
      polkamarktCalls.match(/marketId: Long/g) ?? []
    ).length >= 5 &&
    polkamarktCalls.includes("PolkamarktMarketId.requireValid") &&
    polkamarktCalls.includes("PolkamarktMarketId.toScale(canonicalMarketId)") &&
    (
      piIndexerModels.match(/val marketId: Long\?/g) ?? []
    ).length >= 4 &&
    !piIndexerModels.includes("val marketId: Int?") &&
    (
      piIndexerClient.match(/val marketId: Long\? = null/g) ?? []
    ).length >= 4 &&
    piIndexerClient.includes(
      "fun fieldFilter(field: String, operator: String, value: Long)",
    ) &&
    piIndexerClient.includes("0L..PolkamarktMarketId.MAX_VALUE") &&
    piIndexerClientTest.includes(
      "polkamarkt market ids preserve full runtime u32 range",
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt close blocks preserve only the runtime u32 range",
    ) &&
    piIndexerClientTest.includes(
      "serializable market rejects cached close block overflow",
    ) &&
    piIndexerClientTest.includes("PolkamarktMarket.serializer()") &&
    piIndexerModels.includes(
      'PolkamarktMarketId.requireValid(it, "PI_INDEXER_INVALID_CLOSE_BLOCK")',
    ) &&
    piIndexerClientTest.includes(
      "polkamarkt batch trade history preserves every claimed market",
    ) &&
    piIndexerClientTest.includes(
      "assertThat(legacyCachedTrade.marketIds).isEmpty()",
    ) &&
    piIndexerModels.includes("val marketIds: List<Long>") &&
    piIndexerClient.includes("marketIds.size <= MAX_POLKAMARKT_BATCH_MARKETS") &&
    piIndexerClient.includes("marketIds.distinct().size == marketIds.size") &&
    piIndexerClient.includes(
      "marketIds.isEmpty() || marketIds.first() == marketId",
    ) &&
    polkamarktScreen.includes("trade.marketIds.size > 1") &&
    piIndexerClient.includes(
      "closeBlock in 0L..PolkamarktMarketId.MAX_VALUE",
    ) &&
    piIndexerClientTest.includes("4294967295") &&
    polkamarktTrader.includes("marketIds: List<Long>") &&
    !polkamarktTrader.includes("marketIds: List<Int>") &&
    !polkamarktTrader.includes("marketId: Int") &&
    polkamarktViewModel.includes("val marketId: Long") &&
    polkamarktViewModel.includes("val marketIds: List<Long>") &&
    !polkamarktViewModel.includes("marketId.toInt") &&
    polkamarktScreen.includes("val reviewedPayoutMarketIds: List<Long>") &&
    !polkamarktScreen.includes("marketId.toInt") &&
    polkamarktMarketIdTest.includes(
      "runtime u32 maximum is preserved as exact scale integer",
    ) &&
    polkamarktClaimValidatorTest.includes(
      "market identity accepts full u32 range and rejects overflow",
    ),
  "POLKAMARKT_U32_MARKET_ID_CONTRACT_MISSING",
);
assert(
  polkamarktTrader.includes("internal object PolkamarktClaimValidator") &&
    polkamarktTrader.includes("POLKAMARKT_CLAIM_ACCOUNT_MISMATCH") &&
    polkamarktTrader.includes("POLKAMARKT_CLAIM_MARKET_MISMATCH") &&
    polkamarktTrader.includes("POLKAMARKT_STATUS_NOT_CLAIMABLE") &&
    polkamarktTrader.includes(
      "POLKAMARKT_TRADER_PAYOUT_NOT_CLAIMABLE",
    ) &&
    polkamarktTrader.includes(
      "POLKAMARKT_CREATOR_FEES_NOT_CLAIMABLE",
    ) &&
    polkamarktTrader.includes("POLKAMARKT_DUPLICATE_CLAIM_MARKET") &&
    polkamarktTrader.includes(
      "internal object PolkamarktMutationAccountValidator",
    ) &&
    polkamarktTrader.includes("val accountId: String") &&
    polkamarktTrader.includes("requireConfirmedQuoteIdentity") &&
    polkamarktTrader.includes("quoteBuyForAccount") &&
    polkamarktTrader.includes("quoteSellForAccount") &&
    polkamarktTrader.includes(
      "internal object PolkamarktMutationSubmissionCoordinator",
    ) &&
    polkamarktTrader.includes("withQuoteAdmission") &&
    (
      polkamarktTrader.match(
        /PolkamarktMutationSubmissionCoordinator\.withQuoteAdmission/g,
      ) ?? []
    ).length >= 2 &&
    polkamarktTrader.includes("internal object PolkamarktMutationAdmissionGate") &&
    (
      polkamarktTrader.match(
        /PolkamarktMutationAdmissionGate\.withAdmission/g,
      ) ?? []
    ).length >= 5 &&
    polkamarktMutationSubmissionCoordinatorTest.includes(
      "app wide admission rejects a second screen and releases after completion",
    ) &&
    polkamarktMutationSubmissionCoordinatorTest.includes(
      "app wide admission lease releases on cancellation",
    ) &&
    polkamarktTrader.includes(
      "internal object PolkamarktPendingMutationGate",
    ) &&
    polkamarktTrader.includes("POLKAMARKT_PENDING_RECOVERY_REQUIRED") &&
    /val walletDao = database\.walletIdentityDao\(\)[\s\S]*?countPendingTransactionsRequiringChainRecovery\(\) == 0[\s\S]*?walletDao\.observePendingTransactions\(expectedAccountId\)/.test(
      polkamarktTrader,
    ) &&
    (
      polkamarktTrader.match(
        /requirePendingRecoveryClear\(expectedAccountId(?:, allowedCurrentLocalId)?\)/g,
      ) ?? []
    ).length >= 5 &&
    (
      polkamarktTrader.match(
        /PolkamarktMutationSubmissionCoordinator\.withNonceReservedUntilTransport/g,
      ) ?? []
    ).length >= 3 &&
    polkamarktTrader.includes("PENDING_POLKAMARKT_TRANSACTION_UNRESOLVED") &&
    polkamarktTrader.includes(
      'polkamarktRecords.single().localId == allowedCurrentLocalId',
    ) &&
    polkamarktMutationSubmissionCoordinatorTest.includes(
      "queued mutation cannot prepare the same nonce before first transport handoff",
    ) &&
    polkamarktMutationSubmissionCoordinatorTest.includes(
      "assertEquals(listOf(7, 8), preparedNonces)",
    ) &&
    polkamarktMutationSubmissionCoordinatorTest.includes(
      "assertEquals(2, preparedHashes.distinct().size)",
    ) &&
    (
      polkamarktTrader.match(
        /quoteIdentity\(\s*confirmationNonce,\s*account\.substrateAddress,/g,
      ) ?? []
    ).length >= 2 &&
    polkamarktTrader.includes("quote.confirmationNonce,") &&
    (
      polkamarktTrader.match(
        /preTransportValidation = \{[\s\S]*?requireMutationPreTransport\([\s\S]*?signingRuntime = signingRuntime,[\s\S]*?allowedCurrentLocalId = localId,[\s\S]*?preTransportValidationCompleted = true/g,
      ) ?? []
    ).length >= 2 &&
    (
      polkamarktTrader.match(
        /requireMutationPreTransport\([\s\S]*?signingRuntime = signingRuntime,[\s\S]*?allowedCurrentLocalId = localId,[\s\S]*?markPendingSubmitted\(localId, prepared\.transactionHash\)[\s\S]*?preTransportValidationCompleted = true/g,
      ) ?? []
    ).length >= 2 &&
    polkamarktTrader.includes('state = "SUBMITTED"') &&
    polkamarktTrader.includes(
      "import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus",
    ) &&
    !polkamarktTrader.includes(
      "jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmitStatus",
    ) &&
    polkamarktTrader.includes("PolkamarktPendingRecoveryPlanner.newLocalId()") &&
    polkamarktPendingRecoveryTest.includes(
      "only versioned pre transport signed rows are definitively unsubmitted",
    ) &&
    polkamarktPendingRecoveryTest.includes(
      "recovery planner visits every retained row in bounded batches",
    ) &&
    /internal suspend fun requireMutationPreTransport\([\s\S]*?signingRuntime: QualifiedSora2MutationRuntime,[\s\S]*?allowedCurrentLocalId: String\? = null,[\s\S]*?PolkamarktMutationAccountValidator\.requireExpected\([\s\S]*?requireVisibility\(mutations = true\)[\s\S]*?requirePolkamarktMutationRuntimeUnchanged\(signingRuntime\)[\s\S]*?WALLET_DELETION_ACTIVE[\s\S]*?requirePendingRecoveryClear\(expectedAccountId, allowedCurrentLocalId\)/.test(
      polkamarktTrader,
    ) &&
    polkamarktPreTransportGateTest.includes(
      "unchanged account flags runtime and deletion state open transport",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "account switch immediately before transport fails closed",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "visibility disable immediately before transport fails closed",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "mutation disable immediately before transport fails closed",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "runtime drift immediately before transport fails closed",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "wallet deletion immediately before transport fails closed",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "pending recovery immediately before transport fails closed",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "exact current signed row is allowed only for its own transport",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "unreconciled signed row blocks a new mutation",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "historical chain evidence in another wallet blocks transport globally",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "countPendingTransactionsRequiringChainRecovery()",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "PENDING_POLKAMARKT_TRANSACTION_UNRESOLVED",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "durable submitted row remains ambiguous and blocks a new mutation",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "committed pending reconciliation row blocks without being treated as terminal",
    ) &&
    polkamarktPreTransportGateTest.includes(
      "transport arm is durably marked submitted and ambiguous before rpc",
    ) &&
    polkamarktMutationQualificationTest.includes(
      "trade rejects a stale quote before signing",
    ) &&
    polkamarktMutationQualificationTest.includes(
      "closed market is rejected while refreshing a mutation quote",
    ) &&
    polkamarktMutationQualificationTest.includes(
      "trade rejects an increased network fee before signing",
    ) &&
    polkamarktMutationQualificationTest.includes(
      "buy rejects insufficient KUSD before signing",
    ) &&
    polkamarktMutationQualificationTest.includes(
      "claim rejects insufficient XOR before signing",
    ) &&
    polkamarktRuntimeCallFactoryTest.includes(
      "buy call matches the reviewed runtime contract exactly",
    ) &&
    polkamarktRuntimeCallFactoryTest.includes(
      "sell call matches the reviewed runtime contract exactly",
    ) &&
    polkamarktRuntimeCallFactoryTest.includes(
      "single trader claim uses claim market and a SCALE u32 id",
    ) &&
    polkamarktRuntimeCallFactoryTest.includes(
      "batch trader claim preserves ids and uses claim markets",
    ) &&
    polkamarktRuntimeCallFactoryTest.includes(
      "creator fee claim uses the dedicated runtime call",
    ) &&
    polkamarktTrader.includes("error is DefinitelyNotSubmitted") &&
    polkamarktTrader.includes("error is ExtrinsicSubmissionUnknown") &&
    polkamarktTrader.includes("internal object PolkamarktPendingFailurePolicy") &&
    polkamarktTrader.includes("private data class PolkamarktPendingHandoff") &&
    (
      polkamarktTrader.match(
        /pendingHandoff = PolkamarktPendingHandoff\([\s\S]*?createPending\([\s\S]*?preTransportValidationCompleted = true/g,
      ) ?? []
    ).length >= 2 &&
    (
      polkamarktTrader.match(
        /createPending\([\s\S]*?withContext\(NonCancellable\)[\s\S]*?insertPendingTransaction/g,
      ) ?? []
    ).length >= 1 &&
    polkamarktTrader.includes("markPendingDefinitiveFailure") &&
    polkamarktTrader.includes('state = "REJECTED"') &&
    polkamarktTrader.includes("ambiguous = false") &&
    polkamarktTrader.includes("withContext(NonCancellable)") &&
    polkamarktTrader.includes("val authoritativeTransportResult") &&
    polkamarktTrader.includes("if (!authoritativeTransportResult)") &&
    polkamarktTrader.includes("ExtrinsicSubmissionUnknownException(") &&
    polkamarktPendingFailurePolicyTest.includes(
      "core submission unknown marker overrides stale pre transport bookkeeping",
    ) &&
    polkamarktClaimValidatorTest.includes(
      "claim response is bound to the exact account and market",
    ) &&
    polkamarktClaimValidatorTest.includes(
      "claim status and positive payout are required",
    ) &&
    polkamarktClaimValidatorTest.includes(
      "trader claim batch rejects duplicate markets",
    ) &&
    polkamarktPendingFailurePolicyTest.includes(
      "cancellation before pre transport validation completes is terminal and non ambiguous",
    ) &&
    polkamarktPendingFailurePolicyTest.includes(
      "raw cancellation after pre transport validation remains ambiguous",
    ) &&
    polkamarktPendingFailurePolicyTest.includes(
      "transport marker remains terminal after pre transport validation",
    ) &&
    polkamarktMutationAccountValidatorTest.includes(
      "confirmed account is rejected after wallet selection changes",
    ) &&
    polkamarktMutationAccountValidatorTest.includes(
      "missing confirmed account is rejected",
    ),
  "POLKAMARKT_CLAIM_OR_PRETRANSPORT_SAFETY_MISSING",
);
assert(
  /val scopedPendingRecords = database\.walletIdentityDao\(\)\.getUnresolvedTransactions\(\)[\s\S]*?it\.walletId == walletId[\s\S]*?it\.networkId == "sora2"[\s\S]*?PolkamarktPendingAssetId\.parse\(it\.assetId\) != null[\s\S]*?scopedPendingRecords\.all\(WalletIdentityDao::hasCurrentChainIdentity\)[\s\S]*?val allPendingRecords = scopedPendingRecords\.filter[\s\S]*?PolkamarktPendingRecoveryPlanner\.batches\(reconcilable, maxTransactionsPerBatch\)[\s\S]*?recoverPendingBatch\(batch\)/.test(
    polkamarktTrader,
  ) &&
    polkamarktTrader.includes("private suspend fun recoverPendingBatch(") &&
    polkamarktTrader.includes("const val MAXIMUM_BATCH_SIZE = 50") &&
    polkamarktTrader.includes("getSora2PendingSubmission(") &&
    polkamarktTrader.includes("validateSora2PendingSubmission(witness)") &&
    polkamarktTrader.includes("OPERATION_POLKAMARKT") &&
    polkamarktTrader.includes("STATE_FINALIZED_SUCCESS") &&
    polkamarktTrader.includes("STATE_EXPIRED_NOT_INCLUDED") &&
    polkamarktPendingRecoveryTest.includes(
      "restart reconciliation finalizes by exact hash without resubmitting",
    ) &&
    polkamarktPendingRecoveryTest.includes(
      "legacy pending row without exact generic witness remains unresolved",
    ) &&
    polkamarktPendingRecoveryTest.includes(
      "coVerify(exactly = 0) { calls.getFinalizedHead() }",
    ),
  "POLKAMARKT_PENDING_DURABLE_WITNESS_RECONCILIATION_MISSING",
);
assert(
  polkamarktViewModel.includes("internal object PolkamarktReadProvenance") &&
    polkamarktViewModel.includes(
      "PI_INDEXER_POLKAMARKT_CHECKPOINT_CHANGED",
    ) &&
    polkamarktViewModel.includes("return reads.any { it.fromCache }") &&
    polkamarktViewModel.includes("getMarketsQualified") &&
    polkamarktViewModel.includes("getAccountPositionsQualified") &&
    polkamarktViewModel.includes("getAccountTradesQualified") &&
    polkamarktViewModel.includes("getMarketSnapshotsQualified") &&
    polkamarktViewModel.includes("flowCurSoraAccount()") &&
    polkamarktViewModel.includes(".collectLatest { walletId ->") &&
    polkamarktViewModel.includes("accountGeneration") &&
    polkamarktViewModel.includes("isCurrentRefresh(") &&
    polkamarktViewModel.includes("MAX_AMOUNT_INPUT_LENGTH = 128") &&
    piIndexerClient.includes(
      "PI_INDEXER_MARKET_SNAPSHOT_IDENTITY_MISMATCH",
    ) &&
    piIndexerClient.includes("PI_INDEXER_POSITION_IDENTITY_MISMATCH") &&
    piIndexerClient.includes("PI_INDEXER_TRADE_IDENTITY_MISMATCH") &&
    piIndexerClientTest.includes(
      "polkamarkt account and market reads reject mismatched returned identities",
    ) &&
    polkamarktReadProvenanceTest.includes(
      "positions or trades from cache mark the complete screen cached",
    ) &&
    polkamarktReadProvenanceTest.includes(
      "different qualified checkpoints cannot be combined",
    ),
  "POLKAMARKT_PI_READ_PROVENANCE_GATE_MISSING",
);
assert(
  /collectLatest \{ walletId ->[\s\S]*?accountGeneration \+= 1[\s\S]*?currentAccountAddress = walletId[\s\S]*?quoteRequestVersion \+= 1[\s\S]*?marketDetailRequestVersion \+= 1[\s\S]*?claimReviewRequestVersion \+= 1/.test(
    polkamarktAccountObservationSource,
  ) &&
    /\.onSuccess success@\{ loaded ->\s*if \(\s*!isCurrentRefresh\(\s*accountAddress,\s*generation,\s*requestGeneration,\s*\)\s*\) \{\s*return@success\s*\}\s*coreUsingCachedData = loaded\.usingCachedData[\s\S]*?_state\.value = _state\.value\.copy\([\s\S]*?markets = loaded\.markets[\s\S]*?accountAddress = loaded\.accountAddress/.test(
      polkamarktRefreshSource,
    ) &&
    /\.onFailure failure@\{\s*if \(it is CancellationException\) throw it\s*if \(\s*!isCurrentRefresh\(\s*accountAddress,\s*generation,\s*requestGeneration,\s*\)\s*\) \{\s*return@failure\s*\}\s*_state\.value = _state\.value\.copy\([\s\S]*?errorCode = it\.message \?: "POLKAMARKT_LOAD_FAILED"/.test(
      polkamarktRefreshSource,
    ) &&
    /val quote = when \(current\.side\)[\s\S]*?PolkamarktTradeSide\.SELL -> trader\.quoteSell\([\s\S]*?slippageBps = current\.slippageBps,\s*\)\s*\}\s*if \(\s*version == quoteRequestVersion &&\s*canPrepareQuote\(\)\s*\) \{[\s\S]*?quote = quote/.test(
      polkamarktQuoteSource,
    ) &&
    polkamarktQuoteBeforePostAwaitGuard.length > 0 &&
    !/_state\s*\.\s*value\s*=/.test(polkamarktQuoteBeforePostAwaitGuard) &&
    /catch \(error: Throwable\) \{\s*if \(version == quoteRequestVersion && canPrepareQuote\(\)\) \{[\s\S]*?POLKAMARKT_QUOTE_FAILED/.test(
      polkamarktQuoteSource,
    ) &&
    (
      polkamarktMarketDetailSource.match(
        /isCurrentAccount\(accountAddress, generation\) &&\s*marketDetailRequestVersion == detailRequestVersion &&\s*_state\.value\.selectedMarket\?\.marketId == id/g,
      ) ?? []
    ).length === 4 &&
    /\.onSuccess \{ detail ->\s*if \(\s*isCurrentAccount\(accountAddress, generation\) &&\s*marketDetailRequestVersion == detailRequestVersion &&\s*_state\.value\.selectedMarket\?\.marketId == id\s*\) \{[\s\S]*?authoritativeMarket = detail\.market/.test(
      polkamarktRuntimeDetailCallbackSource,
    ) &&
    /\.onSuccess \{ snapshotRead ->\s*if \(\s*isCurrentAccount\(accountAddress, generation\) &&\s*marketDetailRequestVersion == detailRequestVersion &&\s*_state\.value\.selectedMarket\?\.marketId == id\s*\) \{[\s\S]*?snapshots = snapshotRead\.value/.test(
      polkamarktSnapshotCallbackSource,
    ) &&
    /\.onFailure \{\s*if \(it is CancellationException\) throw it\s*if \(\s*isCurrentAccount\(accountAddress, generation\) &&\s*marketDetailRequestVersion == detailRequestVersion &&\s*_state\.value\.selectedMarket\?\.marketId == id\s*\) \{[\s\S]*?errorCode = "POLKAMARKT_RUNTIME_DETAIL_UNAVAILABLE"/.test(
      polkamarktRuntimeDetailCallbackSource,
    ) &&
    /\.onFailure \{\s*if \(it is CancellationException\) throw it\s*if \(\s*isCurrentAccount\(accountAddress, generation\) &&\s*marketDetailRequestVersion == detailRequestVersion &&\s*_state\.value\.selectedMarket\?\.marketId == id\s*\) \{[\s\S]*?errorCode = "POLKAMARKT_CHART_UNAVAILABLE"/.test(
      polkamarktSnapshotCallbackSource,
    ) &&
    (
      polkamarktClaimReviewSource.match(
        /isCurrentAccount\(accountAddress, generation\) &&\s*claimReviewRequestVersion == requestVersion/g,
      ) ?? []
    ).length === 2 &&
    /\.onSuccess \{ review ->\s*if \(\s*isCurrentAccount\(accountAddress, generation\) &&\s*claimReviewRequestVersion == requestVersion\s*\) \{[\s\S]*?authoritativeClaims = review\.claims/.test(
      polkamarktClaimReviewSource,
    ) &&
    /\.onFailure \{\s*if \(it is CancellationException\) throw it\s*if \(\s*isCurrentAccount\(accountAddress, generation\) &&\s*claimReviewRequestVersion == requestVersion\s*\) \{[\s\S]*?errorCode = "POLKAMARKT_CLAIM_REVIEW_UNAVAILABLE"/.test(
      polkamarktClaimReviewSource,
    ) &&
    /val mutation = trader\.executeTrade\(quote\)\s*if \(isCurrentAccount\(accountAddress, generation\)\) \{[\s\S]*?lastMutation = mutation/.test(
      polkamarktTradeMutationSource,
    ) &&
    /catch \(error: Throwable\) \{\s*if \(isCurrentAccount\(accountAddress, generation\)\) \{[\s\S]*?POLKAMARKT_SUBMISSION_FAILED/.test(
      polkamarktTradeMutationSource,
    ) &&
    /val mutation = when \{[\s\S]*?else -> trader\.claimTraderPayouts\(authorization\)\s*\}\s*if \(isCurrentAccount\(accountAddress, generation\)\) \{[\s\S]*?lastMutation = mutation/.test(
      polkamarktClaimMutationSource,
    ) &&
    polkamarktClaimMutationBeforePostAwaitGuard.length > 0 &&
    !/_state\s*\.\s*value\s*=/.test(
      polkamarktClaimMutationBeforePostAwaitGuard,
    ) &&
    /catch \(error: Throwable\) \{\s*if \(isCurrentAccount\(accountAddress, generation\)\) \{[\s\S]*?POLKAMARKT_CLAIM_FAILED/.test(
      polkamarktClaimMutationSource,
    ),
  "POLKAMARKT_POST_AWAIT_IDENTITY_GUARDS_MISSING",
);
assert(
  polkamarktTrader.includes("getAuthoritativeMarketDetail") &&
    polkamarktTrader.includes("getAuthoritativeClaimables") &&
    polkamarktTrader.includes("PolkamarktClaimReview") &&
    polkamarktTrader.includes("shareBalance = availableShares") &&
    polkamarktViewModel.includes("authoritativeClaims") &&
    polkamarktViewModel.includes("reviewBatchClaims") &&
    polkamarktViewModel.includes("requestClaimConfirmation") &&
    polkamarktViewModel.includes("requireCurrentClaimConfirmation") &&
    polkamarktScreen.includes("Review finalized claims") &&
    polkamarktScreen.includes("Finalized share balance not reviewed") &&
    !polkamarktScreen.includes("isPositive(position.claimablePayoutUsd)") &&
    !polkamarktScreen.includes("if (position.isCreator == true)"),
  "POLKAMARKT_RUNTIME_AUTHORITATIVE_PRESENTATION_MISSING",
);
assert(
  polkamarktTrader.includes("val confirmationNonce: String") &&
    polkamarktTrader.includes("PolkamarktConfirmedQuoteUseGate") &&
    polkamarktTrader.includes("confirmedQuoteUseGate.consume(confirmed.identity)") &&
    polkamarktTrader.includes("class PolkamarktClaimAuthorization(") &&
    polkamarktTrader.includes("fun requireFreshClaims(") &&
    polkamarktTrader.includes("PENDING_TRANSACTION_SUBMISSION_AMBIGUOUS") &&
    (polkamarktTrader.match(
      /PolkamarktClaimValidator\.requireFreshClaims\(confirmed,/g,
    )?.length ?? 0) === 2 &&
    polkamarktMutationQualificationTest.includes(
      "confirmed quote authorization is one shot and bounded",
    ) &&
    polkamarktClaimValidatorTest.includes(
      "claim authorization binds the exact reviewed runtime values",
    ) &&
    polkamarktClaimConfirmation.includes("PolkamarktClaimConfirmationSource") &&
    polkamarktClaimConfirmation.includes("SELECTED_DETAIL") &&
    polkamarktClaimConfirmation.includes("REVIEWED_POSITIONS") &&
    polkamarktClaimConfirmation.includes("fun requireCurrent(") &&
    polkamarktClaimConfirmation.includes("compareAndSet(false, true)") &&
    polkamarktClaimConfirmation.includes("PolkamarktMutationAdmissionPolicy") &&
    polkamarktViewModel.includes("val mutationLoading: Boolean = false") &&
    polkamarktViewModel.includes("pendingClaimConfirmation") &&
    polkamarktViewModel.includes("if (!mutationGate.tryAcquire()) return false") &&
    polkamarktViewModel.includes("quoteRequestVersion += 1") &&
    polkamarktViewModel.includes("private fun canPrepareQuote(): Boolean") &&
    /private fun beginMutation\(\): Boolean[\s\S]*?quoteRefreshJob\?\.cancel\(\)[\s\S]*?quoteRequestVersion \+= 1[\s\S]*?quote = null/.test(
      polkamarktViewModel,
    ) &&
    /private fun endMutation\(\)[\s\S]*?mutationGate\.release\(\)[\s\S]*?quote = null/.test(
      polkamarktViewModel,
    ) &&
    polkamarktViewModel.includes("fun confirmClaim()") &&
    polkamarktScreen.includes("ClaimConfirmationDialog") &&
    polkamarktScreen.includes("!state.mutationLoading") &&
    polkamarktFragment.includes("onClaim = viewModel::requestClaim") &&
    polkamarktFragment.includes("onConfirmClaim = viewModel::confirmClaim") &&
    polkamarktFragment.includes(
      "onDismissClaimConfirmation = viewModel::dismissClaimConfirmation",
    ) &&
    commonStrings.includes('name="polkamarkt_claim_confirmation_title"') &&
    commonStrings.includes(
      'name="polkamarkt_claim_confirmation_batch_title"',
    ) &&
    commonStrings.includes('name="polkamarkt_claim_confirmation_body"') &&
    commonStrings.includes(
      'name="polkamarkt_claim_confirmation_fee_notice"',
    ) &&
    polkamarktWebContractTest.includes(
      'fixture["mobileClaimConfirmation"].asJsonObject',
    ) &&
    polkamarktPreTransportGateTest.includes(
      "PENDING_TRANSACTION_SUBMISSION_AMBIGUOUS",
    ) &&
    polkamarktClaimConfirmationTest.includes(
      "selected confirmation is isolated from a later batch review",
    ) &&
    polkamarktClaimConfirmationTest.includes(
      "batch confirmation is isolated from a later selected detail review",
    ) &&
    polkamarktClaimConfirmationTest.includes(
      "mutation gate rejects duplicate synchronous entry and can be released",
    ) &&
    polkamarktClaimConfirmationTest.includes(
      "quote admission stays closed from in flight mutation through ambiguous recovery",
    ) &&
    polkamarktClaimConfirmation.includes(
      "): Boolean = pending.isNotEmpty()",
    ) &&
    polkamarktClaimConfirmationTest.includes(
      'pending.copy(state = "SIGNED", submissionIsAmbiguous = false)',
    ) &&
    polkamarktViewModel.includes("private var pendingRecoveryJob: Job? = null") &&
    polkamarktViewModel.includes("launchPendingRecovery(accountAddress") &&
    polkamarktViewModel.includes("trader.recoverPendingTransactions(accountAddress)") &&
    polkamarktClaimConfirmationTest.includes(
      "missing claim checkpoint uses the stable domain error",
    ),
  "POLKAMARKT_SINGLE_USE_CONFIRMATION_GATE_MISSING",
);
assert(
  !polkamarktScreen.includes('it.startsWith("http://")'),
  "POLKAMARKT_INSECURE_EXTERNAL_LINKS",
);
assert(
  polkamarktWebContractSource.includes("val TRANSLATION_KEYS = mapOf(") &&
    polkamarktWebContractSource.includes(
      '"pageTitle" to "pageTitle.Polkamarkt"',
    ) &&
    polkamarktWebContractSource.includes(
      '"networkFee" to "networkFeeText"',
    ) &&
    commonStrings.includes('name="polkamarkt_outcome_yes"') &&
    commonStrings.includes('name="polkamarkt_outcome_no"') &&
    commonStrings.includes('name="polkamarkt_action_buy"') &&
    commonStrings.includes('name="polkamarkt_action_sell"') &&
    commonStrings.includes('name="polkamarkt_claim_trader_payout"') &&
    commonStrings.includes('name="polkamarkt_claim_creator_fees"') &&
    commonStrings.includes('name="polkamarkt_ticket_shares_out"') &&
    commonStrings.includes('name="polkamarkt_ticket_collateral_out"') &&
    commonStrings.includes('name="polkamarkt_ticket_slippage"') &&
    commonStrings.includes('name="polkamarkt_ticket_taker_fee"') &&
    polkamarktScreen.includes("private fun polkamarktOutcomeLabel(") &&
    polkamarktScreen.includes("CommonR.string.network_fee") &&
    !polkamarktScreen.includes('Text("Claim payout")') &&
    !polkamarktScreen.includes('Text("Claim creator fees")'),
  "POLKAMARKT_CANONICAL_TRANSLATION_CONTRACT_MISSING",
);
assert(
  polkamarktWebContractSource.includes(
    'SOURCE_REVISION = "893783ba6a19c33043eb5dabe42d949c14d0f257"',
  ) &&
    polkamarktWebContractSource.includes('SOURCE_BRANCH = "ui-updates"') &&
    polkamarktScreen.includes("PolkamarktWebContract.STATUS_FILTERS") &&
    polkamarktScreen.includes("PolkamarktWebContract.CATEGORIES") &&
    polkamarktScreen.includes("PolkamarktWebContract.matchesStatusFilter(") &&
    polkamarktScreen.includes("PolkamarktWebContract.matchesOwnerFilter(") &&
    !polkamarktScreen.includes("normalizedStatus.contains(") &&
    polkamarktWebContractSource.includes(
      "status.orEmpty().trim().lowercase()",
    ) &&
    polkamarktWebContractSource.includes("filter.trim().lowercase()") &&
    polkamarktWebContractSource.includes(
      '"active" -> normalizedStatus in OPEN_STATUSES',
    ) &&
    polkamarktWebContractSource.includes(
      '"finalized" -> normalizedStatus in FINALIZED_STATUSES',
    ) &&
    polkamarktWebContractSource.includes('"all" -> true') &&
    polkamarktWebContractSource.includes("else -> false") &&
    polkamarktWebContractSource.includes("return creator == selectedAccount") &&
    !polkamarktScreen.includes(
      "market.creator.equals(state.accountAddress, ignoreCase = true)",
    ) &&
    polkamarktWebContractTest.includes(
      "status filters require exact normalized membership",
    ) &&
    polkamarktWebContractTest.includes('status = "unresolved"') &&
    polkamarktWebContractTest.includes('status = "not_open"') &&
    polkamarktWebContractTest.includes('creator = "5exactCaseSensitiveOwner"') &&
    polkamarktWebContractTest.includes(
      "all status filter preserves markets outside pinned status sets",
    ) &&
    polkamarktScreen.includes("DpmPricingCurve") &&
    polkamarktScreen.includes(
      "PolkamarktWebContract.DPM_CURVE_POINT_COUNT",
    ) &&
    polkamarktScreen.includes("onBackToMarkets") &&
    polkamarktScreen.includes("state.mineOnly") &&
    polkamarktScreen.includes("onBatchClaim") &&
    polkamarktScreen.includes("PolkamarktExternalLinkPolicy.validated(value)") &&
    polkamarktWebContractSource.includes("uri.rawUserInfo == null") &&
    polkamarktViewModel.includes(
      "delay(PolkamarktWebContract.QUOTE_DEBOUNCE_MILLISECONDS)",
    ) &&
    polkamarktViewModel.includes("quoteRequestVersion") &&
    polkamarktViewModel.includes("requestAllTraderPayouts") &&
    polkamarktTrader.includes(
      "marketIds.size <= PolkamarktWebContract.MAX_BATCH_CLAIMS",
    ) &&
    polkamarktTrader.includes(
      "slippageBps in PolkamarktWebContract.MIN_SLIPPAGE_BPS..",
    ) &&
    polkamarktTrader.split(
      "PolkamarktSlippageValidator.requireMobileQuoteValue(slippageBps)",
    ).length === 3 &&
    !polkamarktTrader.includes("const val MAX_BATCH_CLAIMS = 100"),
  "POLKAMARKT_CANONICAL_WEB_BEHAVIOR_PARITY_MISSING",
);
block(
  !nativeCanaryQualificationReceiptQualified,
  "ANDROID_NATIVE_CANARY_QUALIFICATION_RECEIPT_MISSING",
);
block(
  !productionSignerBindingQualified,
  "ANDROID_NEXUS_SIGNER_FAIL_CLOSED",
);
block(
  !/^\s*(?:private\s+)?const val LOCAL_NEXUS_SENDS_QUALIFIED\s*=\s*true\s*$/m.test(
    productionFeatureManager,
  ),
  "ANDROID_NEXUS_SEND_LOCAL_QUALIFICATION_MISSING",
);
block(
  !/^\s*(?:private\s+)?const val LOCAL_POLKAMARKT_MUTATIONS_QUALIFIED\s*=\s*true\s*$/m.test(
    productionFeatureManager,
  ),
  "ANDROID_POLKAMARKT_LOCAL_QUALIFICATION_MISSING",
);
const fundedCanaryTrustAuthorityNames = [
  "releaseOperator",
  "independentApprover",
  "independentReviewer",
  "consumptionLedger",
];
const fundedCanaryTrustAuthorityRoles = {
  releaseOperator: "release-operator",
  independentApprover: "independent-approver",
  independentReviewer: "independent-reviewer",
  consumptionLedger: "approval-consumption-ledger",
};
const fundedCanaryTrustRootKeys = [
  "schemaVersion",
  "contractId",
  "platform",
  "assessedAt",
  "status",
  "signatureAlgorithm",
  "ledgerStoreId",
  "artifactInspection",
  "authorities",
  "blockingReasons",
];
const fundedCanaryTrustAuthorityKeys = [
  "role",
  "keyId",
  "publicKeyPem",
  "publicKeySha256",
  "enabled",
];
const fundedCanaryTrustArtifactInspectionKeys = [
  "status",
  "bundletoolVersion",
  "bundletoolJarSha256",
  "apksignerVersion",
];
const isFundedCanarySafeToolVersion = (value) =>
  typeof value === "string" &&
  /^[A-Za-z0-9][A-Za-z0-9._+() /-]{0,63}$/.test(value);
const fundedCanaryTrustKeys = {};
let fundedCanaryTrustContractValid = false;
let fundedCanaryTrustEnabled = false;
const fundedCanaryTrustSchemaQualified =
  hasExactKeys(fundedCanaryTrust, fundedCanaryTrustRootKeys) &&
  fundedCanaryTrust.schemaVersion === 1 &&
  fundedCanaryTrust.contractId ===
    "sora-android-funded-nexus-canary-trust-v1" &&
  fundedCanaryTrust.platform === "android" &&
  /^\d{4}-\d{2}-\d{2}$/.test(fundedCanaryTrust.assessedAt ?? "") &&
  ["blocked", "qualified"].includes(fundedCanaryTrust.status) &&
  fundedCanaryTrust.signatureAlgorithm === "Ed25519" &&
  hasExactKeys(
    fundedCanaryTrust.artifactInspection,
    fundedCanaryTrustArtifactInspectionKeys,
  ) &&
  hasExactKeys(fundedCanaryTrust.authorities, fundedCanaryTrustAuthorityNames) &&
  fundedCanaryTrustAuthorityNames.every((name) => {
    const authority = fundedCanaryTrust.authorities[name];
    return (
      hasExactKeys(authority, fundedCanaryTrustAuthorityKeys) &&
      authority.role === fundedCanaryTrustAuthorityRoles[name] &&
      typeof authority.enabled === "boolean"
    );
  }) &&
  Array.isArray(fundedCanaryTrust.blockingReasons) &&
  fundedCanaryTrust.blockingReasons.length <= 16 &&
  fundedCanaryTrust.blockingReasons.every(
    (reason) =>
      typeof reason === "string" &&
      reason.length > 0 &&
      [...reason].length <= 240,
  );
if (fundedCanaryTrustSchemaQualified) {
  if (fundedCanaryTrust.status === "blocked") {
    fundedCanaryTrustContractValid =
      fundedCanaryTrust.ledgerStoreId === null &&
      fundedCanaryTrust.artifactInspection.status === "blocked" &&
      fundedCanaryTrust.artifactInspection.bundletoolVersion === null &&
      fundedCanaryTrust.artifactInspection.bundletoolJarSha256 === null &&
      fundedCanaryTrust.artifactInspection.apksignerVersion === null &&
      fundedCanaryTrust.blockingReasons.length > 0 &&
      fundedCanaryTrustAuthorityNames.every((name) => {
        const authority = fundedCanaryTrust.authorities[name];
        return (
          authority.enabled === false &&
          authority.keyId === null &&
          authority.publicKeyPem === null &&
          authority.publicKeySha256 === null
        );
      });
  } else {
    try {
      fundedCanaryTrustEnabled =
        typeof fundedCanaryTrust.ledgerStoreId === "string" &&
        /^[a-z0-9][a-z0-9._-]{2,127}$/.test(
          fundedCanaryTrust.ledgerStoreId,
        ) &&
        fundedCanaryTrust.artifactInspection.status === "qualified" &&
        isFundedCanarySafeToolVersion(
          fundedCanaryTrust.artifactInspection.bundletoolVersion,
        ) &&
        /^[0-9a-f]{64}$/.test(
          fundedCanaryTrust.artifactInspection.bundletoolJarSha256 ?? "",
        ) &&
        isFundedCanarySafeToolVersion(
          fundedCanaryTrust.artifactInspection.apksignerVersion,
        ) &&
        fundedCanaryTrust.blockingReasons.length === 0 &&
        fundedCanaryTrustAuthorityNames.every((name) => {
          const authority = fundedCanaryTrust.authorities[name];
          if (
            authority.enabled !== true ||
            typeof authority.keyId !== "string" ||
            !/^[a-z0-9][a-z0-9._-]{2,63}$/.test(authority.keyId) ||
            typeof authority.publicKeyPem !== "string" ||
            authority.publicKeyPem.length > 2_048 ||
            !/^[0-9a-f]{64}$/.test(authority.publicKeySha256 ?? "")
          ) {
            return false;
          }
          const publicKey = createPublicKey(authority.publicKeyPem);
          const spki = publicKey.export({ type: "spki", format: "der" });
          if (
            publicKey.asymmetricKeyType !== "ed25519" ||
            createHash("sha256").update(spki).digest("hex") !==
              authority.publicKeySha256
          ) {
            return false;
          }
          fundedCanaryTrustKeys[name] = publicKey;
          return true;
        }) &&
        new Set(
          fundedCanaryTrustAuthorityNames.map(
            (name) => fundedCanaryTrust.authorities[name].keyId,
          ),
        ).size === fundedCanaryTrustAuthorityNames.length &&
        new Set(
          fundedCanaryTrustAuthorityNames.map(
            (name) => fundedCanaryTrust.authorities[name].publicKeySha256,
          ),
        ).size === fundedCanaryTrustAuthorityNames.length;
      fundedCanaryTrustContractValid = fundedCanaryTrustEnabled;
    } catch {
      fundedCanaryTrustContractValid = false;
      fundedCanaryTrustEnabled = false;
    }
  }
}
assert(
  fundedCanaryTrustSchemaQualified && fundedCanaryTrustContractValid,
  "ANDROID_FUNDED_CANARY_TRUST_CONTRACT_INVALID",
);
block(
  !fundedCanaryTrustEnabled,
  "ANDROID_FUNDED_CANARY_TRUST_ROOT_NOT_QUALIFIED",
);
const isFundedCanaryCanonicalBase64Signature = (value) => {
  if (
    typeof value !== "string" ||
    !/^[A-Za-z0-9+/]{86}==$/.test(value)
  ) {
    return false;
  }
  const bytes = Buffer.from(value, "base64");
  return bytes.length === 64 && bytes.toString("base64") === value;
};
const verifyFundedCanarySignature = (authorityName, lines, signature) => {
  if (
    !fundedCanaryTrustEnabled ||
    !isFundedCanaryCanonicalBase64Signature(signature)
  ) {
    return false;
  }
  try {
    return verifySignature(
      null,
      Buffer.from(lines.join("\n"), "utf8"),
      fundedCanaryTrustKeys[authorityName],
      Buffer.from(signature, "base64"),
    );
  } catch {
    return false;
  }
};
const fundedCanaryTemplatePaths = {
  taira: "docs/modernization/qualification/taira-canary.json",
  minamoto: "docs/modernization/qualification/minamoto-canary.json",
};
const fundedCanaryContractDocumentation = read(
  "docs/modernization/qualification/funded-nexus-canary-README.md",
);
const fundedCanaryMaximumReceiptBytes = 64 * 1024;
const fundedCanaryMaximumEvidenceBytes = 256 * 1024;
const fundedCanaryMaximumArtifactBytes = 1024 * 1024 * 1024;
const fundedCanaryMaximumAgeSeconds = 7 * 24 * 60 * 60;
const fundedCanaryMaximumDurationSeconds = 2 * 60 * 60;
const fundedCanaryMaximumRecordDelaySeconds = 24 * 60 * 60;
const fundedCanaryMaximumFutureSkewSeconds = 30;
const fundedCanaryStageAssertions = {
  receive: {
    networkScopedAddressValidated: true,
    discriminantValidated: true,
    fundedBalanceIncreaseObserved: true,
  },
  sendValidation: {
    recipientNetworkValidated: true,
    amountPrecisionValidated: true,
    xorBalanceValidated: true,
    xorFeeBalanceValidated: true,
  },
  fee: {
    positiveFeeObserved: true,
    feeBoundToCanonicalPayload: true,
    feeBalanceRevalidatedBeforeSigning: true,
  },
  signing: {
    candidateSignerIdentityMatched: true,
    oneUseApprovalReservedBeforeSigning: true,
    osSecuredSigningObserved: true,
    privateKeyExportObserved: false,
    selectedWalletRevalidatedBeforeSigning: true,
    selectedNetworkRevalidatedBeforeSigning: true,
    selectedAccountRevalidatedBeforeSigning: true,
    walletDeletionInactiveBeforeSigning: true,
    nexusCapabilityRevalidatedBeforeSigning: true,
    nexusSendsCapabilityRevalidatedBeforeSigning: true,
    quoteIdentityRevalidatedBeforeSigning: true,
  },
  submission: {
    singleHandoffObserved: true,
    oneUseApprovalReservationMatchedBeforeHandoff: true,
    localHashMatchedReceipt: true,
    durablePendingJournalObserved: true,
    selectedNetworkRevalidatedBeforeHandoff: true,
    selectedAccountRevalidatedBeforeHandoff: true,
    walletDeletionInactiveBeforeHandoff: true,
    nexusCapabilityRevalidatedBeforeHandoff: true,
    nexusSendsCapabilityRevalidatedBeforeHandoff: true,
    exactSignedPayloadHandoffObserved: true,
    failureClassification: "none",
  },
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
  balanceReadback: {
    senderDeltaReconciled: true,
    receiverDeltaReconciled: true,
    feeDeltaReconciled: true,
  },
  explorerReconciliation: {
    networkScopedExplorerUsed: true,
    committedTransactionObserved: true,
  },
  historyReconciliation: {
    completeFanoutObserved: true,
    exactTransactionObservedOnce: true,
    networkAssetAmountDirectionMatched: true,
  },
  restartRecovery: {
    coldRestartPerformed: true,
    statusOnlyRecoveryObserved: true,
    readOnlyCapabilityBoundaryObserved: true,
    signingDuringRecoveryObserved: false,
    resubmissionDuringRecoveryObserved: false,
  },
};
const fundedCanaryStageNames = Object.keys(fundedCanaryStageAssertions);
const fundedCanaryStageOrderPairs = [
  ["receive", "sendValidation"],
  ["sendValidation", "fee"],
  ["fee", "signing"],
  ["signing", "submission"],
  ["submission", "terminalStatusReadback"],
  ["terminalStatusReadback", "finalityReadback"],
  ["finalityReadback", "balanceReadback"],
  ["finalityReadback", "explorerReconciliation"],
  ["finalityReadback", "historyReconciliation"],
  ["balanceReadback", "restartRecovery"],
  ["explorerReconciliation", "restartRecovery"],
  ["historyReconciliation", "restartRecovery"],
];
const fundedCanaryRootKeys = [
  "schemaVersion",
  "contractId",
  "status",
  "platform",
  "receiptRecordedAtEpochSeconds",
  "candidate",
  "sora2Runtime",
  "network",
  "featureFlags",
  "signer",
  "finality",
  "operatorApproval",
  "execution",
  "privacy",
  "qualification",
  "blockingReasons",
];
const fundedCanaryCandidateKeys = [
  "applicationId",
  "buildVariant",
  "versionName",
  "versionCode",
  "sourceRevision",
  "aabSha256",
  "aabBytes",
  "aabUploadCertificateSha256",
  "installedAppSigningCertificateSha256",
  "generatedApksSha256",
  "artifactIdentityReceiptSha256",
  "installedCandidateBindingSha256",
  "candidateBindingSha256",
];
const fundedCanaryRuntimeKeys = [
  "sourceRevision",
  "specVersion",
  "transactionVersion",
  "genesisHash",
  "metadataSha256",
  "typesSha256",
];
const fundedCanaryNetworkKeys = [
  "networkId",
  "chainId",
  "i105Discriminant",
  "toriiBaseUrl",
  "explorerBaseUrl",
  "assetSymbol",
  "xorAssetAlias",
  "isTestnet",
];
const fundedCanaryFeatureFlagKeys = [
  "snapshotObservedAtEpochSeconds",
  "snapshotSha256",
  "sourceReceiptSha256",
  "nexusAvailable",
  "nexusSendsAvailable",
  "polkamarktVisible",
  "polkamarktMutationsAvailable",
  "tairaDefaultVisible",
  "tairaPreferenceIsExplicit",
  "tairaEffectiveVisible",
  "localNexusSendsQualified",
];
const fundedCanarySignerKeys = [
  "status",
  "adapterType",
  "adapterSourcePath",
  "adapterSourceSha256",
  "artifactCoordinate",
  "artifactSha256",
  "artifactBytes",
  "requiredNativeAbi",
  "observedNativeAbi",
  "exportInventorySha256",
  "provenanceReceiptPath",
  "provenanceReceiptSha256",
  "nativeCanaryReceiptSha256",
  "bindingSha256",
  "androidSecurityProvider",
];
const fundedCanaryFinalityKeys = FUNDED_CANARY_FINALITY_KEYS;
const fundedCanaryApprovalKeys = [
  "approved",
  "scopeNetworkId",
  "approvalNonce",
  "approvalReferenceSha256",
  "lowValuePolicySha256",
  "approvedAtEpochSeconds",
  "expiresAtEpochSeconds",
  "approverRole",
  "independentApproverRole",
  "dualControlReviewed",
];
const fundedCanaryExecutionKeys = [
  "startedAtEpochSeconds",
  "completedAtEpochSeconds",
  "canaryRunId",
  "evidenceBundleSha256",
  "consumptionReceiptSha256",
  "sendAmountCanonical",
  "feeAmountCanonical",
  "amountWithinApprovedLimit",
  "submissionAttemptCount",
  "automaticRetryAttempted",
  "ambiguousSubmissionObserved",
  "terminalStatus",
  ...fundedCanaryStageNames,
];
const fundedCanaryQualificationKeys = [
  "candidateIdentityMatched",
  "runtimeIdentityMatched",
  "networkIdentityMatched",
  "currentChainIdentityMatched",
  "featureFlagSnapshotMatched",
  "signerIdentityMatched",
  "finalityIdentityMatched",
  "operatorApprovalValid",
  "freshnessQualified",
  "allExecutionStagesQualified",
  "independentReviewCompleted",
];
const fundedCanaryPrivacyKeys = [
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
];
const fundedCanaryPrivacyQualified = (privacy) =>
  hasExactKeys(privacy, fundedCanaryPrivacyKeys) &&
  privacy.redactedAggregateEvidenceOnly === true &&
  fundedCanaryPrivacyKeys
    .slice(1)
    .every((field) => privacy[field] === false);
const fundedCanaryPrivacyProjectionLines = (privacy) =>
  fundedCanaryPrivacyKeys.map((field) => `privacy.${field}=${privacy[field]}`);
const fundedCanarySameShape = (value, template) => {
  if (Array.isArray(template)) return Array.isArray(value);
  if (template !== null && typeof template === "object") {
    return (
      hasExactKeys(value, Object.keys(template)) &&
      Object.keys(template).every((key) =>
        fundedCanarySameShape(value[key], template[key]),
      )
    );
  }
  return value === null || typeof value !== "object";
};
const fundedCanaryStringsBounded = (value) => {
  if (typeof value === "string") {
    return (
      [...value].length <= 256 &&
      !/[\u0000-\u001f\u007f-\u009f]/u.test(value)
    );
  }
  if (Array.isArray(value)) return value.every(fundedCanaryStringsBounded);
  if (value !== null && typeof value === "object") {
    return Object.values(value).every(fundedCanaryStringsBounded);
  }
  return true;
};
const isFundedCanarySha256 = (value) =>
  typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
const isFundedCanaryCanonicalPositiveQuantity = (value) =>
  typeof value === "string" &&
  value.length <= 256 &&
  value !== "0" &&
  /^(?:0|[1-9][0-9]*)(?:\.[0-9]*[1-9])?$/.test(value) &&
  (value.split(".")[1]?.length ?? 0) <= 254;
const compareFundedCanaryCanonicalQuantities = (left, right) => {
  const [leftInteger, leftFraction = ""] = left.split(".");
  const [rightInteger, rightFraction = ""] = right.split(".");
  if (leftInteger.length !== rightInteger.length) {
    return leftInteger.length < rightInteger.length ? -1 : 1;
  }
  if (leftInteger !== rightInteger) return leftInteger < rightInteger ? -1 : 1;
  const fractionLength = Math.max(leftFraction.length, rightFraction.length);
  const normalizedLeft = leftFraction.padEnd(fractionLength, "0");
  const normalizedRight = rightFraction.padEnd(fractionLength, "0");
  if (normalizedLeft === normalizedRight) return 0;
  return normalizedLeft < normalizedRight ? -1 : 1;
};
const isFundedCanaryEpoch = (value) =>
  Number.isSafeInteger(value) && value > 0 && value <= 9_999_999_999;
const fundedCanaryTemplates = Object.fromEntries(
  Object.entries(fundedCanaryTemplatePaths).map(([networkId, path]) => [
    networkId,
    readStrictJsonFile(join(root, path), fundedCanaryMaximumReceiptBytes),
  ]),
);
// These values validate only the inert checked-in blocked templates. They are not
// Taira routing authority and are never used to admit a qualified canary.
const blockedTemplateFundedCanaryNetworks = {
  taira: {
    chainId: "fc56984b-2be7-431d-840e-21514d1883f0",
    i105Discriminant: 369,
    toriiBaseUrl: "https://taira.sora.org",
    explorerBaseUrl: "https://taira-explorer.sora.org",
    hardMaximumAmountCanonical: "1",
    hardMaximumFeeCanonical: "1",
    isTestnet: true,
  },
  minamoto: {
    chainId: "00000000-0000-0000-0000-000000000753",
    i105Discriminant: 753,
    toriiBaseUrl: "https://minamoto.sora.org",
    explorerBaseUrl: "https://minamoto-explorer.sora.org",
    hardMaximumAmountCanonical: "1",
    hardMaximumFeeCanonical: "1",
    isTestnet: false,
  },
};
const qualifiedFundedCanaryNetworks = {
  taira: tairaDeploymentAdmission === null
    ? null
    : {
        chainId: tairaDeploymentAdmission.current.chainId,
        i105Discriminant:
          tairaDeploymentAdmission.current.i105Discriminant,
        toriiBaseUrl: tairaDeploymentAdmission.current.toriiBaseUrl,
        explorerBaseUrl:
          tairaDeploymentAdmission.current.explorerBaseUrl,
        hardMaximumAmountCanonical: "1",
        hardMaximumFeeCanonical: "1",
        isTestnet: true,
      },
  minamoto: blockedTemplateFundedCanaryNetworks.minamoto,
};
const fundedCanaryBlockedFinalityTemplatesQualified = (() => {
  const manifest = fundedCanaryFinalityManifestTemplate?.value;
  const native = fundedCanaryFinalityNativeTemplate?.value;
  if (
    !hasExactKeys(manifest, FUNDED_CANARY_FINALITY_MANIFEST_KEYS) ||
    manifest.schemaVersion !== 1 ||
    manifest.contractId !== "sora-android-nexus-finality-trust-manifest-v1" ||
    manifest.status !== "blocked" ||
    manifest.platform !== "android" ||
    manifest.reviewedAtEpochSeconds !== 0 ||
    manifest.attestationRoute !== FUNDED_CANARY_ATTESTATION_ROUTE ||
    manifest.bundleRoute !== FUNDED_CANARY_BUNDLE_ROUTE ||
    !fundedCanaryPrivacyQualified(manifest.privacy) ||
    !Array.isArray(manifest.blockingReasons) ||
    manifest.blockingReasons.length === 0 ||
    [
      "verifierSourceRevision",
      "verifierArtifactSha256",
      "nativeFinalityCanaryReceiptSha256",
      "serverContractSourceRevision",
      "serverOpenApiSha256",
      "serverRouteSourceSha256",
      "minamotoTrustContextReceiptSha256",
      "tairaTrustContextReceiptSha256",
      "independentReviewerKeyId",
      "independentReviewerSignatureBase64",
    ].some((field) => manifest[field] !== null) ||
    !hasExactKeys(native, FUNDED_CANARY_FINALITY_NATIVE_KEYS) ||
    native.schemaVersion !== 1 ||
    native.contractId !== "sora-android-nexus-finality-native-canary-v1" ||
    native.status !== "blocked" ||
    native.platform !== "android" ||
    native.reviewedAtEpochSeconds !== 0 ||
    native.requiredNativeAbi !== 21 ||
    native.observedNativeAbi !== null ||
    native.artifactClass !== null ||
    native.sourceTreeClean !== null ||
    native.attestationRoute !== FUNDED_CANARY_ATTESTATION_ROUTE ||
    native.bundleRoute !== FUNDED_CANARY_BUNDLE_ROUTE ||
    native.attestationResponseType !== "BridgeFinalityAttestationV1" ||
    native.bundleResponseType !== "BridgeFinalityBundle" ||
    native.dirtyLocalDebugArtifactAccepted !== false ||
    [
      "canonicalNoritoRoundTripQualified",
      "artifactIdentityQualified",
      "abiQualified",
      "exportInventoryQualified",
      "attestationDecodeProjectionQualified",
      "bundleDecodeProjectionQualified",
      "trustContextBindingQualified",
      "attestationNoritoRoundTripKatPassed",
      "bundleNoritoRoundTripKatPassed",
      "challengeBindingKatPassed",
      "genesisProofKatPassed",
      "tipProofKatPassed",
      "nodeSignatureKatPassed",
      "aggregateSignatureKatPassed",
      "successorProofKatPassed",
      "finalizedProjectionKatPassed",
    ].some((field) => native[field] !== false) ||
    [
      "reviewedPlatformArtifactSha256",
      "verifierSourceRevision",
      "verifierArtifactSha256",
      "finalityTrustManifestBindingSha256",
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
      "independentReviewerKeyId",
      "independentReviewerSignatureBase64",
    ].some((field) => native[field] !== null) ||
    !fundedCanaryPrivacyQualified(native.privacy) ||
    !Array.isArray(native.blockingReasons) ||
    native.blockingReasons.length === 0
  ) {
    return false;
  }
  return Object.entries(fundedCanaryFinalityContextTemplates).every(
    ([networkId, record]) => {
      const context = record?.value;
      const expected = blockedTemplateFundedCanaryNetworks[networkId];
      return (
        hasExactKeys(context, FUNDED_CANARY_FINALITY_CONTEXT_KEYS) &&
        context.schemaVersion === 1 &&
        context.contractId ===
          "sora-android-nexus-finality-trust-context-v1" &&
        context.status === "blocked" &&
        context.platform === "android" &&
        context.networkId === networkId &&
        context.chainId === expected.chainId &&
        context.reviewedAtEpochSeconds === 0 &&
        Object.entries(context).every(
          ([field, value]) =>
            [
              "schemaVersion",
              "contractId",
              "status",
              "platform",
              "networkId",
              "chainId",
              "reviewedAtEpochSeconds",
              "privacy",
              "blockingReasons",
            ].includes(field) || value === null,
        ) &&
        fundedCanaryPrivacyQualified(context.privacy) &&
        Array.isArray(context.blockingReasons) &&
        context.blockingReasons.length > 0
      );
    },
  );
})();
assert(
  fundedCanaryBlockedFinalityTemplatesQualified,
  "ANDROID_FUNDED_CANARY_V4_FINALITY_TEMPLATES_INVALID",
);
const fundedCanaryTemplateQualified = (networkId) => {
  const record = fundedCanaryTemplates[networkId];
  const receipt = record?.value;
  const expected = blockedTemplateFundedCanaryNetworks[networkId];
  return (
    record !== null &&
    hasExactKeys(receipt, fundedCanaryRootKeys) &&
    hasExactKeys(receipt.candidate, fundedCanaryCandidateKeys) &&
    hasExactKeys(receipt.sora2Runtime, fundedCanaryRuntimeKeys) &&
    hasExactKeys(receipt.network, fundedCanaryNetworkKeys) &&
    hasExactKeys(receipt.featureFlags, fundedCanaryFeatureFlagKeys) &&
    hasExactKeys(receipt.signer, fundedCanarySignerKeys) &&
    hasExactKeys(receipt.finality, fundedCanaryFinalityKeys) &&
    hasExactKeys(receipt.operatorApproval, fundedCanaryApprovalKeys) &&
    hasExactKeys(receipt.execution, fundedCanaryExecutionKeys) &&
    fundedCanaryStageNames.every((stageName) =>
      hasExactKeys(receipt.execution[stageName], [
        "status",
        "observedAtEpochSeconds",
        "evidenceSha256",
        ...(stageName === "terminalStatusReadback"
          ? FUNDED_CANARY_TERMINAL_EXTRA_KEYS
          : []),
        ...(stageName === "finalityReadback"
          ? FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS
          : []),
        ...Object.keys(fundedCanaryStageAssertions[stageName]),
      ]),
    ) &&
    hasExactKeys(receipt.privacy, fundedCanaryPrivacyKeys) &&
    hasExactKeys(receipt.qualification, fundedCanaryQualificationKeys) &&
    receipt?.schemaVersion === 4 &&
    receipt.contractId === FUNDED_CANARY_V4_CONTRACT_ID &&
    receipt.status === "blocked" &&
    receipt.platform === "android" &&
    receipt.receiptRecordedAtEpochSeconds === 0 &&
    receipt.candidate?.applicationId === "jp.co.soramitsu.sora" &&
    receipt.candidate.buildVariant === "productionRelease" &&
    receipt.candidate.versionName ===
      (/val appVersionName\s*=\s*"([^"]+)"/.exec(appBuild)?.[1] ?? "") &&
    receipt.candidate.versionCode === null &&
    receipt.candidate.sourceRevision === null &&
    receipt.candidate.aabSha256 === null &&
    receipt.candidate.aabBytes === null &&
    receipt.candidate.aabUploadCertificateSha256 === null &&
    receipt.candidate.installedAppSigningCertificateSha256 === null &&
    receipt.candidate.generatedApksSha256 === null &&
    receipt.candidate.artifactIdentityReceiptSha256 === null &&
    receipt.candidate.installedCandidateBindingSha256 === null &&
    receipt.candidate.candidateBindingSha256 === null &&
    receipt.sora2Runtime?.sourceRevision === SORA2_REVISION &&
    receipt.sora2Runtime.specVersion === 130 &&
    receipt.sora2Runtime.transactionVersion === 130 &&
    receipt.sora2Runtime.genesisHash ===
      "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5" &&
    receipt.sora2Runtime.metadataSha256 ===
      "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf" &&
    receipt.sora2Runtime.typesSha256 ===
      "e87760d7a566d1b1b3d21a1e76ad70990fd54e14e6af3ba27dd4440461063601" &&
    receipt.network?.networkId === networkId &&
    receipt.network.chainId === expected.chainId &&
    receipt.network.i105Discriminant === expected.i105Discriminant &&
    receipt.network.toriiBaseUrl === expected.toriiBaseUrl &&
    receipt.network.explorerBaseUrl === expected.explorerBaseUrl &&
    receipt.network.assetSymbol === "XOR" &&
    receipt.network.xorAssetAlias === "xor#universal" &&
    receipt.network.isTestnet === expected.isTestnet &&
    receipt.featureFlags?.snapshotObservedAtEpochSeconds === null &&
    receipt.featureFlags.snapshotSha256 === null &&
    receipt.featureFlags.sourceReceiptSha256 === null &&
    [
      "nexusAvailable",
      "nexusSendsAvailable",
      "polkamarktVisible",
      "polkamarktMutationsAvailable",
      "tairaDefaultVisible",
      "tairaPreferenceIsExplicit",
      "tairaEffectiveVisible",
    ].every((field) => receipt.featureFlags[field] === null) &&
    receipt.featureFlags.localNexusSendsQualified === false &&
    receipt.signer?.status === "blocked" &&
    receipt.signer.requiredNativeAbi === 21 &&
    receipt.signer.provenanceReceiptPath ===
      "config/iroha-mobile-sdk-pin.json" &&
    [
      "adapterType",
      "adapterSourcePath",
      "adapterSourceSha256",
      "artifactCoordinate",
      "artifactSha256",
      "artifactBytes",
      "observedNativeAbi",
      "exportInventorySha256",
      "provenanceReceiptSha256",
      "nativeCanaryReceiptSha256",
      "bindingSha256",
      "androidSecurityProvider",
    ].every((field) => receipt.signer[field] === null) &&
    receipt.finality?.status === "blocked" &&
    receipt.finality.attestationRoute === FUNDED_CANARY_ATTESTATION_ROUTE &&
    receipt.finality.bundleRoute === FUNDED_CANARY_BUNDLE_ROUTE &&
    fundedCanaryFinalityKeys
      .filter(
        (field) =>
          !["status", "attestationRoute", "bundleRoute"].includes(field),
      )
      .every((field) => receipt.finality[field] === null) &&
    receipt.operatorApproval?.approved === false &&
    receipt.operatorApproval.scopeNetworkId === networkId &&
    receipt.operatorApproval.approvalNonce === null &&
    receipt.operatorApproval.dualControlReviewed === false &&
    [
      "approvalReferenceSha256",
      "lowValuePolicySha256",
      "approvedAtEpochSeconds",
      "expiresAtEpochSeconds",
      "approverRole",
      "independentApproverRole",
    ].every((field) => receipt.operatorApproval[field] === null) &&
    receipt.execution?.startedAtEpochSeconds === null &&
    receipt.execution.completedAtEpochSeconds === null &&
    receipt.execution.canaryRunId === null &&
    receipt.execution.evidenceBundleSha256 === null &&
    receipt.execution.consumptionReceiptSha256 === null &&
    receipt.execution.sendAmountCanonical === null &&
    receipt.execution.feeAmountCanonical === null &&
    receipt.execution.amountWithinApprovedLimit === null &&
    receipt.execution.submissionAttemptCount === null &&
    receipt.execution.automaticRetryAttempted === null &&
    receipt.execution.ambiguousSubmissionObserved === null &&
    receipt.execution.terminalStatus === null &&
    fundedCanaryStageNames.every((stageName) => {
      const stage = receipt.execution[stageName];
      return (
        stage.status === "missing" &&
        stage.observedAtEpochSeconds === null &&
        stage.evidenceSha256 === null &&
        Object.keys(fundedCanaryStageAssertions[stageName]).every(
          (field) => stage[field] === null,
        ) &&
        (stageName !== "terminalStatusReadback" ||
          stage.committedBlockHeight === null) &&
        (stageName !== "finalityReadback" ||
          (stage.networkId === networkId &&
            stage.chainId === expected.chainId &&
            FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS.filter(
              (field) => !["networkId", "chainId"].includes(field),
            ).every((field) => stage[field] === null)))
      );
    }) &&
    fundedCanaryPrivacyQualified(receipt.privacy) &&
    Object.values(receipt.qualification).every((value) => value === false) &&
    fundedCanaryStringsBounded(receipt) &&
    Array.isArray(receipt.blockingReasons) &&
    receipt.blockingReasons.length > 0 &&
    receipt.blockingReasons.length <= 16 &&
    receipt.blockingReasons.every(
      (reason) =>
        typeof reason === "string" &&
        [...reason].length > 0 &&
        [...reason].length <= 240,
    )
  );
};
assert(
  fundedCanaryTemplateQualified("taira") &&
    fundedCanaryTemplateQualified("minamoto") &&
    fundedCanarySameShape(
      fundedCanaryTemplates.taira.value,
      fundedCanaryTemplates.minamoto.value,
    ) &&
    fundedCanaryContractDocumentation.includes(
      "sora-android-funded-nexus-canary-v4",
    ) &&
    fundedCanaryContractDocumentation.includes(
      "hashes their actual regular, non-symlink bytes",
    ) &&
    fundedCanaryContractDocumentation.includes(
      "sora-android-funded-nexus-approval-consumption-v2",
    ) &&
    fundedCanaryContractDocumentation.includes(
      "reservation occurs after fee validation",
    ),
  "ANDROID_FUNDED_CANARY_V4_CONTRACT_INVALID",
);
assert(
  fundedCanaryV4LibrarySource.includes(
    "finalizedBlockHeight < terminal.committedBlockHeight",
  ) &&
    fundedCanaryV4LibrarySource.includes(
      "sora-android-funded-nexus-finality-challenge-binding-v1",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "sora-android-nexus-finality-trust-manifest-binding-v1",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "FUNDED_CANARY_V4_READINESS_PREREQUISITE_KEYS.every",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "nativeFinalityCanary.observedNativeAbi !== 21",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "nativeFinalityCanary.attestationNoritoRoundTripKatSha256",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "finalityStage.liveBoundedSequentialStatefulSuccessorChainVerified !== true",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "networkTrustContext.trustedFirstHeight >",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "nativeFinalityCanary[field] === true",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "Array.isArray(finalityManifest.blockingReasons)",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "Array.isArray(networkTrustContext.blockingReasons)",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "Array.isArray(nativeFinalityCanary.blockingReasons)",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "nonzeroSha1(productionFinality.verifierSourceRevision)",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "nonzeroSha1(trustContract.serverContractSourceRevision)",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      'value !== "0".repeat(40)',
    ) &&
    fundedCanaryV4LibrarySource.includes(").size !== 13") &&
    fundedCanaryV4LibrarySource.includes(
      "networkTrustContext.reviewedAtEpochSeconds >",
    ) &&
    fundedCanaryV4LibrarySource.includes(
      "policy.reviewedAtEpochSeconds <= approval.approvedAtEpochSeconds",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      'for (const networkId of ["taira", "minamoto"])',
    ) &&
    fundedCanaryV4HarnessSource.includes("`${networkId} positive`") &&
    fundedCanaryV4HarnessSource.includes("invalid native ABI") &&
    fundedCanaryV4HarnessSource.includes(
      "trusted first height beyond finalized checkpoint",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "missing live bounded sequential stateful successor-chain proof",
    ) &&
    fundedCanaryV4HarnessSource.includes("katPassFlagMutations") &&
    [
      '"attestationNoritoRoundTripKatPassed"',
      '"bundleNoritoRoundTripKatPassed"',
      '"challengeBindingKatPassed"',
      '"genesisProofKatPassed"',
      '"tipProofKatPassed"',
      '"nodeSignatureKatPassed"',
      '"aggregateSignatureKatPassed"',
      '"successorProofKatPassed"',
      '"finalizedProjectionKatPassed"',
    ].every((marker) => fundedCanaryV4HarnessSource.includes(marker)) &&
    fundedCanaryV4HarnessSource.includes(
      "non-array finality manifest blocking reasons",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "non-array network trust context blocking reasons",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "non-array native finality blocking reasons",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "all-zero finality verifier source revision",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "all-zero finality server source revision",
    ) &&
    fundedCanaryV4HarnessSource.includes("retroactive network trust") &&
    fundedCanaryV4HarnessSource.includes("retroactive policy review") &&
    fundedCanaryV4HarnessSource.includes("crossNetworkReuseMutations") &&
    [
      '"receipt hash"',
      '"candidate binding"',
      '"finality binding"',
      '"run ID"',
      '"approval nonce"',
      '"challenge hash"',
    ].every((marker) => fundedCanaryV4HarnessSource.includes(marker)) &&
    fundedCanaryV4HarnessSource.includes("bundle evidence challenge drift") &&
    fundedCanaryV4HarnessSource.includes(
      "finalized block hash challenge drift",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "finality binding challenge drift",
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "aliased finality artifact identities",
    ) &&
    fundedCanaryV4HarnessSource.includes("failClosedMutationCount,") &&
    fundedCanaryV4HarnessSource.includes(
      '33,\n  "exact fail-closed mutation inventory"',
    ) &&
    fundedCanaryV4HarnessSource.includes(
      "${failClosedMutationCount} fail-closed mutations, and cross-network anti-reuse passed",
    ) &&
    productionReleaseWorkflow.includes(
      "node scripts/test-funded-canary-v4-contract.mjs",
    ),
  "ANDROID_FUNDED_CANARY_V4_HERMETIC_REGRESSION_MISSING",
);

const fundedCanaryEvaluationEpoch = Number(
  process.env.PRODUCTION_RELEASE_EVALUATED_AT_EPOCH_SECONDS ?? "",
);
const fundedCanaryEvaluationEpochQualified = isFundedCanaryEpoch(
  fundedCanaryEvaluationEpoch,
);
const fundedCanaryAab = hashExternalRegularFile({
  path: process.env.PRODUCTION_CANDIDATE_AAB_PATH ?? "",
  suffix: ".aab",
  maximumBytes: fundedCanaryMaximumArtifactBytes,
});
const fundedCanarySourceRevision =
  process.env.PRODUCTION_CANDIDATE_SOURCE_REVISION ?? "";
const fundedCanaryVersionName =
  /val appVersionName\s*=\s*"([^"]+)"/.exec(appBuild)?.[1] ?? "";
const fundedCanaryDefaultVersionCode = Number(
  /val appVersionCode[\s\S]{0,160}?\?:\s*([0-9]+)/.exec(appBuild)?.[1] ??
    "",
);
const fundedCanaryVersionCode = Number(
  process.env.CI_BUILD_ID ?? fundedCanaryDefaultVersionCode,
);
const fundedCanaryPiRecord =
  piLiveReceiptPath.length > 0 && isAbsolute(piLiveReceiptPath)
    ? readStrictJsonFile(piLiveReceiptPath, 64 * 1024)
    : null;
const fundedCanaryPiReceipt = fundedCanaryPiRecord?.value;
const fundedCanaryPiCapabilityKeys = PRODUCTION_PI_CAPABILITY_KEYS;
const fundedCanaryPiReceiptQualifiedAt = ({
  receipt,
  referenceEpochSeconds,
  maximumFutureSkewSeconds,
}) =>
  isFundedCanaryEpoch(referenceEpochSeconds) &&
  validateProductionPiRawLiveReceipt(receipt, referenceEpochSeconds, {
    requireAllCapabilities: false,
    maximumFutureSkewSeconds,
  }) !== null;
const fundedCanaryCapabilitySha256For = (receipt) =>
  canonicalSha256([
    `schemaVersion=${receipt.schemaVersion}`,
    `endpoint=${receipt.endpoint}`,
    `serviceId=${receipt.serviceId}`,
    `ecosystem=${receipt.ecosystem}`,
    `chainId=${receipt.chainId}`,
    `network=${receipt.network}`,
    `readOnly=${receipt.readOnly}`,
    `mobileConfigHealthBound=${receipt.mobileConfigHealthBound}`,
    `historyBlockHeightContractDeployed=${receipt.historyBlockHeightContractDeployed}`,
    `sora2GenesisHash=${receipt.sora2GenesisHash}`,
    `finalizedCheckpoint=${receipt.finalizedCheckpoint}`,
    `finalizedCheckpointBlockHash=${receipt.finalizedCheckpointBlockHash}`,
    `capabilityFinalizedCheckpoint=${receipt.capabilityFinalizedCheckpoint}`,
    `capabilityFinalizedCheckpointBlockHash=${receipt.capabilityFinalizedCheckpointBlockHash}`,
    `nexusAvailable=${receipt.capabilities.nexusAvailable}`,
    `nexusSendsAvailable=${receipt.capabilities.nexusSendsAvailable}`,
    `polkamarktVisible=${receipt.capabilities.polkamarktVisible}`,
    `polkamarktMutationsAvailable=${receipt.capabilities.polkamarktMutationsAvailable}`,
    `tairaDefaultVisible=${receipt.capabilities.tairaDefaultVisible}`,
  ]);
const fundedCanaryReleasePiReceiptQualified =
  fundedCanaryPiReceiptQualifiedAt({
    receipt: fundedCanaryPiReceipt,
    referenceEpochSeconds: fundedCanaryEvaluationEpoch,
    maximumFutureSkewSeconds: fundedCanaryMaximumFutureSkewSeconds,
  }) &&
  fundedCanaryPiCapabilityKeys.every(
    (field) => fundedCanaryPiReceipt.capabilities[field] === true,
  );

const fundedCanaryStagedJarPath = join(
  root,
  stagedIrohaRepository.path,
  reviewedIrohaJarManifestPath,
);
const fundedCanaryStagedJar = hashExternalRegularFile({
  path: fundedCanaryStagedJarPath,
  suffix: ".jar",
  maximumBytes: 512 * 1024 * 1024,
});
const fundedCanaryNativeReceiptPath =
  typeof nativeCanaryQualificationEvidence?.receiptPath === "string"
    ? join(root, nativeCanaryQualificationEvidence.receiptPath)
    : "";
const fundedCanaryNativeReceipt = fundedCanaryNativeReceiptPath
  ? readStrictJsonFile(fundedCanaryNativeReceiptPath, 64 * 1024)
  : null;
const fundedCanaryNativeExportInventorySha256 =
  fundedCanaryNativeReceipt?.value?.parityEvidence?.requiredExportInventory
    ?.referenceSha256 ?? null;
const fundedCanarySignerProvenancePath = "config/iroha-mobile-sdk-pin.json";
const fundedCanarySignerProvenanceSha256 = sha256(
  fundedCanarySignerProvenancePath,
);
const fundedCanaryInstalledAppSigningCertificateSha256 =
  androidProductionSigning.productionAppSigningCertificateSha256;
const fundedCanaryAabUploadCertificateSha256 =
  androidProductionSigning.productionUploadCertificateSha256;
const fundedCanaryGeneratedApks = hashExternalRegularFile({
  path: process.env.PRODUCTION_CANDIDATE_APKS_PATH ?? "",
  suffix: ".apks",
  maximumBytes: fundedCanaryMaximumArtifactBytes,
});
const fundedCanaryBundletoolJar = hashExternalRegularFile({
  path: process.env.PRODUCTION_BUNDLETOOL_JAR_PATH ?? "",
  suffix: ".jar",
  maximumBytes: 512 * 1024 * 1024,
});
const fundedCanaryFinalityVerifierArtifact = hashExternalRegularFile({
  path: process.env.IROHA_REVIEWED_FINALITY_VERIFIER_ARTIFACT_PATH ?? "",
  suffix: "",
  maximumBytes: fundedCanaryMaximumArtifactBytes,
});
const fundedCanaryFinalityPlatformArtifact = hashExternalRegularFile({
  path: process.env.IROHA_REVIEWED_FINALITY_PLATFORM_ARTIFACT_PATH ?? "",
  suffix: "",
  maximumBytes: fundedCanaryMaximumArtifactBytes,
});
const fundedCanaryLocalNexusSendsQualified =
  localQualificationValue("LOCAL_NEXUS_SENDS_QUALIFIED") === "true";

const fundedCanaryExternalJson = (environmentName, maximumBytes) => {
  const path = process.env[environmentName] ?? "";
  return isAbsolute(path) ? readStrictJsonFile(path, maximumBytes) : null;
};
const fundedCanaryEvidencePrivacyQualified = (privacy) =>
  fundedCanaryPrivacyQualified(privacy);
const fundedCanaryQualified = (networkId, record) => {
  const expectedNetwork = qualifiedFundedCanaryNetworks[networkId];
  const template = fundedCanaryTemplates[networkId]?.value;
  const receipt = record?.value;
  if (
    receipt?.status !== "qualified" ||
    expectedNetwork === null ||
    !fundedCanarySameShape(receipt, template) ||
    !fundedCanaryStringsBounded(receipt) ||
    receipt.schemaVersion !== 4 ||
    receipt.contractId !== FUNDED_CANARY_V4_CONTRACT_ID ||
    receipt.platform !== "android" ||
    !Array.isArray(receipt.blockingReasons) ||
    receipt.blockingReasons.length !== 0 ||
    !fundedCanaryPrivacyQualified(receipt.privacy) ||
    fundedCanaryAab === null ||
    !fundedCanaryEvaluationEpochQualified ||
    !fundedCanaryReleasePiReceiptQualified ||
    fundedCanaryTrust.status !== "qualified" ||
    !fundedCanaryTrustEnabled ||
    !productionSignerBindingQualified ||
    !nativeCanaryQualificationReceiptQualified ||
    fundedCanaryFinalityVerifierArtifact === null ||
    fundedCanaryFinalityPlatformArtifact === null ||
    !fundedCanaryLocalNexusSendsQualified
  ) {
    return false;
  }

  const candidate = receipt.candidate;
  const runtime = receipt.sora2Runtime;
  const network = receipt.network;
  const flags = receipt.featureFlags;
  const signer = receipt.signer;
  const finality = receipt.finality;
  const approval = receipt.operatorApproval;
  const execution = receipt.execution;
  const qualification = receipt.qualification;
  const environmentPrefix = networkId.toUpperCase();
  const finalityManifestEvidence = fundedCanaryExternalJson(
    "PRODUCTION_FINALITY_TRUST_MANIFEST_RECEIPT",
    fundedCanaryMaximumReceiptBytes,
  );
  const networkTrustContextEvidence = fundedCanaryExternalJson(
    `${environmentPrefix}_FUNDED_CANARY_FINALITY_TRUST_CONTEXT`,
    fundedCanaryMaximumReceiptBytes,
  );
  const nativeFinalityCanaryEvidence = fundedCanaryExternalJson(
    "IROHA_REVIEWED_FINALITY_NATIVE_CANARY_RECEIPT_PATH",
    fundedCanaryMaximumReceiptBytes,
  );
  const canaryPiRecord = fundedCanaryExternalJson(
    `${environmentPrefix}_FUNDED_CANARY_PI_RECEIPT`,
    fundedCanaryMaximumReceiptBytes,
  );
  const canaryPiReceipt = canaryPiRecord?.value;
  const canaryPiReceiptQualified = fundedCanaryPiReceiptQualifiedAt({
    receipt: canaryPiReceipt,
    referenceEpochSeconds: execution.startedAtEpochSeconds,
    maximumFutureSkewSeconds: 0,
  });
  const canaryCapabilitySha256 = canaryPiReceiptQualified
    ? fundedCanaryCapabilitySha256For(canaryPiReceipt)
    : null;
  const artifactIdentityEvidence = fundedCanaryExternalJson(
    `${environmentPrefix}_FUNDED_CANARY_ARTIFACT_IDENTITY_RECEIPT`,
    fundedCanaryMaximumReceiptBytes,
  );
  const artifactIdentity = artifactIdentityEvidence?.value;
  const artifactIdentityKeys = [
    "schemaVersion",
    "contractId",
    "status",
    "platform",
    "networkId",
    "canaryRunId",
    "inspectedAtEpochSeconds",
    "aabSha256",
    "aabBytes",
    "applicationId",
    "versionName",
    "versionCode",
    "sourceRevision",
    "aabUploadCertificateSha256",
    "playAppSigningCertificateSha256",
    "playSigningMappingReviewed",
    "bundletoolVersion",
    "bundletoolJarSha256",
    "apksignerVersion",
    "generatedApksSha256",
    "generatedApksBytes",
    "installedPackageName",
    "installedVersionName",
    "installedVersionCode",
    "installedAppSigningCertificateSha256",
    "installedFromGeneratedApks",
    "releaseOperatorKeyId",
    "releaseOperatorSignatureBase64",
    "independentReviewerKeyId",
    "independentReviewerSignatureBase64",
    "privacy",
  ];
  const artifactIdentityProjection = hasExactKeys(
    artifactIdentity,
    artifactIdentityKeys,
  )
    ? [
        `schemaVersion=${artifactIdentity.schemaVersion}`,
        `contractId=${artifactIdentity.contractId}`,
        `status=${artifactIdentity.status}`,
        `platform=${artifactIdentity.platform}`,
        `networkId=${artifactIdentity.networkId}`,
        `canaryRunId=${artifactIdentity.canaryRunId}`,
        `inspectedAtEpochSeconds=${artifactIdentity.inspectedAtEpochSeconds}`,
        `aabSha256=${artifactIdentity.aabSha256}`,
        `aabBytes=${artifactIdentity.aabBytes}`,
        `applicationId=${artifactIdentity.applicationId}`,
        `versionName=${artifactIdentity.versionName}`,
        `versionCode=${artifactIdentity.versionCode}`,
        `sourceRevision=${artifactIdentity.sourceRevision}`,
        `aabUploadCertificateSha256=${artifactIdentity.aabUploadCertificateSha256}`,
        `playAppSigningCertificateSha256=${artifactIdentity.playAppSigningCertificateSha256}`,
        `playSigningMappingReviewed=${artifactIdentity.playSigningMappingReviewed}`,
        `bundletoolVersion=${artifactIdentity.bundletoolVersion}`,
        `bundletoolJarSha256=${artifactIdentity.bundletoolJarSha256}`,
        `apksignerVersion=${artifactIdentity.apksignerVersion}`,
        `generatedApksSha256=${artifactIdentity.generatedApksSha256}`,
        `generatedApksBytes=${artifactIdentity.generatedApksBytes}`,
        `installedPackageName=${artifactIdentity.installedPackageName}`,
        `installedVersionName=${artifactIdentity.installedVersionName}`,
        `installedVersionCode=${artifactIdentity.installedVersionCode}`,
        `installedAppSigningCertificateSha256=${artifactIdentity.installedAppSigningCertificateSha256}`,
        `installedFromGeneratedApks=${artifactIdentity.installedFromGeneratedApks}`,
        `releaseOperatorKeyId=${artifactIdentity.releaseOperatorKeyId}`,
        `independentReviewerKeyId=${artifactIdentity.independentReviewerKeyId}`,
        ...fundedCanaryPrivacyProjectionLines(artifactIdentity.privacy),
      ]
    : [];
  const installedCandidateBindingSha256 =
    artifactIdentityProjection.length > 0
      ? canonicalSha256(artifactIdentityProjection)
      : null;
  const artifactIdentityQualified =
    artifactIdentityProjection.length > 0 &&
    fundedCanaryStringsBounded(artifactIdentity) &&
    artifactIdentity.schemaVersion === 1 &&
    artifactIdentity.contractId ===
      "sora-android-funded-nexus-installed-candidate-v1" &&
    artifactIdentity.status === "qualified" &&
    artifactIdentity.platform === "android" &&
    artifactIdentity.networkId === networkId &&
    isFundedCanarySha256(artifactIdentity.canaryRunId) &&
    artifactIdentity.canaryRunId === execution.canaryRunId &&
    isFundedCanaryEpoch(artifactIdentity.inspectedAtEpochSeconds) &&
    artifactIdentity.inspectedAtEpochSeconds < execution.startedAtEpochSeconds &&
    fundedCanaryEvaluationEpoch - artifactIdentity.inspectedAtEpochSeconds <=
      fundedCanaryMaximumAgeSeconds &&
    artifactIdentity.aabSha256 === fundedCanaryAab.sha256 &&
    artifactIdentity.aabBytes === fundedCanaryAab.bytes &&
    artifactIdentity.applicationId === "jp.co.soramitsu.sora" &&
    artifactIdentity.versionName === fundedCanaryVersionName &&
    artifactIdentity.versionCode === fundedCanaryVersionCode &&
    artifactIdentity.sourceRevision === fundedCanarySourceRevision &&
    artifactIdentity.aabUploadCertificateSha256 ===
      fundedCanaryAabUploadCertificateSha256 &&
    artifactIdentity.playAppSigningCertificateSha256 ===
      fundedCanaryInstalledAppSigningCertificateSha256 &&
    artifactIdentity.playSigningMappingReviewed === true &&
    artifactIdentity.bundletoolVersion ===
      fundedCanaryTrust.artifactInspection.bundletoolVersion &&
    artifactIdentity.bundletoolJarSha256 ===
      fundedCanaryTrust.artifactInspection.bundletoolJarSha256 &&
    fundedCanaryBundletoolJar !== null &&
    artifactIdentity.bundletoolJarSha256 === fundedCanaryBundletoolJar.sha256 &&
    artifactIdentity.apksignerVersion ===
      fundedCanaryTrust.artifactInspection.apksignerVersion &&
    fundedCanaryGeneratedApks !== null &&
    artifactIdentity.generatedApksSha256 === fundedCanaryGeneratedApks.sha256 &&
    artifactIdentity.generatedApksBytes === fundedCanaryGeneratedApks.bytes &&
    artifactIdentity.installedPackageName === "jp.co.soramitsu.sora" &&
    artifactIdentity.installedVersionName === fundedCanaryVersionName &&
    artifactIdentity.installedVersionCode === fundedCanaryVersionCode &&
    artifactIdentity.installedAppSigningCertificateSha256 ===
      fundedCanaryInstalledAppSigningCertificateSha256 &&
    artifactIdentity.installedFromGeneratedApks === true &&
    artifactIdentity.releaseOperatorKeyId ===
      fundedCanaryTrust.authorities.releaseOperator.keyId &&
    artifactIdentity.independentReviewerKeyId ===
      fundedCanaryTrust.authorities.independentReviewer.keyId &&
    fundedCanaryPrivacyQualified(artifactIdentity.privacy) &&
    verifyFundedCanarySignature(
      "releaseOperator",
      artifactIdentityProjection,
      artifactIdentity.releaseOperatorSignatureBase64,
    ) &&
    verifyFundedCanarySignature(
      "independentReviewer",
      artifactIdentityProjection,
      artifactIdentity.independentReviewerSignatureBase64,
    );
  if (
    !artifactIdentityQualified ||
    candidate.applicationId !== "jp.co.soramitsu.sora" ||
    candidate.buildVariant !== "productionRelease" ||
    candidate.versionName !== fundedCanaryVersionName ||
    candidate.versionCode !== fundedCanaryVersionCode ||
    !Number.isSafeInteger(candidate.versionCode) ||
    candidate.versionCode < 1 ||
    candidate.versionCode > 2_100_000_000 ||
    candidate.sourceRevision !== fundedCanarySourceRevision ||
    !/^[0-9a-f]{40}$/.test(candidate.sourceRevision ?? "") ||
    candidate.aabSha256 !== fundedCanaryAab.sha256 ||
    candidate.aabBytes !== fundedCanaryAab.bytes ||
    candidate.aabUploadCertificateSha256 !==
      fundedCanaryAabUploadCertificateSha256 ||
    candidate.installedAppSigningCertificateSha256 !==
      fundedCanaryInstalledAppSigningCertificateSha256 ||
    candidate.generatedApksSha256 !== fundedCanaryGeneratedApks.sha256 ||
    candidate.artifactIdentityReceiptSha256 !==
      artifactIdentityEvidence.sha256 ||
    candidate.installedCandidateBindingSha256 !==
      installedCandidateBindingSha256 ||
    androidProductionSigning.status !== "qualified" ||
    androidProductionSigning.releaseEnabled !== true ||
    androidProductionSigning.retainedProductionCertificateMatched !== true ||
    androidProductionSigning.signedBundleCertificateMatched !== true ||
    androidProductionSigning.playAppSigningContinuityReviewed !== true ||
    runtime.sourceRevision !== SORA2_REVISION ||
    runtime.specVersion !== 130 ||
    runtime.transactionVersion !== 130 ||
    runtime.genesisHash !==
      "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5" ||
    runtime.metadataSha256 !==
      "2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf" ||
    runtime.typesSha256 !==
      "e87760d7a566d1b1b3d21a1e76ad70990fd54e14e6af3ba27dd4440461063601" ||
    network.networkId !== networkId ||
    network.chainId !== expectedNetwork.chainId ||
    network.i105Discriminant !== expectedNetwork.i105Discriminant ||
    network.toriiBaseUrl !== expectedNetwork.toriiBaseUrl ||
    network.explorerBaseUrl !== expectedNetwork.explorerBaseUrl ||
    network.assetSymbol !== "XOR" ||
    network.xorAssetAlias !== "xor#universal" ||
    network.isTestnet !== expectedNetwork.isTestnet
  ) {
    return false;
  }

  const signerBindingSha256 = canonicalSha256([
    `status=${signer.status}`,
    `adapterType=${signer.adapterType}`,
    `adapterSourcePath=${signer.adapterSourcePath}`,
    `adapterSourceSha256=${signer.adapterSourceSha256}`,
    `artifactCoordinate=${signer.artifactCoordinate}`,
    `artifactSha256=${signer.artifactSha256}`,
    `artifactBytes=${signer.artifactBytes}`,
    `requiredNativeAbi=${signer.requiredNativeAbi}`,
    `observedNativeAbi=${signer.observedNativeAbi}`,
    `exportInventorySha256=${signer.exportInventorySha256}`,
    `provenanceReceiptPath=${signer.provenanceReceiptPath}`,
    `provenanceReceiptSha256=${signer.provenanceReceiptSha256}`,
    `nativeCanaryReceiptSha256=${signer.nativeCanaryReceiptSha256}`,
    `androidSecurityProvider=${signer.androidSecurityProvider}`,
  ]);
  if (
    signer.status !== "qualified" ||
    signer.adapterType !== productionSignerBinding.adapterType ||
    signer.adapterType.includes("Unavailable") ||
    signer.adapterSourcePath !== productionSignerBinding.adapterSourcePath ||
    signer.adapterSourceSha256 !==
      productionSignerBinding.adapterSourceSha256 ||
    signer.artifactCoordinate !== productionSignerBinding.artifactCoordinate ||
    signer.artifactSha256 !== productionSignerBinding.artifactSha256 ||
    fundedCanaryStagedJar === null ||
    signer.artifactSha256 !== fundedCanaryStagedJar.sha256 ||
    signer.artifactBytes !== fundedCanaryStagedJar.bytes ||
    signer.requiredNativeAbi !== 21 ||
    signer.observedNativeAbi !== 21 ||
    signer.exportInventorySha256 !==
      fundedCanaryNativeExportInventorySha256 ||
    signer.provenanceReceiptPath !== fundedCanarySignerProvenancePath ||
    signer.provenanceReceiptSha256 !==
      fundedCanarySignerProvenanceSha256 ||
    signer.nativeCanaryReceiptSha256 !==
      nativeCanaryQualificationEvidence.receiptSha256 ||
    fundedCanaryNativeReceipt?.sha256 !== signer.nativeCanaryReceiptSha256 ||
    signer.bindingSha256 !== signerBindingSha256 ||
    signer.androidSecurityProvider !== "AndroidKeyStore"
  ) {
    return false;
  }

  if (
    flags.snapshotObservedAtEpochSeconds !==
      canaryPiReceipt?.checkedAtEpochSeconds ||
    flags.snapshotSha256 !== canaryCapabilitySha256 ||
    flags.sourceReceiptSha256 !== canaryPiRecord?.sha256 ||
    !canaryPiReceiptQualified ||
    flags.nexusAvailable !==
      canaryPiReceipt.capabilities.nexusAvailable ||
    flags.nexusSendsAvailable !==
      canaryPiReceipt.capabilities.nexusSendsAvailable ||
    flags.polkamarktVisible !==
      canaryPiReceipt.capabilities.polkamarktVisible ||
    flags.polkamarktMutationsAvailable !==
      canaryPiReceipt.capabilities.polkamarktMutationsAvailable ||
    flags.tairaDefaultVisible !==
      canaryPiReceipt.capabilities.tairaDefaultVisible ||
    flags.nexusAvailable !== true ||
    flags.nexusSendsAvailable !== true ||
    flags.localNexusSendsQualified !== true ||
    typeof flags.tairaPreferenceIsExplicit !== "boolean" ||
    typeof flags.tairaEffectiveVisible !== "boolean" ||
    (networkId === "taira" &&
      (flags.tairaPreferenceIsExplicit !== true ||
        flags.tairaEffectiveVisible !== true))
  ) {
    return false;
  }

  const candidateBindingSha256 = canonicalSha256([
    `contractId=${receipt.contractId}`,
    `platform=${receipt.platform}`,
    `applicationId=${candidate.applicationId}`,
    `buildVariant=${candidate.buildVariant}`,
    `versionName=${candidate.versionName}`,
    `versionCode=${candidate.versionCode}`,
    `sourceRevision=${candidate.sourceRevision}`,
    `aabSha256=${candidate.aabSha256}`,
    `aabBytes=${candidate.aabBytes}`,
    `aabUploadCertificateSha256=${candidate.aabUploadCertificateSha256}`,
    `installedAppSigningCertificateSha256=${candidate.installedAppSigningCertificateSha256}`,
    `generatedApksSha256=${candidate.generatedApksSha256}`,
    `artifactIdentityReceiptSha256=${candidate.artifactIdentityReceiptSha256}`,
    `installedCandidateBindingSha256=${candidate.installedCandidateBindingSha256}`,
    `sora2SourceRevision=${runtime.sourceRevision}`,
    `runtimeSpecVersion=${runtime.specVersion}`,
    `runtimeTransactionVersion=${runtime.transactionVersion}`,
    `runtimeGenesisHash=${runtime.genesisHash}`,
    `runtimeMetadataSha256=${runtime.metadataSha256}`,
    `runtimeTypesSha256=${runtime.typesSha256}`,
    `featureFlagSnapshotObservedAtEpochSeconds=${flags.snapshotObservedAtEpochSeconds}`,
    `featureFlagSnapshotSha256=${flags.snapshotSha256}`,
    `featureFlagSourceReceiptSha256=${flags.sourceReceiptSha256}`,
    `nexusAvailable=${flags.nexusAvailable}`,
    `nexusSendsAvailable=${flags.nexusSendsAvailable}`,
    `polkamarktVisible=${flags.polkamarktVisible}`,
    `polkamarktMutationsAvailable=${flags.polkamarktMutationsAvailable}`,
    `tairaDefaultVisible=${flags.tairaDefaultVisible}`,
    `tairaPreferenceIsExplicit=${flags.tairaPreferenceIsExplicit}`,
    `tairaEffectiveVisible=${flags.tairaEffectiveVisible}`,
    `localNexusSendsQualified=${flags.localNexusSendsQualified}`,
    `signerBindingSha256=${signer.bindingSha256}`,
    `finalityBindingSha256=${finality.bindingSha256}`,
    `networkId=${network.networkId}`,
    `chainId=${network.chainId}`,
    `i105Discriminant=${network.i105Discriminant}`,
    `toriiBaseUrl=${network.toriiBaseUrl}`,
    `explorerBaseUrl=${network.explorerBaseUrl}`,
    `isTestnet=${network.isTestnet}`,
  ]);
  if (candidate.candidateBindingSha256 !== candidateBindingSha256) {
    return false;
  }

  const approvalEvidence = fundedCanaryExternalJson(
    `${environmentPrefix}_FUNDED_CANARY_APPROVAL_RECEIPT`,
    fundedCanaryMaximumReceiptBytes,
  );
  const lowValuePolicy = fundedCanaryExternalJson(
    `${environmentPrefix}_FUNDED_CANARY_LOW_VALUE_POLICY`,
    fundedCanaryMaximumReceiptBytes,
  );
  const executionEvidence = fundedCanaryExternalJson(
    `${environmentPrefix}_FUNDED_CANARY_EVIDENCE_BUNDLE`,
    fundedCanaryMaximumEvidenceBytes,
  );
  const consumptionEvidence = fundedCanaryExternalJson(
    `${environmentPrefix}_FUNDED_CANARY_CONSUMPTION_RECEIPT`,
    fundedCanaryMaximumReceiptBytes,
  );
  if (
    approvalEvidence === null ||
    lowValuePolicy === null ||
    executionEvidence === null ||
    consumptionEvidence === null ||
    finalityManifestEvidence === null ||
    networkTrustContextEvidence === null ||
    nativeFinalityCanaryEvidence === null ||
    approval.approvalReferenceSha256 !== approvalEvidence.sha256 ||
    approval.lowValuePolicySha256 !== lowValuePolicy.sha256 ||
    execution.evidenceBundleSha256 !== executionEvidence.sha256 ||
    execution.consumptionReceiptSha256 !== consumptionEvidence.sha256
  ) {
    return false;
  }
  const approvalRecord = approvalEvidence.value;
  const policyRecord = lowValuePolicy.value;
  const executionRecord = executionEvidence.value;
  const consumptionRecord = consumptionEvidence.value;
  if (
    !fundedCanaryStringsBounded(approvalRecord) ||
    !fundedCanaryStringsBounded(policyRecord) ||
    !fundedCanaryStringsBounded(executionRecord) ||
    !hasExactKeys(approvalRecord, [
      "schemaVersion",
      "contractId",
      "platform",
      "networkId",
      "candidateAabSha256",
      "candidateBindingSha256",
      "lowValuePolicySha256",
      "approvalNonce",
      "approved",
      "approvedAtEpochSeconds",
      "expiresAtEpochSeconds",
      "approverRole",
      "independentApproverRole",
      "dualControlReviewed",
      "releaseOperatorKeyId",
      "releaseOperatorSignatureBase64",
      "independentApproverKeyId",
      "independentApproverSignatureBase64",
      "privacy",
    ]) ||
    approvalRecord.schemaVersion !== 1 ||
    approvalRecord.contractId !==
      "sora-android-funded-nexus-canary-approval-v1" ||
    approvalRecord.platform !== "android" ||
    approvalRecord.networkId !== networkId ||
    approvalRecord.candidateAabSha256 !== candidate.aabSha256 ||
    approvalRecord.candidateBindingSha256 !== candidateBindingSha256 ||
    approvalRecord.lowValuePolicySha256 !== lowValuePolicy.sha256 ||
    approvalRecord.approvalNonce !== approval.approvalNonce ||
    !isFundedCanarySha256(approvalRecord.approvalNonce) ||
    approvalRecord.approved !== true ||
    approvalRecord.approvedAtEpochSeconds !== approval.approvedAtEpochSeconds ||
    approvalRecord.expiresAtEpochSeconds !== approval.expiresAtEpochSeconds ||
    approvalRecord.approverRole !== approval.approverRole ||
    approvalRecord.independentApproverRole !==
      approval.independentApproverRole ||
    approvalRecord.dualControlReviewed !== true ||
    approvalRecord.releaseOperatorKeyId !==
      fundedCanaryTrust.authorities.releaseOperator.keyId ||
    approvalRecord.independentApproverKeyId !==
      fundedCanaryTrust.authorities.independentApprover.keyId ||
    !fundedCanaryEvidencePrivacyQualified(approvalRecord.privacy) ||
    !hasExactKeys(policyRecord, [
      "schemaVersion",
      "contractId",
      "platform",
      "networkId",
      "assetAlias",
      "maximumAmountCanonical",
      "maximumFeeCanonical",
      "maximumExecutions",
      "validFromEpochSeconds",
      "validUntilEpochSeconds",
      "reviewedAtEpochSeconds",
      "reviewed",
      "privacy",
    ]) ||
    policyRecord.schemaVersion !== 1 ||
    policyRecord.contractId !==
      "sora-android-funded-nexus-low-value-policy-v1" ||
    policyRecord.platform !== "android" ||
    policyRecord.networkId !== networkId ||
    policyRecord.assetAlias !== "xor#universal" ||
    !isFundedCanaryCanonicalPositiveQuantity(
      policyRecord.maximumAmountCanonical,
    ) ||
    !isFundedCanaryCanonicalPositiveQuantity(
      policyRecord.maximumFeeCanonical,
    ) ||
    compareFundedCanaryCanonicalQuantities(
      policyRecord.maximumAmountCanonical,
      expectedNetwork.hardMaximumAmountCanonical,
    ) > 0 ||
    compareFundedCanaryCanonicalQuantities(
      policyRecord.maximumFeeCanonical,
      expectedNetwork.hardMaximumFeeCanonical,
    ) > 0 ||
    policyRecord.maximumExecutions !== 1 ||
    !isFundedCanaryEpoch(policyRecord.validFromEpochSeconds) ||
    !isFundedCanaryEpoch(policyRecord.validUntilEpochSeconds) ||
    !isFundedCanaryEpoch(policyRecord.reviewedAtEpochSeconds) ||
    policyRecord.validFromEpochSeconds > policyRecord.validUntilEpochSeconds ||
    policyRecord.reviewedAtEpochSeconds < policyRecord.validFromEpochSeconds ||
    policyRecord.reviewedAtEpochSeconds > approvalRecord.approvedAtEpochSeconds ||
    !validateFundedCanaryV4PolicyChronology({
      policy: policyRecord,
      approval: approvalRecord,
    }) ||
    policyRecord.reviewed !== true ||
    !fundedCanaryEvidencePrivacyQualified(policyRecord.privacy) ||
    !hasExactKeys(executionRecord, [
      "schemaVersion",
      "contractId",
      "platform",
      "networkId",
      "canaryRunId",
      "candidateBindingSha256",
      "artifactIdentityReceiptSha256",
      "installedCandidateBindingSha256",
      "approvalReferenceSha256",
      "lowValuePolicySha256",
      "approvalNonce",
      "consumptionReceiptSha256",
      "startedAtEpochSeconds",
      "completedAtEpochSeconds",
      "sendAmountCanonical",
      "feeAmountCanonical",
      "submissionAttemptCount",
      "automaticRetryAttempted",
      "ambiguousSubmissionObserved",
      "terminalStatus",
      "stages",
      "independentReviewerRole",
      "independentReviewerKeyId",
      "independentReviewedAtEpochSeconds",
      "independentReviewCompleted",
      "independentReviewerSignatureBase64",
      "privacy",
    ]) ||
    executionRecord.schemaVersion !== 1 ||
    executionRecord.contractId !==
      "sora-android-funded-nexus-canary-evidence-v1" ||
    executionRecord.platform !== "android" ||
    executionRecord.networkId !== networkId ||
    executionRecord.canaryRunId !== execution.canaryRunId ||
    !isFundedCanarySha256(executionRecord.canaryRunId) ||
    executionRecord.candidateBindingSha256 !== candidateBindingSha256 ||
    executionRecord.artifactIdentityReceiptSha256 !==
      artifactIdentityEvidence.sha256 ||
    executionRecord.installedCandidateBindingSha256 !==
      installedCandidateBindingSha256 ||
    executionRecord.approvalReferenceSha256 !== approvalEvidence.sha256 ||
    executionRecord.lowValuePolicySha256 !== lowValuePolicy.sha256 ||
    executionRecord.approvalNonce !== approval.approvalNonce ||
    executionRecord.consumptionReceiptSha256 !== consumptionEvidence.sha256 ||
    executionRecord.startedAtEpochSeconds !== execution.startedAtEpochSeconds ||
    executionRecord.completedAtEpochSeconds !==
      execution.completedAtEpochSeconds ||
    executionRecord.sendAmountCanonical !== execution.sendAmountCanonical ||
    executionRecord.feeAmountCanonical !== execution.feeAmountCanonical ||
    executionRecord.submissionAttemptCount !==
      execution.submissionAttemptCount ||
    executionRecord.automaticRetryAttempted !==
      execution.automaticRetryAttempted ||
    executionRecord.ambiguousSubmissionObserved !==
      execution.ambiguousSubmissionObserved ||
    executionRecord.terminalStatus !== execution.terminalStatus ||
    !hasExactKeys(executionRecord.stages, fundedCanaryStageNames) ||
    executionRecord.independentReviewerRole !==
      fundedCanaryTrust.authorities.independentReviewer.role ||
    executionRecord.independentReviewerKeyId !==
      fundedCanaryTrust.authorities.independentReviewer.keyId ||
    !isFundedCanaryEpoch(
      executionRecord.independentReviewedAtEpochSeconds,
    ) ||
    executionRecord.independentReviewCompleted !== true ||
    !fundedCanaryEvidencePrivacyQualified(executionRecord.privacy)
  ) {
    return false;
  }

  const approvalProjection = [
    `schemaVersion=${approvalRecord.schemaVersion}`,
    `contractId=${approvalRecord.contractId}`,
    `platform=${approvalRecord.platform}`,
    `networkId=${approvalRecord.networkId}`,
    `candidateAabSha256=${approvalRecord.candidateAabSha256}`,
    `candidateBindingSha256=${approvalRecord.candidateBindingSha256}`,
    `lowValuePolicySha256=${approvalRecord.lowValuePolicySha256}`,
    `approvalNonce=${approvalRecord.approvalNonce}`,
    `approved=${approvalRecord.approved}`,
    `approvedAtEpochSeconds=${approvalRecord.approvedAtEpochSeconds}`,
    `expiresAtEpochSeconds=${approvalRecord.expiresAtEpochSeconds}`,
    `approverRole=${approvalRecord.approverRole}`,
    `independentApproverRole=${approvalRecord.independentApproverRole}`,
    `dualControlReviewed=${approvalRecord.dualControlReviewed}`,
    `releaseOperatorKeyId=${approvalRecord.releaseOperatorKeyId}`,
    `independentApproverKeyId=${approvalRecord.independentApproverKeyId}`,
    ...fundedCanaryPrivacyProjectionLines(approvalRecord.privacy),
  ];
  if (
    !verifyFundedCanarySignature(
      "releaseOperator",
      approvalProjection,
      approvalRecord.releaseOperatorSignatureBase64,
    ) ||
    !verifyFundedCanarySignature(
      "independentApprover",
      approvalProjection,
      approvalRecord.independentApproverSignatureBase64,
    )
  ) {
    return false;
  }

  if (
    !fundedCanaryStringsBounded(consumptionRecord) ||
    !hasExactKeys(consumptionRecord, [
      "schemaVersion",
      "contractId",
      "status",
      "platform",
      "ledgerStoreId",
      "networkId",
      "approvalNonce",
      "canaryRunId",
      "candidateBindingSha256",
      "approvalReferenceSha256",
      "reservedAtEpochSeconds",
      "finalizedAtEpochSeconds",
      "reservationStoreVersion",
      "finalizationStoreVersion",
      "priorReservationCount",
      "submissionHandoffCount",
      "ambiguousAttemptCount",
      "atomicReservationAcquired",
      "ledgerAuthorityKeyId",
      "ledgerAuthoritySignatureBase64",
      "privacy",
    ]) ||
    consumptionRecord.schemaVersion !== 2 ||
    consumptionRecord.contractId !==
      "sora-android-funded-nexus-approval-consumption-v2" ||
    consumptionRecord.status !== "completed" ||
    consumptionRecord.platform !== "android" ||
    consumptionRecord.ledgerStoreId !== fundedCanaryTrust.ledgerStoreId ||
    consumptionRecord.networkId !== networkId ||
    consumptionRecord.approvalNonce !== approval.approvalNonce ||
    consumptionRecord.canaryRunId !== execution.canaryRunId ||
    consumptionRecord.candidateBindingSha256 !== candidateBindingSha256 ||
    consumptionRecord.approvalReferenceSha256 !== approvalEvidence.sha256 ||
    !isFundedCanaryEpoch(consumptionRecord.reservedAtEpochSeconds) ||
    !isFundedCanaryEpoch(consumptionRecord.finalizedAtEpochSeconds) ||
    !Number.isSafeInteger(consumptionRecord.reservationStoreVersion) ||
    consumptionRecord.reservationStoreVersion < 1 ||
    !Number.isSafeInteger(consumptionRecord.finalizationStoreVersion) ||
    consumptionRecord.finalizationStoreVersion <=
      consumptionRecord.reservationStoreVersion ||
    consumptionRecord.priorReservationCount !== 0 ||
    consumptionRecord.submissionHandoffCount !== 1 ||
    consumptionRecord.ambiguousAttemptCount !== 0 ||
    consumptionRecord.atomicReservationAcquired !== true ||
    consumptionRecord.ledgerAuthorityKeyId !==
      fundedCanaryTrust.authorities.consumptionLedger.keyId ||
    !fundedCanaryEvidencePrivacyQualified(consumptionRecord.privacy)
  ) {
    return false;
  }
  const consumptionProjection = [
    `schemaVersion=${consumptionRecord.schemaVersion}`,
    `contractId=${consumptionRecord.contractId}`,
    `status=${consumptionRecord.status}`,
    `platform=${consumptionRecord.platform}`,
    `ledgerStoreId=${consumptionRecord.ledgerStoreId}`,
    `networkId=${consumptionRecord.networkId}`,
    `approvalNonce=${consumptionRecord.approvalNonce}`,
    `canaryRunId=${consumptionRecord.canaryRunId}`,
    `candidateBindingSha256=${consumptionRecord.candidateBindingSha256}`,
    `approvalReferenceSha256=${consumptionRecord.approvalReferenceSha256}`,
    `reservedAtEpochSeconds=${consumptionRecord.reservedAtEpochSeconds}`,
    `finalizedAtEpochSeconds=${consumptionRecord.finalizedAtEpochSeconds}`,
    `reservationStoreVersion=${consumptionRecord.reservationStoreVersion}`,
    `finalizationStoreVersion=${consumptionRecord.finalizationStoreVersion}`,
    `priorReservationCount=${consumptionRecord.priorReservationCount}`,
    `submissionHandoffCount=${consumptionRecord.submissionHandoffCount}`,
    `ambiguousAttemptCount=${consumptionRecord.ambiguousAttemptCount}`,
    `atomicReservationAcquired=${consumptionRecord.atomicReservationAcquired}`,
    `ledgerAuthorityKeyId=${consumptionRecord.ledgerAuthorityKeyId}`,
    ...fundedCanaryPrivacyProjectionLines(consumptionRecord.privacy),
  ];
  if (
    !verifyFundedCanarySignature(
      "consumptionLedger",
      consumptionProjection,
      consumptionRecord.ledgerAuthoritySignatureBase64,
    )
  ) {
    return false;
  }

  if (
    approval.approved !== true ||
    approval.scopeNetworkId !== networkId ||
    !isFundedCanarySha256(approval.approvalNonce) ||
    !isFundedCanarySha256(approval.approvalReferenceSha256) ||
    !isFundedCanarySha256(approval.lowValuePolicySha256) ||
    !isFundedCanaryEpoch(approval.approvedAtEpochSeconds) ||
    !isFundedCanaryEpoch(approval.expiresAtEpochSeconds) ||
    approval.approverRole !==
      fundedCanaryTrust.authorities.releaseOperator.role ||
    approval.independentApproverRole !==
      fundedCanaryTrust.authorities.independentApprover.role ||
    approval.dualControlReviewed !== true ||
    !isFundedCanarySha256(execution.canaryRunId) ||
    !isFundedCanarySha256(execution.evidenceBundleSha256) ||
    !isFundedCanarySha256(execution.consumptionReceiptSha256) ||
    !isFundedCanaryCanonicalPositiveQuantity(execution.sendAmountCanonical) ||
    !isFundedCanaryCanonicalPositiveQuantity(execution.feeAmountCanonical) ||
    compareFundedCanaryCanonicalQuantities(
      execution.sendAmountCanonical,
      policyRecord.maximumAmountCanonical,
    ) > 0 ||
    compareFundedCanaryCanonicalQuantities(
      execution.feeAmountCanonical,
      policyRecord.maximumFeeCanonical,
    ) > 0 ||
    !isFundedCanaryEpoch(execution.startedAtEpochSeconds) ||
    !isFundedCanaryEpoch(execution.completedAtEpochSeconds) ||
    !isFundedCanaryEpoch(receipt.receiptRecordedAtEpochSeconds) ||
    approval.approvedAtEpochSeconds >= execution.startedAtEpochSeconds ||
    approval.approvedAtEpochSeconds >=
      consumptionRecord.reservedAtEpochSeconds ||
    approval.expiresAtEpochSeconds < execution.completedAtEpochSeconds ||
    policyRecord.validFromEpochSeconds > execution.startedAtEpochSeconds ||
    policyRecord.validFromEpochSeconds >
      consumptionRecord.reservedAtEpochSeconds ||
    policyRecord.validUntilEpochSeconds < execution.completedAtEpochSeconds ||
    execution.startedAtEpochSeconds >= execution.completedAtEpochSeconds ||
    execution.completedAtEpochSeconds - execution.startedAtEpochSeconds >
      fundedCanaryMaximumDurationSeconds ||
    receipt.receiptRecordedAtEpochSeconds < execution.completedAtEpochSeconds ||
    consumptionRecord.reservedAtEpochSeconds <
      execution.fee.observedAtEpochSeconds ||
    consumptionRecord.reservedAtEpochSeconds >
      execution.signing.observedAtEpochSeconds ||
    consumptionRecord.finalizedAtEpochSeconds <
      execution.completedAtEpochSeconds ||
    consumptionRecord.finalizedAtEpochSeconds >=
      executionRecord.independentReviewedAtEpochSeconds ||
    executionRecord.independentReviewerRole === approval.approverRole ||
    executionRecord.independentReviewerRole ===
      approval.independentApproverRole ||
    executionRecord.independentReviewedAtEpochSeconds <=
      execution.completedAtEpochSeconds ||
    executionRecord.independentReviewedAtEpochSeconds >=
      receipt.receiptRecordedAtEpochSeconds ||
    receipt.receiptRecordedAtEpochSeconds - execution.completedAtEpochSeconds >
      fundedCanaryMaximumRecordDelaySeconds ||
    receipt.receiptRecordedAtEpochSeconds >
      fundedCanaryEvaluationEpoch + fundedCanaryMaximumFutureSkewSeconds ||
    fundedCanaryEvaluationEpoch - execution.completedAtEpochSeconds >
      fundedCanaryMaximumAgeSeconds ||
    fundedCanaryEvaluationEpoch - flags.snapshotObservedAtEpochSeconds >
      fundedCanaryMaximumAgeSeconds ||
    execution.amountWithinApprovedLimit !== true ||
    execution.submissionAttemptCount !== 1 ||
    execution.automaticRetryAttempted !== false ||
    execution.ambiguousSubmissionObserved !== false ||
    execution.terminalStatus !== "committed"
  ) {
    return false;
  }

  for (const [stageName, assertions] of Object.entries(
    fundedCanaryStageAssertions,
  )) {
    const stage = execution[stageName];
    const evidenceStage = executionRecord.stages[stageName];
    const extraStageKeys =
      stageName === "terminalStatusReadback"
        ? FUNDED_CANARY_TERMINAL_EXTRA_KEYS
        : stageName === "finalityReadback"
          ? FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS
          : [];
    const stageProjection = [
      "contractId=sora-android-funded-nexus-canary-stage-v1",
      `canaryRunId=${execution.canaryRunId}`,
      `candidateBindingSha256=${candidateBindingSha256}`,
      `networkId=${networkId}`,
      `stageName=${stageName}`,
      `status=${stage.status}`,
      `observedAtEpochSeconds=${stage.observedAtEpochSeconds}`,
      ...extraStageKeys.map((field) => `${field}=${stage[field]}`),
      ...Object.keys(assertions).map((field) => `${field}=${stage[field]}`),
    ];
    const expectedStageEvidenceSha256 = canonicalSha256(stageProjection);
    if (
      !hasExactKeys(evidenceStage, [
        "status",
        "observedAtEpochSeconds",
        "evidenceSha256",
        ...extraStageKeys,
        ...Object.keys(assertions),
      ]) ||
      stage.status !== "qualified" ||
      !isFundedCanaryEpoch(stage.observedAtEpochSeconds) ||
      stage.observedAtEpochSeconds < execution.startedAtEpochSeconds ||
      stage.observedAtEpochSeconds > execution.completedAtEpochSeconds ||
      stage.evidenceSha256 !== expectedStageEvidenceSha256 ||
      !Object.keys(stage).every(
        (field) => evidenceStage[field] === stage[field],
      ) ||
      !Object.entries(assertions).every(
        ([field, expected]) => stage[field] === expected,
      )
    ) {
      return false;
    }
  }
  if (
    fundedCanaryStageOrderPairs.some(
      ([earlier, later]) =>
        execution[earlier].observedAtEpochSeconds >=
        execution[later].observedAtEpochSeconds,
    )
  ) {
    return false;
  }

  const executionProjection = [
    `schemaVersion=${executionRecord.schemaVersion}`,
    `contractId=${executionRecord.contractId}`,
    `platform=${executionRecord.platform}`,
    `networkId=${executionRecord.networkId}`,
    `canaryRunId=${executionRecord.canaryRunId}`,
    `candidateBindingSha256=${executionRecord.candidateBindingSha256}`,
    `artifactIdentityReceiptSha256=${executionRecord.artifactIdentityReceiptSha256}`,
    `installedCandidateBindingSha256=${executionRecord.installedCandidateBindingSha256}`,
    `approvalReferenceSha256=${executionRecord.approvalReferenceSha256}`,
    `lowValuePolicySha256=${executionRecord.lowValuePolicySha256}`,
    `approvalNonce=${executionRecord.approvalNonce}`,
    `consumptionReceiptSha256=${executionRecord.consumptionReceiptSha256}`,
    `startedAtEpochSeconds=${executionRecord.startedAtEpochSeconds}`,
    `completedAtEpochSeconds=${executionRecord.completedAtEpochSeconds}`,
    `sendAmountCanonical=${executionRecord.sendAmountCanonical}`,
    `feeAmountCanonical=${executionRecord.feeAmountCanonical}`,
    `submissionAttemptCount=${executionRecord.submissionAttemptCount}`,
    `automaticRetryAttempted=${executionRecord.automaticRetryAttempted}`,
    `ambiguousSubmissionObserved=${executionRecord.ambiguousSubmissionObserved}`,
    `terminalStatus=${executionRecord.terminalStatus}`,
    ...fundedCanaryStageNames.flatMap((stageName) =>
      [
        "status",
        "observedAtEpochSeconds",
        "evidenceSha256",
        ...(stageName === "terminalStatusReadback"
          ? FUNDED_CANARY_TERMINAL_EXTRA_KEYS
          : []),
        ...(stageName === "finalityReadback"
          ? FUNDED_CANARY_FINALITY_STAGE_EXTRA_KEYS
          : []),
        ...Object.keys(fundedCanaryStageAssertions[stageName]),
      ].map(
        (field) =>
          `stages.${stageName}.${field}=${executionRecord.stages[stageName][field]}`,
      ),
    ),
    `independentReviewerRole=${executionRecord.independentReviewerRole}`,
    `independentReviewerKeyId=${executionRecord.independentReviewerKeyId}`,
    `independentReviewedAtEpochSeconds=${executionRecord.independentReviewedAtEpochSeconds}`,
    `independentReviewCompleted=${executionRecord.independentReviewCompleted}`,
    ...fundedCanaryPrivacyProjectionLines(executionRecord.privacy),
  ];
  if (
    !verifyFundedCanarySignature(
      "independentReviewer",
      executionProjection,
      executionRecord.independentReviewerSignatureBase64,
    )
  ) {
    return false;
  }

  const finalityManifest = finalityManifestEvidence.value;
  const networkTrustContext = networkTrustContextEvidence.value;
  const nativeFinalityCanary = nativeFinalityCanaryEvidence.value;
  if (
    [finalityManifest, networkTrustContext, nativeFinalityCanary].some(
      (value) =>
        value === null ||
        typeof value !== "object" ||
        Array.isArray(value) ||
        !fundedCanaryStringsBounded(value),
    )
  ) {
    return false;
  }
  const finalityEvidenceSignaturesQualified =
    finalityManifest.verifierArtifactSha256 ===
      fundedCanaryFinalityVerifierArtifact.sha256 &&
    nativeFinalityCanary.reviewedPlatformArtifactSha256 ===
      fundedCanaryFinalityPlatformArtifact.sha256 &&
    finalityManifest.independentReviewerKeyId ===
      fundedCanaryTrust.authorities.independentReviewer.keyId &&
    networkTrustContext.independentReviewerKeyId ===
      fundedCanaryTrust.authorities.independentReviewer.keyId &&
    nativeFinalityCanary.independentReviewerKeyId ===
      fundedCanaryTrust.authorities.independentReviewer.keyId &&
    verifyFundedCanarySignature(
      "independentReviewer",
      fundedCanaryV4FinalityManifestProjection(finalityManifest),
      finalityManifest.independentReviewerSignatureBase64,
    ) &&
    verifyFundedCanarySignature(
      "independentReviewer",
      fundedCanaryV4TrustContextProjection(networkTrustContext),
      networkTrustContext.independentReviewerSignatureBase64,
    ) &&
    verifyFundedCanarySignature(
      "independentReviewer",
      fundedCanaryV4NativeFinalityProjection(nativeFinalityCanary),
      nativeFinalityCanary.independentReviewerSignatureBase64,
    );
  if (
    !finalityEvidenceSignaturesQualified ||
    !validateFundedCanaryV4Proof({
      receipt,
      evidenceRecord: executionRecord,
      readiness: irohaPin,
      readinessSha256: fundedCanarySignerProvenanceSha256,
      finalityManifest,
      finalityManifestSha256: finalityManifestEvidence.sha256,
      networkTrustContext,
      networkTrustContextSha256: networkTrustContextEvidence.sha256,
      nativeFinalityCanary,
      nativeFinalityCanarySha256: nativeFinalityCanaryEvidence.sha256,
      stageAssertions: fundedCanaryStageAssertions,
      expectedNetwork,
    })
  ) {
    return false;
  }

  return (
    hasExactKeys(qualification, [
      "candidateIdentityMatched",
      "runtimeIdentityMatched",
      "networkIdentityMatched",
      "currentChainIdentityMatched",
      "featureFlagSnapshotMatched",
      "signerIdentityMatched",
      "finalityIdentityMatched",
      "operatorApprovalValid",
      "freshnessQualified",
      "allExecutionStagesQualified",
      "independentReviewCompleted",
    ]) &&
    Object.values(qualification).every((value) => value === true)
  );
};

const fundedCanaryQualifiedRecords = Object.create(null);
for (const networkId of ["taira", "minamoto"]) {
  const receiptEnvironmentName =
    networkId === "taira"
      ? "TAIRA_FUNDED_CANARY_RECEIPT_PATH"
      : "MINAMOTO_FUNDED_CANARY_RECEIPT_PATH";
  const receiptPath =
    process.env[receiptEnvironmentName] ??
    join(root, fundedCanaryTemplatePaths[networkId]);
  const claimedRecord = isAbsolute(receiptPath)
    ? readStrictJsonFile(receiptPath, fundedCanaryMaximumReceiptBytes)
    : null;
  const claimedReceipt = claimedRecord?.value ?? null;
  const qualified = fundedCanaryQualified(networkId, claimedRecord);
  fundedCanaryQualifiedRecords[networkId] =
    qualified && claimedRecord !== null
      ? {
          receiptSha256: claimedRecord.sha256,
          receipt: claimedReceipt,
        }
      : null;
  if (claimedReceipt?.status === "qualified") {
    assert(
      qualified,
      `${networkId.toUpperCase()}_FUNDED_CANARY_V4_RECEIPT_INVALID`,
    );
  }
  block(
    !qualified,
    `${networkId.toUpperCase()}_FUNDED_CANARY_NOT_QUALIFIED`,
  );
}
const fundedCanaryPairDistinct = validateFundedCanaryV4PairDistinct({
  taira: fundedCanaryQualifiedRecords.taira,
  minamoto: fundedCanaryQualifiedRecords.minamoto,
});
assert(
  fundedCanaryQualifiedRecords.taira === null ||
    fundedCanaryQualifiedRecords.minamoto === null ||
    fundedCanaryPairDistinct,
  "FUNDED_CANARY_CROSS_NETWORK_EVIDENCE_REUSED",
);
block(
  !fundedCanaryPairDistinct,
  "FUNDED_CANARY_CROSS_NETWORK_PAIR_NOT_QUALIFIED",
);
const requiredRoomSchemas = [74, 75, 76, 77].map((version) => ({
  version,
  path: `core_db/schemas/jp.co.soramitsu.core_db.AppDatabase/${version}.json`,
}));
for (const schema of requiredRoomSchemas) {
  const presentAsRegularFile = isCanonicalRegularRepoFile(schema.path);
  block(
    !presentAsRegularFile,
    `ROOM_SCHEMA_${schema.version}_NOT_EXPORTED`,
  );
  if (presentAsRegularFile) {
    assert(
      json(schema.path).database?.version === schema.version,
      `ROOM_SCHEMA_${schema.version}_VERSION_MISMATCH`,
    );
  }
}
const migrationQualificationPath =
  "docs/modernization/qualification/android-migration-matrix.json";
const migrationQualificationAbsolutePath = join(root, migrationQualificationPath);
let authenticatedMigrationQualificationReceiptSha256 = null;
try {
  lintAndroidMigrationQualificationTemplates({ root });
} catch {
    assert(false, "ANDROID_MIGRATION_V7_V2_BLOCKED_TEMPLATES_INVALID");
}
if (
  strictRelease &&
  existsSync(migrationQualificationAbsolutePath) &&
  lstatSync(migrationQualificationAbsolutePath).isFile() &&
  !lstatSync(migrationQualificationAbsolutePath).isSymbolicLink()
) {
  const requiredAndroidMigrationChecks = [
    "singleAccountQualified",
    "multiAccountQualified",
    "successfulActivationQualified",
    "currentSchemaSnapshotQualified",
    "legacyEmptySuffixQualified",
    "verifiedPreImportBackupQualified",
    "legacySharedPreferencesImportQualified",
    "retainedDataStoreRestartQualified",
    "encryptedCiphertextParityQualified",
    "wrappedAesKeyParityQualified",
    "keystoreAliasContinuityQualified",
    "noCredentialRewriteQualified",
    "authenticatedEnvelopeTamperQualified",
    "wrappedKeyFailureQualified",
    "missingKeystoreAliasQualified",
    "twelveWordMnemonicQualified",
    "twentyFourWordMnemonicQualified",
    "retainedFifteenWordMnemonicQualified",
    "rawSeedQualified",
    "legacySecretQualified",
    "watchOnlyQualified",
    "missingOrCorruptSecretQualified",
    "encryptedStorageReadbackQualified",
    "sora2AddressParityQualified",
    "sora2SignatureParityQualified",
    "interruptedMigrationQualified",
    "reinstallUpgradeQualified",
    "rollbackQualified",
    "lowStorageQualified",
    "deleteJournalModeQualified",
    "walJournalModeQualified",
    "walSidecarByteParityQualified",
    "rawExecutionEvidenceReviewed",
    "rawExecutionInventoryQualified",
  ];
  const retainedRoomSchemaContractPaths = Array.from(
    { length: 19 },
    (_, index) =>
      `core_db/schemas/jp.co.soramitsu.core_db.AppDatabase/${index + 58}.json`,
  );
  const configuredDependencyEvidencePaths = [
    gradleDependencyProvenance.repositoryPolicy
      ?.sourceQualifiedVendorRepository?.sourceProvenancePath,
    gradleDependencyProvenance.repositoryPolicy
      ?.sourceQualifiedVendorRepository?.contentsManifestPath,
    gradleDependencyProvenance.dependencyVerification?.metadataPath,
    gradleDependencyProvenance.dependencyVerification?.metadataDigestPath,
    ...(gradleDependencyProvenance.dependencyLocking?.materializedInventory
      ?.lockFilePaths ?? []),
  ].filter((path) => typeof path === "string" && path.length > 0);
  const migrationQualificationContractPaths = [...new Set([
    ".github/workflows/production_release_qualification.yml",
    "scripts/run-encrypted-wallet-upgrade-qualification.sh",
    "scripts/run-migration-manager-production-path-qualification.sh",
    "scripts/verify-production-modernization.mjs",
    "scripts/verify-android-migration-qualification.mjs",
    "scripts/lib/android-migration-qualification-v7.mjs",
    "scripts/lib/android-migration-raw-evidence-v1.mjs",
    "scripts/lib/android-migration-controller-envelope-v1.mjs",
    "scripts/collect-android-migration-raw-evidence.mjs",
    "scripts/extract-android-migration-controller-envelope.mjs",
    "scripts/test-android-migration-raw-evidence-v1.mjs",
    "scripts/test-android-migration-controller-envelope-v1.mjs",
    "scripts/test-android-migration-qualification-v7-contract.mjs",
    "docs/modernization/production-release-checklist.md",
    "docs/modernization/qualification/android-migration-matrix-README.md",
    "docs/modernization/qualification/android-migration-matrix.blocked.json",
    "docs/modernization/qualification/android-migration-evidence.blocked.json",
    "docs/modernization/qualification/android-migration-trust.blocked.json",
    "app/src/main/java/jp/co/soramitsu/sora/SoraApp.kt",
    "app/src/androidTestQualification/java/jp/co/soramitsu/sora/splash/domain/MigrationManagerProductionPathQualificationTest.kt",
    "app/src/main/java/jp/co/soramitsu/sora/splash/domain/MigrationManager.kt",
    "app/src/main/java/jp/co/soramitsu/sora/splash/domain/SplashInteractor.kt",
    "app/src/qualification/AndroidManifest.xml",
    "app/src/qualification/java/jp/co/soramitsu/sora/qualification/MigrationQualificationApplication.kt",
    "app/src/test/java/jp/co/soramitsu/sora/splash/domain/MigrationManagerSafetyTest.kt",
    "common/src/main/java/jp/co/soramitsu/common/account/WalletMutationCoordinator.kt",
    "common/src/main/java/jp/co/soramitsu/common/account/WalletRecoveryCapabilityGate.kt",
    "common/src/main/java/jp/co/soramitsu/common/account/Sora2AddressCodec.kt",
    "common/src/main/java/jp/co/soramitsu/common/data/EncryptedPreferences.kt",
    "common/src/main/java/jp/co/soramitsu/common/data/SoraPreferences.kt",
    "common/src/main/java/jp/co/soramitsu/common/data/WalletPreferenceIntegrity.kt",
    "common/src/main/java/jp/co/soramitsu/common/data/WalletPreferenceKeys.kt",
    "common/src/main/java/jp/co/soramitsu/common/nexus/IrohaAddressCodec.kt",
    "common/src/main/java/jp/co/soramitsu/common/nexus/IrohaKeyDerivation.kt",
    "common/src/main/java/jp/co/soramitsu/common/nexus/NexusNetwork.kt",
    "common/src/main/java/jp/co/soramitsu/common/io/FileManager.kt",
    "common/src/main/java/jp/co/soramitsu/common/io/FileManagerImpl.kt",
    "common/src/main/java/jp/co/soramitsu/common/network/BoundedHttpTextClient.kt",
    "common/src/main/java/jp/co/soramitsu/common/network/StrictJsonDocumentAdmission.kt",
    "common/src/main/java/jp/co/soramitsu/common/util/EncryptionUtil.kt",
    "common/src/main/res/values/strings.xml",
    "common/src/test/java/jp/co/soramitsu/common/io/FileManagerAtomicEntryPolicyTest.kt",
    "common/src/androidTest/java/jp/co/soramitsu/common/io/FileManagerAtomicRecoveryTest.kt",
    "common/src/test/java/jp/co/soramitsu/common/network/BoundedHttpTextClientTest.kt",
    "common/src/test/java/jp/co/soramitsu/common/network/StrictJsonDocumentAdmissionTest.kt",
    "common/src/test/java/jp/co/soramitsu/common/nexus/IrohaKeyDerivationTest.kt",
    "common/src/test/java/jp/co/soramitsu/common/account/Sora2AddressCodecTest.kt",
    "common/src/test/resources/wallet-derivation-v1.json",
    "core_db/src/androidTest/java/jp/co/soramitsu/core_db/AppDatabaseV74SchemaFixture.kt",
    "core_db/src/androidTest/java/jp/co/soramitsu/core_db/AppDatabaseV75SchemaFixture.kt",
    "core_db/src/androidTest/java/jp/co/soramitsu/core_db/WalletIdentityMigration75Test.kt",
    "core_db/src/androidTest/java/jp/co/soramitsu/core_db/WalletUpgradeBackupTest.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/AppDatabase.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/WalletDeletionIntegrity.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/WalletMigrationIntegrity.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/WalletUpgradeBackup.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/dao/AccountDao.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/dao/WalletIdentityDao.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/WalletDeletionMigration75.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/WalletIdentityMigration74.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/Sora2PendingSubmissionMigration76.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/migrations/PendingNetworkTransactionChainMigration77.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/NetworkAccountLocal.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/PendingNetworkTransactionLocal.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/Sora2PendingSubmissionLocal.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/SoraAccountLocal.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/WalletDeletionOperationLocal.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/WalletDeletionTargetLocal.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/WalletIdentityLocal.kt",
    "core_db/src/main/java/jp/co/soramitsu/core_db/model/WalletMigrationJournalLocal.kt",
    "feature_account_api/src/main/java/jp/co/soramitsu/feature_account_api/domain/interfaces/CredentialsDatasource.kt",
    "feature_account_api/src/main/java/jp/co/soramitsu/feature_account_api/domain/interfaces/CredentialsRepository.kt",
    "feature_account_api/src/main/java/jp/co/soramitsu/feature_account_api/domain/interfaces/UserDatasource.kt",
    "feature_account_api/src/main/java/jp/co/soramitsu/feature_account_api/domain/interfaces/UserRepository.kt",
    "feature_account_impl/src/androidTest/java/jp/co/soramitsu/feature_account_impl/data/repository/datasource/EncryptedWalletMigrationStorageTest.kt",
    "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/CredentialsRepositoryImpl.kt",
    "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/di/AccountFeatureModule.kt",
    "feature_account_impl/src/test/java/jp/co/soramitsu/feature_account_impl/data/repository/CredentialsRepositoryTest.kt",
    "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/UserRepositoryImpl.kt",
    "feature_account_impl/src/test/java/jp/co/soramitsu/feature_account_impl/data/repository/UserRepositoryTest.kt",
    "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/datasource/PrefsCredentialsDatasource.kt",
    "feature_account_impl/src/main/java/jp/co/soramitsu/feature_account_impl/data/repository/datasource/PrefsUserDatasource.kt",
    "feature_wallet_api/src/main/java/jp/co/soramitsu/feature_wallet_api/domain/interfaces/WalletDatasource.kt",
    "feature_wallet_api/src/main/java/jp/co/soramitsu/feature_wallet_api/domain/interfaces/WalletInteractor.kt",
    "feature_wallet_api/src/main/java/jp/co/soramitsu/feature_wallet_api/domain/interfaces/WalletRepository.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/repository/WalletRepositoryImpl.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/repository/datasource/PrefsWalletDatasource.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/domain/WalletInteractorImpl.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/recovery/Sora2PendingRecoveryWorker.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPendingOverlayValidator.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPendingReconciler.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusTransactionCoordinator.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusSendQualification.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusTransactionSigner.kt",
    "feature_wallet_impl/src/main/java/jp/co/soramitsu/feature_wallet_impl/di/WalletFeatureModule.kt",
    "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/repository/WalletRepositoryTest.kt",
    "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/domain/WalletInteractorTest.kt",
    "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusFinalityCheckpointTest.kt",
    "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusPendingOverlayValidatorTest.kt",
    "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusSendQualificationTest.kt",
    "feature_wallet_impl/src/test/java/jp/co/soramitsu/feature_wallet_impl/data/nexus/NexusTransactionCoordinatorTest.kt",
    "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/presentation/txhistory/TransactionModel.kt",
    "feature_blockexplorer_impl/src/main/java/jp/co/soramitsu/feature_blockexplorer_impl/data/TransactionHistoryRepositoryImpl.kt",
    "feature_blockexplorer_impl/src/main/java/jp/co/soramitsu/feature_blockexplorer_impl/presentation/txhistory/TransactionMappersImpl.kt",
    "feature_blockexplorer_impl/src/main/java/jp/co/soramitsu/feature_blockexplorer_impl/presentation/txdetails/TxDetailsViewModel.kt",
    "feature_blockexplorer_impl/src/test/java/jp/co/soramitsu/feature_blockexplorer_impl/data/TransactionHistoryRepositoryTest.kt",
    "feature_blockexplorer_impl/build.gradle.kts",
    "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktTraderRepository.kt",
    "feature_polkaswap_impl/src/main/java/jp/co/soramitsu/feature_polkaswap_impl/presentation/screens/polkamarkt/PolkamarktScreen.kt",
    "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktPendingRecoveryTest.kt",
    "feature_polkaswap_impl/src/test/java/jp/co/soramitsu/feature_polkaswap_impl/data/repository/PolkamarktPreTransportGateTest.kt",
    "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerOfflineFallbackPolicy.kt",
    "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiCanonicalIntegerLexemeSerializer.kt",
    "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerModels.kt",
    "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerResponseCache.kt",
    "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PolkaswapIndexerClient.kt",
    "feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/SoraConfigManager.kt",
    "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiCanonicalIntegerLexemeSerializerTest.kt",
    "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerOfflineFallbackPolicyTest.kt",
    "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PiIndexerResponseCacheTest.kt",
    "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/PolkaswapIndexerClientTest.kt",
    "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/SoraConfigManagerTest.kt",
    "feature_blockexplorer_api/src/test/java/jp/co/soramitsu/feature_blockexplorer_api/data/SoraConfigPayloadAdmissionTest.kt",
    "feature_select_node_impl/src/main/java/jp/co/soramitsu/feature_select_node_impl/NodeManagerImpl.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/request/RuntimeCheckpointRequest.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/di/SubstrateModule.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/runtime/RuntimeManager.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/runtime/Sora2BoundedRuntimeRpcClient.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/runtime/Sora2RuntimeContract.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/runtime/SubstrateOptionsProvider.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/ConnectionManager.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicBuilderFactory.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicManager.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/Sora2PendingSubmissionCoordinator.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/Sora2MutationTransportCoordinator.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/SubstrateCalls.kt",
    "sorasubstrate/src/main/java/jp/co/soramitsu/sora/substrate/substrate/WsConnectionManager.kt",
    "sorasubstrate/src/androidTest/java/jp/co/soramitsu/sora/substrate/runtime/RuntimeCacheAtomicRecoveryTest.kt",
    "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/runtime/Sora2BoundedRuntimeRpcClientTest.kt",
    "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicBuilderFactoryTest.kt",
    "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/ExtrinsicManagerSafetyTest.kt",
    "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/Sora2PendingRecoveryBatchPlannerTest.kt",
    "sorasubstrate/src/test/java/jp/co/soramitsu/sora/substrate/substrate/Sora2MutationTransportCoordinatorTest.kt",
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "common/build.gradle.kts",
    "core_db/build.gradle.kts",
    "feature_account_api/build.gradle.kts",
    "feature_account_impl/build.gradle.kts",
    "sorasubstrate/build.gradle.kts",
    "config/gradle-dependency-provenance.json",
    "core_db/schemas/jp.co.soramitsu.core_db.AppDatabase/77.json",
    ...retainedRoomSchemaContractPaths,
    ...configuredDependencyEvidencePaths,
  ])].sort();
  const migrationQualificationContractPathsValid =
    migrationQualificationContractPaths.every(isCanonicalRegularRepoFile);
  const expectedMigrationQualificationKeys = [
    "schemaVersion",
    "contractId",
    "platform",
    "status",
    "runId",
    "qualificationSequenceNumber",
    "sourceRevision",
    "qualifiedAtEpochSeconds",
    "reviewedAtEpochSeconds",
    "trustRootSha256",
    "evidenceManifestSha256",
    "deviceEvidenceProducerKeyId",
    "independentReviewerKeyId",
    "identity",
    "privacy",
    "sourceSchemaVersions",
    "targetSchemaVersion",
    "retainedSchemaCohortCount",
    "singleAccountCohortCount",
    "multiAccountCohortCount",
    "successfulSecretSourceCohortCount",
    "secretFailureCohortCount",
    "currentSchemaSnapshotCohortCount",
    "productionPathCohortCount",
    "encryptedStoragePhaseCount",
    "retainedReleaseSnapshotCount",
    "retainedReleaseSnapshotManifestSha256",
    "executedWalletIdentityMigration75TestCount",
    "executedWalletUpgradeBackupTestCount",
    "executedMigrationManagerSafetyTestCount",
    "executedMigrationManagerProductionPathQualificationTestCount",
    "executedEncryptedWalletMigrationStorageTestCount",
    "executedSora2AddressCodecTestCount",
    "testFailureCount",
    "testUnexpectedFailureCount",
    "aggregateTestResultSha256",
    "rawExecutionEvidenceSha256",
    "rawRunIndexSha256",
    "rawResultFileCount",
    "rawResultBundleByteCount",
    "rawResultBundleSha256",
    "zeroLostAccounts",
    "accountCountParity",
    "selectedWalletParity",
    "preferencesParity",
    "verifiedPreImportBackup",
    "encryptedSecretCiphertextParity",
    "wrappedAesKeyParity",
    "keystoreAliasContinuity",
    "noCredentialRewrite",
    "sora2SigningParity",
    "roomSchemaSha256",
    "qualificationContractSha256",
    "qualificationChecks",
    "blockingReasons",
  ];
  const expectedExecutedMigrationTestCounts = {
    executedWalletIdentityMigration75TestCount: countKotlinTestMethods(
      retainedSchemaMigrationTest,
    ),
    executedWalletUpgradeBackupTestCount: countKotlinTestMethods(
      walletUpgradeBackupTest,
    ),
    executedMigrationManagerSafetyTestCount: countKotlinTestMethods(
      migrationManagerSafetyTest,
    ),
    executedMigrationManagerProductionPathQualificationTestCount:
      countKotlinTestMethods(
        migrationManagerProductionPathQualificationTest,
      ),
    executedEncryptedWalletMigrationStorageTestCount: countKotlinTestMethods(
      encryptedWalletMigrationStorageTest,
    ),
    executedSora2AddressCodecTestCount:
      countKotlinTestMethods(sora2AddressCodecTest),
  };
  const migrationQualificationContractSha256 =
    migrationQualificationContractPathsValid
      ? sha256Files(migrationQualificationContractPaths)
      : "";
  let authenticatedMigrationQualification = null;
  try {
    authenticatedMigrationQualification = verifyAndroidMigrationQualificationV7({
      root,
      expectedQualificationContractSha256:
        migrationQualificationContractSha256,
    });
  } catch {
    assert(false, "ANDROID_MIGRATION_QUALIFICATION_V7_AUTHENTICATION_FAILED");
  }
  authenticatedMigrationQualificationReceiptSha256 =
    authenticatedMigrationQualification?.receiptSha256 ?? null;
  const migrationQualification =
    authenticatedMigrationQualification?.receipt ??
    json("docs/modernization/qualification/android-migration-matrix.blocked.json");
  const migrationQualificationContractImmutable =
    authenticatedMigrationQualification !== null &&
    migrationQualificationContractPathsValid &&
    migrationQualificationContractPaths.every(isCanonicalRegularRepoFile) &&
    sha256Files(migrationQualificationContractPaths) ===
      migrationQualificationContractSha256;
  assert(
      hasExactKeys(
        migrationQualification,
        expectedMigrationQualificationKeys,
      ) &&
      migrationQualification.schemaVersion > 6 &&
      migrationQualification.contractId !==
        "sora-android-wallet-migration-qualification-v6" &&
      migrationQualification.schemaVersion === 7 &&
      migrationQualification.contractId ===
        "sora-android-wallet-migration-qualification-v7" &&
      migrationQualification.platform === "android" &&
      migrationQualification.status === "qualified" &&
      authenticatedMigrationQualification?.receiptSha256 ===
        sha256(migrationQualificationPath) &&
      migrationQualificationContractImmutable &&
      migrationQualification.blockingReasons?.length === 0 &&
      Number.isSafeInteger(migrationQualification.qualifiedAtEpochSeconds) &&
      migrationQualification.qualifiedAtEpochSeconds > 0 &&
      migrationQualification.qualifiedAtEpochSeconds <=
        Math.floor(Date.now() / 1000) &&
      hasExactKeys(migrationQualification.privacy, [
        "aggregateOnly",
        "accountIdentifiersIncluded",
        "addressesIncluded",
        "deviceIdentifiersIncluded",
        "secretsIncluded",
        "phrasesOrSeedsIncluded",
        "privateKeysIncluded",
        "publicKeysIncluded",
        "ciphertextsIncluded",
        "wrappedKeysIncluded",
        "keystoreMaterialIncluded",
        "signedPayloadsIncluded",
        "rawSignedPayloadsIncluded",
        "perWalletRecordsIncluded",
      ]) &&
      migrationQualification.privacy?.aggregateOnly === true &&
      migrationQualification.privacy?.accountIdentifiersIncluded === false &&
      migrationQualification.privacy?.addressesIncluded === false &&
      migrationQualification.privacy?.deviceIdentifiersIncluded === false &&
      migrationQualification.privacy?.secretsIncluded === false &&
      migrationQualification.privacy?.phrasesOrSeedsIncluded === false &&
      migrationQualification.privacy?.privateKeysIncluded === false &&
      migrationQualification.privacy?.publicKeysIncluded === false &&
      migrationQualification.privacy?.ciphertextsIncluded === false &&
      migrationQualification.privacy?.wrappedKeysIncluded === false &&
      migrationQualification.privacy?.keystoreMaterialIncluded === false &&
      migrationQualification.privacy?.signedPayloadsIncluded === false &&
      migrationQualification.privacy?.rawSignedPayloadsIncluded === false &&
      migrationQualification.privacy?.perWalletRecordsIncluded === false &&
      Array.isArray(migrationQualification.sourceSchemaVersions) &&
      migrationQualification.sourceSchemaVersions.join(",") ===
        Array.from({ length: 19 }, (_, index) => index + 58).join(",") &&
      migrationQualification.targetSchemaVersion === 77 &&
      migrationQualification.retainedSchemaCohortCount === 76 &&
      migrationQualification.singleAccountCohortCount === 38 &&
      migrationQualification.multiAccountCohortCount === 38 &&
      migrationQualification.successfulSecretSourceCohortCount === 6 &&
      migrationQualification.secretFailureCohortCount === 2 &&
      migrationQualification.currentSchemaSnapshotCohortCount === 1 &&
      migrationQualification.productionPathCohortCount === 7 &&
      migrationQualification.encryptedStoragePhaseCount === 5 &&
      Number.isSafeInteger(
        migrationQualification.retainedReleaseSnapshotCount,
      ) &&
      migrationQualification.retainedReleaseSnapshotCount > 0 &&
      /^[0-9a-f]{64}$/.test(
        migrationQualification.retainedReleaseSnapshotManifestSha256 ?? "",
      ) &&
      Object.entries(expectedExecutedMigrationTestCounts).every(
        ([field, count]) =>
          count > 0 && migrationQualification[field] === count,
      ) &&
      migrationQualification.testFailureCount === 0 &&
      migrationQualification.testUnexpectedFailureCount === 0 &&
      /^[0-9a-f]{64}$/.test(
        migrationQualification.aggregateTestResultSha256 ?? "",
      ) &&
      /^[0-9a-f]{64}$/.test(
        migrationQualification.rawExecutionEvidenceSha256 ?? "",
      ) &&
      /^[0-9a-f]{64}$/.test(
        migrationQualification.rawRunIndexSha256 ?? "",
      ) &&
      Number.isSafeInteger(migrationQualification.rawResultFileCount) &&
      migrationQualification.rawResultFileCount > 0 &&
      Number.isSafeInteger(migrationQualification.rawResultBundleByteCount) &&
      migrationQualification.rawResultBundleByteCount > 0 &&
      /^[0-9a-f]{64}$/.test(
        migrationQualification.rawResultBundleSha256 ?? "",
      ) &&
      migrationQualification.zeroLostAccounts === true &&
      migrationQualification.accountCountParity === true &&
      migrationQualification.selectedWalletParity === true &&
      migrationQualification.preferencesParity === true &&
      migrationQualification.verifiedPreImportBackup === true &&
      migrationQualification.encryptedSecretCiphertextParity === true &&
      migrationQualification.wrappedAesKeyParity === true &&
      migrationQualification.keystoreAliasContinuity === true &&
      migrationQualification.noCredentialRewrite === true &&
      migrationQualification.sora2SigningParity === true &&
      hasExactKeys(migrationQualification.roomSchemaSha256, ["74", "75", "76", "77"]) &&
      migrationQualificationContractPathsValid &&
      migrationQualification.qualificationContractSha256 ===
        sha256Files(migrationQualificationContractPaths) &&
      hasExactKeys(
        migrationQualification.qualificationChecks,
        requiredAndroidMigrationChecks,
      ) &&
      requiredRoomSchemas.every(
        (schema) =>
          /^[0-9a-f]{64}$/.test(
            migrationQualification.roomSchemaSha256?.[
              String(schema.version)
            ] ?? "",
          ) &&
          isCanonicalRegularRepoFile(schema.path) &&
          sha256(schema.path) ===
            migrationQualification.roomSchemaSha256[String(schema.version)],
      ) &&
      requiredAndroidMigrationChecks.every(
        (check) => migrationQualification.qualificationChecks?.[check] === true,
      ),
    "ANDROID_MIGRATION_QUALIFICATION_INVALID",
  );
} else if (strictRelease) {
  block(true, "ANDROID_MIGRATION_MATRIX_NOT_QUALIFIED");
}

const dynamicVersionPattern =
  /(?:version\s*=\s*["'][^"']*(?:\+|SNAPSHOT|latest\.)|:\+["'])/i;
const walk = (directory) => {
  for (const name of readdirSync(directory)) {
    if ([".git", ".gradle", "build", "node_modules"].includes(name)) continue;
    const path = join(directory, name);
    const stat = lstatSync(path);
    if (stat.isSymbolicLink()) {
      assert(false, `SYMLINK_IN_RELEASE_INPUT:${relative(root, path)}`);
      continue;
    }
    if (stat.isDirectory()) {
      walk(path);
    } else if (
      path.endsWith(".gradle") ||
      path.endsWith(".gradle.kts") ||
      path.endsWith(".toml")
    ) {
      assert(
        !dynamicVersionPattern.test(readFileSync(path, "utf8")),
        `DYNAMIC_DEPENDENCY:${relative(root, path)}`,
      );
    } else if (path.endsWith(".properties")) {
      assert(
        !/^\s*(?:authToken|[^#=\r\n]*(?:password|secret|token|api[_-]?key|access[_-]?key)[^=\r\n]*)\s*=/im.test(
          readFileSync(path, "utf8"),
        ),
        `TRACKED_SECRET_PROPERTY:${relative(root, path)}`,
      );
    }
  }
};
walk(root);

const rolloutBundleStepIndex = productionReleaseWorkflowForRollout.indexOf(
  ":app:bundleProductionRelease",
);
const rolloutEvaluationEpochIndex =
  productionReleaseWorkflowForRollout.indexOf(
    "Record explicit release evaluation epoch",
  );
const rolloutCapabilityRefreshIndex =
  productionReleaseWorkflowForRollout.indexOf(
    "Refresh live PI contract after funded canaries",
  );
const rolloutExactCandidateGateIndex =
  productionReleaseWorkflowForRollout.indexOf(
    "Exact built-candidate production gates",
  );
const rolloutPromotionEpochIndex =
  productionReleaseWorkflowForRollout.indexOf(
    "Record explicit rollout evaluation epoch",
  );
const rolloutGateStepIndex = productionReleaseWorkflowForRollout.indexOf(
  "Exact built-candidate staged-rollout gate",
);
const rolloutTrustAuthorities = productionRolloutTrust?.authorities;
const rolloutTrustAuthorityNames = [
  ["telemetryCollector", "privacy-aggregate-telemetry-collector"],
  ["releaseAuthorizer", "production-rollout-authorizer"],
];
const rolloutTrustAuthorityHasExactShape = (name, role) => {
  const authority = rolloutTrustAuthorities?.[name];
  return (
    hasExactKeys(authority, [
      "role",
      "keyId",
      "publicKeyPem",
      "publicKeySha256",
      "enabled",
    ]) && authority.role === role
  );
};
const rolloutTrustReviewedKeysQualified = () => {
  try {
    return rolloutTrustAuthorityNames.every(([name]) => {
      const authority = rolloutTrustAuthorities[name];
      const publicKey = createPublicKey(authority.publicKeyPem);
      const spki = publicKey.export({ type: "spki", format: "der" });
      return (
        publicKey.asymmetricKeyType === "ed25519" &&
        createHash("sha256").update(spki).digest("hex") ===
          authority.publicKeySha256
      );
    });
  } catch {
    return false;
  }
};
const rolloutTrustCommonShapeQualified =
  hasExactKeys(productionRolloutTrust, [
    "schemaVersion",
    "contractId",
    "platform",
    "assessedAt",
    "status",
    "signatureAlgorithm",
    "telemetrySourceId",
    "completenessPolicySha256",
    "authorities",
    "blockingReasons",
  ]) &&
  productionRolloutTrust.schemaVersion === 1 &&
  productionRolloutTrust.contractId ===
    "sora-android-production-rollout-trust-v1" &&
  productionRolloutTrust.platform === "android" &&
  /^\d{4}-\d{2}-\d{2}$/.test(productionRolloutTrust.assessedAt ?? "") &&
  productionRolloutTrust.signatureAlgorithm === "Ed25519" &&
  hasExactKeys(rolloutTrustAuthorities, [
    "telemetryCollector",
    "releaseAuthorizer",
  ]) &&
  rolloutTrustAuthorityNames.every(([name, role]) =>
    rolloutTrustAuthorityHasExactShape(name, role),
  ) &&
  Array.isArray(productionRolloutTrust.blockingReasons) &&
  productionRolloutTrust.blockingReasons.length <= 16 &&
  productionRolloutTrust.blockingReasons.every(
    (reason) =>
      typeof reason === "string" &&
      [...reason].length > 0 &&
      [...reason].length <= 240 &&
      !/[\u0000-\u001f\u007f-\u009f]/u.test(reason),
  );
const rolloutTrustBlockedShapeQualified =
  productionRolloutTrust?.status === "blocked" &&
  productionRolloutTrust?.telemetrySourceId === null &&
  productionRolloutTrust?.completenessPolicySha256 === null &&
  rolloutTrustAuthorityNames.every(([name]) => {
    const authority = rolloutTrustAuthorities?.[name];
    return (
      authority?.enabled === false &&
      authority.keyId === null &&
      authority.publicKeyPem === null &&
      authority.publicKeySha256 === null
    );
  }) &&
  Array.isArray(productionRolloutTrust?.blockingReasons) &&
  productionRolloutTrust.blockingReasons.length > 0;
const rolloutTrustReviewedShapeQualified =
  productionRolloutTrust?.status === "qualified" &&
  /^[a-z0-9][a-z0-9._-]{2,127}$/.test(
    productionRolloutTrust.telemetrySourceId ?? "",
  ) &&
  /^[0-9a-f]{64}$/.test(
    productionRolloutTrust.completenessPolicySha256 ?? "",
  ) &&
  rolloutTrustAuthorityNames.every(([name]) => {
    const authority = rolloutTrustAuthorities?.[name];
    return (
      authority?.enabled === true &&
      /^[a-z0-9][a-z0-9._-]{2,63}$/.test(authority.keyId ?? "") &&
      typeof authority.publicKeyPem === "string" &&
      authority.publicKeyPem.length > 0 &&
      authority.publicKeyPem.length <= 2_048 &&
      /^[0-9a-f]{64}$/.test(authority.publicKeySha256 ?? "")
    );
  }) &&
  new Set(
    rolloutTrustAuthorityNames.map(
      ([name]) => rolloutTrustAuthorities[name].keyId,
    ),
  ).size === rolloutTrustAuthorityNames.length &&
  new Set(
    rolloutTrustAuthorityNames.map(
      ([name]) => rolloutTrustAuthorities[name].publicKeySha256,
    ),
  ).size === rolloutTrustAuthorityNames.length &&
  rolloutTrustReviewedKeysQualified() &&
  Array.isArray(productionRolloutTrust?.blockingReasons) &&
  productionRolloutTrust.blockingReasons.length === 0;
const productionRolloutTrustStateQualified =
  rolloutTrustCommonShapeQualified &&
  (rolloutTrustBlockedShapeQualified || rolloutTrustReviewedShapeQualified);

assert(
    productionRolloutTemplate.schemaVersion === 3 &&
    productionRolloutTemplate.contractId ===
      "sora-android-production-rollout-v3" &&
    productionRolloutTemplate.status === "blocked-template" &&
    productionRolloutTemplate.platform === "android" &&
    productionRolloutTemplate.fromCohortPercent === 1 &&
    productionRolloutTemplate.targetCohortPercent === 5 &&
    productionRolloutTemplate.evaluatedAtEpochSeconds === 0 &&
    productionRolloutTemplate.priorGateReceiptSha256 === null &&
    Array.isArray(productionRolloutTemplate.cohorts) &&
    productionRolloutTemplate.cohorts.length === 0 &&
    hasExactKeys(productionRolloutTemplate.identity, [
      "bindingSha256",
      "candidateArtifactSha256",
      "sora2NetworkRevision",
      "runtimeSpecVersion",
      "runtimeTransactionVersion",
      "runtimeMetadataSha256",
      "productionQualificationReceiptSha256",
      "productionAdmissionBindingSha256",
      "tairaCanaryReceiptSha256",
      "minamotoCanaryReceiptSha256",
      "productionRolloutTrustSha256",
    ]) &&
    productionRolloutTemplate.identity?.bindingSha256 === "UNAVAILABLE" &&
    productionRolloutTemplate.identity?.productionQualificationReceiptSha256 ===
      "UNAVAILABLE" &&
    productionRolloutTemplate.identity?.productionAdmissionBindingSha256 ===
      "UNAVAILABLE" &&
    productionRolloutTemplate.identity?.tairaCanaryReceiptSha256 ===
      "UNAVAILABLE" &&
    productionRolloutTemplate.identity?.minamotoCanaryReceiptSha256 ===
      "UNAVAILABLE" &&
    productionRolloutTemplate.identity?.productionRolloutTrustSha256 ===
      "UNAVAILABLE" &&
    hasExactKeys(productionRolloutTemplate.checkpoint, [
      "piReceiptSha256",
      "capabilitySnapshotSha256",
      "sora2GenesisHash",
      "finalizedCheckpoint",
      "finalizedCheckpointBlockHash",
      "indexedCheckpoint",
      "capabilityFinalizedCheckpoint",
      "capabilityFinalizedCheckpointBlockHash",
      "capabilityIndexedCheckpoint",
      "minamotoChainId",
      "minamotoGenesisHash",
      "minamotoFinalizedCheckpoint",
      "minamotoFinalizedBlockHash",
      "tairaChainId",
      "tairaGenesisHash",
      "tairaFinalizedCheckpoint",
      "tairaFinalizedBlockHash",
      "nexusObservedAtEpochSeconds",
    ]) &&
    productionRolloutTemplate.checkpoint?.piReceiptSha256 === null &&
    productionRolloutTemplate.checkpoint?.finalizedCheckpoint === null &&
    productionRolloutTemplate.checkpoint?.minamotoFinalizedCheckpoint === null &&
    productionRolloutTemplate.checkpoint?.tairaFinalizedCheckpoint === null &&
    hasExactKeys(productionRolloutTemplate.telemetryAttestation, [
      "telemetrySourceId",
      "completenessPolicySha256",
      "observedThroughEpochSeconds",
      "collectorKeyId",
      "collectorSignatureBase64",
    ]) &&
    Object.values(productionRolloutTemplate.telemetryAttestation ?? {}).every(
      (value) => value === null,
    ) &&
    hasExactKeys(productionRolloutTemplate.authorization, [
      "telemetryBindingSha256",
      "collectorSignatureSha256",
      "authorizedTargetPercent",
      "authorizedAtEpochSeconds",
      "releaseAuthorizerKeyId",
      "releaseAuthorizerSignatureBase64",
    ]) &&
    Object.values(productionRolloutTemplate.authorization ?? {}).every(
      (value) => value === null,
    ) &&
    productionRolloutTemplate.privacy?.aggregateOnly === true &&
    productionRolloutTemplate.privacy?.accountIdentifiersIncluded === false &&
    productionRolloutTemplate.privacy?.addressesIncluded === false &&
    productionRolloutTemplate.privacy?.transactionIdentifiersIncluded === false &&
    productionRolloutTemplate.privacy?.phrasesOrSeedsIncluded === false &&
    productionRolloutTemplate.privacy?.privateKeysIncluded === false &&
    productionRolloutTemplate.privacy?.rawSignedPayloadsIncluded === false &&
    productionRolloutTemplate.privacy?.perWalletRecordsIncluded === false &&
    productionRolloutCandidateTemplate.schemaVersion === 3 &&
    productionRolloutCandidateTemplate.contractId ===
      "sora-android-production-rollout-v3" &&
    productionRolloutCandidateTemplate.status === "blocked-template" &&
    productionRolloutCandidateTemplate.platform === "android" &&
    productionRolloutCandidateTemplate.fromCohortPercent === 0 &&
    productionRolloutCandidateTemplate.targetCohortPercent === 1 &&
    productionRolloutCandidateTemplate.evaluatedAtEpochSeconds === 0 &&
    productionRolloutCandidateTemplate.priorGateReceiptSha256 === null &&
    Array.isArray(productionRolloutCandidateTemplate.cohorts) &&
    productionRolloutCandidateTemplate.cohorts.length === 0 &&
    hasExactKeys(productionRolloutCandidateTemplate.identity, [
      "bindingSha256",
      "candidateArtifactSha256",
      "sora2NetworkRevision",
      "runtimeSpecVersion",
      "runtimeTransactionVersion",
      "runtimeMetadataSha256",
      "productionQualificationReceiptSha256",
      "productionAdmissionBindingSha256",
      "tairaCanaryReceiptSha256",
      "minamotoCanaryReceiptSha256",
      "productionRolloutTrustSha256",
    ]) &&
    productionRolloutCandidateTemplate.identity?.bindingSha256 ===
      "UNAVAILABLE" &&
    productionRolloutCandidateTemplate.identity?.candidateArtifactSha256 ===
      "UNAVAILABLE" &&
    productionRolloutCandidateTemplate.identity?.runtimeMetadataSha256 ===
      "UNAVAILABLE" &&
    productionRolloutCandidateTemplate.identity
      ?.productionQualificationReceiptSha256 === "UNAVAILABLE" &&
    productionRolloutCandidateTemplate.identity
      ?.productionAdmissionBindingSha256 === "UNAVAILABLE" &&
    productionRolloutCandidateTemplate.identity?.tairaCanaryReceiptSha256 ===
      "UNAVAILABLE" &&
    productionRolloutCandidateTemplate.identity
      ?.minamotoCanaryReceiptSha256 === "UNAVAILABLE" &&
    productionRolloutCandidateTemplate.identity?.productionRolloutTrustSha256 ===
      "UNAVAILABLE" &&
    hasExactKeys(productionRolloutCandidateTemplate.checkpoint, [
      "piReceiptSha256",
      "capabilitySnapshotSha256",
      "sora2GenesisHash",
      "finalizedCheckpoint",
      "finalizedCheckpointBlockHash",
      "indexedCheckpoint",
      "capabilityFinalizedCheckpoint",
      "capabilityFinalizedCheckpointBlockHash",
      "capabilityIndexedCheckpoint",
      "minamotoChainId",
      "minamotoGenesisHash",
      "minamotoFinalizedCheckpoint",
      "minamotoFinalizedBlockHash",
      "tairaChainId",
      "tairaGenesisHash",
      "tairaFinalizedCheckpoint",
      "tairaFinalizedBlockHash",
      "nexusObservedAtEpochSeconds",
    ]) &&
    productionRolloutCandidateTemplate.checkpoint?.piReceiptSha256 === null &&
    productionRolloutCandidateTemplate.checkpoint?.finalizedCheckpoint === null &&
    productionRolloutCandidateTemplate.checkpoint
      ?.minamotoFinalizedCheckpoint === null &&
    productionRolloutCandidateTemplate.checkpoint?.tairaFinalizedCheckpoint ===
      null &&
    hasExactKeys(productionRolloutCandidateTemplate.telemetryAttestation, [
      "telemetrySourceId",
      "completenessPolicySha256",
      "observedThroughEpochSeconds",
      "collectorKeyId",
      "collectorSignatureBase64",
    ]) &&
    Object.values(
      productionRolloutCandidateTemplate.telemetryAttestation ?? {},
    ).every((value) => value === null) &&
    hasExactKeys(productionRolloutCandidateTemplate.authorization, [
      "telemetryBindingSha256",
      "collectorSignatureSha256",
      "authorizedTargetPercent",
      "authorizedAtEpochSeconds",
      "releaseAuthorizerKeyId",
      "releaseAuthorizerSignatureBase64",
    ]) &&
    Object.values(productionRolloutCandidateTemplate.authorization ?? {}).every(
      (value) => value === null,
    ) &&
    productionRolloutCandidateTemplate.privacy?.aggregateOnly === true &&
    productionRolloutCandidateTemplate.privacy?.accountIdentifiersIncluded ===
      false &&
    productionRolloutCandidateTemplate.privacy?.addressesIncluded === false &&
    productionRolloutCandidateTemplate.privacy?.transactionIdentifiersIncluded ===
      false &&
    productionRolloutCandidateTemplate.privacy?.phrasesOrSeedsIncluded === false &&
    productionRolloutCandidateTemplate.privacy?.privateKeysIncluded === false &&
    productionRolloutCandidateTemplate.privacy?.rawSignedPayloadsIncluded ===
      false &&
    productionRolloutCandidateTemplate.privacy?.perWalletRecordsIncluded ===
      false &&
    productionRolloutTrustStateQualified &&
    productionRolloutValidator.includes("const COHORT_SEQUENCE = [1, 5, 25, 100]") &&
    productionRolloutValidator.includes("const MINIMUM_DWELL_SECONDS = 48 * 60 * 60") &&
    productionRolloutValidator.includes(
      "metrics.eligibleTerminalFailureEvents * 100 <= eligible",
    ) &&
    productionRolloutValidator.includes("eligibleTerminalSuccessEvents") &&
    productionRolloutValidator.includes(
      "metrics.eligibleTerminalFailureEvents !==",
    ) &&
    productionRolloutValidator.includes("confirmedMissingWalletEvents") &&
    productionRolloutValidator.includes("confirmedMissingAccountEvents") &&
    productionRolloutValidator.includes("addressMismatchEvents") &&
    productionRolloutValidator.includes("signatureMismatchEvents") &&
    productionRolloutValidator.includes("crossNetworkRoutingEvents") &&
    productionRolloutValidator.includes("candidateArtifactSha256") &&
    productionRolloutValidator.includes("capabilitySnapshotSha256") &&
    productionRolloutValidator.includes(
      "identity.runtimeMetadataSha256 === runtimeMetadataSha256",
    ) &&
    productionRolloutValidator.includes(
      "identity.tairaCanaryReceiptSha256 ===",
    ) &&
    productionRolloutValidator.includes(
      "identity.minamotoCanaryReceiptSha256 ===",
    ) &&
    productionRolloutValidator.includes(
      "evidence.checkpoint.capabilitySnapshotSha256 !==",
    ) &&
    productionRolloutV3Contract.includes(
      'contractId: "sora-android-production-admission-v3"',
    ) &&
    productionRolloutV3Contract.includes(
      'contractId: "sora-android-production-rollout-v3"',
    ) &&
    productionRolloutV3Contract.includes(
      'contractId: "sora-android-production-rollout-controller-request-v3"',
    ) &&
    productionRolloutV3Contract.includes(
      "value.schemaVersion === contract.schemaVersion",
    ) &&
    productionRolloutV3Contract.includes(
      "value.contractId === contract.contractId",
    ) &&
    productionRolloutV3Contract.includes("isProductionAdmissionV3Envelope") &&
    productionRolloutV3Contract.includes(
      "isProductionRolloutCurrentV3Envelope",
    ) &&
    productionRolloutV3Contract.includes(
      "isProductionRolloutPriorV3Envelope",
    ) &&
    productionRolloutV3Contract.includes(
      "isProductionRolloutCursorV3Envelope",
    ) &&
    productionRolloutV3Contract.includes(
      "isProductionRolloutControllerRequestV3Envelope",
    ) &&
    !productionRolloutV3Contract.includes("-v2") &&
    productionRolloutValidator.includes("isProductionAdmissionV3Envelope") &&
    productionRolloutValidator.includes(
      "isProductionRolloutCurrentV3Envelope",
    ) &&
    productionRolloutValidator.includes(
      "isProductionRolloutPriorV3Envelope",
    ) &&
    !productionRolloutValidator.includes(
      "sora-android-production-admission-v2",
    ) &&
    !productionRolloutValidator.includes(
      "sora-android-production-rollout-v2",
    ) &&
    productionRolloutValidator.includes(
      "PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH",
    ) &&
    productionRolloutValidator.includes(
      "PRODUCTION_CANDIDATE_PI_RECEIPT_SIGNATURE_PATH",
    ) &&
    productionRolloutPiReceipt.includes(
      "productionPiRawCapabilityBinding",
    ) &&
    productionRolloutPiReceipt.includes(
      "productionPiCandidateBinding",
    ) &&
    productionRolloutPiReceipt.includes(
      'contractId: "sora-pi-production-capability-probe-v3"',
    ) &&
    productionRolloutPiReceipt.includes(
      "verifyProductionPiCandidateReceiptV3",
    ) &&
    productionRolloutValidator.includes("MAXIMUM_CAPABILITY_AGE_SECONDS") &&
    productionRolloutPiReceipt.includes(
      "capabilities[key] === true",
    ) &&
    productionRolloutPiReceipt.includes(
      "PRODUCTION_PI_RAW_LIVE_RECEIPT_KEYS",
    ) &&
    productionRolloutPiReceipt.includes(
      "PRODUCTION_PI_CANDIDATE_RECEIPT_KEYS",
    ) &&
    productionRolloutPiReceipt.includes("walletaddress") &&
    productionRolloutPiReceipt.includes("rawpayload") &&
    productionPiCandidateRequest.includes(
      "sora-android-production-pi-candidate-controller-request-v1",
    ) &&
    productionPiCandidateRequest.includes(
      "PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH",
    ) &&
    productionPiCandidateVerifier.includes(
      "--require-raw-live-binding",
    ) &&
    productionPiCandidateVerifier.includes(
      "verifyProductionPiCandidateReceiptV3",
    ) &&
    productionPiV3ContractHarness.includes(
      "legacy v1 receipt is wire-incompatible",
    ) &&
    productionPiV3ContractHarness.includes(
      "legacy v2 receipt is wire-incompatible",
    ) &&
    productionPiV3ContractHarness.includes(
      "2 positive contexts and ${failClosedCases} fail-closed cases passed",
    ) &&
    productionRolloutStrictEvidence.includes("fsConstants.O_NOFOLLOW") &&
    productionRolloutStrictEvidence.includes("parseStrictJsonBytes") &&
    productionRolloutStrictEvidence.includes("keys.has(key)") &&
    productionRolloutStrictEvidence.includes(
      "pathAfter.ino === before.ino",
    ) &&
    productionRolloutValidator.includes(
      "metrics.upgradedWalletsObserved === 0",
    ) &&
    productionRolloutValidator.includes("metrics.accountsObserved === 0") &&
    productionRolloutValidator.includes(
      "metrics.terminalTransactionsObserved === 0",
    ) &&
    productionRolloutValidator.includes(
      "metrics.telemetryCompletenessQualified !== true",
    ) &&
    productionRolloutValidator.includes("containsSensitiveField") &&
    productionRolloutValidator.includes("readStrictJsonFile") &&
    productionRolloutValidator.includes("hashStableRegularFile") &&
    productionRolloutStrictEvidence.includes("Number.isSafeInteger(value)") &&
    productionRolloutValidator.includes("ROLLOUT_TARGET_MISSING") &&
    productionRolloutValidator.includes(
      "PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS",
    ) &&
    productionRolloutValidator.includes(
      "PRODUCTION_ROLLOUT_EVIDENCE_PATH",
    ) &&
    productionRolloutValidator.includes(
      "PRODUCTION_QUALIFICATION_RECEIPT_PATH",
    ) &&
    productionRolloutValidator.includes(
      "PRODUCTION_CANDIDATE_PI_RECEIPT_PATH",
    ) &&
    productionRolloutValidator.includes("PRODUCTION_ROLLOUT_RECEIPT_1_PATH") &&
    productionRolloutValidator.includes("PRODUCTION_ROLLOUT_RECEIPT_5_PATH") &&
    productionRolloutValidator.includes("PRODUCTION_ROLLOUT_RECEIPT_25_PATH") &&
    productionRolloutValidator.includes("validatePriorGateReceipt") &&
    productionRolloutValidator.includes("validatePriorGateChain") &&
    productionRolloutValidator.includes(
      "ROLLOUT_PRIOR_GATE_CHAIN_INCOMPLETE",
    ) &&
    productionRolloutValidator.includes(
      "ROLLOUT_PRIOR_COHORT_PREFIX_REWRITTEN",
    ) &&
    productionRolloutValidator.includes(
      "ROLLOUT_COMPLETED_COHORT_PREDATES_PRIOR_AUTHORIZATION",
    ) &&
    productionRolloutValidator.includes("minamotoFinalizedBlockHash") &&
    productionRolloutValidator.includes("tairaFinalizedBlockHash") &&
    productionRolloutValidator.includes(
      "verifyTairaDeploymentManifestV1",
    ) &&
    productionRolloutValidator.includes(
      "identity?.tairaDeploymentManifestSha256",
    ) &&
    productionRolloutValidator.includes(
      "productionAdmission.tairaDeployment.current.chainId",
    ) &&
    productionRolloutValidator.includes(
      "productionAdmission.tairaDeployment.current.genesisSha256",
    ) &&
    productionRolloutValidator.includes(
      "sora-android-production-rollout-telemetry-v1",
    ) &&
    productionRolloutValidator.includes(
      "sora-android-production-rollout-authorization-v1",
    ) &&
    productionRolloutValidator.includes(
      "sora-android-production-rollout-distribution-v2",
    ) &&
    productionRolloutValidator.includes("distributionAttestationQualified") &&
    productionRolloutValidator.includes("signatureQualified") &&
    productionRolloutValidator.includes("ROLLOUT_TRUST_ROOT_NOT_QUALIFIED") &&
    productionRolloutValidator.includes(
      "ROLLOUT_TRUST_ROOT_PROTECTED_PIN_MISSING_OR_MISMATCHED",
    ) &&
    productionRolloutValidator.includes(
      "cohort.endedAtEpochSeconds <= evidence.evaluatedAtEpochSeconds",
    ) &&
    productionRolloutValidator.includes(
      "evidence.evaluatedAtEpochSeconds !== evaluationEpoch",
    ) &&
    productionRolloutValidator.includes(
      "authorization.authorizedAtEpochSeconds < evaluationEpoch",
    ) &&
    productionRolloutValidator.includes(
      "authorization.authorizedAtEpochSeconds < prior.evaluatedAtEpochSeconds",
    ) &&
    productionRolloutValidator.includes(
      "MAXIMUM_CHECKPOINT_LAG_BLOCKS = 32",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "PRODUCTION_ROLLOUT_TARGET_PERCENT: ${{ inputs.rollout_target_percent }}",
    ) &&
    productionReleaseWorkflowForRollout.includes("environment: production") &&
    productionReleaseWorkflowForRollout.includes(
      "PRODUCTION_CANDIDATE_AAB_PATH: ${{ github.workspace }}/app/build/outputs/bundle/productionRelease/app-production-release.aab",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "PRODUCTION_CANDIDATE_SOURCE_REVISION: ${{ github.sha }}",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "PRODUCTION_CANDIDATE_REPOSITORY: ${{ github.repository }}",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'append_runner_temp_path "PRODUCTION_CANDIDATE_RUN_RECEIPT_PATH" "sora-qualified-candidate-run.json"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'append_runner_temp_path "PRODUCTION_CANDIDATE_ARTIFACT_RECEIPT_PATH" "sora-qualified-candidate-artifacts.json"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'append_runner_temp_path "PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH" "sora-pi-production-raw-live-v1.json"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'append_runner_temp_path "PRODUCTION_CANDIDATE_PI_RECEIPT_PATH" "sora-pi-production-capability-probe-v3.json"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'append_runner_temp_path "PRODUCTION_CANDIDATE_PI_RECEIPT_SIGNATURE_PATH" "sora-pi-production-capability-probe-v3-signature.json"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "node scripts/test-production-pi-receipt-v3.mjs",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "node scripts/create-production-pi-candidate-controller-request.mjs",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "node scripts/verify-production-pi-candidate-receipt.mjs",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "/v3/android/pi-candidate-receipt",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "candidate-pi-receipt-signature.json",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'append_runner_temp_path "PRODUCTION_QUALIFICATION_RECEIPT_PATH" "sora-production-admission.json"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'append_runner_temp_path "PRODUCTION_ROLLOUT_EVIDENCE_PATH" "sora-production-rollout-evidence.json"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "PRODUCTION_ROLLOUT_TRUST_SHA256: ${{ vars.PRODUCTION_ROLLOUT_TRUST_SHA256 }}",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "candidate_run_id:",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "Download immutable qualified candidate",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "Fetch immutable candidate provenance",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "/actions/runs/$PRODUCTION_CANDIDATE_RUN_ID",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "Publish immutable qualified candidate",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "PRODUCTION_ROLLOUT_CONTROLLER_ORIGIN: ${{ vars.PRODUCTION_ROLLOUT_CONTROLLER_ORIGIN }}",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "PRODUCTION_ROLLOUT_CONTROLLER_TLS_SPKI_SHA256_BASE64: ${{ vars.PRODUCTION_ROLLOUT_CONTROLLER_TLS_SPKI_SHA256_BASE64 }}",
    ) &&
    sourceMatchCount(
      productionReleaseWorkflowForRollout,
      /--pinnedpubkey "sha256\/\/\$PRODUCTION_ROLLOUT_CONTROLLER_TLS_SPKI_SHA256_BASE64"/g,
    ) === 4 &&
    productionReleaseWorkflowForRollout.includes(
      "secrets.PRODUCTION_ROLLOUT_CONTROLLER_TOKEN",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "create-production-rollout-controller-request.mjs",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "extract-production-rollout-prior-link.mjs",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "/v3/android/rollout/receipts/sha256/$prior_hash",
    ) &&
    !productionReleaseWorkflowForRollout.includes(
      "/v2/android/rollout/receipts/sha256/$prior_hash",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "/v3/android/rollout/evidence",
    ) &&
    !productionReleaseWorkflowForRollout.includes(
      "/v2/android/rollout/evidence",
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "if: ${{ env.PRODUCTION_ROLLOUT_TARGET_PERCENT != '' }}",
    ) &&
    rolloutBundleStepIndex >= 0 &&
    rolloutCapabilityRefreshIndex > rolloutBundleStepIndex &&
    rolloutEvaluationEpochIndex > rolloutCapabilityRefreshIndex &&
    rolloutExactCandidateGateIndex > rolloutEvaluationEpochIndex &&
    rolloutPromotionEpochIndex > rolloutExactCandidateGateIndex &&
    rolloutGateStepIndex > rolloutPromotionEpochIndex &&
    productionReleaseWorkflowForRollout.includes(
      'PRODUCTION_RELEASE_EVALUATED_AT_EPOCH_SECONDS=$(date +%s)',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS=$(date +%s)',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      'node scripts/verify-production-modernization.mjs --release | tee "$PRODUCTION_QUALIFICATION_RECEIPT_PATH"',
    ) &&
    productionReleaseWorkflowForRollout.includes(
      "node scripts/test-production-rollout-v3-contract.mjs",
    ) &&
    productionReleaseWorkflowForRollout.includes("set -o pipefail") &&
    productionRolloutControllerRequest.includes(
      "ANDROID_PRODUCTION_ROLLOUT_CONTROLLER_REQUEST_V3",
    ) &&
    productionRolloutControllerRequest.includes(
      "isProductionRolloutControllerRequestV3Envelope",
    ) &&
    !productionRolloutControllerRequest.includes(
      "sora-android-production-rollout-controller-request-v2",
    ) &&
    productionRolloutControllerRequest.includes(
      "isProductionAdmissionV3Envelope",
    ) &&
    !productionRolloutControllerRequest.includes(
      "sora-android-production-admission-v2",
    ) &&
    productionRolloutControllerRequest.includes(
      "tairaCanaryReceiptSha256: admissionIdentity.tairaCanaryReceiptSha256",
    ) &&
    productionRolloutControllerRequest.includes(
      "minamotoCanaryReceiptSha256: admissionIdentity.minamotoCanaryReceiptSha256",
    ) &&
    productionRolloutControllerRequest.includes(
      "admissionIdentity?.tairaCanaryReceiptSha256 ===",
    ) &&
    productionRolloutControllerRequest.includes("readStrictJsonFile") &&
    productionRolloutControllerRequest.includes("hashStableRegularFile") &&
    productionRolloutControllerRequest.includes("containsSensitiveField") &&
    productionRolloutControllerRequest.includes(
      "!exactKeys(admissionValue",
    ) &&
    productionRolloutControllerRequest.includes(
      "validateProductionPiRawLiveReceipt",
    ) &&
    productionRolloutControllerRequest.includes(
      "verifyProductionPiCandidateReceiptV3",
    ) &&
    productionRolloutControllerRequest.includes(
      "productionRolloutTrustSha256",
    ) &&
    productionRolloutControllerRequest.includes("candidateQualification") &&
    productionRolloutControllerRequest.includes("candidatePiProbe") &&
    productionRolloutCandidateProvenance.includes(
      "validateQualifiedCandidateProvenance",
    ) &&
    productionRolloutCandidateProvenance.includes(
      "PRODUCTION_ADMISSION_IDENTITY_KEYS",
    ) &&
    productionRolloutCandidateProvenance.includes(
      "artifact.workflow_run?.id !== candidateRunId",
    ) &&
    productionRolloutChainLinkExtractor.includes(
      "ROLLOUT_CHAIN_CURSOR_INITIAL_LINK_NOT_NULL",
    ) &&
    productionRolloutChainLinkExtractor.includes(
      "isProductionRolloutCursorV3Envelope",
    ) &&
    productionRolloutV3ContractHarness.includes(
      'name: "admission"',
    ) &&
    productionRolloutV3ContractHarness.includes(
      'name: "current rollout"',
    ) &&
    productionRolloutV3ContractHarness.includes(
      'name: "prior rollout"',
    ) &&
    productionRolloutV3ContractHarness.includes(
      'name: "rollout cursor"',
    ) &&
    productionRolloutV3ContractHarness.includes(
      'name: "controller request"',
    ) &&
    productionRolloutV3ContractHarness.includes(
      "predicate({ ...contract })",
    ) &&
    productionRolloutV3ContractHarness.includes(
      "predicate({ schemaVersion: 2, contractId: legacyContractId })",
    ) &&
    productionRolloutV3ContractHarness.includes(
      "predicate({ schemaVersion: 3, contractId: legacyContractId })",
    ) &&
    productionRolloutV3ContractHarness.includes(
      "predicate({ schemaVersion: 2, contractId: contract.contractId })",
    ) &&
    productionRolloutV3ContractHarness.includes(
      "5 exact-v3 contexts and 15 fail-closed v2/mixed cases passed",
    ) &&
    productionRolloutV3ContractHarness.includes(
      'legacyContractId: "sora-android-production-admission-v2"',
    ) &&
    productionRolloutV3ContractHarness.includes(
      'legacyContractId: "sora-android-production-rollout-v2"',
    ) &&
    productionRolloutV3ContractHarness.includes(
      'legacyContractId: "sora-android-production-rollout-controller-request-v2"',
    ) &&
    !jenkinsPipelineForRollout.includes("PRODUCTION_ROLLOUT_TARGET_PERCENT") &&
    rootBuildForRollout.includes('tasks.register<Exec>("verifyProductionRollout")') &&
    rootBuildForRollout.includes(
      'commandLine("node", "scripts/verify-production-rollout.mjs")',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_ROLLOUT_TARGET_PERCENT") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_AAB_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_SOURCE_REVISION") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_REPOSITORY") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_RUN_ID") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_RUN_RECEIPT_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_ARTIFACT_RECEIPT_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_PI_RECEIPT_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_CANDIDATE_PI_RECEIPT_SIGNATURE_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_PI_CONTROLLER_ID") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_ROLLOUT_EVIDENCE_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS") ?: ""',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_QUALIFICATION_RECEIPT_PATH") ?: ""',
    ) &&
    rootBuildForRollout.includes("listOf(1, 5, 25).forEach") &&
    rootBuildForRollout.includes(
      '"PRODUCTION_ROLLOUT_RECEIPT_${cohortPercent}_PATH"',
    ) &&
    rootBuildForRollout.includes(
      'System.getenv("PRODUCTION_ROLLOUT_TRUST_SHA256") ?: ""',
    ) &&
    !jenkinsPipelineForRollout.includes(
      "testCmd: 'verifyProductionRollout",
    ) &&
    jenkinsPipelineForRollout.includes(
      "Rollout authorization" +
        "\n    // lives in production_release_qualification.yml",
    ) &&
    productionRolloutDocumentation.includes(
      "Do not infer missing counters and do not fabricate qualification evidence.",
    ) &&
    productionRolloutDocumentation.includes(
      "sora-android-production-rollout-v3",
    ) &&
    productionRolloutDocumentation.includes(
      "restart at a new 1% v3 receipt",
    ) &&
    productionRolloutDocumentation.includes(
      "Protected controller v3 deployment and compatibility approval is an external hard blocker",
    ) &&
    productionRolloutDocumentation.includes(
      "source-text checks alone are not",
    ) &&
    productionRolloutDocumentation.includes(
      "wire-transition evidence.",
    ) &&
    productionRolloutDocumentation.includes(
      "telemetryCompletenessQualified` is an authenticated collector assertion",
    ),
  "PRODUCTION_ROLLOUT_ADVANCEMENT_GATE_MISSING_OR_FAIL_OPEN",
);
block(
  productionRolloutTrust?.status !== "qualified",
  "PRODUCTION_ROLLOUT_TRUST_ROOT_NOT_QUALIFIED",
);
const protectedProductionRolloutTrustSha256 =
  process.env.PRODUCTION_ROLLOUT_TRUST_SHA256 ?? "";
block(
  !/^[0-9a-f]{64}$/.test(protectedProductionRolloutTrustSha256) ||
    protectedProductionRolloutTrustSha256 !==
      productionRolloutTrustStrictRecord?.sha256,
  "PRODUCTION_ROLLOUT_TRUST_ROOT_PROTECTED_PIN_MISSING_OR_MISMATCHED",
);

const releaseAdmissionJsonSha256 = (path, maximumBytes = 256 * 1024) =>
  readStrictJsonFile(join(root, path), maximumBytes)?.sha256 ?? null;
const releaseAdmissionCanarySha256 = (networkId) => {
  return fundedCanaryQualifiedRecords[networkId]?.receiptSha256 ?? null;
};
const releaseAdmissionRuntimeMetadata = hashExternalRegularFile({
  path: join(root, "common/src/production/assets/sora2_metadata"),
  suffix: "sora2_metadata",
  maximumBytes: 2 * 1024 * 1024,
});
const releaseCandidatePiPath =
  process.env.PRODUCTION_CANDIDATE_PI_RECEIPT_PATH ?? "";
const releaseCandidatePiSignaturePath =
  process.env.PRODUCTION_CANDIDATE_PI_RECEIPT_SIGNATURE_PATH ?? "";
const releaseCandidatePiRecord =
  isAbsolute(releaseCandidatePiPath) &&
  resolve(releaseCandidatePiPath) === releaseCandidatePiPath &&
  releaseCandidatePiPath !== piLiveReceiptPath
    ? readStrictJsonFile(releaseCandidatePiPath, 64 * 1024)
    : null;
const releaseCandidatePiSignatureRecord =
  isAbsolute(releaseCandidatePiSignaturePath) &&
  resolve(releaseCandidatePiSignaturePath) ===
    releaseCandidatePiSignaturePath &&
  releaseCandidatePiSignaturePath !== releaseCandidatePiPath &&
  releaseCandidatePiSignaturePath !== piLiveReceiptPath
    ? readStrictJsonFile(releaseCandidatePiSignaturePath, 64 * 1024)
    : null;
const releaseCandidatePiQualification = strictRelease
  ? verifyProductionPiCandidateReceiptV3({
      receiptRecord: releaseCandidatePiRecord,
      signatureRecord: releaseCandidatePiSignatureRecord,
      trustRecord: productionRolloutTrustStrictRecord,
      expectedTrustSha256: protectedProductionRolloutTrustSha256,
      evaluationEpoch: fundedCanaryEvaluationEpoch,
      validationContext: {
        expectedControllerId:
          process.env.PRODUCTION_PI_CONTROLLER_ID ?? "",
        expectedCandidate: {
          artifactSha256: fundedCanaryAab?.sha256,
          artifactBytes: fundedCanaryAab?.bytes,
          sourceRevision: fundedCanarySourceRevision,
        },
        expectedRawLiveReceiptSha256: fundedCanaryPiRecord?.sha256,
        expectedRuntimeMetadataSha256:
          releaseAdmissionRuntimeMetadata?.sha256,
        expectedTairaDeployment: {
          chainId: tairaDeploymentAdmission?.current?.chainId,
          toriiEndpoint:
            tairaDeploymentAdmission?.current?.toriiBaseUrl,
          genesisHash:
            tairaDeploymentAdmission?.current?.genesisSha256,
        },
      },
    })
  : null;
block(
  strictRelease && releaseCandidatePiQualification === null,
  "PI_CANDIDATE_BOUND_V3_RECEIPT_NOT_QUALIFIED",
);
const releaseAdmissionWorkflow = hashExternalRegularFile({
  path: join(root, QUALIFICATION_WORKFLOW_RELATIVE_PATH),
  suffix: "production_release_qualification.yml",
  maximumBytes: 256 * 1024,
});
const releaseAdmissionRunIdRaw = process.env.GITHUB_RUN_ID ?? "";
const releaseAdmissionRunAttemptRaw = process.env.GITHUB_RUN_ATTEMPT ?? "";
const releaseAdmissionRunId = /^[1-9][0-9]{0,14}$/.test(
  releaseAdmissionRunIdRaw,
)
  ? Number(releaseAdmissionRunIdRaw)
  : null;
const releaseAdmissionRunAttempt = /^[1-9][0-9]{0,5}$/.test(
  releaseAdmissionRunAttemptRaw,
)
  ? Number(releaseAdmissionRunAttemptRaw)
  : null;
const releaseAdmissionIdentity = {
  candidateAabSha256: fundedCanaryAab?.sha256 ?? null,
  candidateAabBytes: fundedCanaryAab?.bytes ?? null,
  sourceRevision: fundedCanarySourceRevision || null,
  qualifiedAtEpochSeconds: fundedCanaryEvaluationEpochQualified
    ? fundedCanaryEvaluationEpoch
    : null,
  candidatePiProbeReceiptSha256:
    releaseCandidatePiRecord?.sha256 ?? null,
  qualificationRepository: process.env.GITHUB_REPOSITORY ?? null,
  qualificationWorkflowPath: QUALIFICATION_WORKFLOW_RELATIVE_PATH,
  qualificationWorkflowSha256: releaseAdmissionWorkflow?.sha256 ?? null,
  qualificationRunId: releaseAdmissionRunId,
  qualificationRunAttempt: releaseAdmissionRunAttempt,
  sora2NetworkRevision: SORA2_REVISION,
  runtimeSpecVersion: 130,
  runtimeTransactionVersion: 130,
  runtimeMetadataSha256: releaseAdmissionRuntimeMetadata?.sha256 ?? null,
  productionSigningIdentitySha256: releaseAdmissionJsonSha256(
    "config/android-production-signing-identity.json",
  ),
  dependencySigningReviewManifestSha256:
    authenticatedAndroidDependencySigningReview?.manifestSha256 ?? null,
  irohaMobileSdkPinSha256: releaseAdmissionJsonSha256(
    "config/iroha-mobile-sdk-pin.json",
  ),
  migrationQualificationSha256: strictRelease
    ? authenticatedMigrationQualificationReceiptSha256
    : null,
  tairaDeploymentManifestSha256:
    tairaDeploymentAdmission?.manifestSha256 ?? null,
  tairaCanaryReceiptSha256: releaseAdmissionCanarySha256("taira"),
  minamotoCanaryReceiptSha256: releaseAdmissionCanarySha256("minamoto"),
  fundedCanaryTrustSha256: fundedCanaryTrustRecord?.sha256 ?? null,
  productionRolloutTrustSha256: releaseAdmissionJsonSha256(
    "config/production-rollout-trust.json",
  ),
};
const releaseAdmissionProjection = [
  ...productionAdmissionV3ProjectionPrefix(),
  ...PRODUCTION_ADMISSION_IDENTITY_KEYS.map(
    (key) => `${key}=${releaseAdmissionIdentity[key]}`,
  ),
];
const releaseAdmissionIdentityValuesQualified =
  hasExactKeys(
    releaseAdmissionIdentity,
    PRODUCTION_ADMISSION_IDENTITY_KEYS,
  ) &&
  isFundedCanarySha256(releaseAdmissionIdentity.candidateAabSha256) &&
  Number.isSafeInteger(releaseAdmissionIdentity.candidateAabBytes) &&
  releaseAdmissionIdentity.candidateAabBytes > 0 &&
  /^[0-9a-f]{40}$/.test(releaseAdmissionIdentity.sourceRevision ?? "") &&
  isFundedCanaryEpoch(releaseAdmissionIdentity.qualifiedAtEpochSeconds) &&
  /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(
    releaseAdmissionIdentity.qualificationRepository ?? "",
  ) &&
  releaseAdmissionIdentity.qualificationWorkflowPath ===
    QUALIFICATION_WORKFLOW_RELATIVE_PATH &&
  isFundedCanarySha256(
    releaseAdmissionIdentity.qualificationWorkflowSha256,
  ) &&
  Number.isSafeInteger(releaseAdmissionIdentity.qualificationRunId) &&
  releaseAdmissionIdentity.qualificationRunId > 0 &&
  releaseAdmissionIdentity.qualificationRunAttempt === 1 &&
  releaseAdmissionIdentity.tairaCanaryReceiptSha256 !==
    releaseAdmissionIdentity.minamotoCanaryReceiptSha256 &&
  Object.entries(releaseAdmissionIdentity).every(([key, value]) =>
    key.endsWith("Sha256") ? isFundedCanarySha256(value) : true,
  );
block(
  strictRelease && !releaseAdmissionIdentityValuesQualified,
  "PRODUCTION_ADMISSION_PROVENANCE_OR_IDENTITY_NOT_QUALIFIED",
);
const releaseAdmissionQualified =
  strictRelease &&
  failures.length === 0 &&
  blockers.length === 0 &&
  releaseAdmissionIdentityValuesQualified;
const qualifiedReleaseAdmission = releaseAdmissionQualified
  ? {
      schemaVersion: ANDROID_PRODUCTION_ADMISSION_V3.schemaVersion,
      contractId: ANDROID_PRODUCTION_ADMISSION_V3.contractId,
      status: "qualified",
      platform: "android",
      identity: {
        ...releaseAdmissionIdentity,
        bindingSha256: canonicalSha256(releaseAdmissionProjection),
      },
    }
  : null;
assert(
  qualifiedReleaseAdmission === null ||
    isProductionAdmissionV3Envelope(qualifiedReleaseAdmission),
  "PRODUCTION_ADMISSION_V3_ENVELOPE_INVALID",
);
if (
  preCanaryMode &&
  (strictRelease || dependencyPreflightMode || process.argv.length !== 3)
) {
  assert(false, "PRE_CANARY_ARGUMENTS_INVALID");
}
const deferredPreCanaryBlockerSet = new Set([
  "TAIRA_FUNDED_CANARY_NOT_QUALIFIED",
  "MINAMOTO_FUNDED_CANARY_NOT_QUALIFIED",
  "FUNDED_CANARY_CROSS_NETWORK_PAIR_NOT_QUALIFIED",
]);
const uniqueReleaseBlockers = [...new Set(blockers)].sort();
const preCanaryReleaseBlockers = uniqueReleaseBlockers.filter(
  (code) => !deferredPreCanaryBlockerSet.has(code),
);
const deferredCanaryBlockers = uniqueReleaseBlockers.filter((code) =>
  deferredPreCanaryBlockerSet.has(code),
);
const result = {
  mode: strictRelease ? "release" : preCanaryMode ? "pre-canary" : "audit",
  failures: [...new Set(failures)].sort(),
  releaseBlockers: preCanaryMode
    ? preCanaryReleaseBlockers
    : uniqueReleaseBlockers,
  ...(preCanaryMode ? { deferredCanaryBlockers } : {}),
  admission: qualifiedReleaseAdmission,
};
process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
if (
  result.failures.length > 0 ||
  ((strictRelease || preCanaryMode) && result.releaseBlockers.length > 0)
) {
  process.exitCode = 1;
}
