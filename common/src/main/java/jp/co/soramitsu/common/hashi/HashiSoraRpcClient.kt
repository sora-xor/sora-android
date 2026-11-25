package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import java.time.Instant
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.intOrNull

/** A request status and its raw SCALE value (`Failed(e)` and `Broken(e1, e2)` keep their errors). */
class HashiRequestStatusValue(val status: HashiRequestStatus, val raw: String)

/** Where the operators' signer report is read from (the app's remote config document). */
fun interface HashiSignerReportSource {
    /** The whole config document, or throws when it cannot be read. */
    suspend fun configJson(): String
}

/**
 * Read-only SORA mainnet access for the bridge over the reviewed HTTPS JSON-RPC endpoint (the one
 * the wallet already submits transactions through). Storage that is judged together is read with
 * one `state_queryStorageAt` at one block.
 */
class HashiSoraRpcClient(
    private val transport: HashiRpcTransport,
    private val endpoint: String = SORA_MAINNET_RPC,
) {
    suspend fun request(method: String, params: JsonArray): JsonElement {
        val (id, body) = HashiJsonRpc.request(method, params)
        return HashiJsonRpc.result(transport.post(endpoint, body), id)
    }

    /** The values of [keys] at block [at] (the best block when null); null = no value. */
    suspend fun storage(keys: List<String>, at: String? = null): Map<String, String?> {
        require(keys.isNotEmpty() && keys.size <= MAXIMUM_KEYS_PER_READ) { "HASHI_STORAGE_KEYS_INVALID" }
        val canonical = keys.map { EthHex.encode(EthHex.decode(it)) }.distinct()
        val answer = request(
            "state_queryStorageAt",
            buildJsonArray {
                addJsonArray { canonical.forEach { add(it) } }
                if (at != null) add(at)
            },
        )
        val sets = answer as? JsonArray ?: throw IllegalArgumentException("HASHI_STORAGE_ANSWER_INVALID")
        require(sets.size == 1) { "HASHI_STORAGE_ANSWER_INVALID" }
        val set = sets[0] as? JsonObject ?: throw IllegalArgumentException("HASHI_STORAGE_ANSWER_INVALID")
        if (at != null) {
            require((set["block"] as? JsonPrimitive)?.content.equals(at, ignoreCase = true)) { "HASHI_STORAGE_BLOCK_MISMATCH" }
        }
        val changes = set["changes"] as? JsonArray ?: throw IllegalArgumentException("HASHI_STORAGE_ANSWER_INVALID")
        val values = mutableMapOf<String, String?>()
        changes.forEach { change ->
            val pair = change as? JsonArray ?: throw IllegalArgumentException("HASHI_STORAGE_ANSWER_INVALID")
            require(pair.size == 2) { "HASHI_STORAGE_ANSWER_INVALID" }
            val key = (pair[0] as? JsonPrimitive)?.content?.lowercase()
                ?: throw IllegalArgumentException("HASHI_STORAGE_ANSWER_INVALID")
            val value = when (val raw = pair[1]) {
                is JsonNull -> null
                is JsonPrimitive -> raw.content.also { EthHex.decode(it) }
                else -> throw IllegalArgumentException("HASHI_STORAGE_ANSWER_INVALID")
            }
            require(key in canonical && key !in values) { "HASHI_STORAGE_ANSWER_INVALID" }
            values[key] = value
        }
        // A node leaves absent keys out of the change set: they have no value.
        return canonical.associateWith { values[it] }
    }

    suspend fun specVersion(): Int {
        val version = request("state_getRuntimeVersion", JsonArray(emptyList())) as? JsonObject
            ?: throw IllegalArgumentException("HASHI_RUNTIME_VERSION_INVALID")
        return (version["specVersion"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("HASHI_RUNTIME_VERSION_INVALID")
    }

    /** The finalized head: (hash, number). */
    suspend fun finalizedHead(): Pair<String, Long> {
        val hash = (request("chain_getFinalizedHead", JsonArray(emptyList())) as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("HASHI_FINALIZED_HEAD_INVALID")
        EthHex.decodeFixed(hash, 32)
        val header = request("chain_getHeader", buildJsonArray { add(hash) }) as? JsonObject
            ?: throw IllegalArgumentException("HASHI_HEADER_INVALID")
        val number = (header["number"] as? JsonPrimitive)?.content ?: throw IllegalArgumentException("HASHI_HEADER_INVALID")
        return hash to EthHex.parseQuantity(number).toLong()
    }

    /**
     * Whether new transfers may start (spec §8 A): consensus admission, no guardian halt, bridge
     * `Initialized`, the reviewed contract addresses, no stalled request, and a fresh operator
     * signer report that agrees with the chain's peer set. Every read failure blocks.
     */
    suspend fun availability(signerReports: HashiSignerReportSource, now: Instant): HashiAvailability {
        return try {
            readAvailability(signerReports, now)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            HashiAvailability.readFailed()
        }
    }

    private suspend fun readAvailability(signerReports: HashiSignerReportSource, now: Instant): HashiAvailability {
        val spec = specVersion()
        val (finalizedHash, finalizedNumber) = finalizedHead()
        val recoveryKeys = HashiRecoveryKeys.RECOVERY.associateWith(HashiRecoveryKeys::hex)
        val legacyKey = HashiRecoveryKeys.hex(HashiRecoveryKeys.LEGACY_BRIDGE_HALT)
        val guardianKey = HashiRecoveryKeys.hex(HashiRecoveryKeys.GUARDIAN_HALT)
        val statusKey = HashiSoraStorage.bridgeStatusKey()
        val bridgeKey = HashiSoraStorage.bridgeContractAddressKey()
        val valMasterKey = HashiSoraStorage.valMasterContractAddressKey
        val queueKey = HashiSoraStorage.requestsQueueKey()
        val peersKey = HashiSoraStorage.peersKey()
        val pendingPeerKey = HashiSoraStorage.pendingPeerKey()
        val values = storage(
            recoveryKeys.values.toList() +
                listOf(legacyKey, guardianKey, statusKey, bridgeKey, valMasterKey, queueKey, peersKey, pendingPeerKey),
            at = finalizedHash,
        )
        val blocks = linkedSetOf<HashiBlock>()

        val admitted = if (spec < HashiAvailability.REVIEWED_SPEC_VERSION) {
            values[legacyKey] == null
        } else {
            // A newer runtime is judged by the 142 rule until it is reviewed again.
            HashiRecoveryCompletion.evaluate { key -> values[recoveryKeys.getValue(key)]?.let(EthHex::decode) }.admitted
        }
        if (!admitted) blocks += HashiBlock.RECOVERY_NOT_COMPLETE
        if (values[guardianKey] != null) blocks += HashiBlock.GUARDIAN_HALT
        if (values[statusKey]?.lowercase() != "0x00") blocks += HashiBlock.NOT_INITIALIZED

        val bridge = values[bridgeKey]?.let(HashiSoraStorage::decodeAddress)
        val valMaster = values[valMasterKey]?.let(HashiSoraStorage::decodeAddress)
        if (bridge == null || valMaster == null ||
            !EthAddress.equal(bridge, HashiBridgeContract.bridge) ||
            !EthAddress.equal(valMaster, HashiBridgeContract.valMaster)
        ) {
            blocks += HashiBlock.CONTRACT_MISMATCH
        }

        val queue = values[queueKey]?.let { HashiSoraStorage.decodeFixedVector(it, 32) }.orEmpty()
        if (queue.size > MAXIMUM_QUEUE_CHECKED) {
            blocks += HashiBlock.STALLED_REQUESTS
        } else if (queue.isNotEmpty()) {
            val heights = queue.chunked(MAXIMUM_KEYS_PER_READ).flatMap { chunk ->
                val keys = chunk.map(HashiSoraStorage::requestSubmissionHeightKey)
                val read = storage(keys, at = finalizedHash)
                keys.map { key -> read[key]?.let(HashiSoraStorage::decodeU32) ?: 0L }
            }
            if (heights.any { finalizedNumber - it > HashiAvailability.STALL_BLOCKS }) blocks += HashiBlock.STALLED_REQUESTS
        }

        val peers = values[peersKey]?.let { HashiSoraStorage.decodeFixedVector(it, 32) }.orEmpty()
        val pendingPeer = values[pendingPeerKey] != null
        val report = HashiSignerReports.parse(signerReports.configJson())
        val verdict = HashiSignerReports.judge(report, now, peers.size, pendingPeer)
        when (verdict) {
            HashiSignerReports.Verdict.UP -> Unit
            HashiSignerReports.Verdict.DOWN -> blocks += HashiBlock.SIGNERS_DOWN
            else -> blocks += HashiBlock.SIGNERS_UNVERIFIED
        }
        return HashiAvailability(blocks, spec, finalizedNumber, verdict)
    }

    suspend fun registeredAssets(): List<HashiRegisteredAsset> = HashiRpcParsers.registeredAssets(
        request("ethBridge_getRegisteredAssets", buildJsonArray { add(HashiBridgeContract.NETWORK_ID) })
    )

    suspend fun approvedRequest(requestHash: ByteArray): HashiApprovedRequest? = HashiRpcParsers.approvedRequest(
        request(
            "ethBridge_getApprovedRequests",
            buildJsonArray {
                addJsonArray { add(EthHex.encode(requestHash)) }
                add(HashiBridgeContract.NETWORK_ID)
            },
        ),
        requestHash,
    )

    /** `ethBridge_getRequests([[hash], 0, true])`: finished load requests redirect to the incoming one. */
    suspend fun requests(hash: ByteArray): List<HashiRequestRow> = HashiRpcParsers.requests(
        request(
            "ethBridge_getRequests",
            buildJsonArray {
                addJsonArray { add(EthHex.encode(hash)) }
                add(HashiBridgeContract.NETWORK_ID)
                add(true)
            },
        )
    )

    /** `requestStatuses(0, hash)`; null while the chain has no status for it. */
    suspend fun requestStatus(requestHash: ByteArray): HashiRequestStatus? = requestStatusValue(requestHash)?.status

    /** [requestStatus] with the raw SCALE value, which carries the errors of `Failed` and `Broken`. */
    suspend fun requestStatusValue(requestHash: ByteArray): HashiRequestStatusValue? {
        val key = HashiSoraStorage.requestStatusKey(requestHash)
        val raw = storage(listOf(key))[key] ?: return null
        return HashiRequestStatusValue(HashiRequestStatus.fromScale(raw), raw)
    }

    /** The incoming request hash of an Ethereum deposit, or null before peers imported it. */
    suspend fun incomingRequestHash(ethTxHash: ByteArray): ByteArray? {
        val key = HashiSoraStorage.loadToIncomingRequestHashKey(ethTxHash)
        val value = storage(listOf(key))[key] ?: return null
        return EthHex.decodeFixed(value, 32).takeUnless { bytes -> bytes.all { it == 0.toByte() } }
    }

    /** `bridgeProxy.lockedAssets(EVMLegacy(0), asset)`; 0 when absent. */
    suspend fun lockedAssets(assetId: ByteArray): BigInteger {
        val key = HashiSoraStorage.lockedAssetsKey(assetId)
        return storage(listOf(key))[key]?.let(HashiSoraStorage::decodeU128) ?: BigInteger.ZERO
    }

    /** `Denomination.Denominator` (u128). */
    suspend fun denominator(): BigInteger {
        val key = HashiSoraStorage.denominatorKey
        val value = storage(listOf(key))[key] ?: throw IllegalStateException("HASHI_DENOMINATOR_MISSING")
        return HashiSoraStorage.decodeU128(value).also { require(it.signum() > 0) { "HASHI_DENOMINATOR_INVALID" } }
    }

    /** The bridge's denomination factor of [assetId]: the denominator for TBCD, 1 otherwise. */
    suspend fun denominationFactor(assetId: String): BigInteger =
        if (assetId == HashiBridgeContract.TBCD_ASSET_ID) denominator() else BigInteger.ONE

    /**
     * Applies the signed [extrinsicHex] to the best block's state without importing it, through
     * the runtime API `BlockBuilder_apply_extrinsic` (`state_call`). `system_dryRun` would do the
     * same but public nodes refuse it as unsafe.
     */
    suspend fun dryRun(
        extrinsicHex: String,
        moduleErrorName: (module: Int, error: Int) -> String? = { _, _ -> null },
    ): HashiDryRunResult {
        EthHex.decode(extrinsicHex)
        val result = request("state_call", buildJsonArray { add("BlockBuilder_apply_extrinsic"); add(extrinsicHex) })
        val hex = (result as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("HASHI_DRY_RUN_RESULT_INVALID")
        return HashiDryRun.decode(hex, moduleErrorName)
    }

    companion object {
        const val SORA_MAINNET_RPC = "https://mof2.sora.org"
        private const val MAXIMUM_KEYS_PER_READ = 32
        private const val MAXIMUM_QUEUE_CHECKED = 256
    }
}
