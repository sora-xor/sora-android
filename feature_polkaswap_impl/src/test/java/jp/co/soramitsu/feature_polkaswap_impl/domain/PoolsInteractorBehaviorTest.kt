package jp.co.soramitsu.feature_polkaswap_impl.domain

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common_wallet.domain.model.*
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.*
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PoolsInteractorBehaviorTest {
    private val credentials = mockk<CredentialsRepository>()
    private val users = mockk<UserRepository>()
    private val history = mockk<TransactionHistoryRepository>(relaxed = true)
    private val repo = mockk<PolkaswapRepository>()
    private val subscription = mockk<PolkaswapSubscriptionRepository>()
    private val extrinsic = mockk<PolkaswapExtrinsicRepository>()
    private val assets = mockk<AssetsRepository>()
    private val builder = mockk<TransactionBuilder>()
    private val keys = mockk<Sr25519Keypair>()
    private val tx = mockk<Transaction.Liquidity>()
    private val wallet = SoraAccount("owner", "Main")
    private val selection = MutableStateFlow(wallet)
    private val fee = BigDecimal("0.007")
    private val amount1 = BigDecimal("2.000000000000000001")
    private val amount2 = BigDecimal("4.000000000000000002")
    private fun token(id: String, precision: Int = 18) = Token(id, id.uppercase(), id.uppercase(), precision, true, null, 1.0, 0.0, "$")
    private val xor = token("xor")
    private val valToken = token("val")
    private val ids = "xor" to "val"
    private val subject = PoolsInteractorImpl(credentials, users, history, repo, subscription, extrinsic, assets, builder)
    private fun pool(target: String = "val") = CommonUserPoolData(
        BasicPoolData(xor, token(target), BigDecimal("100"), BigDecimal("200"), BigDecimal("1000"), "reserve", 12.0),
        UserPoolData(amount1, amount2, 2.0, BigDecimal("10"), false, 0))
    @Before fun setup() {
        coEvery { users.getCurSoraAccount() } answers { selection.value }
        every { users.flowCurSoraAccount() } returns selection
        coEvery { credentials.retrieveKeyPair(wallet) } returns keys
        every { builder.buildLiquidity(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns tx
        coEvery { extrinsic.calcAddLiquidityNetworkFee(any(), any(), any(), any(), any(), any(), any(), any()) } returns fee
        coEvery { extrinsic.calcRemoveLiquidityNetworkFee(any(), any(), any()) } returns fee
    }
    private suspend fun details(amount: BigDecimal = amount1, enabled: Boolean = true, presented: Boolean = true, desired: WithDesired = WithDesired.INPUT) =
        subject.calcLiquidityDetails(xor, valToken, BigDecimal("100"), BigDecimal("200"), BigDecimal.ZERO, amount, amount2, desired, 0.5, enabled, presented)

    @Test fun `favorite and ordering writes are scoped to the selected wallet`() = runTest {
        coEvery { repo.poolFavoriteOn(any(), any()) } just Runs
        coEvery { repo.poolFavoriteOff(any(), any()) } just Runs
        coEvery { repo.updatePoolPosition(any(), any()) } just Runs
        subject.poolFavoriteOn(ids)
        val other = SoraAccount("other", "Other"); selection.value = other
        subject.poolFavoriteOff(ids); subject.updatePoolPosition(mapOf(ids to 0))
        coVerify { repo.poolFavoriteOn(ids, wallet) }
        coVerify { repo.poolFavoriteOff(ids, other) }
        coVerify { repo.updatePoolPosition(mapOf(ids to 0), other) }
    }
    @Test fun `current account pool lookup matches both token identifiers`() = runTest {
        val target = pool(); coEvery { repo.getPoolsCacheOfAccount("owner") } returns listOf(pool("eth"), target)
        assertSame(target, subject.getPoolOfCurAccount(ids))
        assertNull(subject.getPoolOfCurAccount("val" to "xor"))
        assertEquals(2, subject.getPoolsCacheOfCurAccount().size)
    }
    @Test fun `basic pool and reward discovery preserve missing data`() = runTest {
        coEvery { repo.getBasicPool("xor", "val") } returns null
        coEvery { repo.getBasicPools() } returns listOf(pool().basic)
        coEvery { assets.getToken(SubstrateOptionsProvider.pswapAssetId) } returns token("pswap")
        assertNull(subject.getBasicPool(ids)); assertEquals(listOf(pool().basic), subject.getBasicPools())
        assertEquals("pswap", subject.getRewardToken().id)
    }
    @Test fun `missing reward metadata fails instead of inventing a token`() = runTest {
        coEvery { assets.getToken(any()) } returns null
        try { subject.getRewardToken(); fail("Missing reward must fail") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `account pool subscription stops publishing the previous wallet`() = runTest {
        val first = MutableStateFlow(listOf(pool()))
        val second = MutableStateFlow(listOf(pool("eth")))
        every { repo.subscribePools("owner") } returns first
        every { repo.subscribePools("other") } returns second
        val seen = mutableListOf<List<CommonUserPoolData>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribePoolsCacheOfCurAccount().toList(seen) }
        runCurrent(); selection.value = SoraAccount("other", "Other"); runCurrent()
        first.value = emptyList(); runCurrent()
        assertEquals(listOf(listOf(pool()), listOf(pool("eth"))), seen)
    }
    @Test fun `explicit account subscription does not follow a different selected wallet`() = runTest {
        every { repo.subscribePools("explicit") } returns flowOf(listOf(pool("eth")))
        assertEquals(listOf(pool("eth")), subject.subscribePoolsCacheOfAccount(SoraAccount("explicit", "Explicit")).first())
        verify(exactly = 0) { repo.subscribePools("owner") }
    }
    @Test fun `single pool subscription keeps the same pair when the selected account changes`() = runTest {
        every { repo.subscribePoolOfAccount("owner", "xor", "val") } returns flowOf(CommonPoolData(pool().basic, pool().user))
        every { repo.subscribePoolOfAccount("other", "xor", "val") } returns flowOf(null)
        val seen = mutableListOf<CommonPoolData?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribePoolCacheOfCurAccount("xor", "val").toList(seen) }
        runCurrent(); selection.value = SoraAccount("other", "Other"); runCurrent()
        assertEquals(listOf(CommonPoolData(pool().basic, pool().user), null), seen)
    }
    @Test fun `reserve subscription replaces the old account source`() = runTest {
        val reserve = LiquidityData(BigDecimal("100"), BigDecimal("200"))
        every { repo.subscribeLocalPoolReserves("owner", "xor", "val") } returns flowOf(reserve)
        every { repo.subscribeLocalPoolReserves("other", "xor", "val") } returns flowOf(null)
        val seen = mutableListOf<LiquidityData?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribeReservesCache("xor", "val").toList(seen) }
        runCurrent(); selection.value = SoraAccount("other", "Other"); runCurrent()
        assertEquals(listOf(reserve, null), seen)
    }
    @Test fun `pair availability and reserve lookup keep account precision and network flags`() = runTest {
        every { subscription.isPairEnabled("xor", "val", "owner") } returns flowOf(true, false)
        every { subscription.isPairPresentedInNetwork("xor", "val", "owner") } returns flowOf(false, true)
        val result = LiquidityData(BigDecimal("100"), BigDecimal("200"))
        coEvery { subscription.getRemotePoolReserves("xor", 18, "val", 6, true, false) } returns result
        assertEquals(listOf(true, false), subject.isPairEnabled("xor", "val").toList())
        assertEquals(listOf(false, true), subject.isPairPresentedInNetwork("xor", "val").toList())
        assertSame(result, subject.getLiquidityData(xor, valToken.copy(precision = 6), true, false))
    }
    @Test fun `pool updates target only the selected account`() = runTest {
        coEvery { subscription.updateAccountPools("owner") } just Runs
        subject.updatePools(); coVerify(exactly = 1) { subscription.updateAccountPools("owner") }
    }
    @Test fun `asset changes are debounced before subscribing to pool data`() = runTest {
        val changes = MutableSharedFlow<String>()
        every { subscription.subscribeToPoolsAssets("owner") } returns changes
        every { subscription.subscribeToPoolsData("owner") } returns flowOf("updated-pools")
        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject.subscribePoolsChanges().toList(seen) }
        runCurrent(); changes.emit("first"); advanceTimeBy(100); changes.emit("second"); advanceTimeBy(299); runCurrent()
        assertTrue(seen.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("updated-pools"), seen)
        verify(exactly = 1) { subscription.subscribeToPoolsData("owner") }
    }
    @Test fun `removal fee is refreshed for a different pair and selected account`() = runTest {
        coEvery { extrinsic.calcRemoveLiquidityNetworkFee(any(), any(), any()) } returnsMany listOf(fee, BigDecimal("0.012"))
        assertEquals(fee, subject.fetchRemoveLiquidityNetworkFee(xor, valToken))
        selection.value = SoraAccount("other", "Other")
        assertEquals(BigDecimal("0.012"), subject.fetchRemoveLiquidityNetworkFee(xor, token("eth", 6)))
        coVerify { extrinsic.calcRemoveLiquidityNetworkFee("xor" to 18, "eth" to 6, "other") }
    }
    @Test fun `missing removal fee can recover on the next request`() = runTest {
        coEvery { extrinsic.calcRemoveLiquidityNetworkFee(any(), any(), any()) } returnsMany listOf(null, fee)
        assertEquals(BigDecimal.ZERO, subject.fetchRemoveLiquidityNetworkFee(xor, valToken))
        assertEquals(fee, subject.fetchRemoveLiquidityNetworkFee(xor, valToken))
    }
    @Test fun `supply fee follows the reviewed amounts instead of reusing a different quote`() = runTest {
        coEvery { extrinsic.calcAddLiquidityNetworkFee(any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(fee, BigDecimal("0.014"))
        assertEquals(fee, details().networkFee)
        assertEquals(BigDecimal("0.014"), details(BigDecimal("20")).networkFee)
        coVerify { extrinsic.calcAddLiquidityNetworkFee("owner", "xor" to 18, "val" to 18, BigDecimal("20"), match { it.compareTo(BigDecimal("40")) == 0 }, true, true, 0.5) }
    }
    @Test fun `missing supply fee can recover without recreating the interactor`() = runTest {
        coEvery { extrinsic.calcAddLiquidityNetworkFee(any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(null, fee)
        assertEquals(BigDecimal.ZERO, details().networkFee)
        assertEquals(fee, details().networkFee)
    }
    @Test fun `new enabled and disabled pairs preserve explicit amounts and flags`() = runTest {
        val enabled = details(enabled = true, presented = false)
        val disabled = details(enabled = false, presented = false)
        assertEquals(amount2, enabled.targetAmount); assertEquals(amount2, disabled.targetAmount)
        assertFalse(enabled.pairPresented); assertTrue(enabled.pairEnabled)
        assertFalse(disabled.pairPresented); assertFalse(disabled.pairEnabled)
        coVerify { extrinsic.calcAddLiquidityNetworkFee("owner", "xor" to 18, "val" to 18, amount1, amount2, true, false, 0.5) }
        coVerify { extrinsic.calcAddLiquidityNetworkFee("owner", "xor" to 18, "val" to 18, amount1, amount2, false, false, 0.5) }
    }
    @Test fun `output desired keeps calculated input and desired output in the correct fee arguments`() = runTest {
        val result = details(BigDecimal("10"), desired = WithDesired.OUTPUT)
        assertEquals(0, BigDecimal("5").compareTo(result.targetAmount))
        coVerify { extrinsic.calcAddLiquidityNetworkFee("owner", "xor" to 18, "val" to 18, match { it.compareTo(BigDecimal("5")) == 0 }, BigDecimal("10"), true, true, 0.5) }
    }
    @Test fun `supply binds exact amounts fee wallet and duplicate consent to pending activity`() = runTest {
        val activity = slot<Sora2SubmissionActivity>()
        coEvery { extrinsic.observeAddLiquidity("owner", keys, xor, valToken, amount1, amount2, true, true, 0.5, capture(activity)) } returns ExtrinsicSubmitStatus(true, "supply-hash", "block")
        val outcome = subject.observeAddLiquidity(xor, valToken, amount1, amount2, true, true, 0.5, true)
        assertEquals(TransactionOutcome.Confirmed("supply-hash", "block"), outcome)
        assertEquals(listOf("xor", "val"), activity.captured.tokenIds)
        assertEquals(listOf(amount1.toPlainString(), amount2.toPlainString()), activity.captured.amounts)
        assertEquals(fee.toPlainString(), activity.captured.fee); assertTrue(activity.captured.duplicateConfirmed)
        verify { builder.buildLiquidity("supply-hash", "block", fee, TransactionStatus.PENDING, any(), xor, valToken, amount1, amount2, TransactionLiquidityType.ADD) }
        verify(exactly = 1) { history.saveTransaction("owner", tx) }
    }
    @Test fun `withdrawal preserves exact minima and leaves duplicate consent unset`() = runTest {
        val activity = slot<Sora2SubmissionActivity>(); val marker = BigDecimal("0.123456789012345678")
        coEvery { extrinsic.observeRemoveLiquidity("owner", keys, xor, valToken, marker, amount1, amount2, capture(activity)) } returns ExtrinsicSubmitStatus(true, "withdraw-hash", "block")
        assertEquals(TransactionOutcome.Confirmed("withdraw-hash", "block"), subject.removeLiquidity(xor, valToken, marker, amount1, amount2, fee, false))
        assertFalse(activity.captured.duplicateConfirmed)
        assertEquals(listOf(amount1.toPlainString(), amount2.toPlainString()), activity.captured.amounts)
        verify { builder.buildLiquidity("withdraw-hash", "block", fee, TransactionStatus.PENDING, any(), xor, valToken, amount1, amount2, TransactionLiquidityType.WITHDRAW) }
        verify(exactly = 1) { history.saveTransaction("owner", tx) }
    }
    @Test fun `activity storage failure does not rewrite a confirmed withdrawal`() = runTest {
        coEvery { extrinsic.observeRemoveLiquidity(any(), any(), any(), any(), any(), any(), any(), any()) } returns ExtrinsicSubmitStatus(true, "hash", "block")
        every { history.saveTransaction(any(), any()) } throws IllegalStateException("storage unavailable")
        assertEquals(TransactionOutcome.Confirmed("hash", "block"), subject.removeLiquidity(xor, valToken, BigDecimal.ONE, amount1, amount2, fee, false))
    }
    @Test fun `locked account cannot submit or create a pending transaction`() = runTest {
        coEvery { credentials.retrieveKeyPair(wallet) } throws IllegalStateException("locked")
        try { subject.observeAddLiquidity(xor, valToken, amount1, amount2, true, true, 0.5, false); fail("Locked account must fail") } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { extrinsic.observeAddLiquidity(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { history.saveTransaction(any(), any()) }
    }
}
