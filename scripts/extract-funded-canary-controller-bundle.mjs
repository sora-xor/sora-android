#!/usr/bin/env node

import { extractFundedCanaryControllerBundleV1 } from "./lib/funded-canary-controller-bundle-v1.mjs";

const required = (name) => {
  const value = process.env[name] ?? "";
  if (value.length === 0) throw new Error(`${name}_MISSING`);
  return value;
};

const receipt = extractFundedCanaryControllerBundleV1({
  tarPath: required("FUNDED_CANARY_CONTROLLER_BUNDLE_PATH"),
  outputRoot: required("FUNDED_CANARY_CONTROLLER_EVIDENCE_ROOT"),
});
process.stdout.write(`${JSON.stringify(receipt, null, 2)}\n`);
