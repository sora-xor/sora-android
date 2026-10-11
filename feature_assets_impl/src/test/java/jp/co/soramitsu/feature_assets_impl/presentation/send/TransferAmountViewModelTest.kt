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
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.Sora2TransferFeePreviewRequest
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.TransferAmountViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
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
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }
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
        every { assetsRouter.showTxDetails(any(), any()) } returns Unit
        initViewModel(
            balance = BigDecimal.TEN,
            initialSendAmount = BigDecimal.ONE.toPlainString(),
        )
        advanceUntilIdle()
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
        verify { assetsRouter.showTxDetails(txHash, true) }
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
            verify { assetsRouter.showTxDetails(txHash, true) }
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
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }
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
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }
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
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }
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
        verify(exactly = 0) { assetsRouter.showTxDetails(any(), any()) }

        question.onConfirm()
        advanceUntilIdle()
        verify { assetsRouter.showTxDetails("0x" + "cd".repeat(32), true) }
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
        initialSendAmount: String? = null
    ) {
        every { assetsInteractor.subscribeAssetsActiveOfCurAccount() } returns flowOf(
            listOf(
                TestAssets.xorAsset(balance),
                TestAssets.valAsset(balance),
                TestAssets.pswapAsset(balance),
            )
        )
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
