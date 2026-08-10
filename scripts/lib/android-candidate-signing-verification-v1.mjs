import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  chmodSync,
  lstatSync,
  mkdtempSync,
  realpathSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { isAbsolute, join, resolve } from "node:path";
import {
  hashStableRegularFile,
  readStrictJsonFile,
} from "./strict-evidence.mjs";

export const ANDROID_CANDIDATE_SIGNING_VERIFICATION_V1 = Object.freeze({
  schemaVersion: 1,
  contractId: "sora-android-candidate-signing-verification-v1",
});

const SHA256 = /^[0-9a-f]{64}$/;
const MAXIMUM_AAB_BYTES = 512 * 1024 * 1024;
const MAXIMUM_APK_BYTES = 512 * 1024 * 1024;
const MAXIMUM_CONFIG_BYTES = 1024 * 1024;
const MAXIMUM_TOOL_OUTPUT_BYTES = 1024 * 1024;
const MAXIMUM_CERTIFICATE_BYTES = 64 * 1024;
const PUBLIC_TRUSTSTORE_PASSWORD = "sora-public-certificate-only";
const KEYTOOL_LOCALE_ARGUMENTS = [
  "-J-Duser.language=en",
  "-J-Duser.country=US",
];

const fail = (code) => {
  throw new Error(code);
};

const hasExactKeys = (value, keys) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  Object.keys(value).sort().join("\0") === [...keys].sort().join("\0");

const canonicalJsonValue = (value) => {
  if (Array.isArray(value)) return value.map(canonicalJsonValue);
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, canonicalJsonValue(value[key])]),
    );
  }
  return value;
};

export const androidCandidateSigningVerificationV1BindingSha256 = (value) =>
  createHash("sha256")
    .update(JSON.stringify(canonicalJsonValue(value)), "utf8")
    .digest("hex");

const canonicalExecutable = (path, code) => {
  if (
    typeof path !== "string" ||
    path.length === 0 ||
    !isAbsolute(path) ||
    resolve(path) !== path
  ) {
    fail(code);
  }
  let stat;
  try {
    stat = lstatSync(path);
  } catch {
    fail(code);
  }
  if (
    !stat.isFile() ||
    stat.isSymbolicLink() ||
    stat.nlink !== 1 ||
    (stat.mode & 0o111) === 0 ||
    realpathSync(path) !== path
  ) {
    fail(code);
  }
  return path;
};

const regularArtifact = (path, maximumBytes, code) => {
  const record = hashStableRegularFile(path, maximumBytes);
  if (record === null) fail(code);
  let stat;
  try {
    stat = lstatSync(path);
  } catch {
    fail(code);
  }
  if (
    !stat.isFile() ||
    stat.isSymbolicLink() ||
    stat.nlink !== 1 ||
    realpathSync(path) !== path
  ) {
    fail(code);
  }
  return record;
};

const runTool = (executable, args, code) => {
  const result = spawnSync(executable, args, {
    encoding: "utf8",
    shell: false,
    windowsHide: true,
    timeout: 120_000,
    maxBuffer: MAXIMUM_TOOL_OUTPUT_BYTES,
    env: {
      PATH: process.env.PATH ?? "",
      LANG: "C",
      LC_ALL: "C",
    },
  });
  if (
    result.error !== undefined ||
    result.signal !== null ||
    result.status !== 0 ||
    typeof result.stdout !== "string" ||
    typeof result.stderr !== "string" ||
    Buffer.byteLength(result.stdout) + Buffer.byteLength(result.stderr) >
      MAXIMUM_TOOL_OUTPUT_BYTES
  ) {
    fail(code);
  }
  return `${result.stdout}\n${result.stderr}`;
};

export const parseApkSignerCertificateSha256 = (output) => {
  if (
    typeof output !== "string" ||
    Buffer.byteLength(output) > MAXIMUM_TOOL_OUTPUT_BYTES ||
    output.includes("\0")
  ) {
    return null;
  }
  const matches = [
    ...output.matchAll(
      /^Signer #([1-9][0-9]*) certificate SHA-256 digest: ([0-9a-fA-F]{64})\r?$/gm,
    ),
  ];
  if (
    matches.length !== 1 ||
    matches[0][1] !== "1" ||
    /Signer #(?:[2-9]|[1-9][0-9]+) certificate /m.test(output)
  ) {
    return null;
  }
  return matches[0][2].toLowerCase();
};

