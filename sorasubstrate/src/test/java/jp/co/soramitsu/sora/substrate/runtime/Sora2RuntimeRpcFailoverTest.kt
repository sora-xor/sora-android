package jp.co.soramitsu.sora.substrate.runtime

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two reviewed endpoints, one set of rules (audit F1, LIVE-4, TX-HOST). The production client is
 * driven through its transport seam with two fake nodes that answer by JSON-RPC method.
 */
class Sora2RuntimeRpcFailoverTest {

    private val gson = Gson()
    private val mainnet = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH
    private val otherChain = "0x${"0f".repeat(32)}"
    private val finalizedHash = "0x${"ab".repeat(32)}"
    private val account = requireNotNull(
        Sora2AddressCodec().toSoraAddressOrNull(ByteArray(32) { (it + 1).toByte() })
    )
    private val signed = "0x2d028400"
    private val signedHash = signed.extrinsicHash().canonicalExtrinsicHash()
    private var now = 0L

    private fun client(first: FakeNode, second: FakeNode) = Sora2BoundedRuntimeRpcClient(
        transport = first,
        gson = gson,
        secondTransport = second,
        expectedGenesisHash = mainnet,
        elapsedMillis = { now },
    )

    @Test
    fun `the reviewed endpoints are two different hosts`() {
        assertEquals("https://ws.mof.sora.org", Sora2RuntimeContract.RUNTIME_RPC_ENDPOINT)
        assertEquals("https://mof2.sora.org", Sora2RuntimeContract.SECOND_RUNTIME_RPC_ENDPOINT)
    }

    @Test
    fun `identity is read in one piece from the second endpoint when the first is unreachable`() =
        runTest {
            val first = FakeNode(finalizedNumber = 100).apply { unreachable() }
            val second = FakeNode(finalizedNumber = 99)

            val identity = client(first, second).getFinalizedIdentity()

            assertEquals(mainnet, identity.genesisHash)
            assertEquals(99L, identity.finalizedBlockNumber)
            assertEquals(133, identity.runtimeVersion.specVersion)
            assertEquals(listOf("chain_getBlockHash"), first.methods)
            assertEquals(
                listOf(
                    "chain_getBlockHash",
                    "chain_getFinalizedHead",
                    "state_getRuntimeVersion",
                    "chain_getHeader",
                ),
                second.methods,
            )
        }

    @Test
    fun `identity that breaks half way on one endpoint is read again completely on the other`() =
        runTest {
            val first = FakeNode().apply {
                failure = { method ->
                    IOException("reset").takeIf { method == "state_getRuntimeVersion" }
                }
            }
            val second = FakeNode()

            client(first, second).getFinalizedIdentity()

            // Nothing of the first endpoint's partial answer is combined with the second's.
            assertEquals(
                listOf(
                    "chain_getBlockHash",
                    "chain_getFinalizedHead",
                    "state_getRuntimeVersion",
                    "chain_getHeader",
                ),
                second.methods,
            )
        }

    @Test
    fun `nonce fee metadata and code hash come from the second endpoint when the first is down`() =
        runTest {
            val first = FakeNode().apply { unreachable() }
            val second = FakeNode()
            val client = client(first, second)

            assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
            assertEquals(BigInteger("700000000000000"), client.queryPartialFee(signed))
            assertEquals(METADATA, client.getMetadataAtFinalized(finalizedHash))
            assertEquals(CODE_HASH, client.getRuntimeCodeHashAtFinalized(finalizedHash))

            // The second endpoint proved its chain once, before its first answer was used.
            assertEquals(
                listOf(
                    "chain_getBlockHash",
                    "system_accountNextIndex",
                    "payment_queryInfo",
                    "state_getMetadata",
                    "state_getStorageHash",
                ),
                second.methods,
            )
            assertEquals(
                listOf("0x3a636f6465", finalizedHash),
                second.params.last().map { it.asString },
            )
        }

