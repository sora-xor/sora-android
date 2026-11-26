package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

/** `eth_call` / `eth_estimateGas` reverted. [reason] is the `Error(string)` text when there is one. */
class EthCallRevertedException(val reason: String?, message: String) : IllegalStateException(message)

/** An endpoint reported a chain other than Ethereum mainnet; it is never used. */
class EthWrongChainException(val endpoint: String, val chainId: String) : IllegalStateException("ETH_WRONG_CHAIN")

class EthLog(val address: ByteArray, val topics: List<ByteArray>, val data: ByteArray)

class EthReceipt(
    val status: Int,
    val blockNumber: BigInteger,
    val gasUsed: BigInteger?,
    val logs: List<EthLog> = emptyList(),
    val transactionHash: ByteArray? = null,
)

/**
 * Ethereum mainnet JSON-RPC for the bridge: public endpoints without API keys, tried in order.
 *
 * Every endpoint must first answer `eth_chainId` with [expectedChainId] (once per process);
 * one that reports another chain is never used again. A transport failure or a malformed answer
 * moves on to the next endpoint. An answer that is an error from the node (a revert, "nonce too
 * low", …) is final: another node would say the same, so it is not asked again.
 * Only `latest`/`pending` state is read: public nodes refuse archive requests.
 */
class EthereumRpcClient(
    private val transport: HashiRpcTransport,
    private val endpoints: List<String> = HashiBridgeContract.ETHEREUM_RPC_ENDPOINTS,
    private val expectedChainId: Long = HashiBridgeContract.ETHEREUM_CHAIN_ID,
) {
    private val admitted = ConcurrentHashMap<String, Boolean>()

    init {
        require(endpoints.isNotEmpty() && endpoints.all { it.startsWith("https://") }) { "ETH_RPC_ENDPOINTS_INVALID" }
    }

    suspend fun request(method: String, params: JsonArray): JsonElement {
        var firstFailure: Throwable? = null
        for (endpoint in endpoints) {
            if (admitted[endpoint] == false) continue
            try {
                if (admitted[endpoint] != true) admit(endpoint)
                return send(endpoint, method, params)
            } catch (error: CancellationException) {
                throw error
            } catch (error: HashiRpcErrorException) {
                if (isFinalAnswer(error)) throw error
                firstFailure = firstFailure?.also { it.addSuppressed(error) } ?: error
            } catch (error: EthWrongChainException) {
                firstFailure = firstFailure ?: error
            } catch (error: Exception) {
                firstFailure = firstFailure?.also { it.addSuppressed(error) } ?: error
            }
        }
        throw HashiRpcUnavailableException(firstFailure)
    }

    /**
     * An error about the request itself (a revert, the nonce, the fee, the balance): every node
     * gives the same one. Rate limits, unknown methods and internal errors are about the node, and
     * the next endpoint is asked instead.
     */
    private fun isFinalAnswer(error: HashiRpcErrorException): Boolean {
        if (error.code == 3) return true
        val message = error.message.lowercase()
        return FINAL_ERROR_MARKERS.any { it in message }
    }

    private suspend fun admit(endpoint: String) {
        val answer = send(endpoint, "eth_chainId", JsonArray(emptyList()))
        val text = (answer as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("ETH_CHAIN_ID_INVALID")
        if (EthHex.parseQuantity(text) != BigInteger.valueOf(expectedChainId)) {
            admitted[endpoint] = false
            throw EthWrongChainException(endpoint, text)
        }
        admitted[endpoint] = true
    }

    private suspend fun send(endpoint: String, method: String, params: JsonArray): JsonElement {
        val (id, body) = HashiJsonRpc.request(method, params)
        return HashiJsonRpc.result(transport.post(endpoint, body), id)
    }

    private fun JsonElement.quantity(): BigInteger {
        val text = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("ETH_QUANTITY_INVALID")
        return EthHex.parseQuantity(text)
    }

    private fun JsonElement.data(): ByteArray {
        val text = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("ETH_DATA_INVALID")
        return EthHex.decode(text)
    }

    private fun callObject(from: ByteArray?, to: ByteArray, data: ByteArray, value: BigInteger?) = buildJsonObject {
        if (from != null) put("from", EthHex.encode(from))
        put("to", EthHex.encode(to))
        put("data", EthHex.encode(data))
        if (value != null && value.signum() > 0) put("value", EthHex.quantity(value))
    }

    private suspend fun <T> revertAware(block: suspend () -> T): T = try {
        block()
    } catch (error: HashiRpcErrorException) {
        val isRevert = error.code == 3 || error.message.contains("revert", ignoreCase = true)
        if (!isRevert) throw error
        val fromData = error.data?.let { runCatching { EthAbi.decodeRevertReason(EthHex.decode(it)) }.getOrNull() }
        val fromMessage = error.message.substringAfter("execution reverted:", "").trim().trim('"').takeIf { it.isNotEmpty() }
        throw EthCallRevertedException(fromData ?: fromMessage, error.message)
    }

    suspend fun chainId(): BigInteger = request("eth_chainId", JsonArray(emptyList())).quantity()

    suspend fun blockNumber(): BigInteger = request("eth_blockNumber", JsonArray(emptyList())).quantity()

    suspend fun balance(address: ByteArray): BigInteger =
        request("eth_getBalance", buildJsonArray { add(EthHex.encode(address)); add("latest") }).quantity()

    suspend fun call(to: ByteArray, data: ByteArray, from: ByteArray? = null): ByteArray = revertAware {
        request("eth_call", buildJsonArray { add(callObject(from, to, data, null)); add("latest") }).data()
    }

    suspend fun pendingNonce(address: ByteArray): BigInteger =
        request("eth_getTransactionCount", buildJsonArray { add(EthHex.encode(address)); add("pending") }).quantity()

    suspend fun estimateGas(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger = BigInteger.ZERO): BigInteger =
        revertAware {
            request("eth_estimateGas", buildJsonArray { add(callObject(from, to, data, value)) }).quantity()
        }

    suspend fun maxPriorityFeePerGas(): BigInteger = request("eth_maxPriorityFeePerGas", JsonArray(emptyList())).quantity()

    suspend fun latestBaseFee(): BigInteger {
        val block = request("eth_getBlockByNumber", buildJsonArray { add("latest"); add(false) }) as? JsonObject
            ?: throw IllegalArgumentException("ETH_BLOCK_INVALID")
        return (block["baseFeePerGas"] ?: throw IllegalArgumentException("ETH_BASE_FEE_MISSING")).quantity()
    }

    /**
     * Broadcasts [raw] and returns its hash. A node that already has this exact transaction
     * answers "already known"; that is the same transaction, so its hash ([expectedHash]) is
     * returned. Any other error is final.
     */
    suspend fun sendRawTransaction(raw: ByteArray, expectedHash: ByteArray): ByteArray = try {
        val returned = request("eth_sendRawTransaction", buildJsonArray { add(EthHex.encode(raw)) }).data()
        check(returned.contentEquals(expectedHash)) { "ETH_TX_HASH_MISMATCH" }
        returned
    } catch (error: HashiRpcErrorException) {
        if (error.message.contains("already known", ignoreCase = true)) expectedHash else throw error
    }

    suspend fun receipt(hash: ByteArray): EthReceipt? {
        val answer = request("eth_getTransactionReceipt", buildJsonArray { add(EthHex.encode(hash)) })
        if (answer is JsonNull) return null
        val receipt = answer as? JsonObject ?: throw IllegalArgumentException("ETH_RECEIPT_INVALID")
        val status = (receipt["status"] ?: throw IllegalArgumentException("ETH_RECEIPT_INVALID")).quantity()
        require(status == BigInteger.ZERO || status == BigInteger.ONE) { "ETH_RECEIPT_INVALID" }
        val block = receipt["blockNumber"]?.takeUnless { it is JsonNull }?.quantity() ?: return null
        val transactionHash = receipt["transactionHash"]?.takeUnless { it is JsonNull }?.data()
        require(transactionHash == null || transactionHash.contentEquals(hash)) { "ETH_RECEIPT_HASH_MISMATCH" }
        // A receipt without its log list is unreadable, never "no events": a mined deposit read
        // that way would look unproven.
        val logs = (receipt["logs"] as? JsonArray ?: throw IllegalArgumentException("ETH_RECEIPT_LOGS_MISSING")).map { entry ->
            val log = entry as? JsonObject ?: throw IllegalArgumentException("ETH_RECEIPT_INVALID")
            EthLog(
                address = EthHex.decodeFixed(
                    (log["address"] as? JsonPrimitive)?.content ?: throw IllegalArgumentException("ETH_RECEIPT_INVALID"),
                    EthAddress.SIZE,
                ),
                topics = (log["topics"] as? JsonArray ?: JsonArray(emptyList())).map { it.data() },
                data = log["data"]?.data() ?: ByteArray(0),
            )
        }
        return EthReceipt(status.toInt(), block, receipt["gasUsed"]?.takeUnless { it is JsonNull }?.quantity(), logs, transactionHash)
    }

    /** The nonce of the last mined transaction + 1 (`latest`, not `pending`). */
    suspend fun latestNonce(address: ByteArray): BigInteger =
        request("eth_getTransactionCount", buildJsonArray { add(EthHex.encode(address)); add("latest") }).quantity()

    /** True when [address] has no contract code (`eth_getCode` = `0x`). */
    suspend fun hasNoCode(address: ByteArray): Boolean =
        request("eth_getCode", buildJsonArray { add(EthHex.encode(address)); add("latest") }).data().isEmpty()

    // --- Contract reads -------------------------------------------------------------------------

    suspend fun erc20Balance(token: ByteArray, owner: ByteArray): BigInteger =
        EthAbi.decodeUint(call(token, HashiBridgeCalls.balanceOf(owner)))

    suspend fun erc20Allowance(token: ByteArray, owner: ByteArray, spender: ByteArray): BigInteger =
        EthAbi.decodeUint(call(token, HashiBridgeCalls.allowance(owner, spender)))

    suspend fun erc20Decimals(token: ByteArray): Int = EthAbi.decodeUint8(call(token, HashiBridgeCalls.decimals()))

    suspend fun isUsed(contract: ByteArray, txHash: ByteArray): Boolean =
        EthAbi.decodeBool(call(contract, HashiBridgeCalls.used(txHash)))

    /** The ERC-20 of a `Thischain` asset, or null when the bridge has none (zero address). */
    /** `acceptedEthTokens(token)`: the bridge takes deposits of this Ethereum-native token. */
    suspend fun isAcceptedEthToken(token: ByteArray): Boolean =
        EthAbi.decodeBool(call(HashiBridgeContract.bridge, HashiBridgeCalls.acceptedEthTokens(token)))

    /** `_sidechainTokensByAddress(token)`: the SORA asset id of a bridge-minted ERC-20 (zero if none). */
    suspend fun sidechainAssetOfToken(token: ByteArray): ByteArray {
        val result = call(HashiBridgeContract.bridge, HashiBridgeCalls.sidechainTokensByAddress(token))
        require(result.size == 32) { "ABI_RESULT_LENGTH_INVALID" }
        return result
    }

    suspend fun sidechainToken(assetId: ByteArray): ByteArray? {
        val token = EthAbi.decodeAddress(call(HashiBridgeContract.bridge, HashiBridgeCalls.sidechainTokens(assetId)))
        return token.takeUnless { it.contentEquals(EthAddress.ZERO) }
    }

    private companion object {
        val FINAL_ERROR_MARKERS = listOf(
            "revert",
            "nonce",
            "already known",
            "insufficient funds",
            "underpriced",
            "intrinsic gas",
            "gas limit",
            "exceeds block gas",
            "fee cap",
            "invalid sender",
        )
    }
}
