package jp.co.soramitsu.common.hashi

import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthHex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Ethereum and SORA JSON-RPC clients against scripted endpoints. */
class HashiRpcClientsTest {

    /** Answers each request with [answer] (method, params) → result | error | throw. */
    private class Scripted(
        val answer: (endpoint: String, method: String, params: JsonArray) -> Any?,
    ) : HashiRpcTransport {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun post(endpoint: String, body: String): String {
            val request = Json.parseToJsonElement(body).jsonObject
            val method = request.getValue("method").jsonPrimitive.content
            val id = request.getValue("id").jsonPrimitive.long
            calls += endpoint to method
            return when (val result = answer(endpoint, method, request.getValue("params").jsonArray)) {
                is Throwable -> throw result
                is RpcError -> buildJsonObject {
                    put("jsonrpc", "2.0"); put("id", id)
                    put("error", buildJsonObject {
                        put("code", result.code); put("message", result.message)
                        if (result.data != null) put("data", result.data)
                    })
                }.toString()
                else -> buildJsonObject {
                    put("jsonrpc", "2.0"); put("id", id)
                    put("result", result as? JsonElement ?: (result as? String)?.let(::JsonPrimitive) ?: JsonNull)
                }.toString()
            }
        }
    }

    private class RpcError(val code: Int, val message: String, val data: String? = null)

    private val endpoints = listOf("https://a.example", "https://b.example")

    @Test
    fun `an endpoint on another chain is never used and the next one answers`() = runBlocking {
        val transport = Scripted { endpoint, method, _ ->
            when {
                method == "eth_chainId" && endpoint.contains("a.") -> "0x5"
                method == "eth_chainId" -> "0x1"
                method == "eth_blockNumber" -> "0x10"
                else -> error(method)
            }
        }
        val client = EthereumRpcClient(transport, endpoints)
        assertEquals(BigInteger.valueOf(16), client.blockNumber())
        assertEquals(BigInteger.valueOf(16), client.blockNumber())
        // a.example was asked for its chain once and then left out.
        assertEquals(1, transport.calls.count { it.first.contains("a.") })
        assertEquals(listOf("eth_chainId", "eth_blockNumber", "eth_blockNumber"), transport.calls.filter { it.first.contains("b.") }.map { it.second })
    }

    @Test
    fun `transport failures and rate limits fail over, every endpoint failing is unavailable`() = runBlocking {
        val transport = Scripted { endpoint, method, _ ->
            when {
                endpoint.contains("a.") && method == "eth_chainId" -> IOException("down")
                method == "eth_chainId" -> "0x1"
                endpoint.contains("b.") -> RpcError(-32005, "rate limit exceeded")
                else -> error("unreachable")
            }
        }
        val client = EthereumRpcClient(transport, endpoints)
        assertTrue(runCatching { client.blockNumber() }.exceptionOrNull() is HashiRpcUnavailableException)
    }

    @Test
    fun `a revert is final and its reason is decoded`() = runBlocking {
        val reason = EthAbi.selector("Error(string)") + EthAbi.uintWord(BigInteger.valueOf(32)) +
            EthAbi.uintWord(BigInteger.valueOf(19)) + "txHash already used".toByteArray().copyOf(32)
        val transport = Scripted { _, method, _ ->
            when (method) {
                "eth_chainId" -> "0x1"
                "eth_estimateGas" -> RpcError(3, "execution reverted: txHash already used", EthHex.encode(reason))
                "eth_call" -> RpcError(-32000, "execution reverted")
                else -> error(method)
            }
        }
        val client = EthereumRpcClient(transport, endpoints)
        val estimate = runCatching { client.estimateGas(ByteArray(20), ByteArray(20), ByteArray(4)) }.exceptionOrNull()
        assertTrue(estimate is EthCallRevertedException)
        assertEquals("txHash already used", (estimate as EthCallRevertedException).reason)
        val call = runCatching { client.call(ByteArray(20), ByteArray(4)) }.exceptionOrNull() as EthCallRevertedException
        assertNull(call.reason)
        // Only the first endpoint was asked: a revert is the chain's answer, not the node's.
        assertTrue(transport.calls.none { it.first.contains("b.") })
    }

    @Test
    fun `a transaction the node already has keeps its own hash, another hash is refused`() = runBlocking {
        val hash = ByteArray(32) { 9 }
        var reply: Any = RpcError(-32000, "already known")
        val client = EthereumRpcClient(Scripted { _, method, _ -> if (method == "eth_chainId") "0x1" else reply }, endpoints)
        assertArrayEquals(hash, client.sendRawTransaction(byteArrayOf(2), hash))
        reply = EthHex.encode(ByteArray(32) { 8 })
        assertTrue(runCatching { client.sendRawTransaction(byteArrayOf(2), hash) }.isFailure)
        reply = RpcError(-32000, "nonce too low")
        assertTrue(runCatching { client.sendRawTransaction(byteArrayOf(2), hash) }.exceptionOrNull() is HashiRpcErrorException)
    }

