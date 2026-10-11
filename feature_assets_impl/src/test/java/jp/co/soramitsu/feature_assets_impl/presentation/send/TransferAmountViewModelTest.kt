/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_assets_impl.presentation.send

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.Sora2TransferFeePreviewRequest
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.TransferAmountViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class TransferAmountViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var assetsInteractor: AssetsInteractor

    @MockK
    private lateinit var walletRouter: WalletRouter

    @MockK
    private lateinit var assetsRouter: AssetsRouter

    @MockK
    private lateinit var drawable: PictureDrawable

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var avatarGenerator: AccountAvatarGenerator

    @MockK
    private lateinit var clipboardManager: BasicClipboardManager

    private val recipientId =
        "recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId"
    private val recipientFullName = "recipientFull Name"
    private val networkFee = 0.07.toBigDecimal()
    private lateinit var transferAmountViewModel: TransferAmountViewModel

    @Before
    fun setUp() = runTest {
        every { resourceManager.getString(R.string.error_transaction_fee_title) } returns "Not enough funds"
        every { avatarGenerator.createAvatar(any(), any()) } returns drawable
        coEvery {
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                any(),
                any(),
            )
        } returns false
        coEvery {
            assetsInteractor.calcTransactionFee(
                recipientId,
                any(),
                any(),
                any(),
            )
        } returns networkFee
        every { assetsInteractor.flowCurSoraAccount() } returns flowOf(
            SoraAccount(WALLET_ID, "Primary")
        )
        coEvery { assetsInteractor.getCurSoraAccount() } returns
            SoraAccount(WALLET_ID, "Primary")
    }

    @Test
    fun `initialized correctly`() = runTest {
        initViewModel(BigDecimal.ZERO)
        advanceUntilIdle()
        val state = transferAmountViewModel.sendState
        assertEquals(
            "recipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientIdrecipientId",
            state.value.address
        )
    }

    @Test
    fun `fee preview failure stops loading and keeps amount editable`() = runTest {
        coEvery {
            assetsInteractor.calcTransactionFee(
                recipientId,
                any(),
                any(),
                any(),
            )
        } returns null

        initViewModel(BigDecimal.TEN)
        advanceUntilIdle()

        val state = transferAmountViewModel.sendState.value
        assertFalse(state.feeLoading)
        assertFalse(state.reviewEnabled)
        assertTrue(state.input?.enabled == true)
        assertEquals(
            R.string.common_error_general_title to R.string.common_error_fee_rate_not_available,
            transferAmountViewModel.errorFromResourceLiveData.value,
        )
        assertEquals(NetworkFeeUnavailable.OTHER, state.feeUnavailable)
    }

    @Test
    fun `fee preview exception shows the calm fee notice instead of an error dialog`() = runTest {
        coEvery {
            assetsInteractor.calcTransactionFee(
                recipientId,
                any(),
                any(),
                any(),
            )
        } throws IllegalStateException("fee RPC unavailable")

        initViewModel(BigDecimal.TEN)
        advanceUntilIdle()

        val state = transferAmountViewModel.sendState.value
        assertFalse(state.feeLoading)
        assertFalse(state.reviewEnabled)
        assertTrue(state.input?.enabled == true)
        assertEquals(NetworkFeeUnavailable.OTHER, state.feeUnavailable)
        assertNull(transferAmountViewModel.errorFromResourceLiveData.value)
    }

    @Test
    fun `unreachable SORA endpoint names the cause and Try again recovers`() = runTest {
        // 3.9.0 sends fee quotes to the reviewed endpoint only; the selected node does not help.
        coEvery {
            assetsInteractor.calcTransactionFee(recipientId, any(), any(), any())
        } throws IllegalStateException(
            "SORA2_RUNTIME_REFRESH_FAILED",
            BoundedHttpTextException("BOUNDED_HTTP_IO"),
        )

        initViewModel(BigDecimal.TEN)
        advanceUntilIdle()
        transferAmountViewModel.amountChanged(BigDecimal.ONE)
        advanceUntilIdle()

        var state = transferAmountViewModel.sendState.value
        assertEquals(NetworkFeeUnavailable.NETWORK_UNREACHABLE, state.feeUnavailable)
        assertFalse(state.reviewEnabled)
        assertFalse(state.feeLoading)
        assertNull(transferAmountViewModel.errorFromResourceLiveData.value)

        // The endpoint is back.
        coEvery {
            assetsInteractor.calcTransactionFee(recipientId, any(), any(), any())
        } returns networkFee
        transferAmountViewModel.onRetryFee()
        advanceUntilIdle()

        state = transferAmountViewModel.sendState.value
        assertNull(state.feeUnavailable)
        assertFalse(state.feeLoading)
        assertTrue(state.fee.isNotEmpty())
        assertTrue(state.reviewEnabled)
    }

    @Test
    fun `finalized transfer failure stays on send screen and shows error`() = runTest {
        coEvery {
            assetsInteractor.observeTransfer(
                recipientId,
                any(),
                any(),
                any(),
                WALLET_ID,
            )
        } throws SoraException.businessError(ResponseCode.BROKEN_TRANSACTION)

        initViewModel(
            balance = BigDecimal.TEN,
            initialSendAmount = BigDecimal.ONE.toPlainString(),
        )
        advanceUntilIdle()
        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        assertEquals(
            R.string.common_error_general_title to R.string.common_error_general_message,
            transferAmountViewModel.errorFromResourceLiveData.value,
        )
        verify(exactly = 0) { walletRouter.returnToHubFragment() }
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
    }

    // --- audit A5: one truthful result for every outcome of Confirm -------------------------

    private val txHash = "0x" + "ab".repeat(32)

    private fun transferAnswers(
        confirmedDuplicate: Boolean,
        answer: () -> TransactionOutcome,
    ) {
        coEvery {
            assetsInteractor.observeTransfer(
                recipientId,
                any(),
                any(),
                any(),
                WALLET_ID,
                confirmedDuplicate,
            )
        } answers { answer() }
    }

    private fun kotlinx.coroutines.test.TestScope.readyToConfirm() {
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { assetsRouter.showTxResult(any(), any()) } returns true
        initViewModel(
            balance = BigDecimal.TEN,
            initialSendAmount = BigDecimal.ONE.toPlainString(),
        )
        advanceUntilIdle()
        // The send screen is the screen in front, as it is when Confirm is tapped.
        transferAmountViewModel.onScreenResumed()
    }

    @Test
    fun `a confirmed transfer says confirmed and opens its transaction page`() = runTest {
        readyToConfirm()
        transferAnswers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        assertEquals(
            TransactionResultBadge.CONFIRMED,
            transferAmountViewModel.transactionSuccessEvent.value,
        )
        verify { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }
        assertFalse(transferAmountViewModel.sendState.value.inProgress)
    }

    @Test
    fun `a transfer that is still waiting says submitted and opens its transaction page`() =
        runTest {
            readyToConfirm()
            transferAnswers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = false) }

            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()

            assertEquals(
                TransactionResultBadge.SUBMITTED,
                transferAmountViewModel.transactionSuccessEvent.value,
            )
            verify { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }
            assertNull(transferAmountViewModel.alertDialogLiveData.value)
        }

    @Test
    fun `a transfer the node refused is shown as a failure and never as submitted`() = runTest {
        readyToConfirm()
        // The node's explicit JSON-RPC rejection, for example when locked XOR cannot pay the fee.
        transferAnswers(false) {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees (e.g. account balance too low)",
            )
        }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        // Before the fix: the green "Transaction submitted" toast and the transaction page.
        assertNull(transferAmountViewModel.transactionSuccessEvent.value)
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_fee}",
            transferAmountViewModel.alertDialogLiveData.value,
        )
        assertFalse(transferAmountViewModel.sendState.value.inProgress)
    }

    @Test
    fun `a transfer that failed on chain is explained with the network's reason`() = runTest {
        readyToConfirm()
        transferAnswers(false) {
            TransactionOutcome.FailedOnChain(
                txHash = txHash,
                blockHash = "0xblock",
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "Token.FundsUnavailable",
            )
        }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        assertNull(transferAmountViewModel.transactionSuccessEvent.value)
        assertEquals(
            "text-${R.string.tx_outcome_failed_title}" to
                "text-${R.string.tx_outcome_note_failed_on_chain}\n\n" +
                "text-${R.string.tx_outcome_note_failed_on_chain_reason}",
            transferAmountViewModel.alertDialogLiveData.value,
        )
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
    }

    @Test
    fun `a transfer that could not be prepared says nothing was sent`() = runTest {
        readyToConfirm()
        transferAnswers(false) {
            throw IllegalStateException(
                "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
                IllegalStateException("SORA2_TRANSFER_FEE_CHANGED"),
            )
        }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        assertNull(transferAmountViewModel.transactionSuccessEvent.value)
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_fee_changed}",
            transferAmountViewModel.alertDialogLiveData.value,
        )
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
    }

    private fun feeChanged() = IllegalStateException(
        "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
        IllegalStateException("SORA2_TRANSFER_FEE_CHANGED"),
    )

    @Test
    fun `after a changed fee the new fee is shown and the next confirm sends with it`() =
        runTest {
            readyToConfirm()
            val newFee = 0.09.toBigDecimal()
            val sentWithFee = mutableListOf<BigDecimal>()
            coEvery {
                assetsInteractor.observeTransfer(recipientId, any(), any(), any(), WALLET_ID, false)
            } answers {
                val reviewedFee = arg<BigDecimal>(3)
                sentWithFee += reviewedFee
                // The signer accepts only the fee the network asks for now.
                if (reviewedFee.compareTo(newFee) != 0) throw feeChanged()
                TransactionOutcome.Confirmed(txHash, "0xblock")
            }
            val feeBefore = transferAmountViewModel.sendState.value.fee
            // The network fee changed between review and confirm.
            coEvery {
                assetsInteractor.calcTransactionFee(recipientId, any(), any(), any())
            } returns newFee

            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()

            assertEquals(
                "text-${R.string.tx_outcome_not_sent_title}" to
                    "text-${R.string.tx_outcome_not_sent_fee_changed}",
                transferAmountViewModel.alertDialogLiveData.value,
            )
            // "Check the new fee and confirm again": the new fee is on screen and Confirm is
            // available. Before, the old fee stayed and Confirm sent it again, every time.
            val state = transferAmountViewModel.sendState.value
            assertTrue(state.fee.isNotEmpty())
            assertTrue(state.fee != feeBefore)
            assertFalse(state.feeLoading)
            assertTrue(state.reviewEnabled)

            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()

            assertEquals(2, sentWithFee.size)
            assertEquals(0, sentWithFee[0].compareTo(networkFee))
            assertEquals(0, sentWithFee[1].compareTo(newFee))
            verify { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }
        }

    @Test
    fun `after a changed fee nothing can be confirmed until a new fee is known`() = runTest {
        readyToConfirm()
        var attempts = 0
        coEvery {
            assetsInteractor.observeTransfer(recipientId, any(), any(), any(), WALLET_ID, false)
        } answers {
            attempts += 1
            throw feeChanged()
        }
        // The new fee cannot be read right now.
        coEvery {
            assetsInteractor.calcTransactionFee(recipientId, any(), any(), any())
        } throws BoundedHttpTextException("BOUNDED_HTTP_IO")

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        val state = transferAmountViewModel.sendState.value
        // The fee that is no longer valid is not left on screen.
        assertEquals("", state.fee)
        assertFalse(state.reviewEnabled)
        assertFalse(state.feeLoading)
        assertEquals(NetworkFeeUnavailable.NETWORK_UNREACHABLE, state.feeUnavailable)

        // A further Confirm does not send the stale fee again.
        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()
        assertEquals(1, attempts)
    }

    @Test
    fun `the same transfer still waiting needs a deliberate second confirmation`() = runTest {
        readyToConfirm()
        transferAnswers(false) { throw DuplicateWaitingTransactionException(txHash, 1L) }
        transferAnswers(true) { TransactionOutcome.Waiting("0x" + "cd".repeat(32), true) }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        val question = checkNotNull(transferAmountViewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_duplicate_title}", question.title)
        assertNull(transferAmountViewModel.transactionSuccessEvent.value)
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }

        question.onConfirm()
        advanceUntilIdle()
        verify { assetsRouter.showTxResult("0x" + "cd".repeat(32), TransactionScreen.SEND) }
    }

    @Test
    fun `a second tap on Confirm while the transfer is followed does nothing`() = runTest {
        readyToConfirm()
        val result = CompletableDeferred<TransactionOutcome>()
        var started = 0
        coEvery {
            assetsInteractor.observeTransfer(recipientId, any(), any(), any(), WALLET_ID, false)
        } coAnswers {
            started += 1
            // What the duplicate guard answers a second request while the first is followed.
            if (started > 1) throw DuplicateWaitingTransactionException(txHash, 1L)
            result.await()
        }

        transferAmountViewModel.onConfirmClick()
        runCurrent()
        // The button is still clickable while its progress mark fades in.
        transferAmountViewModel.onConfirmClick()
        runCurrent()

        // Before: a second coroutine ran into the duplicate guard, cleared the progress mark
        // and asked "send again anyway?" about the transfer sent a moment ago.
        assertEquals(1, started)
        assertTrue(transferAmountViewModel.sendState.value.inProgress)
        assertNull(transferAmountViewModel.confirmDialogLiveData.value)

        result.complete(TransactionOutcome.Confirmed(txHash, "0xblock"))
        advanceUntilIdle()

        assertFalse(transferAmountViewModel.sendState.value.inProgress)
        verify(exactly = 1) { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }
        assertEquals(1, started)
    }

    @Test
    fun `Confirm works again after a transfer that did not reach the network`() = runTest {
        readyToConfirm()
        var attempts = 0
        transferAnswers(false) {
            attempts += 1
            if (attempts == 1) {
                TransactionOutcome.RefusedByNode(
                    txHash = txHash,
                    reason = TransactionNodeRefusal.NETWORK_BUSY,
                    nodeMessage = "Immediately Dropped",
                )
            } else {
                TransactionOutcome.Confirmed(txHash, "0xblock")
            }
        }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }

        // "Please try again in a moment": the next Confirm is a new, deliberate request.
        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        assertEquals(2, attempts)
        verify(exactly = 1) { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }
    }

    @Test
    fun `a balance that is no longer enough is named and the balance is read again`() = runTest {
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { assetsRouter.showTxResult(any(), any()) } returns true
        // The figures on the Send screen come from the local database.
        val balances = MutableStateFlow(
            listOf(TestAssets.xorAsset(BigDecimal.TEN), TestAssets.valAsset(BigDecimal.TEN))
        )
        initViewModel(
            balance = BigDecimal.TEN,
            initialSendAmount = BigDecimal.ONE.toPlainString(),
            assets = balances,
        )
        advanceUntilIdle()
        transferAmountViewModel.onScreenResumed()
        assertTrue(transferAmountViewModel.sendState.value.reviewEnabled)
        // The last check before transport reads the balance from the network: an earlier
        // transfer was included in the meantime and the funds are no longer there.
        transferAnswers(false) {
            throw IllegalStateException(
                "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
                IllegalStateException("SORA2_TRANSFER_XOR_BALANCE_INSUFFICIENT"),
            )
        }
        coEvery { assetsInteractor.updateBalanceVisibleAssets() } answers {
            balances.value = listOf(
                TestAssets.xorAsset(BigDecimal("0.5")),
                TestAssets.valAsset(BigDecimal.TEN),
            )
        }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        // Before: "This transaction couldn't be prepared ... Please try again", on every try.
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_balance}",
            transferAmountViewModel.alertDialogLiveData.value,
        )
        coVerify(exactly = 1) { assetsInteractor.updateBalanceVisibleAssets() }
        // The balance on screen is the current one and the amount is checked against it.
        val state = transferAmountViewModel.sendState.value
        assertFalse(state.reviewEnabled)
        assertTrue(state.input?.error == true)
        assertEquals("text-${R.string.amount_error_no_funds}", state.input?.errorHint)
        assertFalse(state.inProgress)
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
    }

    @Test
    fun `Confirm is not held back while the balance is read again`() = runTest {
        readyToConfirm()
        var attempts = 0
        transferAnswers(false) {
            attempts += 1
            throw IllegalStateException(
                "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
                IllegalStateException("SORA2_TRANSFER_XOR_BALANCE_INSUFFICIENT"),
            )
        }
        // The read of the balances takes as long as the network takes.
        val balancesRead = CompletableDeferred<Unit>()
        coEvery { assetsInteractor.updateBalanceVisibleAssets() } coAnswers {
            balancesRead.await()
        }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()
        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        // The second Confirm is answered (with the same notice) and not passed over in silence.
        assertEquals(2, attempts)
        balancesRead.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `a balance that cannot be read again after the notice does not end the app`() = runTest {
        readyToConfirm()
        transferAnswers(false) {
            throw IllegalStateException(
                "EXTRINSIC_PRE_TRANSPORT_VALIDATION_FAILED",
                IllegalStateException("SORA2_TRANSFER_ASSET_BALANCE_INSUFFICIENT"),
            )
        }
        coEvery { assetsInteractor.updateBalanceVisibleAssets() } throws
            BoundedHttpTextException("BOUNDED_HTTP_IO")

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()

        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_balance}",
            transferAmountViewModel.alertDialogLiveData.value,
        )
        assertFalse(transferAmountViewModel.sendState.value.inProgress)
    }

    // --- the result arrives while the screen is not in front -------------------------------

    @Test
    fun `a result that arrives in the background opens its page on return and not before`() =
        runTest {
            readyToConfirm()
            transferAnswers(false) {
                // Home was pressed (or the phone locked) while the transfer was followed.
                transferAmountViewModel.onScreenPaused()
                TransactionOutcome.Confirmed(txHash, "0xblock")
            }

            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()

            // Before the fix the page was opened from the coroutine at this moment. With the
            // activity stopped that navigation is dropped, and with the PIN lock on top it
            // removes the PIN lock.
            verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
            // The toast is delivered by the screen when it is shown again.
            assertEquals(
                TransactionResultBadge.CONFIRMED,
                transferAmountViewModel.transactionSuccessEvent.value,
            )

            transferAmountViewModel.onScreenResumed()

            verify(exactly = 1) { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }

            // Shown once: a later return to the front opens nothing more.
            transferAmountViewModel.onScreenPaused()
            transferAmountViewModel.onScreenResumed()
            verify(exactly = 1) { assetsRouter.showTxResult(any(), any()) }
        }

    @Test
    fun `a sent transfer cannot be confirmed a second time while its page has not opened`() =
        runTest {
            readyToConfirm()
            var sent = 0
            transferAnswers(false) {
                sent += 1
                transferAmountViewModel.onScreenPaused()
                TransactionOutcome.Confirmed(txHash, "0xblock")
            }

            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()

            // Before the fix Confirm was still on with the same fee. The first transfer is
            // final, so the duplicate question would not have been asked for the second one.
            val state = transferAmountViewModel.sendState.value
            assertFalse(state.reviewEnabled)
            assertFalse(state.inProgress)

            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()
            assertEquals(1, sent)

            // Editing the amount and getting a new fee does not bring Confirm back either.
            transferAmountViewModel.amountChanged(BigDecimal("2"))
            advanceUntilIdle()
            assertFalse(transferAmountViewModel.sendState.value.reviewEnabled)
            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()
            assertEquals(1, sent)
        }

    @Test
    fun `a result is kept while the PIN lock is on top and shown when the screen is back`() =
        runTest {
            readyToConfirm()
            // The router opens the page only when the send screen is the current destination.
            var pinLockOnTop = true
            every { assetsRouter.showTxResult(any(), any()) } answers { !pinLockOnTop }
            transferAnswers(false) {
                transferAmountViewModel.onScreenPaused()
                TransactionOutcome.Waiting(txHash, acceptedByNode = true)
            }

            transferAmountViewModel.onConfirmClick()
            advanceUntilIdle()

            // The app returns after more than five minutes: the activity resumes this screen
            // for a moment while the PIN lock is already the current destination.
            transferAmountViewModel.onScreenResumed()
            verify(exactly = 1) { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }
            assertFalse(transferAmountViewModel.sendState.value.reviewEnabled)
            transferAmountViewModel.onScreenPaused()

            // The PIN was entered and the PIN lock removed itself.
            pinLockOnTop = false
            transferAmountViewModel.onScreenResumed()
            verify(exactly = 2) { assetsRouter.showTxResult(txHash, TransactionScreen.SEND) }

            // Opened: nothing more is asked of the router.
            transferAmountViewModel.onScreenPaused()
            transferAmountViewModel.onScreenResumed()
            verify(exactly = 2) { assetsRouter.showTxResult(any(), any()) }
        }

    @Test
    fun `a failure that arrives in the background is explained and opens nothing`() = runTest {
        readyToConfirm()
        transferAnswers(false) {
            transferAmountViewModel.onScreenPaused()
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees (e.g. account balance too low)",
            )
        }

        transferAmountViewModel.onConfirmClick()
        advanceUntilIdle()
        transferAmountViewModel.onScreenResumed()

        // The dialog is held by the view model and delivered when the screen is shown again.
        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_fee}",
            transferAmountViewModel.alertDialogLiveData.value,
        )
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
        // Nothing was sent, so Confirm is available again.
        assertTrue(transferAmountViewModel.sendState.value.reviewEnabled)
    }

    @Test
    fun `select percent option with balance more than fee EXPECT amount value is set`() = runTest {
        initViewModel(BigDecimal.valueOf(4))
        advanceUntilIdle()
        transferAmountViewModel.optionSelected(75)
        advanceUntilIdle()
        val state = transferAmountViewModel.sendState
        assertTrue(state.value.input?.amount?.equalTo(BigDecimal.valueOf(3)) == true)
    }

    @Test
    fun `select percent option with balance less than fee EXPECT amount value is set`() = runTest {
        initViewModel(0.0001.toBigDecimal())
        advanceUntilIdle()
        transferAmountViewModel.optionSelected(75)
        advanceUntilIdle()
        val state = transferAmountViewModel.sendState
        assertTrue(state.value.input?.amount?.equalTo(BigDecimal.valueOf(0.000075)) == true)
    }

    private fun initViewModel(
        balance: BigDecimal,
        firstTokenId: String? = null,
        initialSendAmount: String? = null,
        assets: Flow<List<Asset>> = flowOf(
            listOf(
                TestAssets.xorAsset(balance),
                TestAssets.valAsset(balance),
                TestAssets.pswapAsset(balance),
            )
        ),
    ) {
        every { assetsInteractor.subscribeAssetsActiveOfCurAccount() } returns assets
        transferAmountViewModel = TransferAmountViewModel(
            interactor = assetsInteractor,
            walletRouter = walletRouter,
            assetsRouter = assetsRouter,
            numbersFormatter = NumbersFormatter(),
            clipboardManager = clipboardManager,
            resourceManager = resourceManager,
            avatarGenerator = avatarGenerator,
            recipientId = recipientId,
            assetId = firstTokenId ?: TestTokens.xorToken.id,
            initialSendAmount = initialSendAmount
        )
    }

    @Test
    fun `WHEN user enters amount starting with XOR EXPECT transaction reminder is checked`() =
        runTest {
            initViewModel(
                BigDecimal.ZERO,
                firstTokenId = PolkaswapTestData.XOR_ASSET.token.id
            )
            advanceUntilIdle()
            transferAmountViewModel.amountChanged(BigDecimal.ONE)
            advanceUntilIdle()
            coVerify {
                assetsInteractor.calcTransactionFee(
                    recipientId,
                    TestTokens.xorToken,
                    BigDecimal.ONE,
                    WALLET_ID,
                )
            }
            coVerify(atMost = 1) {
                assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                    xorChange = BigDecimal.ONE,
                    networkFeeInXor = networkFee,
                )
            }
            transferAmountViewModel.onTokenChange(PolkaswapTestData.VAL_ASSET.token.id)
            advanceUntilIdle()
            transferAmountViewModel.amountChanged(BigDecimal.TEN)
            advanceUntilIdle()
            coVerify(atMost = 1) {
                assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                    networkFeeInXor = networkFee,
                )
            }
        }

    @Test
    fun `WHEN user enters amount starting without XOR EXPECT transaction reminder is checked`() =
        runTest {
            initViewModel(
                BigDecimal.ZERO,
                firstTokenId = TestAssets.pswapAsset().token.id
            )
            advanceUntilIdle()
            transferAmountViewModel.amountChanged(BigDecimal.ONE)
            advanceUntilIdle()
            coVerify(atMost = 1) {
                assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                    networkFeeInXor = any(),
                )
            }
            transferAmountViewModel.onTokenChange(PolkaswapTestData.XOR_ASSET.token.id)
            advanceUntilIdle()
            transferAmountViewModel.amountChanged(BigDecimal.TEN)
            advanceUntilIdle()
            coVerify(atMost = 1) {
                assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                    xorChange = BigDecimal.TEN,
                    networkFeeInXor = networkFee,
                )
            }
        }

    // --- audit A5: nothing on the Send screen may leave viewModelScope uncaught ---------------

    @Test
    fun `a failed local read while an amount is entered does not end the app`() = runTest {
        // The XOR reminder reads the account's XOR row; that read can fail.
        coEvery {
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(any(), any())
        } throws NoSuchElementException("0x0200 not found")

        initViewModel(BigDecimal.TEN, firstTokenId = PolkaswapTestData.XOR_ASSET.token.id)
        advanceUntilIdle()
        transferAmountViewModel.amountChanged(BigDecimal.ONE)
        advanceUntilIdle()

        // runTest reports an exception that left viewModelScope; before, this test failed here.
        coVerify { assetsInteractor.isNotEnoughXorLeftAfterTransaction(any(), any()) }
        // The collector is still alive: the next amount is processed as well.
        transferAmountViewModel.amountChanged(BigDecimal("2"))
        advanceUntilIdle()
        coVerify {
            assetsInteractor.calcTransactionFee(
                recipientId,
                TestTokens.xorToken,
                BigDecimal("2"),
                WALLET_ID,
            )
        }
    }

    @Test
    fun `an account that cannot be read while the balances arrive does not end the app`() =
        runTest {
            // No selected account is known yet when the balances arrive, and reading it fails.
            every { assetsInteractor.flowCurSoraAccount() } returns kotlinx.coroutines.flow.emptyFlow()
            coEvery { assetsInteractor.getCurSoraAccount() } throws
                IllegalArgumentException("Required value was null.")

            initViewModel(BigDecimal.TEN)
            advanceUntilIdle()

            assertFalse(transferAmountViewModel.sendState.value.reviewEnabled)
        }

    @Test
    fun `fee preview request is bound to wallet token amount and revision`() {
        val request = Sora2TransferFeePreviewRequest(
            revision = 7L,
            walletId = WALLET_ID,
            tokenId = TestTokens.xorToken.id,
            amount = BigDecimal("1.00"),
        )

        assertTrue(
            request.matches(
                currentRevision = 7L,
                currentWalletId = WALLET_ID,
                currentTokenId = TestTokens.xorToken.id,
                currentAmount = BigDecimal.ONE,
            )
        )
        assertFalse(
            request.matches(
                currentRevision = 8L,
                currentWalletId = WALLET_ID,
                currentTokenId = TestTokens.xorToken.id,
                currentAmount = BigDecimal.ONE,
            )
        )
        assertFalse(
            request.matches(
                currentRevision = 7L,
                currentWalletId = "another-wallet",
                currentTokenId = TestTokens.xorToken.id,
                currentAmount = BigDecimal.ONE,
            )
        )
    }

    private companion object {
        const val WALLET_ID = "wallet-id"
    }
}
