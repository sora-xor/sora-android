#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import https from "node:https";
import path from "node:path";

const ENDPOINT = "https://ws.mof.sora.org";
const EXPECTED_SPEC_VERSION = 130;
const EXPECTED_TRANSACTION_VERSION = 130;
const MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
const DESTINATIONS = [
  "common/src/production/assets/sora2_metadata",
  "feature_wallet_impl/src/test/resources/sora2_metadata",
];

function rpc(method) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ jsonrpc: "2.0", id: 1, method, params: [] });
    const request = https.request(
      ENDPOINT,
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "content-length": Buffer.byteLength(body),
        },
        timeout: 20_000,
      },
      (response) => {
        if (response.statusCode !== 200) {
          reject(new Error(`RPC_HTTP_${response.statusCode}`));
          response.resume();
          return;
        }
        const chunks = [];
        let size = 0;
        response.on("data", (chunk) => {
          size += chunk.length;
          if (size > MAX_RESPONSE_BYTES) {
            request.destroy(new Error("RPC_RESPONSE_TOO_LARGE"));
            return;
          }
          chunks.push(chunk);
        });
        response.on("end", () => {
          try {
            const envelope = JSON.parse(Buffer.concat(chunks).toString("utf8"));
            if (envelope.error || envelope.result === undefined) {
              throw new Error("RPC_INVALID_ENVELOPE");
            }
            resolve(envelope.result);
          } catch (error) {
            reject(error);
          }
        });
      },
    );
    request.on("timeout", () => request.destroy(new Error("RPC_TIMEOUT")));
    request.on("error", reject);
    request.end(body);
  });
}

const version = await rpc("state_getRuntimeVersion");
if (
  version.specVersion !== EXPECTED_SPEC_VERSION ||
  version.transactionVersion !== EXPECTED_TRANSACTION_VERSION
) {
  throw new Error(
    `RUNTIME_VERSION_MISMATCH_${version.specVersion}_${version.transactionVersion}`,
  );
}

const metadataHex = await rpc("state_getMetadata");
if (
  typeof metadataHex !== "string" ||
  !/^0x[0-9a-f]+$/i.test(metadataHex) ||
  metadataHex.length % 2 !== 0
) {
  throw new Error("INVALID_METADATA_HEX");
}
const metadata = Buffer.from(metadataHex.slice(2), "hex");
if (!metadata.subarray(0, 4).equals(Buffer.from("meta"))) {
  throw new Error("INVALID_METADATA_MAGIC");
}
for (const required of [
  "Polkamarkt",
  "buy",
  "sell",
  "claim_market",
  "claim_markets",
  "claim_creator_fees",
]) {
  if (!metadata.includes(Buffer.from(required))) {
    throw new Error(`METADATA_CONTRACT_MISSING_${required}`);
  }
}

for (const destination of DESTINATIONS) {
  const absolute = path.resolve(destination);
  const temporary = `${absolute}.next`;
  const handle = fs.openSync(temporary, "wx", 0o644);
  try {
    fs.writeFileSync(handle, `${metadataHex.toLowerCase()}\n`, "utf8");
    fs.fsyncSync(handle);
  } finally {
    fs.closeSync(handle);
  }
  fs.renameSync(temporary, absolute);
}

const sha256 = crypto.createHash("sha256").update(`${metadataHex.toLowerCase()}\n`).digest("hex");
process.stdout.write(
  JSON.stringify({
    endpoint: ENDPOINT,
    specVersion: version.specVersion,
    transactionVersion: version.transactionVersion,
    decodedBytes: metadata.length,
    fileSha256: sha256,
    destinations: DESTINATIONS,
  }) + "\n",
);
