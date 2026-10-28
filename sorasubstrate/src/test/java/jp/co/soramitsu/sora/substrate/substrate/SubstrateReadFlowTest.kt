package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.Gson
import io.mockk.*
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.*
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.response.RpcResponse
import jp.co.soramitsu.xsubstrate.wsrpc.subscription.response.SubscriptionChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigInteger
import java.util.Collections

class SubstrateReadFlowTest {
    private val socket = mockk<SocketService>()
    private val runtime = mockk<RuntimeManager>()
    private val connection = mockk<ConnectionManager>()
    private val lease = mockk<Sora2MutationTransportLease>(relaxed = true)
    private val cancelled = mockk<SocketService.Cancellable>(relaxed = true)
    private val requests = Collections.synchronizedList(mutableListOf<RuntimeRequest>())
    private var answer: (RuntimeRequest) -> Any? = { error("Unexpected RPC ${it.method}") }
    private var changes = listOf<Any>()
    private val subject = SubstrateCalls(socket, runtime, connection)
    private val publicKey = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
    private val owner = publicKey.fromHex().toAddress(42.toShort())
    private val xor = "0x02" + "00".repeat(31)
    private val valId = "0x04" + "00".repeat(31)
    private val block = "0x" + "ab".repeat(32)
    private val large = BigInteger("123456789012345678901234567890")
    @Before fun setup() {
        every { socket.jsonMapper } returns Gson()
        coEvery { runtime.getRuntimeSnapshot() } returns TestRuntimeSnapshots.mainnet133
        every { connection.acquireReviewedSora2MutationTransport() } returns lease
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>(); requests.add(request)
            val callback = thirdArg<SocketService.ResponseListener<RpcResponse>>()
            try { callback.onNext(RpcResponse("2.0", answer(request), request.id, null)) }
            catch (error: Throwable) { callback.onError(error) }
            cancelled
        }
        every { socket.subscribe(any(), any(), any()) } answers {
            requests.add(firstArg())
            val callback = secondArg<SocketService.ResponseListener<SubscriptionChange>>()
            changes.forEach { callback.onNext(SubscriptionChange("2.0", "notification", SubscriptionChange.Params(it, "subscription"))) }
            cancelled
        }
    }
    private fun u128(value: BigInteger) = (0 until 16).joinToString("") { value.shiftRight(it * 8).and(BigInteger.valueOf(255)).toString(16).padStart(2, '0') }
    private fun tokenBalance(free: BigInteger, frozen: BigInteger = BigInteger.ZERO) = "0x" + u128(free) + u128(BigInteger.ZERO) + u128(frozen)
    private fun storageChanges(key: String, raw: String?) = mapOf("block" to block, "changes" to listOf(listOf(key, raw)))
    private fun header() = mapOf("parentHash" to "parent", "number" to "0x10", "stateRoot" to "state", "extrinsicsRoot" to "extrinsics")
    private suspend fun expectCancellation(action: suspend () -> Unit) {
        try { action(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
    @Test fun `storage lookup preserves absent and present values`() = runTest {
        answer = { assertEquals("state_getStorage", it.method); null }
        assertNull(subject.getStorageHex("key")); assertEquals(listOf("key"), requests.single().params)
        answer = { "0x1234" }; assertEquals("0x1234", subject.getStorageHex("key"))
    }
    @Test fun `key discovery preserves ordering and handles missing result`() = runTest {
        answer = { listOf("second", "first") }; assertEquals(listOf("second", "first"), subject.getStateKeys("prefix"))
        assertEquals("state_getKeys", requests.single().method); assertEquals(listOf("prefix"), requests.single().params)
        answer = { null }; assertTrue(subject.getStateKeys("prefix").isEmpty())
    }
    @Test fun `storage subscription emits removal and cancels when collection ends`() = runTest {
        changes = listOf(storageChanges("key", "0x12"), storageChanges("key", null))
        assertEquals(listOf("0x12", ""), subject.observeStorage("key").take(2).toList())
        verify { socket.subscribe(any(), any(), "state_unsubscribeStorage") }
        verify(exactly = 1) { cancelled.cancel() }
    }
    @Test fun `referrer balance decodes integer units and preserves malformed or removed values`() = runTest {
        changes = listOf(storageChanges("key", "0x" + u128(large)), storageChanges("key", "0x01"), storageChanges("key", null))
        assertEquals(listOf(large, null, null), subject.observeReferrerBalance(owner).take(3).toList())
    }
    @Test fun `free token balances follow requested order despite reordered storage response`() = runTest {
        answer = { request ->
            val keys = request.params[0] as List<*>
            listOf(mapOf("block" to block, "changes" to listOf(listOf(keys[1], tokenBalance(BigInteger.TEN)), listOf(keys[0], tokenBalance(large)))))
        }
        assertEquals(listOf(large, BigInteger.TEN), subject.fetchBalances(owner, listOf(xor, valId)))
        assertEquals("state_queryStorageAt", requests.single().method)
    }
    @Test fun `free balances do not subtract locks but transferable balances do`() = runTest {
        answer = { request -> listOf(storageChanges((request.params[0] as List<*>).single() as String, tokenBalance(large, BigInteger.TEN))) }
        assertEquals(listOf(large), subject.fetchBalances(owner, listOf(valId), block))
        assertEquals(listOf(large - BigInteger.TEN), subject.fetchTransferableBalances(owner, listOf(valId), block))
        assertTrue(requests.all { it.params[1] == block })
    }
    @Test fun `token frozen floor above free never produces a negative spendable balance`() = runTest {
        answer = { request -> listOf(storageChanges((request.params[0] as List<*>).single() as String, tokenBalance(BigInteger.ONE, BigInteger.TEN))) }
        assertEquals(listOf(BigInteger.ZERO), subject.fetchTransferableBalances(owner, listOf(valId)))
    }
    @Test fun `missing token accounts are zero in both balance projections`() = runTest {
        answer = { listOf(mapOf("block" to block, "changes" to emptyList<Any>())) }
        assertEquals(listOf(BigInteger.ZERO, BigInteger.ZERO), subject.fetchBalances(owner, listOf(xor, valId)))
        assertEquals(listOf(BigInteger.ZERO), subject.fetchTransferableBalances(owner, listOf(valId)))
    }
    @Test fun `present malformed token balance fails closed`() = runTest {
        answer = { request -> listOf(storageChanges((request.params[0] as List<*>).single() as String, "0x01")) }
        try { subject.fetchTransferableBalances(owner, listOf(valId)); fail("Malformed account must fail") } catch (_: Exception) { }
    }
    @Test fun `large token query is bounded to one hundred accounts per request`() = runTest {
        val ids = (1..205).map { "0x" + it.toString(16).padStart(64, '0') }
        answer = { request ->
            val keys = request.params[0] as List<*>
            listOf(mapOf("block" to block, "changes" to keys.map { listOf(it, tokenBalance(BigInteger.ONE)) }))
        }
        assertEquals(List(205) { BigInteger.ONE }, subject.fetchTransferableBalances(owner, ids, block))
        assertEquals(listOf(100, 100, 5), requests.map { (it.params[0] as List<*>).size })
        assertTrue(requests.all { it.params[1] == block })
    }
    @Test fun `empty token queries do not contact the node`() = runTest {
        assertTrue(subject.fetchBalances(owner, emptyList()).isEmpty()); assertTrue(subject.fetchTransferableBalances(owner, emptyList()).isEmpty())
        assertTrue(requests.isEmpty())
    }
    @Test fun `XOR account absent returns all zero balance components`() = runTest {
        answer = { null }
        val balance = subject.fetchXORBalances(owner)
        assertEquals(BigInteger.ZERO, balance.free); assertEquals(BigInteger.ZERO, balance.reserved)
        assertEquals(BigInteger.ZERO, balance.bonded); assertEquals(BigInteger.ZERO, balance.redeemable); assertEquals(BigInteger.ZERO, balance.unbonding)
        assertEquals(1, requests.size)
    }
    @Test fun `XOR balances include referral bond and runtime frozen floor`() = runTest {
        val snapshot = TestRuntimeSnapshots.mainnet133
        val accountKey = snapshot.metadata.module("System").storage("Account").storageKey(snapshot, publicKey.fromHex())
        val bondedKey = snapshot.metadata.module("Staking").storage("Bonded").storageKey(snapshot, publicKey.fromHex())
        val eraKey = snapshot.metadata.module("Staking").storage("ActiveEra").storageKey()
        answer = { request -> when (request.params[0]) {
            accountKey -> "0x" + "00000000".repeat(4) + u128(large) + u128(BigInteger.ZERO) + u128(BigInteger.TEN) + u128(BigInteger.ZERO)
            bondedKey -> null
            eraKey -> "0x05000000"
            else -> error("Unexpected storage key")
        } }
        changes = listOf(storageChanges("referral", "0x" + u128(BigInteger.valueOf(7))))
        val balance = subject.fetchXORBalances(owner)
        assertEquals(large, balance.free); assertEquals(BigInteger.TEN, balance.miscFrozen); assertEquals(BigInteger.TEN, balance.feeFrozen)
        assertEquals(BigInteger.valueOf(7), balance.bonded); assertEquals(BigInteger.ZERO, balance.unbonding)
    }
    @Test fun `empty asset registry avoids a storage value query`() = runTest {
        answer = { assertEquals("state_getKeysPaged", it.method); emptyList<String>() }
        assertTrue(subject.fetchAssetsList().isEmpty()); assertEquals(1, requests.size)
        assertEquals(400, requests.single().params[1])
    }
    @Test fun `asset registry tolerates missing change rows`() = runTest {
        answer = { when (it.method) {
            "state_getKeysPaged" -> listOf("key")
            "state_queryStorageAt" -> listOf(mapOf("block" to block, "changes" to listOf(listOf("key", null), listOf(null, "0x00"))))
            else -> error(it.method)
        } }
        assertTrue(subject.fetchAssetsList().isEmpty())
    }
    @Test fun `asset registry empty query response produces an empty result`() = runTest {
        answer = { if (it.method == "state_getKeysPaged") listOf("key") else emptyList<Any>() }
        assertTrue(subject.fetchAssetsList().isEmpty())
    }
    @Test fun `block and header queries preserve explicit hash and genesis default`() = runTest {
        answer = { when(it.method) {
            "chain_getBlockHash" -> block
            "chain_getFinalizedHead" -> block
            "chain_getHeader" -> header()
            "chain_getBlock" -> mapOf("block" to mapOf("header" to header(), "extrinsics" to listOf("0x01", "0x02")), "justification" to null)
            else -> error(it.method)
        } }
        assertEquals(block, subject.getBlockHash()); assertEquals(listOf(0), requests.last().params)
        assertEquals(block, subject.getBlockHash(16)); assertEquals(listOf(16), requests.last().params)
        assertEquals(block, subject.getFinalizedHead())
        assertEquals("0x10", subject.getChainHeader(block).number); assertEquals(listOf(block), requests.last().params)
        assertEquals("parent", subject.getChainLastHeader().parentHash); assertTrue(requests.last().params.isEmpty())
        assertEquals(listOf("0x01", "0x02"), subject.getBlock(block).block.extrinsics)
    }
    @Test fun `runtime metadata and migration checks use their distinct RPC methods`() = runTest {
        answer = { when (it.method) {
            "state_getMetadata" -> "0x6d657461"
            "chain_getRuntimeVersion" -> mapOf("specVersion" to 133, "transactionVersion" to 131, "specName" to "sora")
            "irohaMigration_needsMigration" -> true
            else -> error(it.method)
        } }
        assertEquals("0x6d657461", subject.getMetadataHex()); assertEquals(133, subject.getRuntimeVersion().specVersion)
        assertTrue(subject.needsMigration("alice@sora")); assertEquals(listOf("alice@sora"), requests.last().params)
    }
    @Test fun `buy quote uses finalized head and serializes large amounts as decimal strings`() = runTest {
        answer = { if (it.method == "chain_getFinalizedHead") block else mapOf("marketId" to 7, "outcome" to "Yes", "collateralIn" to large.toString(), "feeAmount" to "2", "pricingCollateral" to "3", "sharesOut" to "4") }
        val quote = subject.polkamarktQuoteBuy(7, "Yes", large)
        assertEquals(block, quote.blockHash); assertEquals(large, quote.value!!.collateralIn)
        assertEquals(listOf(7L, "Yes", large.toString(), block), requests.last().params)
    }
    @Test fun `sell quote uses supplied finalized hash without another head read`() = runTest {
        answer = { mapOf("marketId" to 7, "outcome" to "No", "sharesIn" to large.toString(), "grossCollateralOut" to "10", "feeAmount" to "2", "collateralOut" to "8") }
        val quote = subject.polkamarktQuoteSell(7, "No", large, block)
        assertEquals(BigInteger.valueOf(8), quote.value!!.collateralOut); assertEquals(block, quote.blockHash)
        assertEquals(listOf(7L, "No", large.toString(), block), requests.single().params)
    }
    @Test fun `unavailable market state stays absent at the requested block`() = runTest {
        answer = { null }; val state = subject.polkamarktMarketState(7, block)
        assertNull(state.value); assertEquals(block, state.blockHash); assertEquals(listOf(7L, block), requests.single().params)
    }
    @Test fun `claimable read is scoped to account market and finalized block`() = runTest {
        answer = { if(it.method == "chain_getFinalizedHead") block else null }
        val result = subject.polkamarktClaimable(owner, 7)
        assertNull(result.value); assertEquals(block, result.blockHash); assertEquals(listOf(owner, 7L, block), requests.last().params)
    }
    @Test fun `invalid market id is rejected before head discovery or RPC`() = runTest {
        try { subject.polkamarktQuoteBuy(-1, "Yes", large); fail("Invalid id") } catch (_: IllegalArgumentException) { }
        try { subject.polkamarktQuoteSell(4294967296, "No", large); fail("Invalid id") } catch (_: IllegalArgumentException) { }
        try { subject.polkamarktMarketState(-1); fail("Invalid id") } catch (_: IllegalArgumentException) { }
        try { subject.polkamarktClaimable(owner, -1); fail("Invalid id") } catch (_: IllegalArgumentException) { }
        assertTrue(requests.isEmpty())
    }
    @Test fun `submission holds reviewed transport until the response`() = runTest {
        answer = { verify(exactly = 0) { lease.close() }; assertEquals("author_submitExtrinsic", it.method); "hash" }
        assertEquals("hash", subject.submitExtrinsic("0x00")); verify(exactly = 1) { lease.close() }
        assertEquals(listOf("0x00"), requests.single().params)
    }
    @Test fun `failed submission releases its transport lease`() = runTest {
        val error = IllegalStateException("node disconnected"); answer = { throw error }
        try { subject.submitExtrinsic("0x00"); fail("Transport error") } catch (actual: IllegalStateException) { assertEquals(error.message, actual.message) }
        verify(exactly = 1) { lease.close() }
    }
    @Test fun `unreviewed transport cannot send a submission`() = runTest {
        every { connection.acquireReviewedSora2MutationTransport() } throws IllegalStateException("unreviewed")
        try { subject.submitExtrinsic("0x00"); fail("Transport must be reviewed") } catch (_: IllegalStateException) { }
        assertTrue(requests.isEmpty())
    }
    @Test fun `watching submission distinguishes inclusion finality timeout and finalization`() = runTest {
        changes = listOf("ready", mapOf("inBlock" to "reorgable"), mapOf("finalityTimeout" to "timeout"), mapOf("finalized" to block))
        val results = subject.submitAndWatchExtrinsic("0x0400").take(4).toList()
        assertTrue(results[0].second is jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse.ExtrinsicStatusPending)
        assertTrue(results[1].second is jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse.ExtrinsicStatusPending)
        assertTrue(results[2].second is jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse.ExtrinsicStatusFinalityTimeout)
        assertTrue(results[3].second is jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse.ExtrinsicStatusFinalized)
        assertEquals(1, results.map { it.first }.distinct().size)
        verify(exactly = 1) { lease.close() }; verify(exactly = 1) { cancelled.cancel() }
    }
    @Test fun `empty finalized events decode as empty`() = runTest {
        answer = { assertEquals(block, it.params[1]); "0x00" }
        assertTrue(subject.checkEvents(block).isEmpty())
    }
    @Test fun `cancelled finalized event read propagates cancellation`() = runTest {
        answer = { throw CancellationException("screen closed") }
        expectCancellation { subject.checkEvents(block) }
    }
    @Test fun `bulk custom page size controls requests and last page detection`() = runTest {
        answer = { request -> when(request.params.last()) { "key-b" -> listOf("key-c"); else -> listOf("key-a", "key-b") } }
        assertEquals(listOf("key-a", "key-b", "key-c"), BulkRetriever(2).retrieveAllKeys(socket, "prefix"))
        assertEquals(listOf(listOf("prefix", 2), listOf("prefix", 2, "key-b")), requests.map { it.params })
    }
    @Test fun `bulk missing query result is empty`() = runTest {
        answer = { emptyList<Any>() }
        assertTrue(BulkRetriever().queryKeys(socket, listOf("key")).isEmpty())
    }
    @Test fun `bulk query cancellation is never converted to an empty successful result`() = runTest {
        answer = { throw CancellationException("cancelled") }
        expectCancellation { BulkRetriever().queryKeys(socket, listOf("key")) }
    }
    @Test fun `bulk page size must be positive before starting a request`() {
        assertThrows(IllegalArgumentException::class.java) { BulkRetriever(0) }
        assertThrows(IllegalArgumentException::class.java) { BulkRetriever(-1) }
        assertTrue(requests.isEmpty())
    }
    @Test fun `single bulk key lookup preserves an absent value`() = runTest {
        answer = { emptyList<Any>() }
        assertNull(BulkRetriever().queryKey(socket, "missing"))
    }
    @Test fun `single bulk key lookup cannot return a different key value`() = runTest {
        answer = { listOf(mapOf("block" to block, "changes" to listOf(listOf("other", "0x01"), listOf("requested", "0x02")))) }
        assertEquals("0x02", BulkRetriever().queryKey(socket, "requested"))
    }
    @Test fun `staking ledger separates mature unlocks from future locks without losing referral bond`() = runTest {
        val snapshot = TestRuntimeSnapshots.mainnet133
        val accountKey = snapshot.metadata.module("System").storage("Account").storageKey(snapshot, publicKey.fromHex())
        val bondedKey = snapshot.metadata.module("Staking").storage("Bonded").storageKey(snapshot, publicKey.fromHex())
        val ledgerKey = snapshot.metadata.module("Staking").storage("Ledger").storageKey(snapshot, publicKey.fromHex())
        val eraKey = snapshot.metadata.module("Staking").storage("ActiveEra").storageKey()
        every { runtime.toSoraAddressOrNull(any()) } returns owner
        answer = { request -> when (request.params[0]) {
            accountKey -> "0x" + "00000000".repeat(4) + u128(large) + u128(BigInteger.ZERO) + u128(BigInteger.TEN) + u128(BigInteger.ZERO)
            bondedKey -> "0x" + publicKey
            // Stash, compact total=25, active=10, 3 unlocks: (3,4), (5,5), (7,6), no claimed rewards.
            ledgerKey -> "0x" + publicKey + "64280c0c1014141c1800"
            eraKey -> "0x05000000"
            else -> error("Unexpected storage key")
        } }
        changes = listOf(storageChanges("referral", "0x" + u128(BigInteger.valueOf(7))))
        val balance = subject.fetchXORBalances(owner)
        assertEquals(BigInteger.valueOf(7), balance.bonded)
        assertEquals(BigInteger.valueOf(8), balance.redeemable)
        assertEquals(BigInteger.valueOf(7), balance.unbonding)
        verify(exactly = 1) { runtime.toSoraAddressOrNull(match { it.contentEquals(publicKey.fromHex()) }) }
        assertTrue(requests.any { it.params.first() == ledgerKey })
    }


}
