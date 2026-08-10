#!/usr/bin/env node

import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  chmodSync,
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
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import {
  parseApkSignerCertificateSha256,
  parseKeytoolJarSignerCertificate,
  validateAndroidCandidateSigningVerificationV1,
  verifyAndroidCandidateSigningV1,
} from "./lib/android-candidate-signing-verification-v1.mjs";

const TEST_CERTIFICATE_DER = Buffer.alloc(128, 0x5a);
const TEST_CERTIFICATE_BASE64 = TEST_CERTIFICATE_DER.toString("base64");
const UPLOAD_CERTIFICATE = createHash("sha256")
  .update(TEST_CERTIFICATE_DER)
  .digest("hex");
const APP_CERTIFICATE = "b".repeat(64);
const writeJson = (path, value) =>
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
const keytoolScript = ({
  certificateBase64 = TEST_CERTIFICATE_BASE64,
  secondSigner = false,
} = {}) => `#!/bin/sh
case " $* " in
  *" -printcert "*)
    printf '%s\\n' \\
      'Signer #1:' \\
      '-----BEGIN CERTIFICATE-----' \\
      '${certificateBase64}' \\
      '-----END CERTIFICATE-----'${secondSigner ? " \\\n      'Signer #2:' \\\n      '-----BEGIN CERTIFICATE-----' \\\n      '" + certificateBase64 + "' \\\n      '-----END CERTIFICATE-----'" : ""}
    ;;
  *" -importcert "*)
    while [ "$#" -gt 0 ]; do
      if [ "$1" = '-keystore' ]; then
        shift
        printf '%s\\n' 'public certificate truststore' > "$1"
        exit 0
      fi
      shift
    done
    exit 1
    ;;
  *) exit 1 ;;
esac
`;

const createFixture = () => {
  const root = realpathSync(
    resolve(mkdtempSync(join(tmpdir(), "sora-signing-verification-"))),
  );
  mkdirSync(join(root, "tools"), { mode: 0o700 });
  const paths = {
    root,
    aab: join(root, "candidate.aab"),
    apk: join(root, "candidate.apk"),
    config: join(root, "signing.json"),
    jarsigner: join(root, "tools/jarsigner"),
    keytool: join(root, "tools/keytool"),
    apksigner: join(root, "tools/apksigner"),
  };
  writeFileSync(paths.aab, "signed aab\n", { mode: 0o600 });
  writeFileSync(paths.apk, "signed apk\n", { mode: 0o600 });
  writeFileSync(paths.jarsigner, "#!/bin/sh\nexit 0\n", { mode: 0o700 });
  writeFileSync(paths.keytool, keytoolScript(), { mode: 0o700 });
  writeFileSync(
    paths.apksigner,
    `#!/bin/sh\nprintf '%s\\n' 'Verifies' 'Verified using v2 scheme (APK Signature Scheme v2): true' 'Signer #1 certificate SHA-256 digest: ${UPLOAD_CERTIFICATE}'\n`,
    { mode: 0o700 },
  );
  chmodSync(paths.jarsigner, 0o700);
  chmodSync(paths.keytool, 0o700);
  chmodSync(paths.apksigner, 0o700);
  const config = {
    schemaVersion: 1,
    platform: "android",
    applicationId: "jp.co.soramitsu.sora",
    status: "qualified",
    releaseEnabled: true,
    debugFallbackAllowedForRelease: false,
    productionAppSigningCertificateSha256: APP_CERTIFICATE,
    productionUploadCertificateSha256: UPLOAD_CERTIFICATE,
    retainedProductionCertificateMatched: true,
    signedBundleCertificateMatched: true,
    playAppSigningContinuityReviewed: true,
    secretsRecordedInEvidence: false,
  };
  writeJson(paths.config, config);
  return { root, paths, config };
};

const verify = (fixture) =>
  verifyAndroidCandidateSigningV1({
    aabPath: fixture.paths.aab,
    apkPath: fixture.paths.apk,
    signingConfigPath: fixture.paths.config,
    jarsignerPath: fixture.paths.jarsigner,
    keytoolPath: fixture.paths.keytool,
    apksignerPath: fixture.paths.apksigner,
  });

