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
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.demeter.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.editfarm.EditFarmViewModel
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.PolkaswapTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val assetsRouter = mockk<AssetsRouter>(relaxed = true).also {
        // The router opens the transaction page when asked.
        every { it.showTxResult(any(), any()) } returns true
    }
    private val assetsInteractor = mockk<AssetsInteractor>()
    private val demeter = mockk<DemeterFarmingInteractor>()
    private val resourceManager = mockk<ResourceManager>().also {
        every { it.getString(any()) } answers { "text-${firstArg<Int>()}" }
    }

    private val ids =
        Triple(TestTokens.xorToken.id, TestTokens.valToken.id, TestTokens.pswapToken.id)
    private val txHash = "0x" + "ab".repeat(32)
    private var feeOffline = false
    private var feeNeedsAppUpdate = false

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
            if (feeNeedsAppUpdate) {
                throw IllegalStateException(
                    "SORA2_FEE_QUOTE_FAILED",
                    IllegalStateException(NetworkFeeUnavailable.APP_UPDATE_REQUIRED_CODE),
                )
            }
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
        // The farm screen is the screen in front, as it is when the button is tapped.
        viewModel.onScreenResumed()
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

        verify { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE) }
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
        verify { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE) }
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

        verify { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE) }
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
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
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
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
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
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
        assertFalse(viewModel.state.value.isButtonLoading)

        question.onConfirm()
        advanceUntilIdle()
        coVerify(exactly = 1) { demeter.depositDemeterFarming(ids, any(), any(), true) }
        verify { assetsRouter.showTxResult("0x" + "cd".repeat(32), TransactionScreen.DEMETER_STAKE) }
    }

    @Test
    fun `a second tap on Confirm while the stake is followed does nothing`() = runTest {
        val viewModel = readyToStake()
        val result = CompletableDeferred<TransactionOutcome>()
        var started = 0
        coEvery { demeter.depositDemeterFarming(ids, any(), any(), false) } coAnswers {
            started += 1
            // What the duplicate guard answers a second request while the first is followed.
            if (started > 1) throw DuplicateWaitingTransactionException(txHash, 1L)
            result.await()
        }

        viewModel.onConfirm()
        runCurrent()
        // The button is still clickable while its progress mark fades in.
        viewModel.onConfirm()
        runCurrent()

        // Before: a second coroutine ran into the duplicate guard, cleared the progress mark
        // and asked "send again anyway?" about the stake sent a moment ago.
        assertEquals(1, started)
        assertTrue(viewModel.state.value.isButtonLoading)
        assertNull(viewModel.confirmDialogLiveData.value)

        result.complete(TransactionOutcome.Confirmed(txHash, "0xblock"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isButtonLoading)
        verify(exactly = 1) { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE) }
        assertEquals(1, started)
    }

    @Test
    fun `Confirm works again after a stake that did not reach the network`() = runTest {
        val viewModel = readyToStake()
        var attempts = 0
        stakeAnswers(false) {
            attempts += 1
            if (attempts == 1) throw IOException("BOUNDED_HTTP_IO")
            TransactionOutcome.Confirmed(txHash, "0xblock")
        }

        viewModel.onConfirm()
        advanceUntilIdle()
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }

        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(2, attempts)
        verify(exactly = 1) { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE) }
    }

    // --- the result arrives while the screen is not in front -------------------------------

    @Test
    fun `a result that arrives in the background opens its page on return and not before`() =
        runTest {
            val viewModel = readyToStake()
            var sent = 0
            stakeAnswers(false) {
                sent += 1
                // Home was pressed (or the phone locked) while the stake was followed.
                viewModel.onScreenPaused()
                TransactionOutcome.Confirmed(txHash, "0xblock")
            }

            viewModel.onConfirm()
            advanceUntilIdle()

            // Before the fix the page was opened from the coroutine at this moment. With the
            // activity stopped that navigation is dropped, and with the PIN lock on top it
            // removes the PIN lock.
            verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
            // Before the fix the button was active again, with no message.
            assertFalse(viewModel.state.value.isButtonLoading)
            assertFalse(viewModel.state.value.isButtonActive)
            viewModel.onConfirm()
            viewModel.onSliderChange(0.75)
            assertFalse(viewModel.state.value.isButtonActive)
            viewModel.onConfirm()
            advanceUntilIdle()
            assertEquals(1, sent)

            viewModel.onScreenResumed()

            verify(exactly = 1) {
                assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE)
            }

            // Shown once: a later return to the front opens nothing more.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            verify(exactly = 1) { assetsRouter.showTxResult(any(), any()) }
        }

    @Test
    fun `a result is kept while the PIN lock is on top and shown when the screen is back`() =
        runTest {
            val viewModel = readyToStake()
            // The router opens the page only when the farm screen is the current destination.
            var pinLockOnTop = true
            every { assetsRouter.showTxResult(any(), any()) } answers { !pinLockOnTop }
            stakeAnswers(false) {
                viewModel.onScreenPaused()
                TransactionOutcome.Waiting(txHash, acceptedByNode = true)
            }

            viewModel.onConfirm()
            advanceUntilIdle()

            // The app returns after more than five minutes: the activity resumes this screen
            // for a moment while the PIN lock is already the current destination.
            viewModel.onScreenResumed()
            verify(exactly = 1) {
                assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE)
            }
            assertFalse(viewModel.state.value.isButtonActive)
            viewModel.onScreenPaused()

            // The PIN was entered and the PIN lock removed itself.
            pinLockOnTop = false
            viewModel.onScreenResumed()
            verify(exactly = 2) {
                assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_STAKE)
            }

            // Opened: nothing more is asked of the router.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            verify(exactly = 2) { assetsRouter.showTxResult(any(), any()) }
        }

    @Test
    fun `a network that needs a newer app says so and offers the update instead of another try`() =
        runTest {
            feeNeedsAppUpdate = true

            val viewModel = screen()

            val question = checkNotNull(viewModel.confirmDialogLiveData.value)
            assertEquals("text-${R.string.network_fee_needs_app_update}", question.message)
            assertEquals("text-${R.string.common_update}", question.confirmText)
            assertEquals("text-${R.string.common_close}", question.dismissText)
            assertNull(viewModel.openAppStoreLiveData.value)

            question.onConfirm()
            advanceUntilIdle()

            assertNotNull(viewModel.openAppStoreLiveData.value)
            assertNotNull(viewModel.navigationPop.value)
            coVerify(exactly = 1) { demeter.calcDepositDemeterNetworkFee(ids) }
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
