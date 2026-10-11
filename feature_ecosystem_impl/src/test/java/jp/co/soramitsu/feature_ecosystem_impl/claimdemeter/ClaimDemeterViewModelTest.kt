package jp.co.soramitsu.feature_ecosystem_impl.claimdemeter

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
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.demeter.domain.DemeterFarmingPool
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_ecosystem_impl.presentation.claimdemeter.ClaimDemeterViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
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
 * Demeter claim (audit finding A5). Like stake and unstake, this screen had no error handling:
 * any exception from the submit call or from the fee read ended the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimDemeterViewModelTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

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
    private val fee = BigDecimal("0.0007")
    private var feeOffline = false

    private fun TestScope.screen(): ClaimDemeterViewModel {
        coEvery { walletInteractor.getFeeToken() } returns TestTokens.xorToken
        coEvery { demeter.calcClaimDemeterRewards(ids) } coAnswers {
            if (feeOffline) throw IOException("BOUNDED_HTTP_IO")
            fee
        }
        coEvery { assetsInteractor.getXorBalance(any()) } returns mockk<XorAssetBalance>().also {
            every { it.transferable } returns BigDecimal.TEN
        }
        coEvery { demeter.getFarmedPool(ids) } returns DemeterFarmingPool(
            tokenBase = TestTokens.xorToken,
            tokenTarget = TestTokens.valToken,
            tokenReward = TestTokens.pswapToken,
            apr = 1.0,
            amount = BigDecimal.ONE,
            amountReward = BigDecimal.ONE,
        )
        val viewModel = ClaimDemeterViewModel(
            walletInteractor,
            assetsRouter,
            assetsInteractor,
            NumbersFormatter(),
            resourceManager,
            demeter,
            ids.first,
            ids.second,
            ids.third,
            BigDecimal.ONE,
        )
        advanceUntilIdle()
        // The claim screen is the screen in front, as it is when the button is tapped.
        viewModel.onScreenResumed()
        return viewModel
    }

    private fun claimAnswers(
        confirmedDuplicate: Boolean,
        answer: () -> TransactionOutcome,
    ) {
        coEvery {
            demeter.claimDemeterRewards(ids, BigDecimal.ONE, fee, confirmedDuplicate)
        } answers { answer() }
    }

    @Test
    fun `a confirmed claim opens its transaction page`() = runTest {
        val viewModel = screen()
        claimAnswers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

        viewModel.onClaim()
        advanceUntilIdle()

        verify { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_CLAIM) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a claim that is still waiting opens its transaction page and does not crash`() = runTest {
        val viewModel = screen()
        // What a failed status read or an unfinished finality wait gives after a broadcast.
        claimAnswers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

        viewModel.onClaim()
        advanceUntilIdle()

        verify { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_CLAIM) }
        assertNull(viewModel.alertDialogLiveData.value)
        assertNull(viewModel.errorFromResourceLiveData.value)
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a claim that failed on chain opens its transaction page`() = runTest {
        val viewModel = screen()
        claimAnswers(false) {
            TransactionOutcome.FailedOnChain(
                txHash = txHash,
                blockHash = "0xblock",
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "DemeterFarmingPlatform.ZeroRewards",
            )
        }

        viewModel.onClaim()
        advanceUntilIdle()

        verify { assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_CLAIM) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a claim the node refused is a failure with its reason and is not submitted`() = runTest {
        val viewModel = screen()
        claimAnswers(false) {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.NOT_VALID,
                nodeMessage = "Transaction has a bad signature",
            )
        }

        viewModel.onClaim()
        advanceUntilIdle()

        val dialog = checkNotNull(viewModel.alertDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_refused_title}", dialog.first)
        // The fixed sentence, then the node's own short text.
        assertEquals(
            "text-${R.string.tx_outcome_note_refused_invalid}\n\n" +
                "text-${R.string.tx_outcome_note_network_message}",
            dialog.second,
        )
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `a claim that could not be prepared says nothing was sent and does not crash`() = runTest {
        val viewModel = screen()
        // Before the fix any exception here was uncaught in viewModelScope: the app ended.
        claimAnswers(false) { throw IllegalStateException("SORA2_PENDING_WALLET_WITNESS_MISMATCH") }

        viewModel.onClaim()
        advanceUntilIdle()

        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_generic}",
            viewModel.alertDialogLiveData.value,
        )
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
        assertFalse(viewModel.state.value.isButtonLoading)
    }

    @Test
    fun `the same claim still waiting needs a deliberate second confirmation`() = runTest {
        val viewModel = screen()
        claimAnswers(false) { throw DuplicateWaitingTransactionException(txHash, 1L) }
        claimAnswers(true) { TransactionOutcome.Confirmed("0x" + "cd".repeat(32), "0xblock") }

        viewModel.onClaim()
        advanceUntilIdle()

        val question = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_duplicate_message}", question.message)
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
        assertFalse(viewModel.state.value.isButtonLoading)

        question.onConfirm()
        advanceUntilIdle()
        coVerify(exactly = 1) { demeter.claimDemeterRewards(ids, BigDecimal.ONE, fee, true) }
        verify { assetsRouter.showTxResult("0x" + "cd".repeat(32), TransactionScreen.DEMETER_CLAIM) }
    }

    // --- the result arrives while the screen is not in front -------------------------------

    @Test
    fun `a result that arrives in the background opens its page on return and not before`() =
        runTest {
            val viewModel = screen()
            var sent = 0
            claimAnswers(false) {
                sent += 1
                // Home was pressed (or the phone locked) while the claim was followed.
                viewModel.onScreenPaused()
                TransactionOutcome.Confirmed(txHash, "0xblock")
            }

            viewModel.onClaim()
            advanceUntilIdle()

            // Before the fix the page was opened from the coroutine at this moment. With the
            // activity stopped that navigation is dropped, and with the PIN lock on top it
            // removes the PIN lock.
            verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
            // Before the fix the button was active again, with no message.
            assertFalse(viewModel.state.value.isButtonLoading)
            assertFalse(viewModel.state.value.isButtonActive)
            viewModel.onClaim()
            advanceUntilIdle()
            assertEquals(1, sent)

            viewModel.onScreenResumed()

            verify(exactly = 1) {
                assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_CLAIM)
            }

            // Shown once: a later return to the front opens nothing more.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            verify(exactly = 1) { assetsRouter.showTxResult(any(), any()) }
        }

    @Test
    fun `a result is kept while the PIN lock is on top and shown when the screen is back`() =
        runTest {
            val viewModel = screen()
            // The router opens the page only when the claim screen is the current destination.
            var pinLockOnTop = true
            every { assetsRouter.showTxResult(any(), any()) } answers { !pinLockOnTop }
            claimAnswers(false) {
                viewModel.onScreenPaused()
                TransactionOutcome.Waiting(txHash, acceptedByNode = true)
            }

            viewModel.onClaim()
            advanceUntilIdle()

            // The app returns after more than five minutes: the activity resumes this screen
            // for a moment while the PIN lock is already the current destination.
            viewModel.onScreenResumed()
            verify(exactly = 1) {
                assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_CLAIM)
            }
            assertFalse(viewModel.state.value.isButtonActive)
            viewModel.onScreenPaused()

            // The PIN was entered and the PIN lock removed itself.
            pinLockOnTop = false
            viewModel.onScreenResumed()
            verify(exactly = 2) {
                assetsRouter.showTxResult(txHash, TransactionScreen.DEMETER_CLAIM)
            }

            // Opened: nothing more is asked of the router.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            verify(exactly = 2) { assetsRouter.showTxResult(any(), any()) }
        }

    @Test
    fun `opening the screen offline explains and recovers instead of crashing`() = runTest {
        feeOffline = true

        val viewModel = screen()

        val question = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_screen_needs_network}", question.message)
        assertEquals("text-${R.string.common_try_again}", question.confirmText)

        feeOffline = false
        question.onConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }
}