assert.equal(
  parseApkSignerCertificateSha256(
    `Signer #1 certificate SHA-256 digest: ${UPLOAD_CERTIFICATE}\n`,
  ),
  UPLOAD_CERTIFICATE,
);
assert.equal(
  parseApkSignerCertificateSha256(
    `Signer #1 certificate SHA-256 digest: ${UPLOAD_CERTIFICATE}\nSigner #2 certificate SHA-256 digest: ${APP_CERTIFICATE}\n`,
  ),
  null,
);
assert.equal(
  parseKeytoolJarSignerCertificate(
    `Signer #1:\n-----BEGIN CERTIFICATE-----\n${TEST_CERTIFICATE_BASE64}\n-----END CERTIFICATE-----\n`,
  )?.sha256,
  UPLOAD_CERTIFICATE,
);
assert.equal(
  parseKeytoolJarSignerCertificate(
    `Signer #1:\n-----BEGIN CERTIFICATE-----\n${TEST_CERTIFICATE_BASE64}\n-----END CERTIFICATE-----\nSigner #2:\n-----BEGIN CERTIFICATE-----\n${TEST_CERTIFICATE_BASE64}\n-----END CERTIFICATE-----\n`,
  ),
  null,
);
assert.equal(
  parseKeytoolJarSignerCertificate(
    `Signer #01:\n-----BEGIN CERTIFICATE-----\n${TEST_CERTIFICATE_BASE64}\n-----END CERTIFICATE-----\n`,
  ),
  null,
);
assert.equal(
  parseApkSignerCertificateSha256(
    `Signer #01 certificate SHA-256 digest: ${UPLOAD_CERTIFICATE}\n`,
  ),
  null,
);

const positive = createFixture();
try {
  const receipt = verify(positive);
  assert.equal(validateAndroidCandidateSigningVerificationV1(receipt), true);
  assert.equal(
    receipt.signingIdentity.observedUploadCertificateSha256,
    UPLOAD_CERTIFICATE,
  );
  assert.equal(
    receipt.signingIdentity.observedAabUploadCertificateSha256,
    UPLOAD_CERTIFICATE,
  );
  assert.equal(receipt.authorization.authorizesRelease, false);
  const tampered = structuredClone(receipt);
  tampered.artifacts.aabBytes += 1;
  assert.equal(validateAndroidCandidateSigningVerificationV1(tampered), false);
  tampered.signingIdentity.observedUploadCertificateSha256 = APP_CERTIFICATE;
  assert.equal(validateAndroidCandidateSigningVerificationV1(tampered), false);
} finally {
  rmSync(positive.root, { recursive: true, force: true });
}