export const parseKeytoolJarSignerCertificate = (output) => {
  if (
    typeof output !== "string" ||
    Buffer.byteLength(output) > MAXIMUM_TOOL_OUTPUT_BYTES ||
    output.includes("\0")
  ) {
    return null;
  }
  const signerHeaders = [
    ...output.matchAll(/^Signer #([1-9][0-9]*):\r?$/gm),
  ];
  if (
    signerHeaders.length !== 1 ||
    signerHeaders[0][1] !== "1" ||
    [...output.matchAll(/^Signer #/gm)].length !== 1
  ) {
    return null;
  }
  const signerBody = output.slice(
    signerHeaders[0].index + signerHeaders[0][0].length,
  );
  const certificateMatch =
    /-----BEGIN CERTIFICATE-----\r?\n([A-Za-z0-9+/=\r\n]+?)\r?\n-----END CERTIFICATE-----/.exec(
      signerBody,
    );
  if (certificateMatch === null) return null;
  const canonicalBase64 = certificateMatch[1].replace(/\r?\n/g, "");
  if (
    !/^[A-Za-z0-9+/]+={0,2}$/.test(canonicalBase64) ||
    canonicalBase64.length % 4 !== 0
  ) {
    return null;
  }
  const certificateBytes = Buffer.from(canonicalBase64, "base64");
  if (
    certificateBytes.length < 1 ||
    certificateBytes.length > MAXIMUM_CERTIFICATE_BYTES ||
    certificateBytes.toString("base64") !== canonicalBase64
  ) {
    return null;
  }
  return {
    pem: `${certificateMatch[0].replace(/\r\n/g, "\n")}\n`,
    sha256: createHash("sha256").update(certificateBytes).digest("hex"),
  };
};

export const verifyAndroidCandidateSigningV1 = ({
  aabPath,
  apkPath,
  signingConfigPath,
  jarsignerPath,
  keytoolPath,
  apksignerPath,
}) => {
  const aab = regularArtifact(
    aabPath,
    MAXIMUM_AAB_BYTES,
    "ANDROID_CANDIDATE_SIGNING_AAB_INVALID",
  );
  const apk = regularArtifact(
    apkPath,
    MAXIMUM_APK_BYTES,
    "ANDROID_CANDIDATE_SIGNING_APK_INVALID",
  );
  const config = readStrictJsonFile(signingConfigPath, MAXIMUM_CONFIG_BYTES);
  if (config === null) fail("ANDROID_CANDIDATE_SIGNING_CONFIG_INVALID");
  const configFile = regularArtifact(
    signingConfigPath,
    MAXIMUM_CONFIG_BYTES,
    "ANDROID_CANDIDATE_SIGNING_CONFIG_INVALID",
  );
  if (
    configFile.sha256 !== config.sha256 ||
    configFile.bytes !== config.byteCount
  ) {
    fail("ANDROID_CANDIDATE_SIGNING_CONFIG_INVALID");
  }
  if (
    config.value?.schemaVersion !== 1 ||
    config.value?.platform !== "android" ||
    config.value?.applicationId !== "jp.co.soramitsu.sora" ||
    config.value?.status !== "qualified" ||
    config.value?.releaseEnabled !== true ||
    config.value?.debugFallbackAllowedForRelease !== false ||
    !SHA256.test(config.value?.productionAppSigningCertificateSha256 ?? "") ||
    !SHA256.test(config.value?.productionUploadCertificateSha256 ?? "") ||
    config.value?.retainedProductionCertificateMatched !== true ||
    config.value?.signedBundleCertificateMatched !== true ||
    config.value?.playAppSigningContinuityReviewed !== true ||
    config.value?.secretsRecordedInEvidence !== false
  ) {
    fail("ANDROID_CANDIDATE_SIGNING_IDENTITY_NOT_QUALIFIED");
  }

  const jarsigner = canonicalExecutable(
    jarsignerPath,
    "ANDROID_CANDIDATE_JARSIGNER_INVALID",
  );
  const keytool = canonicalExecutable(
    keytoolPath,
    "ANDROID_CANDIDATE_KEYTOOL_INVALID",
  );
  const apksigner = canonicalExecutable(
    apksignerPath,
    "ANDROID_CANDIDATE_APKSIGNER_INVALID",
  );
  const aabCertificate = parseKeytoolJarSignerCertificate(
    runTool(
      keytool,
      [
        ...KEYTOOL_LOCALE_ARGUMENTS,
        "-printcert",
        "-rfc",
        "-jarfile",
        aabPath,
      ],
      "ANDROID_CANDIDATE_AAB_CERTIFICATE_INVALID",
    ),
  );
  if (aabCertificate === null) {
    fail("ANDROID_CANDIDATE_AAB_CERTIFICATE_INVALID");
  }
  if (
    aabCertificate.sha256 !==
    config.value.productionUploadCertificateSha256
  ) {
    fail("ANDROID_CANDIDATE_AAB_UPLOAD_CERTIFICATE_MISMATCH");
  }
  const trustRoot = realpathSync(
    mkdtempSync(join(tmpdir(), "sora-android-aab-public-trust-")),
  );
  chmodSync(trustRoot, 0o700);
  try {
    const certificatePath = join(trustRoot, "upload-certificate.pem");
    const truststorePath = join(trustRoot, "upload-certificate.p12");
    writeFileSync(certificatePath, aabCertificate.pem, { mode: 0o600 });
    runTool(
      keytool,
      [
        ...KEYTOOL_LOCALE_ARGUMENTS,
        "-importcert",
        "-noprompt",
        "-alias",
        "sora-upload-certificate",
        "-file",
        certificatePath,
        "-keystore",
        truststorePath,
        "-storetype",
        "PKCS12",
        "-storepass",
        PUBLIC_TRUSTSTORE_PASSWORD,
      ],
      "ANDROID_CANDIDATE_AAB_TRUSTSTORE_INVALID",
    );
    chmodSync(truststorePath, 0o600);
    regularArtifact(
      truststorePath,
      MAXIMUM_CONFIG_BYTES,
      "ANDROID_CANDIDATE_AAB_TRUSTSTORE_INVALID",
    );
    runTool(
      jarsigner,
      [
        "-verify",
        "-strict",
        "-keystore",
        truststorePath,
        "-storetype",
        "PKCS12",
        "-storepass",
        PUBLIC_TRUSTSTORE_PASSWORD,
        aabPath,
        "sora-upload-certificate",
      ],
      "ANDROID_CANDIDATE_AAB_SIGNATURE_INVALID",
    );
  } finally {
    rmSync(trustRoot, { recursive: true, force: true });
  }
  const apkSignerOutput = runTool(
    apksigner,
    ["verify", "--verbose", "--print-certs", apkPath],
    "ANDROID_CANDIDATE_APK_SIGNATURE_INVALID",
  );
  const observedUploadCertificateSha256 =
    parseApkSignerCertificateSha256(apkSignerOutput);
  if (
    observedUploadCertificateSha256 === null ||
    observedUploadCertificateSha256 !==
      config.value.productionUploadCertificateSha256
  ) {
    fail("ANDROID_CANDIDATE_UPLOAD_CERTIFICATE_MISMATCH");
  }

  const verifiedAab = regularArtifact(
    aabPath,
    MAXIMUM_AAB_BYTES,
    "ANDROID_CANDIDATE_SIGNING_AAB_INVALID",
  );
  const verifiedApk = regularArtifact(
    apkPath,
    MAXIMUM_APK_BYTES,
    "ANDROID_CANDIDATE_SIGNING_APK_INVALID",
  );
  const verifiedConfig = regularArtifact(
    signingConfigPath,
    MAXIMUM_CONFIG_BYTES,
    "ANDROID_CANDIDATE_SIGNING_CONFIG_INVALID",
  );
  if (
    verifiedAab.sha256 !== aab.sha256 ||
    verifiedAab.bytes !== aab.bytes ||
    verifiedApk.sha256 !== apk.sha256 ||
    verifiedApk.bytes !== apk.bytes ||
    verifiedConfig.sha256 !== configFile.sha256 ||
    verifiedConfig.bytes !== configFile.bytes
  ) {
    fail("ANDROID_CANDIDATE_SIGNING_INPUT_CHANGED_DURING_VERIFICATION");
  }

  const payload = {
    schemaVersion: ANDROID_CANDIDATE_SIGNING_VERIFICATION_V1.schemaVersion,
    contractId: ANDROID_CANDIDATE_SIGNING_VERIFICATION_V1.contractId,
    status: "verified",
    platform: "android",
    artifacts: {
      aabSha256: aab.sha256,
      aabBytes: aab.bytes,
      apkSha256: apk.sha256,
      apkBytes: apk.bytes,
    },
    signingIdentity: {
      configSha256: config.sha256,
      productionAppSigningCertificateSha256:
        config.value.productionAppSigningCertificateSha256,
      expectedUploadCertificateSha256:
        config.value.productionUploadCertificateSha256,
      observedAabUploadCertificateSha256: aabCertificate.sha256,
      observedUploadCertificateSha256,
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

export const validateAndroidCandidateSigningVerificationV1 = (value) => {
  if (!hasExactKeys(value, [
    "schemaVersion",
    "contractId",
    "status",
    "platform",
    "artifacts",
    "signingIdentity",
    "verification",
    "privacy",
    "authorization",
    "receiptBindingSha256",
  ])) return false;
  const { receiptBindingSha256, ...payload } = value;
  return value.schemaVersion === ANDROID_CANDIDATE_SIGNING_VERIFICATION_V1.schemaVersion &&
  value.contractId === ANDROID_CANDIDATE_SIGNING_VERIFICATION_V1.contractId &&
  value.status === "verified" &&
  value.platform === "android" &&
  hasExactKeys(value.artifacts, [
    "aabSha256",
    "aabBytes",
    "apkSha256",
    "apkBytes",
  ]) &&
  Object.entries(value.artifacts).every(([key, fieldValue]) =>
    key.endsWith("Sha256")
      ? SHA256.test(fieldValue)
      : Number.isSafeInteger(fieldValue) && fieldValue > 0,
  ) &&
  hasExactKeys(value.signingIdentity, [
    "configSha256",
    "productionAppSigningCertificateSha256",
    "expectedUploadCertificateSha256",
    "observedAabUploadCertificateSha256",
    "observedUploadCertificateSha256",
  ]) &&
  Object.values(value.signingIdentity).every((fieldValue) =>
    SHA256.test(fieldValue),
  ) &&
  value.signingIdentity.expectedUploadCertificateSha256 ===
    value.signingIdentity.observedAabUploadCertificateSha256 &&
  value.signingIdentity.expectedUploadCertificateSha256 ===
    value.signingIdentity.observedUploadCertificateSha256 &&
  hasExactKeys(value.verification, [
    "aabJarSignatureVerifiedStrictly",
    "aabUploadCertificateMatched",
    "exactlyOneAabSigner",
    "apkSignatureVerified",
    "exactlyOneApkSigner",
    "uploadCertificateMatched",
  ]) &&
  Object.values(value.verification).every((fieldValue) => fieldValue === true) &&
  hasExactKeys(value.privacy, [
    "certificatePublicFingerprintsOnly",
    "privateKeysIncluded",
    "keyPasswordsIncluded",
  ]) &&
  value.privacy?.certificatePublicFingerprintsOnly === true &&
  value.privacy?.privateKeysIncluded === false &&
  value.privacy?.keyPasswordsIncluded === false &&
  hasExactKeys(value.authorization, [
    "authorizesRelease",
    "authorizesProductionMutation",
  ]) &&
  value.authorization?.authorizesRelease === false &&
  value.authorization?.authorizesProductionMutation === false &&
  SHA256.test(receiptBindingSha256 ?? "") &&
  receiptBindingSha256 ===
    androidCandidateSigningVerificationV1BindingSha256(payload);
};
