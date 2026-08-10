#!/usr/bin/env node

import { parseStrictJsonBytes } from "./lib/strict-evidence.mjs";

const ENDPOINT = "https://pi.soramitsu.io/graphql";
const SORA2_RPC_ENDPOINT = "https://ws.mof.sora.org/";
const SORA2_GENESIS_HASH =
  "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5";
const REQUEST_TIMEOUT_MILLIS = 20_000;
const MAXIMUM_RESPONSE_BYTES = 256 * 1024;
const MAXIMUM_CHECKPOINT_AGE_SECONDS = 5 * 60;
const MAXIMUM_FUTURE_SKEW_SECONDS = 30;
const MAXIMUM_CHECKPOINT_LAG_BLOCKS = 32;
const SYNTHETIC_HISTORY_ACCOUNT = "sora-mobile-contract-probe-no-account";

const HEALTH_FIELDS = `
  ok
  repositoryReady
  service
  serviceId
  schemaVersion
  ecosystem
  chainId
  network
  publicBaseUrl
  readOnly
  workerAvailable
  workerReady
  workerReadinessReason
  workerLifecycle
  workerStartupComplete
  workerLatestFinalizedBlock
  workerLatestIndexedBlock
  workerLag
  workerLastSuccessfulIndexTimestamp
  workerLastError
  workerLastErrorTimestamp
`;

const HEALTH_QUERY = `
  query Health {
    _health { ${HEALTH_FIELDS} }
  }
`;

const MOBILE_CONFIG_QUERY = `
  query MobileConfigWithHealth {
    _health { ${HEALTH_FIELDS} }
    mobileConfig {
      blockExplorerUrl
      substrateTypesUrl
      soracard
      nodes { name address }
      nexusAvailable
      nexusSendsAvailable
      polkamarktVisible
      polkamarktMutationsAvailable
      tairaDefaultVisible
    }
  }
`;

const HISTORY_CONTRACT_QUERY = `
  query HistoryContract($probe: String!) {
    historyElements(
      first: 1
      orderBy: [TIMESTAMP_DESC, ID_DESC]
      filter: { address: { equalTo: $probe } }
    ) {
      totalCount
      pageInfo { hasNextPage }
      edges {
        node {
          id
          timestamp
          blockHeight
          blockHash
          address
          dataFrom
          dataTo
        }
      }
    }
  }
`;

function requireContract(condition, code) {
  if (!condition) throw new Error(code);
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

async function readBoundedBody(response, controller) {
  requireContract(response.body !== null, "PI_PROBE_RESPONSE_BODY_MISSING");
  const reader = response.body.getReader();
  const chunks = [];
  let totalBytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      requireContract(value instanceof Uint8Array, "PI_PROBE_RESPONSE_INVALID");
      totalBytes += value.byteLength;
      if (totalBytes > MAXIMUM_RESPONSE_BYTES) {
        controller.abort();
        throw new Error("PI_PROBE_RESPONSE_TOO_LARGE");
      }
      chunks.push(value);
    }
  } catch (error) {
    if (error instanceof Error && error.message.startsWith("PI_PROBE_")) {
      throw error;
    }
    throw new Error("PI_PROBE_RESPONSE_READ_FAILED");
  } finally {
    reader.releaseLock();
  }
  requireContract(totalBytes > 0, "PI_PROBE_RESPONSE_SIZE_INVALID");
  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

async function graphql(query, variables = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);
  try {
    let response;
    try {
      response = await fetch(ENDPOINT, {
        method: "POST",
        redirect: "error",
        headers: {
          accept: "application/json",
          "content-type": "application/json",
        },
        body: JSON.stringify({ query, variables }),
        signal: controller.signal,
      });
    } catch {
      throw new Error("PI_PROBE_TRANSPORT_FAILED");
    }

    requireContract(response.ok, "PI_PROBE_HTTP_FAILED");
    requireContract(response.url === ENDPOINT, "PI_PROBE_REDIRECTED");
    const contentType = response.headers.get("content-type") ?? "";
    requireContract(
      contentType.toLowerCase().startsWith("application/json"),
      "PI_PROBE_CONTENT_TYPE_INVALID",
    );
    const contentLength = response.headers.get("content-length");
    if (contentLength !== null) {
      const parsedLength = Number(contentLength);
      requireContract(
        Number.isSafeInteger(parsedLength) &&
          parsedLength >= 0 &&
          parsedLength <= MAXIMUM_RESPONSE_BYTES,
        "PI_PROBE_RESPONSE_TOO_LARGE",
      );
    }

    const bytes = await readBoundedBody(response, controller);
    let envelope;
    try {
      envelope = parseStrictJsonBytes(bytes);
    } catch {
      throw new Error("PI_PROBE_RESPONSE_INVALID");
    }
    requireContract(isPlainObject(envelope), "PI_PROBE_ENVELOPE_INVALID");
    requireContract(
      envelope.errors === undefined ||
        (Array.isArray(envelope.errors) && envelope.errors.length === 0),
      "PI_PROBE_GRAPHQL_ERROR",
    );
    requireContract(isPlainObject(envelope.data), "PI_PROBE_DATA_MISSING");
    return envelope.data;
  } finally {
    clearTimeout(timeout);
  }
}