    @Test
    fun `an endpoint that failed is tried last until its pause is over and is never locked out`() =
        runTest {
            val first = FakeNode().apply { unreachable() }
            val second = FakeNode()
            val client = client(first, second)

            client.getAccountNextIndex(account)
            val firstAttempts = first.methods.size
            first.failure = { null }

            // Within the pause the healthy endpoint answers first; the other is not asked.
            now += 29_000
            client.getAccountNextIndex(account)
            assertEquals(firstAttempts, first.methods.size)

            // If the preferred endpoint fails too, the paused one is still used.
            second.unreachable()
            assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
            assertTrue(first.methods.contains("system_accountNextIndex"))

            // After the pause the reviewed order is back.
            second.failure = { null }
            now += 10 * 60_000
            first.methods.clear()
            second.methods.clear()
            client.getAccountNextIndex(account)
            assertEquals(listOf("system_accountNextIndex"), first.methods)
            assertEquals(emptyList<String>(), second.methods)
        }

    @Test
    fun `an endpoint on another chain serves nothing`() = runTest {
        val first = FakeNode().apply { unreachable() }
        val second = FakeNode(genesis = otherChain)
        val client = client(first, second)

        val error = captureFailure { client.getAccountNextIndex(account) }

        // The failure of the endpoint tried first is reported; the chain mismatch is attached.
        assertTrue(error is BoundedHttpTextException)
        assertEquals(
            listOf("SORA2_RUNTIME_RPC_ENDPOINT_GENESIS_MISMATCH"),
            error.suppressed.map { it.message },
        )
        assertEquals(listOf("chain_getBlockHash"), second.methods)

        // It gets the signed transaction neither.
        val submitError = captureFailure { client.submitExtrinsicOnce(signed) }
        assertTrue(submitError is Sora2SubmissionNotSentException)
        assertFalse("author_submitExtrinsic" in second.methods)
    }

    @Test
    fun `identity skips an endpoint on another chain while the other endpoint is right`() =
        runTest {
            val first = FakeNode(genesis = otherChain)
            val second = FakeNode()
            val client = client(first, second)

            assertEquals(mainnet, client.getFinalizedIdentity().genesisHash)

            // The wrong endpoint lost its admission: later requests ask it for its chain again
            // and use the other endpoint when the answer is still wrong.
            first.methods.clear()
            second.methods.clear()
            assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
            assertEquals(listOf("chain_getBlockHash"), first.methods)
            assertEquals(listOf("system_accountNextIndex"), second.methods)
        }

    @Test
    fun `when no endpoint is on the expected chain the caller gets what was observed`() = runTest {
        val first = FakeNode(genesis = otherChain)
        val second = FakeNode().apply { unreachable() }

        val identity = client(first, second).getFinalizedIdentity()

        // RuntimeManager rejects this genesis as identity drift; it is not "network unreachable".
        assertEquals(otherChain, identity.genesisHash)
    }

    @Test
    fun `a signed transaction goes to the second endpoint only when the first was never reached`() =
        runTest {
            val first = FakeNode().apply {
                failure = { BoundedHttpTextException("BOUNDED_HTTP_IO", requestNotSent = true) }
            }
            val second = FakeNode()

            assertEquals(signedHash, client(first, second).submitExtrinsicOnce(signed))

            assertEquals(0, first.submissions)
            assertEquals(1, second.submissions)
        }

    @Test
    fun `a signed transaction the first endpoint may have received is never sent again`() =
        runTest {
            val ambiguous = listOf<(FakeNode) -> Unit>(
                // The body left the device and the answer was lost.
                { node -> node.failOnSubmit(BoundedHttpTextException("BOUNDED_HTTP_IO")) },
                // The request timed out after it was written.
                { node -> node.failOnSubmit(BoundedHttpTextException("BOUNDED_HTTP_DEADLINE")) },
                // An HTTP error from the node or a proxy in front of it.
                { node -> node.failOnSubmit(BoundedHttpTextException("BOUNDED_HTTP_STATUS_502")) },
                // Any other transport error says nothing about delivery.
                { node -> node.failOnSubmit(IOException("broken pipe")) },
                // The node answered: a refusal, "already imported", an internal error.
                { node -> node.submitError = 1010 to "Invalid Transaction" },
                { node -> node.submitError = 1013 to "Transaction Already Imported" },
                { node -> node.submitError = -32603 to "Internal error" },
                // The node answered with something that is not the transaction's hash.
                { node -> node.submitResult = "0x${"99".repeat(32)}" },
            )
            ambiguous.forEachIndexed { index, arrange ->
                val first = FakeNode().also(arrange)
                val second = FakeNode()
                val client = client(first, second)
                client.getFinalizedIdentity()

                val error = captureFailure { client.submitExtrinsicOnce(signed) }

                assertFalse("case $index", error is Sora2SubmissionNotSentException)
                assertEquals("case $index", 1, first.submissions)
                assertEquals("case $index", 0, second.submissions)
            }
        }