const mutations = [
  [
    "unqualified-config",
    "ANDROID_CANDIDATE_SIGNING_IDENTITY_NOT_QUALIFIED",
    (fixture) => {
      fixture.config.status = "blocked";
      fixture.config.releaseEnabled = false;
      writeJson(fixture.paths.config, fixture.config);
    },
  ],
  [
    "aab-certificate-mismatch",
    "ANDROID_CANDIDATE_AAB_UPLOAD_CERTIFICATE_MISMATCH",
    (fixture) => {
      const otherCertificate = Buffer.alloc(128, 0x33).toString("base64");
      writeFileSync(
        fixture.paths.keytool,
        keytoolScript({ certificateBase64: otherCertificate }),
        { mode: 0o700 },
      );
    },
  ],
  [
    "multiple-aab-signers",
    "ANDROID_CANDIDATE_AAB_CERTIFICATE_INVALID",
    (fixture) =>
      writeFileSync(
        fixture.paths.keytool,
        keytoolScript({ secondSigner: true }),
        { mode: 0o700 },
      ),
  ],
  [
    "apk-certificate-mismatch",
    "ANDROID_CANDIDATE_UPLOAD_CERTIFICATE_MISMATCH",
    (fixture) =>
      writeFileSync(
        fixture.paths.apksigner,
        `#!/bin/sh\nprintf '%s\\n' 'Signer #1 certificate SHA-256 digest: ${APP_CERTIFICATE}'\n`,
        { mode: 0o700 },
      ),
  ],
  [
    "multiple-signers",
    "ANDROID_CANDIDATE_UPLOAD_CERTIFICATE_MISMATCH",
    (fixture) => {
      writeFileSync(
        fixture.paths.apksigner,
        `#!/bin/sh\nprintf '%s\\n' 'Signer #1 certificate SHA-256 digest: ${UPLOAD_CERTIFICATE}' 'Signer #2 certificate SHA-256 digest: ${APP_CERTIFICATE}'\n`,
        { mode: 0o700 },
      );
    },
  ],
  [
    "keytool-failure",
    "ANDROID_CANDIDATE_AAB_CERTIFICATE_INVALID",
    (fixture) =>
      writeFileSync(fixture.paths.keytool, "#!/bin/sh\nexit 1\n", {
        mode: 0o700,
      }),
  ],
  [
    "jarsigner-failure",
    "ANDROID_CANDIDATE_AAB_SIGNATURE_INVALID",
    (fixture) =>
      writeFileSync(fixture.paths.jarsigner, "#!/bin/sh\nexit 1\n", {
        mode: 0o700,
      }),
  ],
  [
    "aab-changed-during-verification",
    "ANDROID_CANDIDATE_SIGNING_INPUT_CHANGED_DURING_VERIFICATION",
    (fixture) =>
      writeFileSync(
        fixture.paths.jarsigner,
        `#!/bin/sh\nfor candidate in "$@"; do\n  case "$candidate" in\n    *.aab) printf '%s\\n' 'changed after certificate extraction' > "$candidate" ;;\n  esac\ndone\nexit 0\n`,
        { mode: 0o700 },
      ),
  ],
  [
    "symbolic-apk",
    "ANDROID_CANDIDATE_SIGNING_APK_INVALID",
    (fixture) => {
      const target = join(fixture.root, "real.apk");
      writeFileSync(target, readFileSync(fixture.paths.apk), { mode: 0o600 });
      unlinkSync(fixture.paths.apk);
      symlinkSync(target, fixture.paths.apk);
    },
  ],
  [
    "hardlinked-aab",
    "ANDROID_CANDIDATE_SIGNING_AAB_INVALID",
    (fixture) =>
      linkSync(fixture.paths.aab, join(fixture.root, "candidate-alias.aab")),
  ],
  [
    "symbolic-tool",
    "ANDROID_CANDIDATE_APKSIGNER_INVALID",
    (fixture) => {
      const target = join(fixture.root, "tools/real-apksigner");
      writeFileSync(target, readFileSync(fixture.paths.apksigner), { mode: 0o700 });
      unlinkSync(fixture.paths.apksigner);
      symlinkSync(target, fixture.paths.apksigner);
    },
  ],
  [
    "symbolic-keytool",
    "ANDROID_CANDIDATE_KEYTOOL_INVALID",
    (fixture) => {
      const target = join(fixture.root, "tools/real-keytool");
      writeFileSync(target, readFileSync(fixture.paths.keytool), { mode: 0o700 });
      unlinkSync(fixture.paths.keytool);
      symlinkSync(target, fixture.paths.keytool);
    },
  ],
  [
    "duplicate-config-key",
    "ANDROID_CANDIDATE_SIGNING_CONFIG_INVALID",
    (fixture) =>
      writeFileSync(
        fixture.paths.config,
        '{"schemaVersion":1,"schemaVersion":1}\n',
      ),
  ],
];

for (const [name, expectedCode, mutate] of mutations) {
  const fixture = createFixture();
  try {
    mutate(fixture);
    assert.throws(
      () => verify(fixture),
      (error) => error instanceof Error && error.message === expectedCode,
      name,
    );
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
}

process.stdout.write(
  `Android candidate signing verification v1: positive verification, strict parser checks, and ${mutations.length} fail-closed mutations passed.\n`,
);