async function substrateBlockHash(height, id) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);
  try {
    let response;
    try {
      response = await fetch(SORA2_RPC_ENDPOINT, {
        method: "POST",
        redirect: "error",
        headers: {
          accept: "application/json",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          jsonrpc: "2.0",
          id,
          method: "chain_getBlockHash",
          params: [height],
        }),
        signal: controller.signal,
      });
    } catch {
      throw new Error("SORA2_PROBE_TRANSPORT_FAILED");
    }
    requireContract(response.ok, "SORA2_PROBE_HTTP_FAILED");
    requireContract(
      response.url === SORA2_RPC_ENDPOINT,
      "SORA2_PROBE_REDIRECTED",
    );
    requireContract(
      (response.headers.get("content-type") ?? "")
        .toLowerCase()
        .startsWith("application/json"),
      "SORA2_PROBE_CONTENT_TYPE_INVALID",
    );
    const bytes = await readBoundedBody(response, controller);
    let envelope;
    try {
      envelope = parseStrictJsonBytes(bytes);
    } catch {
      throw new Error("SORA2_PROBE_RESPONSE_INVALID");
    }
    requireContract(
      isPlainObject(envelope) &&
        Object.keys(envelope).sort().join("\n") ===
          ["id", "jsonrpc", "result"].join("\n") &&
        envelope.jsonrpc === "2.0" &&
        envelope.id === id &&
        typeof envelope.result === "string" &&
        /^0x[0-9a-f]{64}$/.test(envelope.result) &&
        !/^0x0{64}$/.test(envelope.result),
      "SORA2_PROBE_RESPONSE_INVALID",
    );
    return envelope.result;
  } finally {
    clearTimeout(timeout);
  }
}

function validateHealth(health) {
  requireContract(isPlainObject(health), "PI_HEALTH_MISSING");
  requireContract(health.ok === true, "PI_HEALTH_NOT_OK");
  requireContract(health.repositoryReady === true, "PI_REPOSITORY_NOT_READY");
  requireContract(health.service === "polkaswap-indexer", "PI_SERVICE_MISMATCH");
  requireContract(
    health.serviceId === "pi.soramitsu.io",
    "PI_SERVICE_ID_MISMATCH",
  );
  requireContract(health.schemaVersion === 1, "PI_SCHEMA_VERSION_MISMATCH");
  requireContract(health.ecosystem === "sora2", "PI_ECOSYSTEM_MISMATCH");
  requireContract(health.chainId === "sora:mainnet", "PI_CHAIN_ID_MISMATCH");
  requireContract(health.network === "mainnet", "PI_NETWORK_MISMATCH");
  requireContract(health.publicBaseUrl === ENDPOINT, "PI_PUBLIC_URL_MISMATCH");
  requireContract(health.readOnly === true, "PI_NOT_READ_ONLY");
  requireContract(health.workerAvailable === true, "PI_WORKER_UNAVAILABLE");
  requireContract(health.workerReady === true, "PI_WORKER_NOT_READY");
  requireContract(health.workerLifecycle === "running", "PI_WORKER_NOT_RUNNING");
  requireContract(health.workerStartupComplete === true, "PI_WORKER_STARTING");
  requireContract(
    health.workerReadinessReason === null,
    "PI_WORKER_READINESS_ERROR",
  );
  requireContract(health.workerLastError === null, "PI_WORKER_ERROR_PRESENT");
  requireContract(
    health.workerLastErrorTimestamp === null,
    "PI_WORKER_ERROR_TIMESTAMP_PRESENT",
  );

  const finalized = health.workerLatestFinalizedBlock;
  const indexed = health.workerLatestIndexedBlock;
  const lag = health.workerLag;
  requireContract(
    Number.isSafeInteger(finalized) && finalized > 0,
    "PI_FINALIZED_CHECKPOINT_INVALID",
  );
  requireContract(
    Number.isSafeInteger(indexed) && indexed > 0 && indexed <= finalized,
    "PI_INDEXED_CHECKPOINT_INVALID",
  );
  requireContract(
    Number.isSafeInteger(lag) &&
      lag === finalized - indexed &&
      lag <= MAXIMUM_CHECKPOINT_LAG_BLOCKS,
    "PI_WORKER_LAG_INVALID",
  );
  const lastIndexedAt = health.workerLastSuccessfulIndexTimestamp;
  requireContract(
    Number.isSafeInteger(lastIndexedAt) && lastIndexedAt >= 0,
    "PI_WORKER_TIMESTAMP_INVALID",
  );
  const now = Math.floor(Date.now() / 1000);
  requireContract(
    lastIndexedAt <= now + MAXIMUM_FUTURE_SKEW_SECONDS &&
      now - lastIndexedAt <= MAXIMUM_CHECKPOINT_AGE_SECONDS,
    "PI_WORKER_CHECKPOINT_STALE",
  );
  return { finalized, indexed, lastIndexedAt };
}