    @Test
    fun `when no endpoint can be connected to the transaction was provably not sent`() = runTest {
        val first = FakeNode().apply {
            failure = { BoundedHttpTextException("BOUNDED_HTTP_IO", requestNotSent = true) }
        }
        val second = FakeNode().apply {
            failure = { BoundedHttpTextException("BOUNDED_HTTP_DEADLINE", requestNotSent = true) }
        }

        val error = captureFailure { client(first, second).submitExtrinsicOnce(signed) }

        assertTrue(error is Sora2SubmissionNotSentException)
        assertEquals("SORA2_SUBMISSION_NOT_SENT", error.message)
        // The cause chain still names a network failure for the caller's wording.
        assertTrue(error.cause is IOException)
        assertEquals(0, first.submissions + second.submissions)
    }

    @Test
    fun `status reads work while the first endpoint is unreachable`() = runTest {
        val first = FakeNode().apply { unreachable() }
        val second = FakeNode().apply { extrinsics = listOf("0x01", signed) }
        val client = client(first, second)

        val identity = client.getFinalizedIdentity()
        val block = client.getCanonicalBlock(
            blockNumber = 7,
            finalizedHeight = identity.finalizedBlockNumber,
            recoveryHashes = setOf(signedHash),
        )
        val events = client.getStorageAt(EVENTS_KEY, block.blockHash)

        assertEquals(mapOf(signedHash to 1), block.matchingExtrinsicIndices)
        assertEquals("0x0400", events)
        assertTrue(first.methods.all { it == "chain_getBlockHash" })
    }

    @Test
    fun `a block hash and its block always come from the same endpoint`() = runTest {
        val first = FakeNode().apply {
            blockHashes[7] = "0x${"c1".repeat(32)}"
            failure = { method -> IOException("reset").takeIf { method == "chain_getBlock" } }
        }
        val second = FakeNode().apply { blockHashes[7] = "0x${"c2".repeat(32)}" }
        val client = client(first, second)
        client.getFinalizedIdentity()
        second.methods.clear()

        val block = client.getCanonicalBlock(7, 100, setOf(signedHash))

        assertEquals("0x${"c2".repeat(32)}", block.blockHash)
        assertEquals(
            listOf(
                // The second endpoint proved its chain and its finalized height when the two
                // endpoints were compared, so block 7 is known to be final on it too.
                "chain_getBlockHash",
                "chain_getBlock",
            ),
            second.methods,
        )
        assertEquals(listOf("0x${"c2".repeat(32)}"), second.params.last().map { it.asString })
    }

    @Test
    fun `a block is never read from an endpoint that has not finalized it`() = runTest {
        // The first endpoint finalized block 100 and then stops answering. The second is still
        // at 98: its block 99 is only its best block and could be replaced.
        val first = FakeNode(finalizedNumber = 100)
        val second = FakeNode(finalizedNumber = 98).apply {
            blockHashes[99] = "0x${"f0".repeat(32)}"
            blockNumberInBlock = 99
        }
        val client = client(first, second)
        val identity = client.getFinalizedIdentity()
        assertEquals(100L, identity.finalizedBlockNumber)
        first.failure = { IOException("down") }

        val error = captureFailure {
            client.getCanonicalBlock(99, identity.finalizedBlockNumber, setOf(signedHash))
        }

        assertTrue(error is IOException)
        assertEquals(
            "SORA2_RUNTIME_RPC_ENDPOINT_BEHIND",
            error.suppressed.single().message,
        )
        assertFalse("chain_getBlock" in second.methods)

        // Once the second endpoint has finalized it, the same read succeeds there.
        second.finalizedNumber = 99
        val block = client.getCanonicalBlock(99, identity.finalizedBlockNumber, setOf(signedHash))
        assertEquals("0x${"f0".repeat(32)}", block.blockHash)
        // What it has finalized is remembered: block 98 needs no second proof.
        second.methods.clear()
        second.blockNumberInBlock = 98
        client.getCanonicalBlock(98, identity.finalizedBlockNumber, setOf(signedHash))
        assertEquals(listOf("chain_getBlockHash", "chain_getBlock"), second.methods)
    }

