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

/** What the endpoints know of one transaction, asked of every one of them. */
sealed class EthTransactionLookup {
    /** In a block, with its receipt status (1 success, 0 reverted). */
    data class Mined(val status: Int) : EthTransactionLookup()

    /** Known to an endpoint, not mined yet. */
    object Pending : EthTransactionLookup()

    /** No endpoint that answered (at least two) has it. */
    object Unknown : EthTransactionLookup()
}

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

    suspend fun request(method: String, params: JsonArray): JsonElement = request(method, params, broadcast = false)

    /**
     * [broadcast]: the call hands a transaction to the network. Once an earlier endpoint failed
     * without an answer, it may have taken the transaction; a refusal from the next endpoint then
     * says nothing about it (the bytes may be pending or mined already), so it is reported as
     * unknown ([HashiRpcUnavailableException]), never as a refusal.
     */
    private suspend fun request(method: String, params: JsonArray, broadcast: Boolean): JsonElement {
        var firstFailure: Throwable? = null
        // An earlier endpoint received the transaction and gave no final answer: it may have it.
        var mayHaveIt = false
        for (endpoint in endpoints) {
            if (admitted[endpoint] == false) continue
            try {
                if (admitted[endpoint] != true) admit(endpoint)
            } catch (error: CancellationException) {
                throw error
            } catch (error: EthWrongChainException) {
                firstFailure = firstFailure ?: error
                continue
            } catch (error: Exception) {
                firstFailure = firstFailure?.also { it.addSuppressed(error) } ?: error
                continue
            }
            try {
                return send(endpoint, method, params)
            } catch (error: CancellationException) {
                throw error
            } catch (error: HashiRpcErrorException) {
                if (isFinalAnswer(error)) {
                    val positive = error.message.contains("already known", ignoreCase = true)
                    if (broadcast && mayHaveIt && !positive) throw HashiRpcUnavailableException(error)
                    throw error
                }
                // An error answer (rate limit, unknown method) means this endpoint did not take it.
                firstFailure = firstFailure?.also { it.addSuppressed(error) } ?: error
            } catch (error: Exception) {
                mayHaveIt = mayHaveIt || broadcast
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
        val returned = request("eth_sendRawTransaction", buildJsonArray { add(EthHex.encode(raw)) }, broadcast = true).data()
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
                // A log without its topics or data is unreadable: read the receipt again.
                topics = (log["topics"] as? JsonArray ?: throw IllegalArgumentException("ETH_RECEIPT_INVALID")).map { topic ->
                    topic.data().also { require(it.size == 32) { "ETH_RECEIPT_INVALID" } }
                },
                data = (log["data"] ?: throw IllegalArgumentException("ETH_RECEIPT_INVALID")).data(),
            )
        }
        return EthReceipt(status.toInt(), block, receipt["gasUsed"]?.takeUnless { it is JsonNull }?.quantity(), logs, transactionHash)
    }

    /**
     * What every reachable endpoint knows of the transaction [hash], the most live answer winning:
     * a receipt with status 1 anywhere; else KNOWN when one has it pending (also over another
     * endpoint's status-0 receipt, which a reorganisation can leave behind on a lagging node); else
     * that status-0 receipt; else KNOWN when one has it mined but cannot show its receipt (status
     * unknown, so it counts as live); else UNKNOWN. Anything but a status-1 receipt needs at least
     * [MINIMUM_UNANIMOUS] endpoints to have answered in full, else this throws: a lagging or missing
     * endpoint must never make a mined or pending transaction look absent or reverted.
     */
    suspend fun lookupEverywhere(hash: ByteArray): EthTransactionLookup {
        var answers = 0
        var pending = false
        var minedWithoutReceipt = false
        var reverted: EthTransactionLookup.Mined? = null
        var failure: Throwable? = null
        for (endpoint in endpoints) {
            if (admitted[endpoint] == false) continue
            try {
                if (admitted[endpoint] != true) admit(endpoint)
                val receipt = send(endpoint, "eth_getTransactionReceipt", buildJsonArray { add(EthHex.encode(hash)) })
                if (receipt is JsonObject && receipt["blockNumber"]?.takeUnless { it is JsonNull } != null) {
                    val status = (receipt["status"] ?: throw IllegalArgumentException("ETH_RECEIPT_INVALID")).quantity().toInt()
                    if (status == 1) return EthTransactionLookup.Mined(1)
                    reverted = EthTransactionLookup.Mined(status)
                } else {
                    val transaction = send(endpoint, "eth_getTransactionByHash", buildJsonArray { add(EthHex.encode(hash)) })
                    if (transaction is JsonObject) {
                        if (transaction["blockNumber"]?.takeUnless { it is JsonNull } != null) minedWithoutReceipt = true else pending = true
                    }
                }
                answers += 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = failure?.also { it.addSuppressed(error) } ?: error
            }
        }
        if (answers < minOf(MINIMUM_UNANIMOUS, endpoints.size)) throw HashiRpcUnavailableException(failure)
        if (pending) return EthTransactionLookup.Pending
        reverted?.let { return it }
        if (minedWithoutReceipt) return EthTransactionLookup.Pending
        return EthTransactionLookup.Unknown
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
        const val MINIMUM_UNANIMOUS = 2
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
