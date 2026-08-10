package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.math.BigInteger
import java.util.ArrayDeque
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Sora2BoundedRuntimeRpcClientTest {

    private val gson = Gson()
    private val genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH
    private val finalizedHash = "0x${"ab".repeat(32)}"
    private val accountAddress = requireNotNull(
        Sora2AddressCodec().toSoraAddressOrNull(
            ByteArray(32) { index -> (index + 1).toByte() }
        )
    )

    @Test
    fun `finalized identity uses canonical checkpoint requests and method-specific limits`() =
        runTest {
            val transport = ScriptedTransport(
                result(1, gson.toJson(genesisHash)),
                result(2, gson.toJson(finalizedHash)),
                result(3, runtimeVersionResult()),
                result(4, "{\"number\":\"0x2a\"}"),
            )
            val client = Sora2BoundedRuntimeRpcClient(transport, gson)

            val identity = client.getFinalizedIdentity()

            assertEquals(genesisHash, identity.genesisHash)
            assertEquals(finalizedHash, identity.finalizedHash)
            assertEquals(130, identity.runtimeVersion.specVersion)
            assertEquals(130, identity.runtimeVersion.transactionVersion)
            assertEquals(42L, identity.finalizedBlockNumber)
            assertEquals(
                listOf(
                    Sora2BoundedRuntimeRpcClient.MAXIMUM_HASH_RESPONSE_BYTES,
                    Sora2BoundedRuntimeRpcClient.MAXIMUM_HASH_RESPONSE_BYTES,
                    Sora2BoundedRuntimeRpcClient.MAXIMUM_RUNTIME_VERSION_RESPONSE_BYTES,
                    Sora2BoundedRuntimeRpcClient.MAXIMUM_HEADER_RESPONSE_BYTES,
                ),
                transport.limits,
            )
            assertRequest(transport.requests[0], 1, "chain_getBlockHash", "[0]")
            assertRequest(transport.requests[1], 2, "chain_getFinalizedHead", "[]")
            assertRequest(
                transport.requests[2],
                3,
                "state_getRuntimeVersion",
                gson.toJson(listOf(finalizedHash)),
            )
            assertRequest(
                transport.requests[3],
                4,
                "chain_getHeader",
                gson.toJson(listOf(finalizedHash)),
            )
            assertEquals("https://ws.mof.sora.org", Sora2RuntimeContract.RUNTIME_RPC_ENDPOINT)
        }

    @Test
    fun `canonical block retains only exact requested extrinsic hashes`() = runTest {
        val encoded = "0x00"
        val hash = encoded.extrinsicHash()
        val blockHash = "0x${"cd".repeat(32)}"
        val transport = ScriptedTransport(
            result(1, gson.toJson(blockHash)),
            result(
                2,
                "{\"block\":{\"header\":{\"number\":\"0x7\"}," +
                    "\"extrinsics\":[\"0x01\",\"$encoded\"]}," +
                    "\"justifications\":null}",
            ),
        )
        val client = Sora2BoundedRuntimeRpcClient(transport, gson)

        val block = client.getCanonicalBlock(
            blockNumber = 7L,
            finalizedHeight = 9L,
            recoveryHashes = setOf(hash),
        )

        assertEquals(blockHash, block.blockHash)
        assertEquals(7L, block.blockNumber)
        assertEquals(mapOf(hash to 1), block.matchingExtrinsicIndices)
        assertRequest(transport.requests[0], 1, "chain_getBlockHash", "[7]")
        assertRequest(
            transport.requests[1],
            2,
            "chain_getBlock",
            gson.toJson(listOf(blockHash)),
        )
    }

    @Test
    fun `canonical block rejects duplicate inclusion for one recovery hash`() = runTest {
        val encoded = "0x00"
        val blockHash = "0x${"cd".repeat(32)}"
        val client = Sora2BoundedRuntimeRpcClient(
            ScriptedTransport(
                result(1, gson.toJson(blockHash)),
                result(
                    2,
                    "{\"block\":{\"header\":{\"number\":\"0x7\"}," +
                        "\"extrinsics\":[\"$encoded\",\"$encoded\"]}}",
                ),
            ),
            gson,
        )

        val error = captureFailure {
            client.getCanonicalBlock(7L, 9L, setOf(encoded.extrinsicHash()))
        }

        assertEquals("SORA2_STATUS_DUPLICATE_EXTRINSIC", error.message)
    }

    @Test
    fun `storage status read is pinned to exact block and rejects malformed payload`() = runTest {
        val storageKey = "0x${"11".repeat(32)}"
        val blockHash = "0x${"cd".repeat(32)}"
        val transport = ScriptedTransport(result(1, gson.toJson("0x0102")))
        val client = Sora2BoundedRuntimeRpcClient(transport, gson)

        assertEquals("0x0102", client.getStorageAt(storageKey, blockHash))
        assertRequest(
            transport.requests.single(),
            1,
            "state_getStorage",
            gson.toJson(listOf(storageKey, blockHash)),
        )

        val malformed = Sora2BoundedRuntimeRpcClient(
            ScriptedTransport(result(1, gson.toJson("0x0"))),
            gson,
        )
        assertEquals(
            "SORA2_STATUS_STORAGE_INVALID",
            captureFailure { malformed.getStorageAt(storageKey, blockHash) }.message,
        )
    }

    @Test
    fun `signed submission is one bounded exact hash checked request`() = runTest {
        val encoded = "0x00"
        val expectedHash = encoded.extrinsicHash()
        val transport = ScriptedTransport(result(1, gson.toJson(expectedHash)))
        val client = Sora2BoundedRuntimeRpcClient(transport, gson)

        assertEquals(expectedHash, client.submitExtrinsicOnce(encoded))
        assertEquals(
            listOf(Sora2BoundedRuntimeRpcClient.MAXIMUM_HASH_RESPONSE_BYTES),
            transport.limits,
        )
        assertRequest(
            transport.requests.single(),
            1,
            "author_submitExtrinsic",
            gson.toJson(listOf(encoded)),
        )
    }

    @Test
    fun `signed submission rejects returned hash mismatch without retry`() = runTest {
        val transport = ScriptedTransport(
            result(1, gson.toJson("0x${"ef".repeat(32)}")),
        )
        val client = Sora2BoundedRuntimeRpcClient(transport, gson)

        val error = captureFailure { client.submitExtrinsicOnce("0x00") }

        assertEquals("SORA2_SUBMISSION_HASH_MISMATCH", error.message)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `metadata request is pinned to admitted finalized hash and bounded before decode`() =
        runTest {
            val transport = ScriptedTransport(result(1, gson.toJson("0x0102")))
            val client = Sora2BoundedRuntimeRpcClient(transport, gson)

            assertEquals("0x0102", client.getMetadataAtFinalized(finalizedHash))
            assertEquals(
                listOf(Sora2BoundedRuntimeRpcClient.MAXIMUM_METADATA_RESPONSE_BYTES),
                transport.limits,
            )
            assertRequest(
                transport.requests.single(),
                1,
                "state_getMetadata",
                gson.toJson(listOf(finalizedHash)),
            )
        }

    @Test
    fun `metadata never sends a malformed finalized checkpoint`() = runTest {
        val transport = ScriptedTransport(result(1, gson.toJson("0x0102")))
        val client = Sora2BoundedRuntimeRpcClient(transport, gson)

        val error = captureFailure { client.getMetadataAtFinalized("latest") }

        assertEquals("SORA2_RUNTIME_RPC_FINALIZED_INVALID", error.message)
        assertEquals(emptyList<String>(), transport.requests)
    }

    @Test
    fun `account nonce preserves the exact canonical u32 wire integer`() = runTest {
        listOf("0", "2147483648", "4294967295").forEach { rawNonce ->
            val transport = ScriptedTransport(result(1, rawNonce))
            val client = Sora2BoundedRuntimeRpcClient(transport, gson)

            assertEquals(
                BigInteger(rawNonce),
                client.getAccountNextIndex(accountAddress),
            )
            assertEquals(
                listOf(
                    Sora2BoundedRuntimeRpcClient
                        .MAXIMUM_ACCOUNT_NONCE_RESPONSE_BYTES
                ),
                transport.limits,
            )
            assertRequest(
                transport.requests.single(),
                1,
                "system_accountNextIndex",
                gson.toJson(listOf(accountAddress)),
            )
        }
    }

    @Test
    fun `account nonce rejects normalized signed quoted and overflowing values`() = runTest {
        val invalidResponses = listOf(
            result(1, "1.0") to "SORA2_RUNTIME_RPC_NONCE_INVALID",
            result(1, "1e3") to "SORA2_RUNTIME_RPC_NONCE_INVALID",
            result(1, "-1") to "SORA2_RUNTIME_RPC_NONCE_INVALID",
            result(1, "01") to "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            result(1, "+1") to "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            result(1, gson.toJson("1")) to
                "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            result(1, "true") to "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            result(1, "4294967296") to "SORA2_RUNTIME_RPC_NONCE_INVALID",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":1,\"result\":2}" to
                "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
        )

        invalidResponses.forEach { (response, expectedCode) ->
            val client = Sora2BoundedRuntimeRpcClient(
                ScriptedTransport(response),
                gson,
            )

            val error = captureFailure {
                client.getAccountNextIndex(accountAddress)
            }

            assertEquals(expectedCode, error.message)
        }
    }

    @Test
    fun `account nonce rejects a noncanonical SORA2 account before transport`() = runTest {
        val transport = ScriptedTransport(result(1, "0"))
        val client = Sora2BoundedRuntimeRpcClient(transport, gson)
        val otherNetworkAddress =
            ByteArray(32) { index -> (index + 1).toByte() }
                .toAddress(42.toShort())

        listOf("not-a-sora2-account", otherNetworkAddress).forEach { address ->
            val error = captureFailure {
                client.getAccountNextIndex(address)
            }

            assertEquals("SORA2_RUNTIME_RPC_ACCOUNT_INVALID", error.message)
        }
        assertEquals(emptyList<String>(), transport.requests)
    }

    @Test
    fun `strict envelope rejects version id extra fields null and result error ambiguity`() =
        runTest {
            val cases = listOf(
                "{\"jsonrpc\":\"1.0\",\"id\":1,\"result\":\"0x0102\"}" to
                    "SORA2_RUNTIME_RPC_VERSION_INVALID",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"0x0102\"}" to
                    "SORA2_RUNTIME_RPC_ID_MISMATCH",
                "{\"jsonrpc\":\"2.0\",\"id\":1.0,\"result\":\"0x0102\"}" to
                    "SORA2_RUNTIME_RPC_ID_MISMATCH",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x0102\",\"extra\":true}" to
                    "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":1,\"result\":\"0x0102\"}" to
                    "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}" to
                    "SORA2_RUNTIME_RPC_RESULT_MISSING",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x0102\"," +
                    "\"error\":{\"code\":-1,\"message\":\"ambiguous\"}}" to
                    "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x0102\"}{}" to
                    "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
                "{jsonrpc:\"2.0\",id:1,result:\"0x0102\"}" to
                    "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            )

            cases.forEach { (response, expectedCode) ->
                val client = Sora2BoundedRuntimeRpcClient(
                    ScriptedTransport(response),
                    gson,
                )
                val error = captureFailure {
                    client.getMetadataAtFinalized(finalizedHash)
                }
                assertEquals(expectedCode, error.message)
            }
        }

    @Test
    fun `strict remote error is safe and never publishes remote message or data`() = runTest {
        val transport = ScriptedTransport(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{" +
                "\"code\":-32603,\"message\":\"secret remote detail\"," +
                "\"data\":{\"untrusted\":true}}}",
        )
        val client = Sora2BoundedRuntimeRpcClient(transport, gson)

        val error = captureFailure { client.getMetadataAtFinalized(finalizedHash) }

        assertEquals("SORA2_RUNTIME_RPC_REMOTE_ERROR", error.message)
    }

    @Test
    fun `runtime version requires canonical integer spec and transaction fields`() = runTest {
        val invalidVersions = listOf(
            "{\"specVersion\":130.0,\"transactionVersion\":130}",
            "{\"specVersion\":130,\"transactionVersion\":1e2}",
            "{\"specVersion\":0,\"transactionVersion\":130}",
            "{\"specVersion\":130}",
            "{\"specVersion\":2147483648,\"transactionVersion\":130}",
        )

        invalidVersions.forEach { invalidVersion ->
            val client = Sora2BoundedRuntimeRpcClient(
                ScriptedTransport(
                    result(1, gson.toJson(genesisHash)),
                    result(2, gson.toJson(finalizedHash)),
                    result(3, invalidVersion),
                ),
                gson,
            )
            val error = captureFailure { client.getFinalizedIdentity() }
            assertEquals("SORA2_RUNTIME_RPC_VERSION_RESULT_INVALID", error.message)
        }
    }

    @Test
    fun `runtime version rejects duplicate security fields before Gson materialization`() =
        runTest {
            val client = Sora2BoundedRuntimeRpcClient(
                ScriptedTransport(
                    result(1, gson.toJson(genesisHash)),
                    result(2, gson.toJson(finalizedHash)),
                    result(
                        3,
                        "{\"specVersion\":130,\"specVersion\":131," +
                            "\"transactionVersion\":130}",
                    ),
                ),
                gson,
            )

            val error = captureFailure { client.getFinalizedIdentity() }

            assertEquals("SORA2_RUNTIME_RPC_RESPONSE_INVALID", error.message)
        }

    @Test
    fun `runtime version structured depth and token work are independently bounded`() = runTest {
        val deeplyNested = "[".repeat(33) + "0" + "]".repeat(33)
        val tooManyTokens = List(4_097) { "0" }.joinToString(",")
        listOf(
            "{\"specVersion\":130,\"transactionVersion\":130," +
                "\"apis\":$deeplyNested}",
            "{\"specVersion\":130,\"transactionVersion\":130," +
                "\"apis\":[$tooManyTokens]}",
        ).forEach { runtimeResult ->
            val client = Sora2BoundedRuntimeRpcClient(
                ScriptedTransport(
                    result(1, gson.toJson(genesisHash)),
                    result(2, gson.toJson(finalizedHash)),
                    result(3, runtimeResult),
                ),
                gson,
            )

            val error = captureFailure { client.getFinalizedIdentity() }

            assertEquals("SORA2_RUNTIME_RPC_RESPONSE_INVALID", error.message)
        }
    }

    @Test
    fun `identity rejects non-string and noncanonical block hashes`() = runTest {
        val cases = listOf(
            result(1, "42") to "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            result(1, gson.toJson("0x1234")) to "SORA2_RUNTIME_RPC_GENESIS_INVALID",
            result(1, gson.toJson("0X${"ab".repeat(32)}")) to
                "SORA2_RUNTIME_RPC_GENESIS_INVALID",
        )

        cases.forEach { (response, expectedCode) ->
            val client = Sora2BoundedRuntimeRpcClient(
                ScriptedTransport(response),
                gson,
            )
            val error = captureFailure { client.getFinalizedIdentity() }
            assertEquals(expectedCode, error.message)
        }
    }

    @Test
    fun `malformed error envelope and non-string metadata fail closed`() = runTest {
        val responses = listOf(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{" +
                "\"code\":-32603.0,\"message\":\"bad code\"}}" to
                "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{" +
                "\"code\":-32603,\"message\":\"\"}}" to
                "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
            result(1, "[]") to "SORA2_RUNTIME_RPC_RESPONSE_INVALID",
        )

        responses.forEach { (response, expectedCode) ->
            val client = Sora2BoundedRuntimeRpcClient(
                ScriptedTransport(response),
                gson,
            )
            val error = captureFailure { client.getMetadataAtFinalized(finalizedHash) }
            assertEquals(expectedCode, error.message)
        }
    }

    @Test
    fun `malformed metadata hex is rejected before publication`() = runTest {
        val client = Sora2BoundedRuntimeRpcClient(
            ScriptedTransport(result(1, gson.toJson("not-metadata"))),
            gson,
        )

        val error = captureFailure { client.getMetadataAtFinalized(finalizedHash) }

        assertEquals("SORA2_RUNTIME_METADATA_INVALID", error.message)
    }

    @Test
    fun `mutation transport accepts only exact reviewed websocket counterpart`() {
        assertEquals(
            Sora2RuntimeContract.RUNTIME_WS_ENDPOINT,
            requireReviewedSora2WebSocketEndpoint("wss://ws.mof.sora.org"),
        )
        assertEquals(
            Sora2RuntimeContract.RUNTIME_WS_ENDPOINT,
            requireReviewedSora2WebSocketEndpoint("wss://ws.mof.sora.org/"),
        )
        listOf(
            null,
            "ws://ws.mof.sora.org",
            "wss://ws.mof2.sora.org",
            "wss://ws.mof.sora.org:443",
            "wss://user@ws.mof.sora.org",
            "wss://ws.mof.sora.org/?selected=other",
            "WSS://WS.MOF.SORA.ORG",
        ).forEach { endpoint ->
            assertEquals(
                "SORA2_MUTATION_TRANSPORT_UNREVIEWED",
                assertThrows(Sora2RuntimeIdentityException::class.java) {
                    requireReviewedSora2WebSocketEndpoint(endpoint)
                }.message,
            )
        }
    }

    private fun assertRequest(
        raw: String,
        id: Int,
        method: String,
        paramsJson: String,
    ) {
        val request = gson.fromJson(raw, JsonObject::class.java)
        assertEquals(setOf("jsonrpc", "id", "method", "params"), request.keySet())
        assertEquals("2.0", request.get("jsonrpc").asString)
        assertEquals(id, request.get("id").asInt)
        assertEquals(method, request.get("method").asString)
        assertEquals(paramsJson, gson.toJson(request.get("params")))
    }

    private fun runtimeVersionResult(): String =
        "{\"specName\":\"sora\",\"implName\":\"sora\"," +
            "\"authoringVersion\":1,\"specVersion\":130,\"implVersion\":0," +
            "\"apis\":[],\"transactionVersion\":130,\"stateVersion\":1}"

    private fun result(id: Int, resultJson: String): String =
        "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$resultJson}"

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Exception) {
            return error
        }
        throw AssertionError("Expected bounded runtime RPC validation to fail")
    }

    private class ScriptedTransport(
        vararg responses: String,
    ) : Sora2RuntimeRpcTransport {
        private val pending = ArrayDeque(responses.toList())
        val requests = mutableListOf<String>()
        val limits = mutableListOf<Int>()

        override suspend fun post(
            requestBody: String,
            maximumResponseBytes: Int,
        ): String {
            requests += requestBody
            limits += maximumResponseBytes
            return pending.removeFirst()
        }
    }
}