    @Test
    fun `finality does not step backwards while one endpoint is not behind`() = runTest {
        val first = FakeNode(finalizedNumber = 100)
        val second = FakeNode(finalizedNumber = 101)
        val client = client(first, second)
        assertEquals(100L, client.getFinalizedIdentity().finalizedBlockNumber)

        // The first endpoint now answers from a node that is behind what it reported before.
        first.finalizedNumber = 99
        assertEquals(101L, client.getFinalizedIdentity().finalizedBlockNumber)

        // When every endpoint that answers is behind, the most advanced answer is used rather
        // than no answer; nothing is locked out.
        second.unreachable()
        assertEquals(99L, client.getFinalizedIdentity().finalizedBlockNumber)
        first.finalizedNumber = 102
        assertEquals(102L, client.getFinalizedIdentity().finalizedBlockNumber)
    }

    @Test
    fun `state the first endpoint discarded is read from the archive endpoint`() = runTest {
        val blockHash = "0x${"cd".repeat(32)}"
        val discarded = 4003 to "Client error: UnknownBlock: State already discarded for $blockHash"
        val first = FakeNode().apply {
            readErrors["state_getStorage"] = discarded
            readErrors["state_getRuntimeVersion"] = discarded
            readErrors["state_getMetadata"] = discarded
        }
        val second = FakeNode()
        val client = client(first, second)

        assertEquals("0x0400", client.getStorageAt(EVENTS_KEY, blockHash))
        assertEquals(133, client.getHistoricRuntimeVersionForStatus(blockHash).specVersion)
        assertEquals(METADATA, client.getHistoricMetadataForStatus(blockHash))
        assertEquals(
            listOf(
                "chain_getBlockHash",
                "state_getStorage",
                "state_getRuntimeVersion",
                "state_getMetadata",
            ),
            second.methods,
        )
    }

    @Test
    fun `when both endpoints refuse a read the first answer is reported with the second attached`() =
        runTest {
            val first = FakeNode().apply { readErrors["payment_queryInfo"] = -32603 to "first" }
            val second = FakeNode().apply { readErrors["payment_queryInfo"] = -32000 to "second" }

            val error = captureFailure { client(first, second).queryPartialFee(signed) }

            assertEquals("first", (error as Sora2RuntimeRpcRemoteException).remoteMessage)
            val attached = error.suppressed.single() as Sora2RuntimeRpcRemoteException
            assertEquals("second", attached.remoteMessage)
        }

    @Test
    fun `a malformed answer from one endpoint is replaced by a valid one from the other`() =
        runTest {
            val first = FakeNode().apply { nonce = "7.0" }
            val second = FakeNode()

            assertEquals(BigInteger.valueOf(7), client(first, second).getAccountNextIndex(account))
        }

    @Test
    fun `a single endpoint behaves as before and cancellation is never retried`() = runTest {
        val only = FakeNode(genesis = otherChain)
        val client = Sora2BoundedRuntimeRpcClient(only, gson)

        // No second endpoint, no admission request: the caller checks the chain.
        assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
        assertEquals(listOf("system_accountNextIndex"), only.methods)

        val first = FakeNode()
        val second = FakeNode()
        val cancellation = kotlinx.coroutines.CancellationException("screen left")
        first.failure = { cancellation }
        val thrown = captureFailure { client(first, second).getFinalizedIdentity() }
        assertSame(cancellation, thrown)
        assertEquals(emptyList<String>(), second.methods)
    }

    // --- an endpoint that answers but no longer follows the chain ---------------------------

    @Test
    fun `an endpoint that answers but is far behind is not what a transaction is signed against`() =
        runTest {
            // The first endpoint restarted and is syncing: every request is answered, 500
            // blocks behind. Before the fix a fresh process took identity, nonce and fee from
            // it and sent the transaction to it, although the second endpoint was healthy.
            val first = FakeNode(finalizedNumber = 500)
            val second = FakeNode(finalizedNumber = 1_000)
            val client = client(first, second)

            assertEquals(1_000L, client.getFinalizedIdentity().finalizedBlockNumber)

            first.methods.clear()
            second.methods.clear()
            assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
            assertEquals(BigInteger("700000000000000"), client.queryPartialFee(signed))
            assertEquals(signedHash, client.submitExtrinsicOnce(signed))
            assertEquals(1_000L, client.getFinalizedIdentity().finalizedBlockNumber)

            assertEquals(emptyList<String>(), first.methods)
            assertEquals(0, first.submissions)
            assertEquals(1, second.submissions)
        }

