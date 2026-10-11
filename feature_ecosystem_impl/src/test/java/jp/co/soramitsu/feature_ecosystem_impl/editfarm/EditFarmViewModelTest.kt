package jp.co.soramitsu.feature_ecosystem_impl.editfarm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.demeter.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.EditFarmViewModel
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.PolkaswapTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * Demeter stake and unstake (audit finding A5). This screen had no error handling at all: an
 * exception from the submit call, including "submitted, status unknown" after a broadcast, left
 * viewModelScope uncaught and ended the app. runTest fails a test on any such exception.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditFarmViewModelTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val poolsInteractor = mockk<PoolsInteractor>()
    private val walletInteractor = mockk<WalletInteractor>()
    private val assetsRouter = mockk<AssetsRouter>(relaxed = true)
    private val assetsInteractor = mockk<AssetsInteractor>()
    private val demeter = mockk<DemeterFarmingInteractor>()
    private val resourceManager = mockk<ResourceManager>().also {
        every { it.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }

    private val ids =
        Triple(TestTokens.xorToken.id, TestTokens.valToken.id, TestTokens.pswapToken.id)
    private val txHash = "0x" + "ab".repeat(32)
    private var feeOffline = false

    private fun TestScope.screen(): EditFarmViewModel {
        coEvery {
            poolsInteractor.getPoolOfCurAccount(StringPair(ids.first, ids.second))
        } returns PolkaswapTestData.POOL_DATA
        coEvery {
            assetsInteractor.getAssetOrThrow(SubstrateOptionsProvider.feeAssetId)
        } returns TestAssets.xorAsset(BigDecimal.TEN)
        coEvery { demeter.getFarmedBasicPool(ids) } returns DemeterFarmingBasicPool(
            tokenBase = TestTokens.xorToken,
            tokenTarget = TestTokens.valToken,
            tokenReward = TestTokens.pswapToken,
            apr = 1.0,
            tvl = BigDecimal.TEN,
            fee = 1.0,
        )
        coEvery { demeter.getFarmedPool(ids) } returns null
        coEvery { demeter.calcDepositDemeterNetworkFee(ids) } coAnswers {
            if (feeOffline) throw IOException("BOUNDED_HTTP_IO")
            BigDecimal("0.0007")
        }
        coEvery { demeter.calcWithdrawDemeterNetworkFee(ids) } returns BigDecimal("0.0007")
        val viewModel = EditFarmViewModel(
            poolsInteractor,
            walletInteractor,
            assetsRouter,
            assetsInteractor,
            NumbersFormatter(),
            resourceManager,
            demeter,
            ids.first,
            ids.second,
            ids.third,
        )
        advanceUntilIdle()
        return viewModel
    }

    /** Moves the slider up, so Confirm stakes a positive amount. */
    private fun TestScope.readyToStake(): EditFarmViewModel = screen().also {
        it.onSliderChange(0.5)
        advanceUntilIdle()
    }

    private fun stakeAnswers(
        confirmedDuplicate: Boolean,
        answer: () -> TransactionOutcome,
    ) {
        coEvery {
            demeter.depositDemeterFarming(ids, any(), any(), confirmedDuplicate)
        } answers { answer() }
    }

    @Test
    fun `a confirmed stake opens its transaction page`() = runTest {
        val viewModel = readyToStake()
        stakeAnswers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

        viewModel.onConfirm()
        advanceUntilIdle()

        verify { assetsRouter.showTxDetails(txHash, true) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a stake that is still waiting opens its transaction page and does not crash`() = runTest {
        val viewModel = readyToStake()
        // What a failed status read or an unfinished finality wait gives after a broadcast.
        stakeAnswers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

        viewModel.onConfirm()
        advanceUntilIdle()

        // The transaction page carries "waiting for confirmation" and follows the result.
        verify { assetsRouter.showTxDetails(txHash, true) }
        assertNull(viewModel.alertDialogLiveData.value)
        assertNull(viewModel.errorFromResourceLiveData.value)
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a stake that failed on chain opens its transaction page`() = runTest {
        val viewModel = readyToStake()
        stakeAnswers(false) {
            TransactionOutcome.FailedOnChain(
                txHash = txHash,
                blockHash = "0xblock",
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "DemeterFarmingPlatform.InsufficientFunds",
            )
        }

        viewModel.onConfirm()
        advanceUntilIdle()

        verify { assetsRouter.showTxDetails(txHash, true) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a stake the node refused is a failure with its reason and is not submitted`() = runTest {
        val viewModel = readyToStake()
        stakeAnswers(false) {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees",
            )
        }

        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_fee}",
            viewModel.alertDialogLiveData.value,
        )
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a stake that could not be prepared says nothing was sent and does not crash`() = runTest {
        val viewModel = readyToStake()
        // Before the fix any exception here was uncaught in viewModelScope: the app ended.
        stakeAnswers(false) { throw IOException("BOUNDED_HTTP_IO") }

        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_network}",
            viewModel.alertDialogLiveData.value,
        )
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `the same stake still waiting needs a deliberate second confirmation`() = runTest {
        val viewModel = readyToStake()
        stakeAnswers(false) { throw DuplicateWaitingTransactionException(txHash, 1L) }
        stakeAnswers(true) { TransactionOutcome.Waiting("0x" + "cd".repeat(32), true) }

        viewModel.onConfirm()
        advanceUntilIdle()

        val question = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_duplicate_title}", question.title)
        assertEquals("text-${R.string.tx_outcome_duplicate_send_again}", question.confirmText)
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }
        assertFalse(viewModel.state.value.isButtonLoading)

        question.onConfirm()
        advanceUntilIdle()
        coVerify(exactly = 1) { demeter.depositDemeterFarming(ids, any(), any(), true) }
        verify { assetsRouter.showTxDetails("0x" + "cd".repeat(32), true) }
    }

    @Test
    fun `opening the screen offline explains and recovers instead of crashing`() = runTest {
        feeOffline = true

        val viewModel = screen()

        val question = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_screen_needs_network}", question.message)
        assertEquals("text-${R.string.common_try_again}", question.confirmText)
        assertEquals("text-${R.string.common_close}", question.dismissText)

        feeOffline = false
        question.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isCardLoading)
    }
}
