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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap.liquidity.add

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.mockkObject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd.LiquidityAddViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.LIQUIDITY_DATA
import jp.co.soramitsu.test_data.PolkaswapTestData.LIQUIDITY_DETAILS
import jp.co.soramitsu.test_data.PolkaswapTestData.VAL_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XSTXAU_ASSET
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify as kVerify

@FlowPreview
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AddLiquidityViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var poolsInteractor: PoolsInteractor

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var router: WalletRouter

    private lateinit var viewModel: LiquidityAddViewModel

    private fun setUpViewModel(
        secondTokenId: String?,
        firstTokenId: String? = null,
    ) {
        viewModel = LiquidityAddViewModel(
            assetsInteractor,
            assetsRouter,
            router,
            walletInteractor,
            poolsInteractor,
            NumbersFormatter(),
            resourceManager,
            coroutineManager,
            firstTokenId ?: TestTokens.xorToken.id,
            secondTokenId,
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        mockkObject(FirebaseWrapper)
        given(
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = any(),
                xorChange = any(),
            )
        ).willReturn(false)
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(LIQUIDITY_DATA))
        given(
            poolsInteractor.calcLiquidityDetails(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).willReturn(LIQUIDITY_DETAILS)
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    VAL_ASSET,
                    XSTXAU_ASSET,
                    TestAssets.xstusdAsset(BigDecimal.ONE),
                    TestAssets.pswapAsset(BigDecimal.TEN),
                    TestAssets.xstAsset(BigDecimal.TEN),
                )
            )
        )
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)
        given(coroutineManager.io).willReturn(this.coroutineContext[CoroutineDispatcher])

        given(resourceManager.getString(R.string.common_supply)).willReturn("Supply")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose tokens")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.common_insufficient_balance)).willReturn("Insufficient balance")
    }

    /** Errors a test expects the screen to report; every other test must report none. */
    private var expectedReportedErrors = 0

    @After
    fun tearDown() {
        io.mockk.verify(exactly = expectedReportedErrors) {
            FirebaseWrapper.recordException(any())
        }
    }

    @Test
    fun `init viewModel EXPECT initial button state text`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        assertEquals("Choose tokens", viewModel.addState.value.btnState.text)
    }

    @Test
    fun `init viewModel with both tokens EXPECT initial button state text`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TestTokens.valToken.id)
        advanceUntilIdle()
        assertEquals("Enter amount", viewModel.addState.value.btnState.text)
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        viewModel.slippageChanged(1.0)
        advanceUntilIdle()
        assertEquals(1.0, viewModel.stateSlippage.value, 0.01)
    }

    @Test
    fun `amount from changed EXPECT update fromAssetAmount`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals(BigDecimal("110.34"), viewModel.addState.value.assetState1?.amount)
    }

    @Test
    fun `amount to changed EXPECT update toAssetAmount`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount2Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals(BigDecimal("110.34"), viewModel.addState.value.assetState2?.amount)
    }

    @Test
    fun `set tokens from args EXPECT update visible assets`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        verify(poolsInteractor).isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id)
        verify(poolsInteractor).isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id)
        verify(poolsInteractor).subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id)
    }

    @Test
    fun `set tokens from args EXPECT update toToken`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        assertEquals(VAL_ASSET.token, viewModel.addState.value.assetState2?.token)
    }

    @Test
    fun `set tokens from args EXPECT update fromToken`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()

        assertEquals(XOR_ASSET.token, viewModel.addState.value.assetState1?.token)
    }

    @Test
    fun `option selected and INPUT desired EXPECT update amountFrom value`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Focused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertTrue(viewModel.addState.value.assetState1?.amount?.equalTo(BigDecimal(0.5)) == true)
    }

    @Test
    fun `option selected and OUTPUT desired  EXPECT update amountTo value`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount2Focused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertTrue(viewModel.addState.value.assetState2?.amount?.equalTo(BigDecimal(0.5)) == true)
    }

    @Test
    fun `pair is not exists EXPECT pairNotExists is true`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(null))
        given(
            poolsInteractor.getLiquidityData(
                XOR_ASSET.token,
                VAL_ASSET.token,
                enabled = false,
                presented = false
            )
        )
            .willReturn(LiquidityData())
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        val pair = viewModel.addState.value.pairNotExist
        assertTrue(pair == true)
    }

    @Test
    fun `reserves that cannot be read from the network do not end the app`() = runTest {
        // The pair is not in the local cache, so its reserves are read from the network, and
        // that read fails. Before, the exception left viewModelScope uncaught (runTest reports
        // that), which ends the app on a device.
        expectedReportedErrors = 1
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(null))
        given(
            poolsInteractor.getLiquidityData(
                XOR_ASSET.token,
                VAL_ASSET.token,
                enabled = false,
                presented = false
            )
        ).willAnswer { throw java.io.IOException("socket closed") }

        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        // The screen is still there with both tokens, and the amounts can be entered.
        assertEquals(VAL_ASSET.token.id, viewModel.addState.value.assetState2?.token?.id)
        assertEquals("Enter amount", viewModel.addState.value.btnState.text)
    }

    @Test
    fun `pair is exists EXPECT pairNotExists is false`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        val pair = viewModel.addState.value.pairNotExist
        assertTrue(pair == false)
    }

    @Test
    fun `assets balance are zero EXPECT clean up liquidity details`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        viewModel.onAmount1Change(BigDecimal("110.34"))
        viewModel.onAmount2Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals("1", viewModel.addState.value.prices.pair1Value)
    }

    @Test
    fun `WHEN user enters amount including one XOR EXPECT transaction reminder is checked`() =
        runTest {
            given(
                poolsInteractor.isPairEnabled(
                    inputAssetId = XOR_ASSET.token.id,
                    outputAssetId = VAL_ASSET.token.id
                )
            ).willReturn(
                flowOf(
                    true
                )
            )

            given(
                poolsInteractor.isPairPresentedInNetwork(
                    baseTokenId = XOR_ASSET.token.id,
                    tokenId = VAL_ASSET.token.id
                )
            ).willReturn(
                flowOf(
                    true
                )
            )

            setUpViewModel(
                firstTokenId = XOR_ASSET.token.id,
                secondTokenId = VAL_ASSET.token.id
            )

            advanceUntilIdle()

            viewModel.onAmount1Change(BigDecimal.TEN)

            advanceUntilIdle()

            kVerify(
                assetsInteractor,
                times(1)
            ).isNotEnoughXorLeftAfterTransaction(
                xorChange = BigDecimal.TEN,
                networkFeeInXor = LIQUIDITY_DETAILS.networkFee,
            )
        }

    // --- audit A5: one truthful result for every outcome of Confirm -------------------------

    private val txHash = "0x" + "ab".repeat(32)

    private suspend fun TestScope.readyToConfirm() {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TestTokens.valToken.id)
        advanceUntilIdle()
    }

    private suspend fun answers(confirmedDuplicate: Boolean, answer: () -> TransactionOutcome) {
        given(
            poolsInteractor.observeAddLiquidity(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(confirmedDuplicate),
            )
        ).willAnswer { answer() }
    }

    private fun confirm() = viewModel.onConfirmClick()

    private fun shownBadge() = viewModel.addState.value.confirm.confirmResult

    private fun confirmButton() = viewModel.addState.value.confirm.btnState

    private fun stubOutcomeTexts() {
        listOf(
            R.string.tx_outcome_refused_title,
            R.string.tx_outcome_note_refused_busy,
            R.string.tx_outcome_duplicate_title,
            R.string.tx_outcome_duplicate_message,
            R.string.tx_outcome_duplicate_send_again,
            R.string.tx_outcome_duplicate_wait,
        ).forEach { id ->
            given(resourceManager.getString(id)).willReturn("text-$id")
        }
    }

    @Test
    fun `a confirmed transaction shows confirmed and opens its transaction page`() = runTest {
        readyToConfirm()
        answers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

        confirm()
        runCurrent()

        assertEquals(TransactionResultBadge.CONFIRMED, shownBadge())
        advanceUntilIdle()
        assertEquals(null, shownBadge())
        kVerify(assetsRouter).showTxDetails(txHash, true)
    }

    @Test
    fun `a transaction that is still waiting is shown as submitted and never as rejected`() =
        runTest {
            readyToConfirm()
            // What a failed status read or an unfinished finality wait gives after a broadcast.
            answers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

            confirm()
            runCurrent()

            assertEquals(TransactionResultBadge.SUBMITTED, shownBadge())
            assertEquals(R.string.wallet_transaction_submitted_1, shownBadge()?.text)
            advanceUntilIdle()
            // The transaction page carries "waiting for confirmation" and follows the result.
            kVerify(assetsRouter).showTxDetails(txHash, true)
            kVerify(router, never()).returnToHubFragment()
            assertEquals(null, viewModel.alertDialogLiveData.value)
        }

    @Test
    fun `a transaction that failed on chain shows failed and opens its transaction page`() =
        runTest {
            readyToConfirm()
            answers(false) {
                TransactionOutcome.FailedOnChain(
                    txHash = txHash,
                    blockHash = "0xblock",
                    reason = TransactionChainFailure.EXECUTION_FAILED,
                    detail = "PoolXYK.InsufficientLiquidity",
                )
            }

            confirm()
            runCurrent()

            assertEquals(TransactionResultBadge.FAILED, shownBadge())
            advanceUntilIdle()
            kVerify(assetsRouter).showTxDetails(txHash, true)
        }

    @Test
    fun `a transaction the node refused is a failure with its reason and is not submitted`() =
        runTest {
            readyToConfirm()
            stubOutcomeTexts()
            answers(false) {
                TransactionOutcome.RefusedByNode(
                    txHash = txHash,
                    reason = TransactionNodeRefusal.NETWORK_BUSY,
                    nodeMessage = "Immediately Dropped",
                )
            }

            confirm()
            runCurrent()

            assertEquals(TransactionResultBadge.NOT_SENT, shownBadge())
            advanceUntilIdle()
            assertEquals(
                "text-${R.string.tx_outcome_refused_title}" to
                    "text-${R.string.tx_outcome_note_refused_busy}",
                viewModel.alertDialogLiveData.value,
            )
            kVerify(assetsRouter, never()).showTxDetails(any(), any())
            // The dialog is on this screen, so the screen stays and offers Confirm again.
            kVerify(router, never()).returnToHubFragment()
            assertTrue(confirmButton().enabled)
            assertFalse(confirmButton().loading)
        }

    @Test
    fun `the same action still waiting needs a deliberate second confirmation`() = runTest {
        readyToConfirm()
        stubOutcomeTexts()
        answers(false) { throw DuplicateWaitingTransactionException(txHash, 1L) }
        answers(true) { TransactionOutcome.Waiting("0x" + "cd".repeat(32), true) }

        confirm()
        advanceUntilIdle()

        val question = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_duplicate_title}", question.title)
        assertEquals(null, shownBadge())
        kVerify(assetsRouter, never()).showTxDetails(any(), any())
        assertTrue(confirmButton().enabled)

        question.onConfirm()
        advanceUntilIdle()
        kVerify(assetsRouter).showTxDetails("0x" + "cd".repeat(32), true)
    }
}