    @Test
    fun `endpoints a few blocks apart keep the reviewed order`() = runTest {
        val first = FakeNode(finalizedNumber = 1_000 - TOLERANCE)
        val second = FakeNode(finalizedNumber = 1_000)
        val client = client(first, second)

        assertEquals(1_000L - TOLERANCE, client.getFinalizedIdentity().finalizedBlockNumber)

        second.methods.clear()
        assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
        assertEquals(emptyList<String>(), second.methods)

        // One block more and the first endpoint counts as not following the chain.
        val behind = FakeNode(finalizedNumber = 1_000 - TOLERANCE - 1)
        val current = FakeNode(finalizedNumber = 1_000)
        assertEquals(
            1_000L,
            client(behind, current).getFinalizedIdentity().finalizedBlockNumber,
        )
    }

    @Test
    fun `the other endpoint is asked for its height once per interval and with two requests`() =
        runTest {
            val first = FakeNode(finalizedNumber = 1_000)
            val second = FakeNode(finalizedNumber = 1_000)
            val client = client(first, second)

            client.getFinalizedIdentity()
            assertEquals(
                listOf("chain_getBlockHash", "chain_getFinalizedHead", "chain_getHeader"),
                second.methods,
            )

            // Every fee quote reads the identity; the comparison is not repeated with it.
            second.methods.clear()
            repeat(5) {
                now += 10_000
                client.getFinalizedIdentity()
            }
            assertEquals(emptyList<String>(), second.methods)

            now += 2 * 60_000
            client.getFinalizedIdentity()
            assertEquals(listOf("chain_getFinalizedHead", "chain_getHeader"), second.methods)
        }

    @Test
    fun `an endpoint that cannot be reached is not asked for its height on every identity`() =
        runTest {
            val first = FakeNode(finalizedNumber = 1_000)
            val second = FakeNode(finalizedNumber = 1_000).apply { unreachable() }
            val client = client(first, second)

            assertEquals(1_000L, client.getFinalizedIdentity().finalizedBlockNumber)
            assertEquals(1, second.methods.size)

            // While its pause lasts the unreachable endpoint costs nothing more.
            now += 20_000
            assertEquals(1_000L, client.getFinalizedIdentity().finalizedBlockNumber)
            assertEquals(1, second.methods.size)
        }

    @Test
    fun `an endpoint that has caught up is the first choice again`() = runTest {
        val first = FakeNode(finalizedNumber = 500)
        val second = FakeNode(finalizedNumber = 1_000)
        val client = client(first, second)
        assertEquals(1_000L, client.getFinalizedIdentity().finalizedBlockNumber)

        // It finished syncing. Its next height report, at the next comparison, ends the pause.
        first.finalizedNumber = 1_003
        second.finalizedNumber = 1_002
        now += 2 * 60_000 + 1
        assertEquals(1_003L, client.getFinalizedIdentity().finalizedBlockNumber)

        first.methods.clear()
        second.methods.clear()
        assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
        assertEquals(listOf("system_accountNextIndex"), first.methods)
        assertEquals(emptyList<String>(), second.methods)
    }

    @Test
    fun `an endpoint that is behind is still used when the other one stops answering`() = runTest {
        val first = FakeNode(finalizedNumber = 500)
        val second = FakeNode(finalizedNumber = 1_000)
        val client = client(first, second)
        assertEquals(1_000L, client.getFinalizedIdentity().finalizedBlockNumber)

        second.unreachable()

        // Nothing is locked out: the caller gets the best answer there is and decides.
        assertEquals(500L, client.getFinalizedIdentity().finalizedBlockNumber)
        assertEquals(BigInteger.valueOf(7), client.getAccountNextIndex(account))
    }

    @Test
    fun `a stalled first endpoint is used when the second cannot be reached at all`() = runTest {
        val first = FakeNode(finalizedNumber = 500)
        val second = FakeNode(finalizedNumber = 1_000).apply { unreachable() }

        assertEquals(500L, client(first, second).getFinalizedIdentity().finalizedBlockNumber)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        throw AssertionError("Expected a failure")
    }

