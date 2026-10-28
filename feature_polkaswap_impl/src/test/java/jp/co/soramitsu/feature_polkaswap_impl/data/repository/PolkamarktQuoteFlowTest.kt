package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.*
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.*
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
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

/** Real quote orchestration; all network, persistence, and fee transport boundaries are isolated. */
class PolkamarktQuoteFlowTest {
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
    @Before fun setup() {
        every { database.walletIdentityDao() } returns dao
        coEvery { dao.countPendingTransactionsRequiringChainRecovery() } returns 0
        every { dao.observePendingTransactions("owner") } returns flowOf(emptyList())
        coEvery { users.getCurSoraAccount() } returns wallet
        coEvery { flags.getState() } returns ProductionFeatureState(false, false, true, false, false, false, false)
        coEvery { indexer.getMarket("42") } returns market()
        coEvery { calls.getFinalizedHead() } returns block
        coEvery { calls.polkamarktAuthoritativeMarket(42, block) } returns FinalizedRuntimeValue(block, authoritative())
        coEvery { calls.polkamarktMarketState(42, block) } returns FinalizedRuntimeValue(block, state())
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, claim())
        coEvery { calls.polkamarktQuoteBuy(42, "Yes", amount, block) } returns FinalizedRuntimeValue(block, buyDto())
        coEvery { calls.polkamarktQuoteSell(42, "No", amount, block) } returns FinalizedRuntimeValue(block, sellDto())
        coEvery { calls.fetchBalances("owner", any(), block) } returns listOf(n(10000), n(20000))
        coEvery { extrinsics.calcFee("owner", false, any()) } returns n(7)
        coEvery { cache.store(any(), any()) } just Runs
    }
    private suspend fun buy() = repo.quoteBuy("owner", 42, PolkamarktOutcome.YES, amount, 100)
    private suspend fun sell() = repo.quoteSell("owner", 42, PolkamarktOutcome.NO, amount, 100)
    private suspend fun fails(code: String, call: suspend () -> Any?) {
        try { call(); fail("Expected $code") } catch (error: IllegalStateException) { assertEquals(code, error.message) }
    }
    @Test fun `buy review binds exact runtime output minimum fees balances and account`() = runTest {
        val q = buy()
        assertEquals("owner", q.accountId); assertEquals(PolkamarktTradeSide.BUY, q.side); assertEquals(PolkamarktOutcome.YES, q.outcome)
        assertEquals(amount, q.amountIn); assertEquals(n(2222), q.outputAmount); assertEquals(n(2199), q.minimumOutput)
        assertEquals(n(5), q.marketFee); assertEquals(n(7), q.xorNetworkFee)
        assertEquals(n(10000), q.kusdBalance); assertEquals(n(20000), q.xorBalance)
        assertEquals("Open", q.market.status); assertEquals("DynamicParimutuel", q.authoritativeMechanism)
        assertEquals(n(500), q.authoritativeCloseBlock); assertEquals(block, q.observedFinalizedBlock)
        assertTrue(q.identity.isNotBlank()); assertTrue(q.confirmationNonce.isNotBlank())
        coVerify { calls.fetchBalances("owner", listOf(SubstrateOptionsProvider.kusdAssetId, SubstrateOptionsProvider.feeAssetId), block) }
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `sell review uses the chosen outcome share balance and exact collateral minimum`() = runTest {
        val q = sell()
        assertEquals(PolkamarktTradeSide.SELL, q.side); assertEquals(PolkamarktOutcome.NO, q.outcome)
        assertEquals(amount, q.amountIn); assertEquals(n(555), q.outputAmount); assertEquals(n(549), q.minimumOutput)
        assertEquals(n(1500), q.shareBalance); assertEquals(n(1), q.marketFee)
        coVerify(exactly = 0) { credentials.retrieveKeyPair(any()) }
    }
    @Test fun `each independent review receives a distinct confirmation identity`() = runTest {
        val first = buy(); val second = buy()
        assertNotEquals(first.identity, second.identity); assertNotEquals(first.confirmationNonce, second.confirmationNonce)
        assertEquals(first.outputAmount, second.outputAmount)
    }
    @Test fun `sell cannot borrow shares from the other outcome`() = runTest {
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, claim().copy(noShares = n(999)))
        fails("POLKAMARKT_INSUFFICIENT_SHARES") { sell() }
        coVerify(exactly = 0) { calls.polkamarktQuoteSell(any(), any(), any(), any()) }
    }
    @Test fun `missing position cannot become available shares`() = runTest {
        coEvery { calls.polkamarktClaimable("owner", 42, block) } returns FinalizedRuntimeValue(block, null)
        fails("POLKAMARKT_INSUFFICIENT_SHARES") { sell() }
    }
    @Test fun `runtime status overrides an open catalog and blocks closed markets`() = runTest {
        coEvery { indexer.getMarket("42") } returns market().copy(status = "Open")
        coEvery { calls.polkamarktAuthoritativeMarket(42, block) } returns FinalizedRuntimeValue(block, authoritative().copy(status = "Closed"))
        fails("POLKAMARKT_MARKET_CLOSED") { buy() }
        fails("POLKAMARKT_MARKET_CLOSED") { sell() }
    }
    @Test fun `quote and state must come from the exact finalized checkpoint`() = runTest {
        coEvery { calls.polkamarktMarketState(42, block) } returns FinalizedRuntimeValue("0x" + "cd".repeat(32), state())
        fails("POLKAMARKT_MARKET_BLOCK_MISMATCH") { buy() }
        fails("POLKAMARKT_MARKET_BLOCK_MISMATCH") { sell() }
    }
    @Test fun `quote from another block is rejected for both sides`() = runTest {
        coEvery { calls.polkamarktQuoteBuy(any(), any(), any(), any()) } returns FinalizedRuntimeValue("0x" + "cd".repeat(32), buyDto())
        coEvery { calls.polkamarktQuoteSell(any(), any(), any(), any()) } returns FinalizedRuntimeValue("0x" + "cd".repeat(32), sellDto())
        fails("POLKAMARKT_QUOTE_BLOCK_MISMATCH") { buy() }; fails("POLKAMARKT_QUOTE_BLOCK_MISMATCH") { sell() }
    }
    @Test fun `unavailable quote cannot be synthesized from indexer prices`() = runTest {
        coEvery { calls.polkamarktQuoteBuy(any(), any(), any(), any()) } returns FinalizedRuntimeValue(block, null)
        coEvery { calls.polkamarktQuoteSell(any(), any(), any(), any()) } returns FinalizedRuntimeValue(block, null)
        fails("POLKAMARKT_QUOTE_UNAVAILABLE") { buy() }; fails("POLKAMARKT_QUOTE_UNAVAILABLE") { sell() }
    }
    @Test fun `buy rejects contradictory market outcome input output and fee`() = runTest {
        for ((dto, code) in listOf(buyDto().copy(marketId = 43) to "POLKAMARKT_QUOTE_MARKET_MISMATCH",
            buyDto().copy(outcome = "No") to "POLKAMARKT_QUOTE_OUTCOME_MISMATCH", buyDto().copy(collateralIn = n(999)) to "POLKAMARKT_QUOTE_AMOUNT_MISMATCH",
            buyDto().copy(sharesOut = n(0)) to "POLKAMARKT_QUOTE_EMPTY", buyDto().copy(feeAmount = n(-1)) to "POLKAMARKT_QUOTE_INVALID_FEE")) {
            coEvery { calls.polkamarktQuoteBuy(any(), any(), any(), any()) } returns FinalizedRuntimeValue(block, dto)
            fails(code) { buy() }
        }
    }
    @Test fun `sell rejects contradictory market outcome input output and fee`() = runTest {
        for ((dto, code) in listOf(sellDto().copy(marketId = 43) to "POLKAMARKT_QUOTE_MARKET_MISMATCH",
            sellDto().copy(outcome = "Yes") to "POLKAMARKT_QUOTE_OUTCOME_MISMATCH", sellDto().copy(sharesIn = n(999)) to "POLKAMARKT_QUOTE_AMOUNT_MISMATCH",
            sellDto().copy(collateralOut = n(0)) to "POLKAMARKT_QUOTE_EMPTY", sellDto().copy(feeAmount = n(-1)) to "POLKAMARKT_QUOTE_INVALID_FEE")) {
            coEvery { calls.polkamarktQuoteSell(any(), any(), any(), any()) } returns FinalizedRuntimeValue(block, dto)
            fails(code) { sell() }
        }
    }
    @Test fun `missing network fee prevents confirmation for either side`() = runTest {
        coEvery { extrinsics.calcFee(any(), any(), any()) } returns null
        fails("POLKAMARKT_FEE_UNAVAILABLE") { buy() }; fails("POLKAMARKT_FEE_UNAVAILABLE") { sell() }
    }
    @Test fun `partial balance response cannot imply sufficient funds`() = runTest {
        coEvery { calls.fetchBalances(any(), any(), any()) } returns listOf(n(10000))
        fails("POLKAMARKT_BALANCE_UNAVAILABLE") { buy() }; fails("POLKAMARKT_BALANCE_UNAVAILABLE") { sell() }
    }
    @Test fun `wallet change during quote IO cannot return the old wallet review`() = runTest {
        for (isBuy in listOf(true, false)) {
            coEvery { users.getCurSoraAccount() } returnsMany listOf(wallet, SoraAccount("other", "Other"))
            fails("POLKAMARKT_ACCOUNT_CHANGED") { if (isBuy) buy() else sell() }
        }
    }
    @Test fun `authoritative market identity must match the requested quote`() = runTest {
        coEvery { calls.polkamarktAuthoritativeMarket(42, block) } returns FinalizedRuntimeValue(block, authoritative().copy(marketId = 43))
        fails("POLKAMARKT_MARKET_STATE_MISSING") { buy() }; fails("POLKAMARKT_MARKET_STATE_MISSING") { sell() }
    }
    @Test fun `catalog lookup can use a qualified catalog but keeps runtime pricing`() = runTest {
        val catalog = PiQualifiedRead(listOf(market()), mockk<PiIndexerHealth>(), false)
        coEvery { indexer.getMarket("42") } returns null
        coEvery { indexer.getMarketsQualified() } returns catalog
        assertEquals(n(2222), buy().outputAmount)
        coVerify(exactly = 1) { cache.store(catalog, any()) }
    }
    @Test fun `catalog cache write failure cannot lose a qualified live quote`() = runTest {
        coEvery { indexer.getMarket("42") } returns null
        coEvery { indexer.getMarketsQualified() } returns PiQualifiedRead(listOf(market()), mockk<PiIndexerHealth>(), false)
        coEvery { cache.store(any(), any()) } throws IllegalStateException("disk unavailable")
        assertEquals(n(555), sell().outputAmount)
    }
    @Test fun `offline metadata fallback still obtains fresh runtime quote and fees`() = runTest {
        coEvery { indexer.getMarket("42") } throws UnknownHostException("offline")
        coEvery { cache.read(any()) } returns listOf(market())
        assertEquals(n(2222), buy().outputAmount)
        coVerify(exactly = 1) { calls.polkamarktQuoteBuy(42, "Yes", amount, block) }
    }
    @Test fun `catalog cancellation is propagated without using offline data`() = runTest {
        coEvery { indexer.getMarket("42") } throws CancellationException("closed")
        try { buy(); fail("Cancellation was swallowed") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { cache.read(any()) }
    }
    @Test fun `catalog identity mismatch fails before runtime quote requests`() = runTest {
        coEvery { indexer.getMarket("42") } returns market().copy(id = "43")
        fails("POLKAMARKT_MARKET_ID_MISMATCH") { buy() }
        coVerify(exactly = 0) { calls.getFinalizedHead() }
    }
    @Test fun `pending recovery failure blocks quotes before any fee or network read`() = runTest {
        coEvery { dao.countPendingTransactionsRequiringChainRecovery() } returns 1
        fails("POLKAMARKT_PENDING_RECOVERY_REQUIRED") { buy() }
        coVerify(exactly = 0) { calls.getFinalizedHead() }
        coVerify(exactly = 0) { extrinsics.calcFee(any(), any(), any()) }
    }
}
