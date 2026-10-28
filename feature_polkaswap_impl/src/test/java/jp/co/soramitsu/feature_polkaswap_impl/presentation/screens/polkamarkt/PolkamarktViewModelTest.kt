package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.*
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.net.UnknownHostException

/** Exercise the real coordinator and state transitions, with deterministic transport boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class PolkamarktViewModelTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val instant = InstantTaskExecutorRule()
    private val store = ViewModelStore()
    private val indexer = mockk<PolkaswapIndexerClient>()
    private val trader = mockk<PolkamarktTraderRepository>()
    private val users = mockk<UserRepository>()
    private val features = mockk<ProductionFeatureManager>()
    private val cache = mockk<PolkamarktCatalogCache>()
    private val accounts = MutableStateFlow(SoraAccount("owner", "Main"))
    private val pending = MutableStateFlow<List<PolkamarktPendingTransaction>>(emptyList())
    private val health = mockk<PiIndexerHealth>()
    private val checkpoint = "0x" + "ab".repeat(32)
    private val flags = ProductionFeatureState(true, true, true, true, true, true, true)
    private fun units(value: Long) = BigInteger.valueOf(value).multiply(BigInteger.TEN.pow(18))
    private fun <T> read(value: T, cached: Boolean = false) = PiQualifiedRead(value, health, cached)

    @Before fun setup() {
        every { users.flowCurSoraAccount() } returns accounts
        every { trader.observePending(any()) } returns pending
        coEvery { trader.recoverPendingTransactions(any()) } returns emptyList()
        coEvery { features.getState() } returns flags
        coEvery { indexer.getMarketsQualified() } returns read(listOf(market()))
        coEvery { indexer.getAccountPositionsQualified(any()) } returns read(listOf(position()))
        coEvery { indexer.getAccountTradesQualified(any()) } returns read(emptyList())
        coEvery { indexer.getPolkamarktSignalsQualified() } returns read(PolkamarktSignals("10", 1, 1, "5", emptyList(), emptyList(), null))
        coEvery { indexer.getMarketSnapshotsQualified(any()) } returns read(emptyList())
        coEvery { cache.store(any(), any()) } just Runs
        coEvery { cache.read(any()) } returns null
        coEvery { trader.getAuthoritativeMarketDetail(any(), any()) } returns PolkamarktMarketDetail(market(), claim(), checkpoint)
        coEvery { trader.getAuthoritativeClaimables(any(), any()) } returns PolkamarktClaimReview(setOf(42), mapOf(42L to claim()), checkpoint)
        coEvery { trader.quoteBuy(any(), any(), any(), any(), any()) } returns quote()
        coEvery { trader.quoteSell(any(), any(), any(), any(), any()) } returns quote().copy(side = PolkamarktTradeSide.SELL)
        coEvery { trader.executeTrade(any()) } returns PolkamarktMutationResult("tx", checkpoint, "FINALIZED")
        coEvery { trader.claimTraderPayout(any()) } returns PolkamarktMutationResult("claim", checkpoint, "FINALIZED")
        coEvery { trader.claimCreatorFees(any()) } returns PolkamarktMutationResult("fees", checkpoint, "FINALIZED")
    }
    @After fun close() { store.clear() }
    private fun TestScope.vm(): PolkamarktViewModel = PolkamarktViewModel(indexer, trader, users, features, cache).also {
        store.put("market", it); runCurrent()
    }
    private fun TestScope.selected(): PolkamarktViewModel = vm().also { it.selectMarket(market()); runCurrent() }
    private fun TestScope.quoted(): PolkamarktViewModel = selected().also { it.setAmount("1"); it.requestQuote(); runCurrent() }

    @Test fun `qualified refresh populates account state and persists the catalog`() = runTest {
        val vm = vm()
        assertFalse(vm.state.value.loading)
        assertEquals(listOf(market()), vm.state.value.markets)
        assertEquals(listOf(position()), vm.state.value.positions)
        assertEquals("owner", vm.state.value.accountAddress)
        assertTrue(vm.state.value.mutationsEnabled)
        assertNull(vm.state.value.errorCode)
        coVerify(exactly = 1) { cache.store(any(), any()) }
    }

    @Test fun `optional signals transport failure does not hide markets`() = runTest {
        coEvery { indexer.getPolkamarktSignalsQualified() } throws UnknownHostException("offline")
        val vm = vm()
        assertEquals(listOf(market()), vm.state.value.markets)
        assertNull(vm.state.value.signals)
        assertNull(vm.state.value.errorCode)
    }

    @Test fun `offline fallback never carries stale positions or claims`() = runTest {
        coEvery { indexer.getMarketsQualified() } throws UnknownHostException("offline")
        coEvery { cache.read(any()) } returns listOf(market())
        val vm = vm()
        assertTrue(vm.state.value.usingCachedData)
        assertEquals(listOf(market()), vm.state.value.markets)
        assertTrue(vm.state.value.positions.isEmpty())
        assertTrue(vm.state.value.trades.isEmpty())
        assertTrue(vm.state.value.authoritativeClaims.isEmpty())
    }

    @Test fun `checkpoint disagreement never uses an offline catalog`() = runTest {
        coEvery { indexer.getAccountPositionsQualified(any()) } returns PiQualifiedRead(listOf(position()), mockk(), false)
        val vm = vm()
        assertEquals("PI_INDEXER_POLKAMARKT_CHECKPOINT_CHANGED", vm.state.value.errorCode)
        assertTrue(vm.state.value.markets.isEmpty())
        coVerify(exactly = 0) { cache.read(any()) }
    }

    @Test fun `cache persistence error does not discard a qualified live result`() = runTest {
        coEvery { cache.store(any(), any()) } throws IllegalStateException("disk full")
        val vm = vm()
        assertEquals(listOf(market()), vm.state.value.markets)
        assertNull(vm.state.value.errorCode)
    }

    @Test fun `disabled markets never fetch a catalog`() = runTest {
        coEvery { features.getState() } returns flags.copy(polkamarktVisible = false)
        val vm = vm()
        assertEquals("POLKAMARKT_DISABLED", vm.state.value.errorCode)
        coVerify(exactly = 0) { indexer.getMarketsQualified() }
    }

    @Test fun `invalid filters and malformed input keep previous selections`() = runTest {
        val vm = vm()
        vm.filterStatus("all"); vm.filterStatus("invented")
        vm.filterCategory("all"); vm.filterCategory("invented")
        vm.toggleMineOnly()
        vm.setAmount("12.000000000000000001")
        for (bad in listOf("1..2", "1e5", "-1", "01", "1,2", "1.0000000000000000001", "9".repeat(129))) vm.setAmount(bad)
        vm.setSlippageBps(100); vm.setSlippageBps(-1); vm.setSlippageBps(100_000)
        assertEquals("all", vm.state.value.statusFilter)
        assertEquals("all", vm.state.value.categoryFilter)
        assertTrue(vm.state.value.mineOnly)
        assertEquals("12.000000000000000001", vm.state.value.amountInput)
        assertEquals(100, vm.state.value.slippageBps)
    }

    @Test fun `market details require runtime data and mark cached charts`() = runTest {
        coEvery { indexer.getMarketSnapshotsQualified(any()) } returns read(emptyList(), cached = true)
        val vm = selected()
        assertEquals(market(), vm.state.value.authoritativeMarket)
        assertEquals(claim(), vm.state.value.authoritativeClaimable)
        assertEquals(checkpoint, vm.state.value.authoritativeFinalizedBlockHash)
        assertTrue(vm.state.value.usingCachedData)
        assertFalse(vm.state.value.marketDetailLoading)
        vm.clearSelection()
        assertNull(vm.state.value.selectedMarket)
        assertNull(vm.state.value.authoritativeClaimable)
        assertFalse(vm.state.value.usingCachedData)
    }

    @Test fun `runtime detail failure never presents catalog prices as authoritative`() = runTest {
        coEvery { trader.getAuthoritativeMarketDetail(any(), any()) } throws IllegalStateException("stale")
        val vm = selected()
        assertNull(vm.state.value.authoritativeMarket)
        assertFalse(vm.state.value.marketDetailLoading)
        assertEquals("POLKAMARKT_RUNTIME_DETAIL_UNAVAILABLE", vm.state.value.errorCode)
    }

    @Test fun `chart failure leaves authoritative market available`() = runTest {
        coEvery { indexer.getMarketSnapshotsQualified(any()) } throws UnknownHostException()
        val vm = selected()
        assertEquals(market(), vm.state.value.authoritativeMarket)
        assertEquals("POLKAMARKT_CHART_UNAVAILABLE", vm.state.value.errorCode)
    }

    @Test fun `market with no identity never requests runtime information`() = runTest {
        val vm = vm(); vm.selectMarket(market().copy(marketId = null)); runCurrent()
        assertEquals("POLKAMARKT_MARKET_ID_MISSING", vm.state.value.errorCode)
        assertFalse(vm.state.value.marketDetailLoading)
        coVerify(exactly = 0) { trader.getAuthoritativeMarketDetail(any(), any()) }
    }

    @Test fun `clearing selection discards an in-flight detail response`() = runTest {
        val result = CompletableDeferred<PolkamarktMarketDetail>()
        coEvery { trader.getAuthoritativeMarketDetail(any(), any()) } coAnswers { result.await() }
        val vm = selected()
        vm.clearSelection()
        result.complete(PolkamarktMarketDetail(market(), claim(), checkpoint)); runCurrent()
        assertNull(vm.state.value.selectedMarket)
        assertNull(vm.state.value.authoritativeMarket)
    }

    @Test fun `quote debounce sends only the latest exact amount`() = runTest {
        val vm = selected()
        vm.setAmount("1"); advanceTimeBy(100)
        vm.setAmount("12.000000000000000001"); advanceUntilIdle()
        coVerify(exactly = 1) { trader.quoteBuy("owner", 42, PolkamarktOutcome.YES, BigInteger("12000000000000000001"), any()) }
        assertEquals(quote(), vm.state.value.quote)
        assertFalse(vm.state.value.quoteLoading)
    }

    @Test fun `sell quote follows side outcome and slippage choices`() = runTest {
        val vm = selected(); vm.setSide(PolkamarktTradeSide.SELL); vm.setOutcome(PolkamarktOutcome.NO)
        vm.setSlippageBps(100); vm.setAmount("2"); vm.requestQuote(); runCurrent()
        coVerify(exactly = 1) { trader.quoteSell("owner", 42, PolkamarktOutcome.NO, units(2), 100) }
        assertEquals(PolkamarktTradeSide.SELL, vm.state.value.quote?.side)
    }

    @Test fun `zero amount is rejected before any quote transport`() = runTest {
        val vm = selected(); vm.setAmount("0"); vm.requestQuote(); runCurrent()
        assertEquals("POLKAMARKT_INVALID_AMOUNT", vm.state.value.errorCode)
        assertNull(vm.state.value.quote)
        assertFalse(vm.state.value.quoteLoading)
        coVerify(exactly = 0) { trader.quoteBuy(any(), any(), any(), any(), any()) }
    }

    @Test fun `quote failure clears stale review state`() = runTest {
        coEvery { trader.quoteBuy(any(), any(), any(), any(), any()) } throws IllegalStateException("fee unavailable")
        val vm = quoted()
        assertEquals("fee unavailable", vm.state.value.errorCode)
        assertNull(vm.state.value.quote)
        assertFalse(vm.state.value.quoteLoading)
    }

    @Test fun `confirmation is consumed synchronously and cannot submit twice`() = runTest {
        val result = CompletableDeferred<PolkamarktMutationResult>()
        coEvery { trader.executeTrade(any()) } coAnswers { result.await() }
        val vm = quoted(); vm.confirmTrade(); vm.confirmTrade(); runCurrent()
        assertTrue(vm.state.value.mutationLoading)
        assertNull(vm.state.value.quote)
        coVerify(exactly = 1) { trader.executeTrade(quote()) }
        result.complete(PolkamarktMutationResult("tx", checkpoint, "FINALIZED")); runCurrent()
        assertEquals("", vm.state.value.amountInput)
        assertEquals("tx", vm.state.value.lastMutation?.localId)
        assertFalse(vm.state.value.mutationLoading)
    }

    @Test fun `submission error requires a new review and releases mutation guard`() = runTest {
        coEvery { trader.executeTrade(any()) } throws IllegalStateException("not submitted")
        val vm = quoted(); vm.confirmTrade(); runCurrent(); vm.confirmTrade(); runCurrent()
        assertEquals("not submitted", vm.state.value.errorCode)
        assertFalse(vm.state.value.mutationLoading)
        assertNull(vm.state.value.quote)
        coVerify(exactly = 1) { trader.executeTrade(any()) }
    }

    @Test fun `account change invalidates quote and pending consent`() = runTest {
        val vm = quoted()
        accounts.value = SoraAccount("second", "Other"); runCurrent()
        assertEquals("second", vm.state.value.accountAddress)
        assertEquals("", vm.state.value.amountInput)
        assertNull(vm.state.value.quote)
        assertNull(vm.state.value.pendingClaimConfirmation)
        coVerify { indexer.getAccountPositionsQualified("second") }
    }

    @Test fun `pending recovery removes quotes and blocks duplicate submission`() = runTest {
        val vm = quoted()
        pending.value = listOf(PolkamarktPendingTransaction("pending", checkpoint, "buy", "1", "PENDING", true)); runCurrent()
        assertTrue(vm.state.value.pendingRecoveryRequired)
        assertNull(vm.state.value.quote)
        vm.requestQuote(); vm.confirmTrade(); runCurrent()
        coVerify(exactly = 1) { trader.quoteBuy(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { trader.executeTrade(any()) }
        pending.value = emptyList(); runCurrent()
        assertFalse(vm.state.value.pendingRecoveryRequired)
        assertNull(vm.state.value.errorCode)
    }

    @Test fun `claim review requires explicit confirmation and supports cancellation`() = runTest {
        val vm = vm(); vm.reviewClaim(42); runCurrent(); vm.requestClaim(position(), false)
        assertEquals(claim(), vm.state.value.pendingClaimConfirmation?.claims?.get(42))
        coVerify(exactly = 0) { trader.claimTraderPayout(any()) }
        vm.dismissClaimConfirmation(); vm.confirmClaim(); runCurrent()
        coVerify(exactly = 0) { trader.claimTraderPayout(any()) }
    }

    @Test fun `confirmed payout authorization binds exact account checkpoint and amount`() = runTest {
        val vm = vm(); vm.reviewClaim(42); runCurrent(); vm.requestClaim(position(), false)
        vm.confirmClaim(); vm.confirmClaim(); runCurrent()
        coVerify(exactly = 1) { trader.claimTraderPayout(match { it.accountId == "owner" && it.marketIds == listOf(42L) && it.reviewedFinalizedBlockHash == checkpoint && it.reviewedClaims[42] == claim() }) }
        assertNull(vm.state.value.pendingClaimConfirmation)
        assertFalse(vm.state.value.mutationLoading)
    }

    @Test fun `selected creator fees use the creator authorization path`() = runTest {
        val vm = selected(); vm.requestSelectedClaim(true); vm.confirmClaim(); runCurrent()
        coVerify(exactly = 1) { trader.claimCreatorFees(match { it.creatorFees && it.marketIds == listOf(42L) }) }
        coVerify(exactly = 0) { trader.claimTraderPayout(any()) }
    }

    @Test fun `failed claim review cannot reuse earlier authorization`() = runTest {
        val vm = vm(); vm.reviewClaim(42); runCurrent(); vm.requestClaim(position(), false)
        coEvery { trader.getAuthoritativeClaimables(any(), any()) } throws UnknownHostException()
        vm.reviewClaim(42); runCurrent()
        assertTrue(vm.state.value.authoritativeClaims.isEmpty())
        assertNull(vm.state.value.pendingClaimConfirmation)
        assertFalse(vm.state.value.claimReviewLoading)
        assertEquals("POLKAMARKT_CLAIM_REVIEW_UNAVAILABLE", vm.state.value.errorCode)
    }
    @Test fun `batch payout reviews canonical unique positions and confirms all claims once`() = runTest {
        val positions = listOf(position().copy(id = "p43", marketId = 43), position(), position().copy(id = "p42duplicate"))
        val claims = mapOf(42L to claim(), 43L to claim().copy(marketId = 43))
        coEvery { indexer.getAccountPositionsQualified(any()) } returns read(positions)
        coEvery { trader.getAuthoritativeClaimables("owner", listOf(42, 43)) } returns PolkamarktClaimReview(setOf(42, 43), claims, checkpoint)
        coEvery { trader.claimTraderPayouts(any()) } returns PolkamarktMutationResult("batch", checkpoint, "FINALIZED")
        val vm = vm(); vm.search("fixture"); assertEquals("fixture", vm.state.value.search)
        vm.reviewBatchClaims(); runCurrent(); vm.requestAllTraderPayouts()
        assertEquals(listOf(42L, 43L), vm.state.value.pendingClaimConfirmation!!.marketIds)
        coVerify(exactly = 0) { trader.claimTraderPayouts(any()) }
        vm.confirmClaim(); vm.confirmClaim(); runCurrent()
        coVerify(exactly = 1) { trader.claimTraderPayouts(match { it.marketIds == listOf(42L, 43L) && it.reviewedClaims == claims && !it.creatorFees }) }
        assertEquals("batch", vm.state.value.lastMutation?.localId)
        assertFalse(vm.state.value.mutationLoading)
    }

    @Test fun `batch payout excludes nonclaimable zero and unreviewed positions`() = runTest {
        val positions = (42L..45L).map { position().copy(id = "p$it", marketId = it) }
        val claims = mapOf(42L to claim(), 43L to claim().copy(marketId = 43, status = "Active"), 44L to claim().copy(marketId = 44, claimablePayout = BigInteger.ZERO))
        coEvery { indexer.getAccountPositionsQualified(any()) } returns read(positions)
        coEvery { trader.getAuthoritativeClaimables(any(), any()) } returns PolkamarktClaimReview(setOf(42, 43, 44, 45), claims, checkpoint)
        val vm = vm(); vm.reviewBatchClaims(); runCurrent(); vm.requestAllTraderPayouts()
        assertNull(vm.state.value.pendingClaimConfirmation)
        assertEquals("POLKAMARKT_CLAIM_BATCH_INVALID", vm.state.value.errorCode)
        vm.requestClaim(positions.first(), false)
        vm.confirmClaim(); runCurrent(); coVerify(exactly = 1) { trader.claimTraderPayout(match { it.marketIds == listOf(42L) }) }
    }

    @Test fun `missing and stale positions never create claim consent`() = runTest {
        val vm = vm(); vm.requestSelectedClaim(false)
        assertEquals("POLKAMARKT_CLAIM_POSITION_STALE", vm.state.value.errorCode)
        vm.requestClaim(position().copy(marketId = 43), false)
        assertEquals("POLKAMARKT_CLAIM_POSITION_STALE", vm.state.value.errorCode)
        assertNull(vm.state.value.pendingClaimConfirmation)
        coVerify(exactly = 0) { trader.claimTraderPayout(any()) }
    }

    @Test fun `empty position batch does not ask runtime for claims`() = runTest {
        coEvery { indexer.getAccountPositionsQualified(any()) } returns read(emptyList())
        val vm = vm(); vm.reviewBatchClaims(); runCurrent()
        coVerify(exactly = 0) { trader.getAuthoritativeClaimables(any(), any()) }
        assertFalse(vm.state.value.claimReviewLoading)
    }

    @Test fun `claim transport failure consumes consent and releases busy guard`() = runTest {
        coEvery { trader.claimTraderPayout(any()) } throws IllegalStateException("FIXTURE_CLAIM_FAILED")
        val vm = vm(); vm.reviewClaim(42); runCurrent(); vm.requestClaim(position(), false); vm.confirmClaim(); runCurrent()
        assertEquals("FIXTURE_CLAIM_FAILED", vm.state.value.errorCode)
        assertNull(vm.state.value.pendingClaimConfirmation); assertFalse(vm.state.value.mutationLoading)
        vm.confirmClaim(); runCurrent(); coVerify(exactly = 1) { trader.claimTraderPayout(any()) }
    }

    @Test fun `account switch discards late claim review success`() = runTest {
        val delayed = CompletableDeferred<PolkamarktClaimReview>()
        coEvery { trader.getAuthoritativeClaimables(any(), any()) } coAnswers { delayed.await() }
        val vm = vm(); vm.reviewClaim(42); runCurrent(); vm.reviewClaim(42)
        accounts.value = SoraAccount("new-owner", "Other"); runCurrent()
        delayed.complete(PolkamarktClaimReview(setOf(42), mapOf(42L to claim()), checkpoint)); runCurrent()
        assertTrue(vm.state.value.authoritativeClaims.isEmpty())
        assertEquals("new-owner", vm.state.value.accountAddress)
        coVerify(exactly = 1) { trader.getAuthoritativeClaimables(any(), any()) }
    }

    @Test fun `disabled mutation feature cannot authorize reviewed claims`() = runTest {
        coEvery { features.getState() } returns flags.copy(polkamarktMutationsAvailable = false)
        val vm = vm(); vm.reviewClaim(42); runCurrent(); vm.requestClaim(position(), false)
        assertNull(vm.state.value.pendingClaimConfirmation)
        coVerify(exactly = 0) { trader.claimTraderPayout(any()) }
    }
    private fun claim() = PolkamarktClaimableDto(42, "owner", "Resolved", "Yes", units(4), units(2), units(3), units(4), units(4), units(1), true)
    private fun position() = PolkamarktAccountPosition("p42", "owner", 42, "Yes", "4", "4", "2", "3", "3", "4", "1", "0", "4", true, "Resolved", "2026-10-01", market())
    private fun quote() = PolkamarktTradeQuote("q", "nonce", "owner", market(), PolkamarktTradeSide.BUY, PolkamarktOutcome.YES,
        units(1), units(3), units(2), units(1), units(1), checkpoint, "DynamicParimutuel", BigInteger.valueOf(100), units(20), units(10))
    private fun market() = PolkamarktMarket(
        id = "42", marketId = 42, title = "Test market", category = "science", tags = "space", description = "Moon landing prediction",
        rulesUri = null, resolutionSource = "Public observations", closeBlock = 100, status = "Active", mechanism = "DynamicParimutuel", creator = "owner",
        collateralAsset = "KUSD", creatorFees = "1", liquidityUsd = "100", volumeUsd = "500", chartProbability = 0.6, chartPriceYes = 0.6, chartPriceNo = 0.4,
        virtualDepth = "100", dpmCollateral = "10", realYesShares = "4", realNoShares = "2", marginalYesPriceBps = 6000, marginalNoPriceBps = 4000,
        impliedYesProbabilityBps = 6000, impliedNoProbabilityBps = 4000, collateral = "10", yesShares = "4", noShares = "2", resolutionOutcome = null,
        resolutionEvidenceUri = null, governanceUrl = null, updatedAtBlock = 90, timestamp = 1_800_000_000_000, displayYesProbabilityBps = 6000, displayNoProbabilityBps = 4000)
}