function validateMobileConfig(config) {
  requireContract(isPlainObject(config), "PI_MOBILE_CONFIG_MISSING");
  const flags = [
    "nexusAvailable",
    "nexusSendsAvailable",
    "polkamarktVisible",
    "polkamarktMutationsAvailable",
    "tairaDefaultVisible",
  ];
  for (const flag of flags) {
    requireContract(
      typeof config[flag] === "boolean",
      `PI_MOBILE_CONFIG_${flag}_INVALID`,
    );
  }
  requireContract(
    !config.nexusSendsAvailable || config.nexusAvailable,
    "PI_NEXUS_CAPABILITY_CONFLICT",
  );
  requireContract(
    !config.polkamarktMutationsAvailable || config.polkamarktVisible,
    "PI_POLKAMARKT_CAPABILITY_CONFLICT",
  );
  requireContract(
    Array.isArray(config.nodes) && config.nodes.length <= 100,
    "PI_NODES_INVALID",
  );
  return Object.fromEntries(flags.map((flag) => [flag, config[flag]]));
}

function validateHistoryContract(history) {
  requireContract(isPlainObject(history), "PI_HISTORY_CONTRACT_MISSING");
  requireContract(history.totalCount === 0, "PI_HISTORY_PROBE_COLLISION");
  requireContract(
    isPlainObject(history.pageInfo) && history.pageInfo.hasNextPage === false,
    "PI_HISTORY_PAGE_INVALID",
  );
  requireContract(
    Array.isArray(history.edges) && history.edges.length === 0,
    "PI_HISTORY_ROWS_UNEXPECTED",
  );
}

try {
  const healthData = await graphql(HEALTH_QUERY);
  const checkpoint = validateHealth(healthData._health);
  const mobileConfigData = await graphql(MOBILE_CONFIG_QUERY);
  const capabilityCheckpoint = validateHealth(mobileConfigData._health);
  requireContract(
    capabilityCheckpoint.finalized >= checkpoint.finalized &&
      capabilityCheckpoint.indexed >= checkpoint.indexed &&
      capabilityCheckpoint.lastIndexedAt >= checkpoint.lastIndexedAt,
    "PI_MOBILE_CONFIG_HEALTH_REGRESSED",
  );
  const capabilities = validateMobileConfig(mobileConfigData.mobileConfig);
  const historyData = await graphql(HISTORY_CONTRACT_QUERY, {
    probe: SYNTHETIC_HISTORY_ACCOUNT,
  });
  validateHistoryContract(historyData.historyElements);
  const sora2GenesisHash = await substrateBlockHash(0, 1);
  requireContract(
    sora2GenesisHash === SORA2_GENESIS_HASH,
    "SORA2_PROBE_GENESIS_MISMATCH",
  );
  const finalizedCheckpointBlockHash = await substrateBlockHash(
    checkpoint.finalized,
    2,
  );
  const capabilityFinalizedCheckpointBlockHash =
    capabilityCheckpoint.finalized === checkpoint.finalized
      ? finalizedCheckpointBlockHash
      : await substrateBlockHash(capabilityCheckpoint.finalized, 3);

  process.stdout.write(
    `${JSON.stringify(
      {
        schemaVersion: 1,
        endpoint: ENDPOINT,
        checkedAtEpochSeconds: Math.floor(Date.now() / 1000),
        serviceId: "pi.soramitsu.io",
        ecosystem: "sora2",
        chainId: "sora:mainnet",
        network: "mainnet",
        readOnly: true,
        finalizedCheckpoint: checkpoint.finalized,
        indexedCheckpoint: checkpoint.indexed,
        lastIndexedAtEpochSeconds: checkpoint.lastIndexedAt,
        mobileConfigHealthBound: true,
        capabilityFinalizedCheckpoint: capabilityCheckpoint.finalized,
        capabilityIndexedCheckpoint: capabilityCheckpoint.indexed,
        sora2GenesisHash,
        finalizedCheckpointBlockHash,
        capabilityFinalizedCheckpointBlockHash,
        historyBlockHeightContractDeployed: true,
        capabilities,
        privacy: {
          accountIdentifiersIncluded: false,
          mutationsAttempted: false,
        },
      },
      null,
      2,
    )}\n`,
  );
} catch (error) {
  const code = error instanceof Error ? error.message : "PI_PROBE_FAILED";
  process.stderr.write(`error: ${code}\n`);
  process.exitCode = 1;
}
