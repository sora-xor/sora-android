package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.*
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.*
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.sora.substrate.runtime.QualifiedSora2MutationRuntime
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmissionUnknownException
import jp.co.soramitsu.sora.substrate.substrate.PreparedExtrinsicPreTransportException
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_account_api.domain.model.WalletMutationSnapshot
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigInteger
import java.net.UnknownHostException

/** Exercises real quote-to-journal orchestration with fake signing and transport boundaries. */
class PolkamarktSubmissionFlowTest {
    private val indexer = mockk<PolkaswapIndexerClient>()
    private val calls = mockk<SubstrateCalls>()
    private val extrinsics = mockk<ExtrinsicManager>()
    private val runtime = mockk<Sora2RuntimeContract>(relaxed = true)
    private val credentials = mockk<CredentialsRepository>()
    private val users = mockk<UserRepository>()
    private val flags = mockk<ProductionFeatureManager>()
    private val database = mockk<AppDatabase>()
    private val dao = mockk<WalletIdentityDao>()
    private val cache = mockk<PolkamarktCatalogCache>()
    private val repo = PolkamarktTraderRepository(indexer, calls, extrinsics, runtime, credentials, users, flags, database, cache)
    private val wallet = SoraAccount("owner", "Wallet")
    private val block = "0x" + "ab".repeat(32)
    private fun n(value: Long) = BigInteger.valueOf(value)
    private val amount = n(1000)
    private fun authoritative() = PolkamarktAuthoritativeMarketDto(42, "Open", n(500), n(400))
    private fun state() = PolkamarktMarketStateDto(42, "DynamicParimutuel", n(100), n(6000), n(4000), n(10000), 5500, 4500, 6000, 4000)
    private fun claim() = PolkamarktClaimableDto(42, "owner", "Open", null, n(2000), n(1500), n(900), n(0), n(0), n(0), false)
    private fun buyDto() = PolkamarktBuyQuoteDto(42, "Yes", amount, n(5), n(995), n(2222))
    private fun sellDto() = PolkamarktSellQuoteDto(42, "No", amount, n(556), n(1), n(555))
    private fun market() = PolkamarktMarket("42", 42, "Catalog title", "science", "space", "Catalog description", null, "Public source", 100,
        "Resolved", "CachedMechanism", "owner", "KUSD", "0", "100", "500", 0.1, 0.1, 0.9, "0", "0", "0", "0", 1000, 9000, 1000, 9000,
        "0", "0", "0", null, null, null, 90, 1_800_000_000_000)
    private val signingRuntime = mockk<QualifiedSora2MutationRuntime>()
    private val keys = mockk<Sr25519Keypair>()
    private val privateBytes = ByteArray(64) { 1 }
    private val nonceBytes = ByteArray(32) { 2 }
    private val prepared = mockk<ExtrinsicManager.PreparedExtrinsic>()
    private val hash = "0x" + "12".repeat(32)
    private val pending = linkedMapOf<String, PendingNetworkTransactionLocal>()
    private val states = mutableListOf<String>()
    private var selected = wallet
    private var lockedAccount: SoraAccount? = wallet
    private var inWalletLock = false
    private var transportCalls = 0
    private var beforeTransport: suspend () -> Unit = {}
    private var afterTransport: suspend () -> ExtrinsicSubmitStatus = { ExtrinsicSubmitStatus(true, hash, block) }
    private val signedCalls = mutableListOf<Pair<String, Map<String, Any?>>>()
    private val builder = mockk<ExtrinsicBuilder>()
    private val feeBuilder = mockk<ExtrinsicBuilder>()
    private val feeCalls = mutableListOf<Pair<String, Map<String, Any?>>>()
    @Before fun setup() {
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.countPendingTransactionsRequiringChainRecovery() } returns 0
        every { dao.observePendingTransactions("owner") } answers {
            flowOf(pending.values.filter { it.state !in setOf("FINALIZED", "REJECTED") })
        }
        coEvery { users.getCurSoraAccount() } answers { check(!inWalletLock) { "Non-reentrant user repository lock" }; selected }
        coEvery { flags.getState() } returns ProductionFeatureState(false, false, true, true, false, false, false)
        coEvery { indexer.getMarket("42") } returns market()
        coEvery { calls.getFinalizedHead() } returns block
        coEvery { calls.polkamarktAuthoritativeMarket(42, block) } returns FinalizedRuntimeValue(block, authoritative())
        coEvery { calls.polkamarktMarketState(42, block) } returns FinalizedRuntimeValue(block, state())
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, claim())
        coEvery { calls.polkamarktQuoteBuy(42, "Yes", amount, block) } returns FinalizedRuntimeValue(block, buyDto())
        coEvery { calls.polkamarktQuoteSell(42, "No", amount, block) } returns FinalizedRuntimeValue(block, sellDto())
        coEvery { calls.fetchBalances("owner", any(), block) } returns listOf(n(10000), n(20000))
        coEvery { extrinsics.calcFee("owner", false, any()) } coAnswers { thirdArg<suspend ExtrinsicBuilder.() -> Unit>().invoke(feeBuilder); n(7) }
        coEvery { cache.store(any(), any()) } just Runs
        coEvery { users.withWalletMutationLocked<Any?>(any()) } coAnswers {
            inWalletLock = true
            try { firstArg<suspend (WalletMutationSnapshot) -> Any?>().invoke(WalletMutationSnapshot(lockedAccount, OnboardingState.REGISTRATION_FINISHED)) }
            finally { inWalletLock = false }
        }
        coEvery { runtime.requirePolkamarktMutationRuntimeContext() } returns signingRuntime
        coEvery { dao.hasActiveDeletionOperation() } returns false
        coEvery { dao.insertPendingTransaction(any()) } coAnswers {
            val row = firstArg<PendingNetworkTransactionLocal>(); pending[row.localId] = row; states += row.state
        }
        coEvery { dao.updatePendingTransaction(any(), any(), any(), any(), any()) } coAnswers {
            val id = firstArg<String>(); val row = pending.getValue(id)
            pending[id] = row.copy(transactionHash = secondArg(), state = thirdArg(), submissionIsAmbiguous = arg(3), updatedAt = arg(4))
            states += thirdArg<String>()
        }
        coEvery { credentials.retrieveKeyPair(wallet) } returns keys
        every { keys.privateKey } returns privateBytes
        every { keys.nonce } returns nonceBytes
        every { prepared.transactionHash } returns hash
        every { prepared.walletId } returns "owner"
        every { builder.call(any<String>(), any<String>(), any()) } answers {
            signedCalls += secondArg<String>() to thirdArg<Map<String, Any?>>()
            builder
        }
        every { feeBuilder.call(any<String>(), any<String>(), any()) } answers { feeCalls += secondArg<String>() to thirdArg<Map<String, Any?>>(); feeBuilder }
        coEvery { extrinsics.calcPolkamarktFee("owner", signingRuntime, false, any()) } coAnswers { arg<suspend ExtrinsicBuilder.() -> Unit>(3).invoke(feeBuilder); n(7) }
        coEvery { extrinsics.preparePolkamarktExtrinsic("owner", keys, signingRuntime, false, any()) } coAnswers {
            arg<suspend ExtrinsicBuilder.() -> Unit>(4).invoke(builder)
            prepared
        }
        coEvery { extrinsics.submitPreparedAndWait(prepared, SubstrateCalls.FINALIZED, any()) } coAnswers {
            beforeTransport()
            thirdArg<suspend () -> Unit>().invoke()
            transportCalls++
            afterTransport()
        }
    }
    private suspend fun buy() = repo.quoteBuy("owner", 42, PolkamarktOutcome.YES, amount, 100)
    private suspend fun sell() = repo.quoteSell("owner", 42, PolkamarktOutcome.NO, amount, 100)
    private suspend fun fails(code: String, call: suspend () -> Any?) {
        try { call(); fail("Expected $code") } catch (error: IllegalStateException) { assertEquals(code, error.message) }
    }
    @Test fun `confirmed buy stages and arms exact identity before one transport`() = runTest {
        val quote = buy()
        afterTransport = {
            assertEquals(listOf("SIGNED", "SUBMITTED"), states)
            val row = pending.values.single()
            assertEquals("owner", row.walletId); assertEquals(hash, row.transactionHash)
            assertEquals(amount.toString(), row.amount); assertEquals("Yes", row.recipient)
            assertTrue(row.submissionIsAmbiguous)
            ExtrinsicSubmitStatus(true, hash, block)
        }
        val result = repo.executeTrade(quote)
        assertEquals("FINALIZED", result.state); assertEquals(hash, result.transactionHash)
        assertEquals(listOf("SIGNED", "SUBMITTED", "FINALIZED"), states); assertEquals(1, transportCalls)
        assertEquals("buy", signedCalls.single().first)
        assertEquals(quote.minimumOutput, signedCalls.single().second["min_shares_out"])
        assertTrue(feeCalls.isNotEmpty()); feeCalls.forEach { (method, args) ->
            assertEquals(signedCalls.single().first, method)
            assertEquals(signedCalls.single().second - "outcome" - "min_shares_out" - "min_collateral_out", args - "outcome" - "min_shares_out" - "min_collateral_out")
            assertEquals((signedCalls.single().second["outcome"] as jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum.Entry<*>).name, (args["outcome"] as jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum.Entry<*>).name)
        }
        assertEquals(quote.minimumOutput, feeCalls.last().second["min_shares_out"])
        assertTrue(privateBytes.all { it == 0.toByte() }); assertTrue(nonceBytes.all { it == 0.toByte() })
    }
    @Test fun `confirmed sell signs exact shares and original minimum`() = runTest {
        val quote = sell(); assertEquals("FINALIZED", repo.executeTrade(quote).state)
        assertEquals("sell", signedCalls.single().first); assertEquals(amount, signedCalls.single().second["shares_in"])
        assertEquals(quote.minimumOutput, signedCalls.single().second["min_collateral_out"])
        assertEquals("No", pending.values.single().recipient)
        assertTrue(feeCalls.isNotEmpty()); feeCalls.forEach { (method, args) ->
            assertEquals(signedCalls.single().first, method)
            assertEquals(signedCalls.single().second - "outcome" - "min_shares_out" - "min_collateral_out", args - "outcome" - "min_shares_out" - "min_collateral_out")
            assertEquals((signedCalls.single().second["outcome"] as jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum.Entry<*>).name, (args["outcome"] as jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum.Entry<*>).name)
        }
        assertEquals(quote.minimumOutput, feeCalls.last().second["min_collateral_out"])
    }
    @Test fun `same confirmation cannot be executed twice`() = runTest {
        val quote = buy(); repo.executeTrade(quote)
        fails("POLKAMARKT_QUOTE_ALREADY_USED") { repo.executeTrade(quote) }
        assertEquals(1, transportCalls)
    }
    @Test fun `edited confirmation cannot reach credentials or signing`() = runTest {
        val quote = buy()
        fails("POLKAMARKT_QUOTE_IDENTITY_MISMATCH") { repo.executeTrade(quote.copy(minimumOutput = n(1))) }
        fails("POLKAMARKT_QUOTE_IDENTITY_MISMATCH") { repo.executeTrade(quote.copy(amountIn = n(1))) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }; assertTrue(pending.isEmpty())
    }
    @Test fun `account switch before admission rejects confirmed trade`() = runTest {
        val quote = buy(); selected = SoraAccount("other", "Other")
        fails("POLKAMARKT_ACCOUNT_CHANGED") { repo.executeTrade(quote) }
        coVerify(exactly = 0) { users.withWalletMutationLocked<Any?>(any()) }
    }
    @Test fun `account disappearing inside locked snapshot prevents signing`() = runTest {
        val quote = buy(); lockedAccount = null
        fails("POLKAMARKT_ACCOUNT_CHANGED") { repo.executeTrade(quote) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }; assertEquals(0, transportCalls)
    }
    @Test fun `quote slipping below confirmed minimum cannot reach signing`() = runTest {
        val quote = buy()
        coEvery { calls.polkamarktQuoteBuy(any(), any(), any(), any()) } returns FinalizedRuntimeValue(block, buyDto().copy(sharesOut = n(1)))
        fails("POLKAMARKT_STALE_QUOTE") { repo.executeTrade(quote) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `network fee increase requires a new confirmation`() = runTest {
        val quote = buy(); coEvery { extrinsics.calcPolkamarktFee(any(), any(), any(), any()) } returns n(8)
        fails("POLKAMARKT_FEE_INCREASED") { repo.executeTrade(quote) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `missing final network fee cannot become zero`() = runTest {
        val quote = buy(); coEvery { extrinsics.calcPolkamarktFee(any(), any(), any(), any()) } returns null
        fails("POLKAMARKT_FEE_UNAVAILABLE") { repo.executeTrade(quote) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `active account deletion blocks key access`() = runTest {
        val quote = buy(); coEvery { dao.hasActiveDeletionOperation() } returns true
        fails("WALLET_DELETION_ACTIVE") { repo.executeTrade(quote) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }; assertTrue(pending.isEmpty())
    }
    @Test fun `signing failure still wipes the retrieved private material`() = runTest {
        val quote = buy()
        coEvery { extrinsics.preparePolkamarktExtrinsic(any(), any(), any(), any(), any()) } throws IllegalStateException("signing failed")
        fails("signing failed") { repo.executeTrade(quote) }
        assertTrue(privateBytes.all { it == 0.toByte() }); assertTrue(nonceBytes.all { it == 0.toByte() })
        assertTrue(pending.isEmpty()); assertEquals(0, transportCalls)
    }
    @Test fun `failure after durable staging but before transport is terminal`() = runTest {
        val quote = buy(); beforeTransport = { throw PreparedExtrinsicPreTransportException(IllegalStateException("changed")) }
        try { repo.executeTrade(quote); fail("Pre-transport error") } catch (_: PreparedExtrinsicPreTransportException) { }
        assertEquals(listOf("SIGNED", "REJECTED"), states)
        assertFalse(pending.values.single().submissionIsAmbiguous); assertEquals(0, transportCalls)
    }
    @Test fun `wallet switch after signing cannot open transport`() = runTest {
        val quote = buy(); beforeTransport = { selected = SoraAccount("other", "Other") }
        fails("POLKAMARKT_ACCOUNT_CHANGED") { repo.executeTrade(quote) }
        assertEquals(listOf("SIGNED", "REJECTED"), states); assertEquals(0, transportCalls)
    }
    @Test fun `lost transport response remains unknown and non retryable`() = runTest {
        val quote = buy(); afterTransport = { throw ExtrinsicSubmissionUnknownException(hash, IllegalStateException("lost")) }
        try { repo.executeTrade(quote); fail("Unknown submission") } catch (_: ExtrinsicSubmissionUnknownException) { }
        assertEquals(listOf("SIGNED", "SUBMITTED", "UNKNOWN"), states)
        assertTrue(pending.values.single().submissionIsAmbiguous); assertEquals(1, transportCalls)
    }
    @Test fun `cancelling finality wait preserves unknown journal state`() = runTest {
        val quote = sell(); afterTransport = { throw CancellationException("closed") }
        try { repo.executeTrade(quote); fail("Cancellation") } catch (_: CancellationException) { }
        assertEquals("UNKNOWN", pending.values.single().state); assertTrue(pending.values.single().submissionIsAmbiguous)
    }
    @Test fun `finalized runtime failure is rejected instead of successful`() = runTest {
        val quote = buy(); afterTransport = { ExtrinsicSubmitStatus(false, hash, block) }
        assertEquals("REJECTED", repo.executeTrade(quote).state); assertFalse(pending.values.single().submissionIsAmbiguous)
    }
    @Test fun `response without finalized block remains unknown`() = runTest {
        val quote = buy(); afterTransport = { ExtrinsicSubmitStatus(true, hash, null) }
        assertEquals("UNKNOWN", repo.executeTrade(quote).state); assertTrue(pending.values.single().submissionIsAmbiguous)
    }
    @Test fun `mismatched returned transaction hash never overwrites signed identity`() = runTest {
        val quote = buy(); afterTransport = { ExtrinsicSubmitStatus(true, "0x" + "34".repeat(32), block) }
        try { repo.executeTrade(quote); fail("Mismatched transaction") } catch (error: ExtrinsicSubmissionUnknownException) { assertEquals(hash, error.transactionHash) }
        assertEquals(hash, pending.values.single().transactionHash); assertEquals("UNKNOWN", pending.values.single().state)
    }
    @Test fun `terminal journal write failure cannot rewrite authoritative chain success`() = runTest {
        val quote = buy()
        coEvery { dao.updatePendingTransaction(any(), any(), "FINALIZED", any(), any()) } throws IllegalStateException("disk full")
        assertEquals("FINALIZED", repo.executeTrade(quote).state); assertEquals(1, transportCalls)
    }
    private fun settled(id: Long = 42, creator: Boolean = false) = claim().copy(marketId = id, status = "Resolved", resolutionOutcome = "Yes", traderPayout = n(40), claimablePayout = n(40), creatorFees = if (creator) n(3) else n(0), isCreator = creator)
    private fun authorization(ids: List<Long> = listOf(42), creator: Boolean = false): PolkamarktClaimAuthorization {
        val claims = ids.associateWith { settled(it, creator) }
        claims.forEach { (id, claim) -> coEvery { calls.polkamarktClaimable("owner", id, block) } returns FinalizedRuntimeValue(block, claim) }
        coEvery { calls.fetchBalances("owner", listOf(SubstrateOptionsProvider.feeAssetId), block) } returns listOf(n(1000))
        return PolkamarktClaimAuthorization("owner", ids, creator, block, claims)
    }
    @Test fun `single trader payout revalidates claims and uses dedicated call`() = runTest {
        val review = authorization(); assertEquals("FINALIZED", repo.claimTraderPayout(review).state)
        assertEquals("claim_market", signedCalls.single().first); assertEquals(n(42), signedCalls.single().second["market_id"])
        assertEquals("claims", pending.values.single().recipient); assertEquals("0", pending.values.single().amount)
        assertEquals(listOf(signedCalls.single(), signedCalls.single()), feeCalls)
        coVerify(exactly = 2) { calls.polkamarktClaimable("owner", 42, block) }
    }
    @Test fun `batch trader payout preserves reviewed market order`() = runTest {
        val review = authorization(listOf(42, 43)); assertEquals("FINALIZED", repo.claimTraderPayouts(review).state)
        assertEquals("claim_markets", signedCalls.single().first)
        assertEquals(listOf(n(42), n(43)), signedCalls.single().second["market_ids"])
        assertEquals(listOf(signedCalls.single(), signedCalls.single()), feeCalls)
    }
    @Test fun `creator fees cannot use the trader payout call`() = runTest {
        val review = authorization(creator = true)
        fails("POLKAMARKT_CLAIM_AUTHORIZATION_MISMATCH") { repo.claimTraderPayout(review) }
        assertEquals("FINALIZED", repo.claimCreatorFees(review).state)
        assertEquals("claim_creator_fees", signedCalls.single().first)
        assertEquals(listOf(signedCalls.single(), signedCalls.single()), feeCalls)
    }
    @Test fun `claim changed since review requires another confirmation`() = runTest {
        val review = authorization()
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, settled().copy(claimablePayout = n(41)))
        fails("POLKAMARKT_CLAIM_CHANGED") { repo.claimTraderPayout(review) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }; assertEquals(0, transportCalls)
    }
    @Test fun `claim from a different checkpoint cannot authorize signing`() = runTest {
        val review = authorization()
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue("0x" + "cd".repeat(32), settled())
        fails("POLKAMARKT_CLAIM_BLOCK_MISMATCH") { repo.claimTraderPayout(review) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `claim fee balance must still cover the latest exact fee`() = runTest {
        val review = authorization()
        coEvery { calls.fetchBalances("owner", listOf(SubstrateOptionsProvider.feeAssetId), block) } returnsMany listOf(listOf(n(1000)), listOf(n(1)))
        fails("POLKAMARKT_INSUFFICIENT_XOR") { repo.claimTraderPayout(review) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `claim cancellation after transport remains recoverable`() = runTest {
        val review = authorization(); afterTransport = { throw CancellationException("closed") }
        try { repo.claimTraderPayout(review); fail("Cancellation") } catch (_: CancellationException) { }
        assertEquals("UNKNOWN", pending.values.single().state); assertTrue(pending.values.single().submissionIsAmbiguous)
        assertTrue(privateBytes.all { it == 0.toByte() })
    }
}