    /** One node behind one endpoint. It answers by method and records what it was asked. */
    private inner class FakeNode(
        var genesis: String = mainnet,
        var finalizedNumber: Long = 100,
    ) : Sora2RuntimeRpcTransport {
        val methods = mutableListOf<String>()
        val params = mutableListOf<JsonArray>()
        var failure: (String) -> Exception? = { null }
        val readErrors = mutableMapOf<String, Pair<Int, String>>()
        val blockHashes = mutableMapOf<Long, String>()
        var extrinsics: List<String> = emptyList()
        var blockNumberInBlock = 7L
        var nonce = "7"
        var submissions = 0
        var submitError: Pair<Int, String>? = null
        var submitResult: String? = null
        private var submitFailure: Exception? = null

        fun unreachable() {
            failure = { BoundedHttpTextException("BOUNDED_HTTP_IO", requestNotSent = true) }
        }

        /** The request reaches the node; what comes back does not. */
        fun failOnSubmit(error: Exception) {
            submitFailure = error
        }

        override suspend fun post(requestBody: String, maximumResponseBytes: Int): String {
            val request = gson.fromJson(requestBody, JsonObject::class.java)
            val method = request.get("method").asString
            val id = request.get("id").asLong
            val arguments = request.get("params").asJsonArray
            methods += method
            params += arguments
            failure(method)?.let { throw it }
            readErrors[method]?.let { (code, message) -> return error(id, code, message) }
            return when (method) {
                "chain_getBlockHash" -> {
                    val number = arguments.get(0).asLong
                    val hash = if (number == 0L) {
                        genesis
                    } else {
                        blockHashes[number] ?: "0x${"cd".repeat(32)}"
                    }
                    result(id, gson.toJson(hash))
                }
                "chain_getFinalizedHead" -> result(id, gson.toJson(finalizedHash))
                "state_getRuntimeVersion" -> result(
                    id,
                    "{\"specName\":\"sora-substrate\",\"implName\":\"sora-substrate\"," +
                        "\"authoringVersion\":1,\"specVersion\":133,\"implVersion\":1," +
                        "\"apis\":[],\"transactionVersion\":131,\"stateVersion\":1}",
                )
                "chain_getHeader" -> result(
                    id,
                    "{\"number\":\"0x${finalizedNumber.toString(16)}\"}",
                )
                "system_accountNextIndex" -> result(id, nonce)
                "payment_queryInfo" -> result(
                    id,
                    "{\"weight\":{\"ref_time\":1,\"proof_size\":1},\"class\":\"normal\"," +
                        "\"partialFee\":\"700000000000000\"}",
                )
                "state_getMetadata" -> result(id, gson.toJson(METADATA))
                "state_getStorageHash" -> result(id, gson.toJson(CODE_HASH))
                "state_getStorage" -> result(id, gson.toJson("0x0400"))
                "chain_getBlock" -> result(
                    id,
                    "{\"block\":{\"header\":{\"number\":\"0x${blockNumberInBlock.toString(16)}\"}," +
                        "\"extrinsics\":" +
                        gson.toJson(extrinsics) + "},\"justifications\":null}",
                )
                "author_submitExtrinsic" -> {
                    submissions += 1
                    submitFailure?.let { throw it }
                    submitError?.let { (code, message) -> return error(id, code, message) }
                    result(id, gson.toJson(submitResult ?: signedHash))
                }
                else -> throw AssertionError("Unexpected method $method")
            }
        }

        private fun result(id: Long, resultJson: String): String =
            "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$resultJson}"

        private fun error(id: Long, code: Int, message: String): String =
            "{\"jsonrpc\":\"2.0\",\"id\":$id,\"error\":{\"code\":$code," +
                "\"message\":\"$message\"}}"
    }

    private companion object {
        const val TOLERANCE = Sora2BoundedRuntimeRpcClient.FINALITY_LAG_TOLERANCE_BLOCKS
        const val METADATA = "0x6d657461"
        const val CODE_HASH =
            "0xfe91daf57982171d8051acc71c1ca7626041cec571561232b3d6256544acbed3"
        const val EVENTS_KEY =
            "0x26aa394eea5630e07c48ae0c9558cef780d41e5e16056765bc8461851072c9d7"
    }
}
