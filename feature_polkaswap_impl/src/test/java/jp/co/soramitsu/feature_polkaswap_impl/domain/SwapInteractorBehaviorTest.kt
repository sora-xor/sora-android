package jp.co.soramitsu.feature_polkaswap_impl.domain

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.feature_account_api.domain.interfaces.*
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.*
import jp.co.soramitsu.feature_polkaswap_api.domain.model.*
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SwapInteractorBehaviorTest {
    private val assets = mockk<AssetsRepository>()
    private val credentials = mockk<CredentialsRepository>()
    private val users = mockk<UserRepository>()
    private val history = mockk<TransactionHistoryRepository>(relaxed = true)
    private val repo = mockk<PolkaswapRepository>()
    private val extrinsic = mockk<PolkaswapExtrinsicRepository>()
    private val subscription = mockk<PolkaswapSubscriptionRepository>()
    private val builder = mockk<TransactionBuilder>()
    private val keys = mockk<Sr25519Keypair>()
    private val transaction = mockk<Transaction.Swap>()
    private val wallet = SoraAccount("owner", "Main")
    private fun token(id: String) = Token(id, id.uppercase(), id.uppercase(), 18, true, null, 1.0, 0.0, "$")
    private val xor = token("xor")
    private val valToken = token("val")
    private val fee = BigDecimal("0.01")
    private val dex = PoolDex(0, "xor", "XOR")
    private val secondDex = PoolDex(1, "val", "VAL")
    private val subject = SwapInteractorImpl(assets, credentials, users, history, repo, extrinsic, subscription, builder)

    @Before fun setUp() {
        coEvery { users.getCurSoraAccount() } returns wallet
        coEvery { credentials.retrieveKeyPair(wallet) } returns keys
        coEvery { extrinsic.calcSwapNetworkFee(xor, "owner") } returns fee
        every { builder.buildSwap(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns transaction
    }

    private suspend fun discover() {
        coEvery { repo.getPoolBaseTokens() } returns listOf(dex, secondDex)
        coEvery { subscription.isSwapAvailable("xor", "val", any()) } returns true
        coEvery { subscription.getAvailableSources("xor", "val", 0) } returns listOf(Market.XYK)
        coEvery { subscription.getAvailableSources("xor", "val", 1) } returns listOf(Market.TBC)
        subject.fetchAvailableSources("xor", "val")
    }

    private suspend fun quote(desired: WithDesired = WithDesired.INPUT) = subject.calcDetails(
        xor, valToken, xor, BigDecimal("10.00"), desired, 1.0,
    )

    @Test fun `input quote maximizes output and preserves route and minimum received`() = runTest {
        discover()
        coEvery { subscription.getSwapQuote("xor", "val", any(), WithDesired.INPUT, emptyList(), xor, 0) } returns SwapQuote(BigDecimal("20.00"))
        coEvery { subscription.getSwapQuote("xor", "val", any(), WithDesired.INPUT, emptyList(), xor, 1) } returns SwapQuote(BigDecimal("25.00"), listOf("xor", "missing", "val"))
        coEvery { assets.getToken("xor") } returns xor
        coEvery { assets.getToken("val") } returns valToken
        coEvery { assets.getToken("missing") } returns null
        val result = checkNotNull(quote())
        assertEquals(secondDex, result.dex)
        assertEquals(BigDecimal("25.00"), result.amount)
        assertEquals(BigDecimal("0.40"), result.per1)
        assertEquals(BigDecimal("2.50"), result.per2)
        assertEquals(0, result.minmax.compareTo(BigDecimal("24.75")))
        assertEquals(listOf("XOR", "VAL"), result.swapRoute)
        assertEquals(fee, result.networkFee)
    }

    @Test fun `output quote minimizes required input and adds slippage allowance`() = runTest {
        discover()
        coEvery { subscription.getSwapQuote(any(), any(), any(), WithDesired.OUTPUT, any(), any(), 0) } returns SwapQuote(BigDecimal("20.00"))
        coEvery { subscription.getSwapQuote(any(), any(), any(), WithDesired.OUTPUT, any(), any(), 1) } returns SwapQuote(BigDecimal("25.00"))
        val result = checkNotNull(quote(WithDesired.OUTPUT))
        assertEquals(dex, result.dex)
        assertEquals(BigDecimal("20.00"), result.amount)
        assertEquals(0, result.minmax.compareTo(BigDecimal("20.20")))
        assertNull(result.swapRoute)
    }

    @Test fun `explicit market only queries DEXes supporting that market`() = runTest {
        discover(); subject.setSwapMarket(Market.XYK)
        coEvery { subscription.getSwapQuote(any(), any(), any(), any(), listOf(Market.XYK), xor, 0) } returns SwapQuote(BigDecimal.ONE)
        assertEquals(dex, quote()?.dex)
        assertEquals(Market.XYK, subject.observeSelectedMarket().first())
        coVerify(exactly = 0) { subscription.getSwapQuote(any(), any(), any(), any(), any(), any(), 1) }
    }

    @Test fun `missing and zero quotes yield no executable swap`() = runTest {
        discover()
        coEvery { subscription.getSwapQuote(any(), any(), any(), any(), any(), any(), 0) } returns null
        coEvery { subscription.getSwapQuote(any(), any(), any(), any(), any(), any(), 1) } returns SwapQuote(BigDecimal.ZERO)
        assertNull(quote())
        coVerify(exactly = 0) { extrinsic.calcSwapNetworkFee(any(), any()) }
    }

    @Test fun `pair discovery falls back to smart when the selected market disappears`() = runTest {
        subject.setSwapMarket(Market.TBC)
        coEvery { repo.getPoolBaseTokens() } returns listOf(dex, secondDex)
        coEvery { subscription.isSwapAvailable("xor", "val", 0) } returns true
        coEvery { subscription.isSwapAvailable("xor", "val", 1) } returns false
        coEvery { subscription.getAvailableSources("xor", "val", 0) } returns listOf(Market.XYK)
        assertEquals(setOf(Market.SMART, Market.XYK), subject.fetchAvailableSources("xor", "val"))
        assertEquals(Market.SMART, subject.observeSelectedMarket().first())
        coVerify(exactly = 0) { subscription.getAvailableSources(any(), any(), 1) }
    }

    @Test fun `balance checks distinguish input shortfall from fee shortfall and output funded fees`() {
        val details = SwapDetails(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal("9"), fee, dex)
        fun check(from: Token = valToken, balance: String = "10", amount: String = "2", feeBalance: String = "1", to: Token = valToken, toBalance: String = "0", output: String = "10", desired: WithDesired = WithDesired.INPUT) = subject.checkSwapBalances(
            from, BigDecimal(balance), BigDecimal(amount), BigDecimal("1"), BigDecimal(feeBalance), xor,
            to, BigDecimal(toBalance), BigDecimal(output), desired, details,
        )
        assertEquals(valToken, check(amount = "11"))
        assertEquals(xor, check(from = xor, feeBalance = "2"))
        assertEquals(xor, check(feeBalance = "0"))
        assertNull(check(feeBalance = "0", to = xor))
        assertEquals(xor, check(feeBalance = "0", to = xor, output = "1", desired = WithDesired.OUTPUT))
        assertNull(check(feeBalance = "0", to = xor, output = "2", desired = WithDesired.OUTPUT))
        assertNull(check())
    }

    @Test fun `disclaimer changes and block subscriptions retain repository values`() = runTest {
        every { repo.getPolkaswapDisclaimerVisibility() } returns flowOf(true, false)
        coEvery { repo.setPolkaswapDisclaimerVisibility(false) } just Runs
        every { subscription.subscribeEachBlock() } returns flowOf("block-1", "block-2")
        assertEquals(listOf(true, false), subject.getPolkaswapDisclaimerVisibility().toList())
        subject.setPolkaswapDisclaimerVisibility(false)
        coVerify(exactly = 1) { repo.setPolkaswapDisclaimerVisibility(false) }
        assertEquals(listOf("block-1", "block-2"), subject.observePoolReserves().toList())
    }

    @Test fun `confirmed input swap persists exact reviewed amounts and duplicate consent`() = runTest {
        val activity = slot<Sora2SubmissionActivity>()
        coEvery { extrinsic.observeSwap(xor, valToken, keys, "owner", emptyList(), WithDesired.INPUT, BigDecimal("2.01"), BigDecimal("3.99"), 0, capture(activity)) } returns ExtrinsicSubmitStatus(true, "hash", "block")
        val outcome = subject.swap(xor, valToken, WithDesired.INPUT, BigDecimal("2.01"), BigDecimal("3.99"), fee, 0, BigDecimal("4.03"), true)
        assertEquals(TransactionOutcome.Confirmed("hash", "block"), outcome)
        assertTrue(subject.observeSwap().first())
        assertEquals(listOf("2.01", "4.03"), activity.captured.amounts)
        assertTrue(activity.captured.duplicateConfirmed)
        verify { builder.buildSwap("hash", "block", fee, TransactionStatus.PENDING, any(), xor, valToken, BigDecimal("2.01"), BigDecimal("4.03"), Market.SMART) }
        verify { history.saveTransaction("owner", transaction) }
    }

    @Test fun `output swap uses selected market and activity failures preserve the chain result`() = runTest {
        subject.setSwapMarket(Market.XYK)
        val activity = slot<Sora2SubmissionActivity>()
        coEvery { extrinsic.observeSwap(xor, valToken, keys, "owner", listOf(Market.XYK), WithDesired.OUTPUT, BigDecimal("4.03"), BigDecimal("2.02"), 0, capture(activity)) } returns ExtrinsicSubmitStatus(true, "hash", "block")
        every { history.saveTransaction(any(), any()) } throws IllegalStateException("cache unavailable")
        assertEquals(TransactionOutcome.Confirmed("hash", "block"), subject.swap(xor, valToken, WithDesired.OUTPUT, BigDecimal("4.03"), BigDecimal("2.02"), fee, 0, BigDecimal("2.01"), false))
        assertEquals(listOf("2.01", "4.03"), activity.captured.amounts)
        assertFalse(activity.captured.duplicateConfirmed)
        assertEquals(Market.XYK.name, activity.captured.market)
    }
}
