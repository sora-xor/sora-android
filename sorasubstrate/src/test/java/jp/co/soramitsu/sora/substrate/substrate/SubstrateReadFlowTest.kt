package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.Gson
import io.mockk.*
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.mapCodeToken
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.*
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.exception.RpcException
import jp.co.soramitsu.xsubstrate.wsrpc.response.RpcError
import jp.co.soramitsu.xsubstrate.wsrpc.response.RpcResponse
import jp.co.soramitsu.xsubstrate.wsrpc.subscription.response.SubscriptionChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runCurrent
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
    /** A request the node answers with a JSON-RPC error instead of a result. */
    private var nodeError: (RuntimeRequest) -> RpcError? = { null }
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
        every { runtime.toSoraAddressOrNull(any()) } answers { firstArg<ByteArray?>()?.toAddress(42.toShort()) }
        every { connection.acquireReviewedSora2MutationTransport() } returns lease
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>(); requests.add(request)
            val callback = thirdArg<SocketService.ResponseListener<RpcResponse>>()
            try {
                val failure = nodeError(request)
                callback.onNext(RpcResponse("2.0", if (failure == null) answer(request) else null, request.id, failure))
            }
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
    @Test fun `token accounts with no storage are zero in both balance projections`() = runTest {
        // What the node answers for an account that never held the token: the key, with null.
        answer = { request -> listOf(mapOf("block" to block, "changes" to (request.params[0] as List<*>).map { listOf(it, null) })) }
        assertEquals(listOf(BigInteger.ZERO, BigInteger.ZERO), subject.fetchBalances(owner, listOf(xor, valId)))
        assertEquals(listOf(BigInteger.ZERO), subject.fetchTransferableBalances(owner, listOf(valId)))
    }
    @Test fun `a token key the node leaves out fails the read and is never zero`() = runTest {
        answer = { request ->
            val keys = request.params[0] as List<*>
            listOf(mapOf("block" to block, "changes" to listOf(listOf(keys[0], tokenBalance(large)))))
        }
        val error = runCatching { subject.fetchBalances(owner, listOf(xor, valId)) }.exceptionOrNull()
        assertEquals("SORA2_STORAGE_ANSWER_INCOMPLETE", (error as IllegalStateException).message)
        answer = { listOf(mapOf("block" to block, "changes" to emptyList<Any>())) }
        assertTrue(runCatching { subject.fetchBalances(owner, listOf(valId)) }.exceptionOrNull() is IllegalStateException)
    }
    @Test fun `a token key listed twice or one that was not asked for fails the read`() = runTest {
        answer = { request ->
            val key = (request.params[0] as List<*>).single()
            listOf(mapOf("block" to block, "changes" to listOf(listOf(key, null), listOf(key, tokenBalance(large)))))
        }
        assertTrue(runCatching { subject.fetchBalances(owner, listOf(valId)) }.exceptionOrNull() is IllegalStateException)
        answer = { request ->
            val key = (request.params[0] as List<*>).single()
            listOf(mapOf("block" to block, "changes" to listOf(listOf(key, tokenBalance(large)), listOf("0xother", null))))
        }
        assertTrue(runCatching { subject.fetchBalances(owner, listOf(valId)) }.exceptionOrNull() is IllegalStateException)
    }
    @Test fun `a stored token balance that does not decode fails the read and is never zero`() = runTest {
        answer = { request -> listOf(storageChanges((request.params[0] as List<*>).single() as String, "0x01")) }
        assertTrue(runCatching { subject.fetchBalances(owner, listOf(valId)) }.isFailure)
    }
    @Test fun `the same token asked for twice is answered twice from one key`() = runTest {
        answer = { request -> listOf(mapOf("block" to block, "changes" to (request.params[0] as List<*>).map { listOf(it, tokenBalance(BigInteger.TEN)) })) }
        assertEquals(listOf(BigInteger.TEN, BigInteger.TEN), subject.fetchBalances(owner, listOf(valId, valId)))
        assertEquals(1, (requests.single().params[0] as List<*>).size)
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
    @Test fun `the pages of a large token read are asked for together and answered in the order asked`() = runTest {
        val ids = (1..205).map { "0x" + it.toString(16).padStart(64, '0') }
        // The node holds every answer back until all three pages have been asked for: pages read
        // in turn would never get past the first.
        val held = mutableListOf<Pair<RuntimeRequest, SocketService.ResponseListener<RpcResponse>>>()
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>(); requests.add(request)
            synchronized(held) { held += request to thirdArg<SocketService.ResponseListener<RpcResponse>>() }
            cancelled
        }
        val read = async { subject.fetchBalances(owner, ids, block) }
        runCurrent()
        assertEquals(listOf(100, 100, 5), requests.map { (it.params[0] as List<*>).size })
        // Answered last page first, each key with the position of its asset id in the list
        // asked for as the balance (worked out from the ids, not from the order of the requests).
        val tokens = snapshot.metadata.module("Tokens").storage("Accounts")
        val position = ids.withIndex().associate { (index, id) ->
            tokens.storageKey(snapshot, publicKey.fromHex(), id.mapCodeToken()) to index
        }
        held.reversed().forEach { (request, listener) ->
            val keys = request.params[0] as List<*>
            listener.onNext(RpcResponse("2.0", listOf(mapOf("block" to block, "changes" to keys.map { listOf(it, tokenBalance(BigInteger.valueOf(position.getValue(it as String).toLong()))) })), request.id, null))
        }
        assertEquals(List(205) { BigInteger.valueOf(it.toLong()) }, read.await())
        assertTrue(requests.all { it.params[1] == block })
    }
    @Test fun `one page failing fails the whole token read`() = runTest {
        val ids = (1..150).map { "0x" + it.toString(16).padStart(64, '0') }
        answer = { request ->
            val keys = request.params[0] as List<*>
            if (keys.size == 50) error("page lost")
            listOf(mapOf("block" to block, "changes" to keys.map { listOf(it, tokenBalance(BigInteger.ONE)) }))
        }
        assertTrue(runCatching { subject.fetchBalances(owner, ids) }.isFailure)
    }
    @Test fun `empty token queries do not contact the node`() = runTest {
        assertTrue(subject.fetchBalances(owner, emptyList()).isEmpty()); assertTrue(subject.fetchTransferableBalances(owner, emptyList()).isEmpty())
        assertTrue(requests.isEmpty())
    }
    private val snapshot get() = TestRuntimeSnapshots.mainnet133
    private val accountKey get() = snapshot.metadata.module("System").storage("Account").storageKey(snapshot, publicKey.fromHex())
    private val controllerKey get() = snapshot.metadata.module("Staking").storage("Bonded").storageKey(snapshot, publicKey.fromHex())
    private val eraKey get() = snapshot.metadata.module("Staking").storage("ActiveEra").storageKey()
    private val referralKey get() = snapshot.metadata.module("Referrals").storage("ReferrerBalances").storageKey(snapshot, publicKey.fromHex())
    private val controller = "01".repeat(32)
    private val ledgerKey get() = snapshot.metadata.module("Staking").storage("Ledger").storageKey(snapshot, controller.fromHex())
    private fun xorAccount(free: BigInteger, frozen: BigInteger = BigInteger.ZERO) = "0x" + "00000000".repeat(4) + u128(free) + u128(BigInteger.ZERO) + u128(frozen) + u128(BigInteger.ZERO)
    /** The node's answer to a keyed read: every key that was asked for, with [stored]'s value or null. */
    private fun keyed(request: RuntimeRequest, stored: Map<String, String?>) =
        listOf(mapOf("block" to block, "changes" to (request.params[0] as List<*>).map { listOf(it, stored[it]) }))
    @Test fun `XOR account absent returns all zero balance components`() = runTest {
        answer = { keyed(it, emptyMap()) }
        val balance = subject.fetchXORBalances(owner)
        assertEquals(BigInteger.ZERO, balance.free); assertEquals(BigInteger.ZERO, balance.reserved)
        assertEquals(BigInteger.ZERO, balance.bonded); assertEquals(BigInteger.ZERO, balance.redeemable); assertEquals(BigInteger.ZERO, balance.unbonding)
        // The account, the staking controller, the active era and the referral bond are one read.
        val request = requests.single()
        assertEquals("state_queryStorageAt", request.method)
        assertEquals(listOf(accountKey, controllerKey, eraKey, referralKey), request.params[0])
    }
    @Test fun `a node error on the XOR read is not an account without XOR`() = runTest {
        answer = { null }
        nodeError = { RpcError(-32000, "Client error: state pruned") }
        val error = runCatching { subject.fetchXORBalances(owner) }.exceptionOrNull()
        assertTrue(error is RpcException); assertEquals("Client error: state pruned", error?.message)
    }
    @Test fun `an XOR answer that leaves a key out, or is not one answer, fails the read`() = runTest {
        answer = { request -> listOf(mapOf("block" to block, "changes" to (request.params[0] as List<*>).drop(1).map { listOf(it, null) })) }
        assertEquals("SORA2_STORAGE_ANSWER_INCOMPLETE", runCatching { subject.fetchXORBalances(owner) }.exceptionOrNull()?.message)
        answer = { request -> keyed(request, emptyMap()) + keyed(request, mapOf(accountKey to xorAccount(large))) }
        assertTrue(runCatching { subject.fetchXORBalances(owner) }.isFailure)
        answer = { emptyList<Any>() }
        assertTrue(runCatching { subject.fetchXORBalances(owner) }.isFailure)
    }
    @Test fun `a stored XOR account that does not decode fails the read and is never zero`() = runTest {
        answer = { keyed(it, mapOf(accountKey to "0x01", eraKey to "0x05000000")) }
        assertTrue(runCatching { subject.fetchXORBalances(owner) }.isFailure)
        assertTrue(runCatching { subject.fetchXORBalances(owner, forDisplay = true) }.isFailure)
        // The same answer with an account that decodes is read.
        answer = { keyed(it, mapOf(accountKey to xorAccount(large), eraKey to "0x05000000")) }
        assertEquals(large, subject.fetchXORBalances(owner).free)
    }
    @Test fun `a stored referral bond that does not decode fails a read for display and is never zero there`() = runTest {
        answer = { keyed(it, mapOf(accountKey to xorAccount(large), eraKey to "0x05000000", referralKey to "0x01")) }
        assertEquals("SORA2_REFERRAL_BOND_INVALID", runCatching { subject.fetchXORBalances(owner, forDisplay = true) }.exceptionOrNull()?.message)
        // The check before a transfer does not use the bond, and keeps its tolerance for it.
        val forTransfer = subject.fetchXORBalances(owner)
        assertEquals(large, forTransfer.free); assertEquals(BigInteger.ZERO, forTransfer.bonded)
        // The same account with a bond that decodes is read either way.
        answer = { keyed(it, mapOf(accountKey to xorAccount(large), eraKey to "0x05000000", referralKey to "0x" + u128(BigInteger.ONE))) }
        assertEquals(BigInteger.ONE, subject.fetchXORBalances(owner, forDisplay = true).bonded)
        assertEquals(BigInteger.ONE, subject.fetchXORBalances(owner).bonded)
    }
    @Test fun `an XOR answer without a block hash is not joined with a ledger read`() = runTest {
        val ledger = "0x" + "02".repeat(32) + "9101" + "a0" + "00" + "00"
        val stored = mapOf(accountKey to xorAccount(large), controllerKey to "0x$controller", eraKey to "0x05000000", ledgerKey to ledger)
        // Left out, sent as null, too short, without its prefix, not hex.
        val leftOut = Any()
        for (hash in listOf<Any?>(leftOut, null, "0x00", "ab".repeat(32), "0x" + "zz".repeat(32))) {
            requests.clear()
            answer = { request ->
                val keys = request.params[0] as List<*>
                val set = mutableMapOf<String, Any?>("changes" to keys.map { listOf(it, stored[it]) })
                if (hash !== leftOut) set["block"] = hash
                listOf(set)
            }
            assertEquals("SORA2_STORAGE_ANSWER_INCOMPLETE", runCatching { subject.fetchXORBalances(owner) }.exceptionOrNull()?.message)
            // The ledger was never asked for.
            assertEquals(1, requests.size)
        }
    }
    @Test fun `an account that exists is not read without the active era, staking or not`() = runTest {
        answer = { keyed(it, mapOf(accountKey to xorAccount(large))) }
        assertEquals("SORA2_ACTIVE_ERA_MISSING", runCatching { subject.fetchXORBalances(owner) }.exceptionOrNull()?.message)
    }
    @Test fun `a ledger answered from another block than the account fails the XOR read`() = runTest {
        val other = "0x" + "cd".repeat(32)
        val ledger = "0x" + "02".repeat(32) + "9101" + "a0" + "00" + "00"
        answer = { request ->
            val stored = mapOf(accountKey to xorAccount(large), controllerKey to "0x$controller", eraKey to "0x05000000", ledgerKey to ledger)
            val keys = request.params[0] as List<*>
            listOf(mapOf("block" to (if (keys == listOf(ledgerKey)) other else block), "changes" to keys.map { listOf(it, stored[it]) }))
        }
        assertEquals("SORA2_STORAGE_ANSWER_INCOMPLETE", runCatching { subject.fetchXORBalances(owner) }.exceptionOrNull()?.message)
    }
    @Test fun `token balances asked for at a block are not taken from another block`() = runTest {
        val other = "0x" + "cd".repeat(32)
        answer = { request -> listOf(mapOf("block" to other, "changes" to (request.params[0] as List<*>).map { listOf(it, tokenBalance(BigInteger.TEN)) })) }
        assertEquals("SORA2_STORAGE_ANSWER_INCOMPLETE", runCatching { subject.fetchBalances(owner, listOf(valId), block) }.exceptionOrNull()?.message)
        // Without a block named, the node's own choice of block is the answer.
        assertEquals(listOf(BigInteger.TEN), subject.fetchBalances(owner, listOf(valId)))
    }
    @Test fun `XOR balances include referral bond and runtime frozen floor`() = runTest {
        answer = { keyed(it, mapOf(accountKey to xorAccount(large, frozen = BigInteger.TEN), eraKey to "0x05000000", referralKey to "0x" + u128(BigInteger.valueOf(7)))) }
        val balance = subject.fetchXORBalances(owner)
        assertEquals(large, balance.free); assertEquals(BigInteger.TEN, balance.miscFrozen); assertEquals(BigInteger.TEN, balance.feeFrozen)
        assertEquals(BigInteger.valueOf(7), balance.bonded); assertEquals(BigInteger.ZERO, balance.unbonding)
        // No controller: no second request, and no subscription for the referral bond.
        assertEquals(listOf("state_queryStorageAt"), requests.map { it.method })
    }
    @Test fun `a controller without a ledger, or without an active era, fails the XOR read`() = runTest {
        answer = { keyed(it, mapOf(accountKey to xorAccount(large), controllerKey to "0x$controller", eraKey to "0x05000000")) }
        assertEquals("SORA2_STAKING_LEDGER_MISSING", runCatching { subject.fetchXORBalances(owner) }.exceptionOrNull()?.message)
        val ledger = "0x" + "02".repeat(32) + "9101" + "a0" + "00" + "00"
        answer = { keyed(it, mapOf(accountKey to xorAccount(large), controllerKey to "0x$controller", ledgerKey to ledger)) }
        assertEquals("SORA2_ACTIVE_ERA_MISSING", runCatching { subject.fetchXORBalances(owner) }.exceptionOrNull()?.message)
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
    @Test fun `block hash and header queries preserve explicit hash and genesis default`() = runTest {
        answer = { when(it.method) {
            "chain_getBlockHash" -> block
            "chain_getFinalizedHead" -> block
            "chain_getHeader" -> header()
            else -> error(it.method)
        } }
        assertEquals(block, subject.getBlockHash()); assertEquals(listOf(0), requests.last().params)
        assertEquals(block, subject.getBlockHash(16)); assertEquals(listOf(16), requests.last().params)
        assertEquals(block, subject.getFinalizedHead())
        assertEquals("0x10", subject.getChainHeader(block).number); assertEquals(listOf(block), requests.last().params)
    }
    @Test fun `the SORA 1 migration check asks its own RPC method for the Iroha address`() = runTest {
        answer = { when (it.method) {
            "irohaMigration_needsMigration" -> true
            else -> error(it.method)
        } }
        assertTrue(subject.needsMigration("alice@sora")); assertEquals(listOf("alice@sora"), requests.last().params)
        assertEquals("irohaMigration_needsMigration", requests.last().method)
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
        // The account is its own controller.
        val ownLedgerKey = snapshot.metadata.module("Staking").storage("Ledger").storageKey(snapshot, publicKey.fromHex())
        every { runtime.toSoraAddressOrNull(any()) } returns owner
        answer = { keyed(it, mapOf(
            accountKey to xorAccount(large, frozen = BigInteger.TEN),
            controllerKey to "0x$publicKey",
            // Stash, compact total=25, active=10, 3 unlocks: (3,4), (5,5), (7,6), no claimed rewards.
            ownLedgerKey to "0x" + publicKey + "64280c0c1014141c1800",
            eraKey to "0x05000000",
            referralKey to "0x" + u128(BigInteger.valueOf(7)),
        )) }
        val balance = subject.fetchXORBalances(owner)
        assertEquals(BigInteger.valueOf(7), balance.bonded)
        assertEquals(BigInteger.valueOf(8), balance.redeemable)
        assertEquals(BigInteger.valueOf(7), balance.unbonding)
        verify(exactly = 1) { runtime.toSoraAddressOrNull(match { it.contentEquals(publicKey.fromHex()) }) }
        // Two requests: the four keys, then the ledger at the block they were read at.
        assertEquals(2, requests.size)
        assertEquals(listOf(listOf(ownLedgerKey), block), requests[1].params)
    }


}