    @Test
    fun `receipts, base fee and contract reads parse`() = runBlocking {
        val client = EthereumRpcClient(
            Scripted { _, method, params ->
                when (method) {
                    "eth_chainId" -> "0x1"
                    "eth_getTransactionReceipt" -> when {
                        params[0].jsonPrimitive.content.endsWith("01") -> JsonNull
                        // A receipt without its log list.
                        params[0].jsonPrimitive.content.endsWith("02") ->
                            buildJsonObject { put("status", "0x1"); put("blockNumber", "0x10"); put("gasUsed", "0x5208") }
                        else -> buildJsonObject {
                            put("status", "0x1"); put("blockNumber", "0x10"); put("gasUsed", "0x5208"); put("logs", JsonArray(emptyList()))
                        }
                    }
                    "eth_getBlockByNumber" -> buildJsonObject { put("baseFeePerGas", "0x3b9aca00"); put("number", "0x1") }
                    "eth_call" -> EthHex.encode(EthAbi.uintWord(BigInteger.ONE))
                    else -> error(method)
                }
            },
            endpoints,
        )
        assertNull(client.receipt(ByteArray(31) + byteArrayOf(1)))
        val receipt = checkNotNull(client.receipt(ByteArray(32)))
        assertEquals(1, receipt.status)
        assertEquals(BigInteger.valueOf(16), receipt.blockNumber)
        // Missing logs are an unreadable receipt (read again later), not an empty event list.
        assertTrue(runCatching { client.receipt(ByteArray(31) + byteArrayOf(2)) }.isFailure)
        assertEquals(BigInteger.valueOf(1_000_000_000), client.latestBaseFee())
        assertTrue(client.isUsed(HashiBridgeContract.bridge, ByteArray(32)))
    }

    // --- SORA ---------------------------------------------------------------------------------

    private fun soraClient(values: Map<String, String?>, fail: Boolean = false) = HashiSoraRpcClient(
        Scripted { _, method, params ->
            if (fail) return@Scripted IOException("down")
            check(method == "state_queryStorageAt")
            val keys = params[0].jsonArray.map { it.jsonPrimitive.content }
            buildJsonArray {
                add(buildJsonObject {
                    put("block", "0x" + "ab".repeat(32))
                    put("changes", JsonArray(keys.filter { values.containsKey(it) }.map { key ->
                        JsonArray(listOf(JsonPrimitive(key), values[key]?.let(::JsonPrimitive) ?: JsonNull))
                    }))
                })
            }
        },
        "https://sora.example",
    )

    @Test
    fun `request statuses, incoming hashes and the denominator decode`() = runBlocking {
        val hash = ByteArray(32) { 1 }
        val client = soraClient(
            mapOf(
                HashiSoraStorage.requestStatusKey(hash) to "0x04",
                HashiSoraStorage.loadToIncomingRequestHashKey(hash) to "0x" + "22".repeat(32),
                // u128 little endian.
                HashiSoraStorage.denominatorKey to EthHex.encode(BigInteger.TEN.pow(38).toByteArray().reversedArray().copyOf(16)),
            )
        )
        assertEquals(HashiRequestStatus.DONE, client.requestStatus(hash))
        assertNull(client.requestStatus(ByteArray(32) { 2 }))
        assertArrayEquals(ByteArray(32) { 0x22 }, client.incomingRequestHash(hash))
        assertNull(client.incomingRequestHash(ByteArray(32) { 3 }))
        assertEquals(BigInteger.TEN.pow(38), client.denominator())
        assertEquals(BigInteger.TEN.pow(38), client.denominationFactor(HashiBridgeContract.TBCD_ASSET_ID))
        assertEquals(BigInteger.ONE, client.denominationFactor(HashiBridgeContract.XOR_ASSET_ID))
    }

    @Test
    fun `a storage answer with a key that was not asked is refused`() = runBlocking {
        val client = HashiSoraRpcClient(
            Scripted { _, _, _ ->
                buildJsonArray {
                    add(buildJsonObject {
                        put("block", "0x00")
                        put("changes", JsonArray(listOf(JsonArray(listOf(JsonPrimitive("0x1234"), JsonPrimitive("0x01"))))))
                    })
                }
            },
            "https://sora.example",
        )
        assertTrue(runCatching { client.storage(listOf(HashiSoraStorage.bridgeStatusKey())) }.isFailure)
        // A read that fails is never an available bridge.
        val availability = client.availability({ "{}" }, java.time.Instant.now())
        assertEquals(setOf(HashiBlock.READ_FAILED), availability.blocks)
        assertFalse(availability.newTransfersAllowed)
    }
}
