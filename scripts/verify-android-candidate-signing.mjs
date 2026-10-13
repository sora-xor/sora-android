#!/usr/bin/env node

import { join, resolve } from "node:path";
import {
  validateAndroidCandidateSigningVerificationV1,
  verifyAndroidCandidateSigningV1,
} from "./lib/android-candidate-signing-verification-v1.mjs";

const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) throw new Error(`${name}_MISSING`);
  return value;
};

const root = resolve(new URL("..", import.meta.url).pathname);
const receipt = verifyAndroidCandidateSigningV1({
  aabPath: required("PRODUCTION_CANDIDATE_AAB_PATH"),
  apkPath: required("PRODUCTION_CANDIDATE_APK_PATH"),
  signingConfigPath: join(root, "config/android-production-signing-identity.json"),
  jarsignerPath: required("PRODUCTION_JARSIGNER_PATH"),
  keytoolPath: required("PRODUCTION_KEYTOOL_PATH"),
  apksignerPath: required("PRODUCTION_APKSIGNER_PATH"),
});
if (!validateAndroidCandidateSigningVerificationV1(receipt)) {
  throw new Error("ANDROID_CANDIDATE_SIGNING_RECEIPT_SELF_CHECK_FAILED");
}
process.stdout.write(`${JSON.stringify(receipt, null, 2)}\n`);
