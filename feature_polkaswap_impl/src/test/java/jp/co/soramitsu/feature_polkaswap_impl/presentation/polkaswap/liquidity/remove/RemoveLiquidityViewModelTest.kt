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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap.liquidity.remove

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove.LiquidityRemoveRoutes
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove.LiquidityRemoveViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.COMMON_POOL_DATA
import jp.co.soramitsu.test_data.PolkaswapTestData.NETWORK_FEE
import jp.co.soramitsu.test_data.PolkaswapTestData.VAL_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET_ZERO_BALANCE
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.willSuspendableAnswer

@FlowPreview
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class RemoveLiquidityViewModelTest {

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
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var demeterFarmingInteractor:
        jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor

    private lateinit var viewModel: LiquidityRemoveViewModel

    private val poolShareAfterTxText = "Pool share"
    private val sbApyText = "SB APY"
    private val networkFeeText = "network fee"

    private fun setUpViewModel(
        firstTokenId: String? = null,
        secondTokenId: String? = null
    ) {
        viewModel = LiquidityRemoveViewModel(
            assetsInteractor = assetsInteractor,
            assetsRouter = assetsRouter,
            router = router,
            walletInteractor = walletInteractor,
            poolsInteractor = poolsInteractor,
            numbersFormatter = NumbersFormatter(),
            resourceManager = resourceManager,
            token1IdStart = firstTokenId ?: TestTokens.xorToken.id,
            token2IdStart = secondTokenId ?: TestTokens.valToken.id,
            demeterFarmingInteractor = demeterFarmingInteractor,
        )
    }

    @Before
    fun setUp() = runTest {
        given(resourceManager.getString(R.string.xor)).willReturn("XOR")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.pool_button_remove)).willReturn("Remove")
        given(resourceManager.getString(R.string.polkaswap_insufficient_balance)).willReturn("Insufficient %s balance")
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    VAL_ASSET
                )
            )
        )

        given(
            poolsInteractor.fetchRemoveLiquidityNetworkFee(
                XOR_ASSET.token,
                VAL_ASSET.token
            )
        ).willReturn(NETWORK_FEE)

        given(
            poolsInteractor.subscribePoolCacheOfCurAccount(
                XOR_ASSET.token.id,
                VAL_ASSET.token.id
            )
        ).willReturn(flowOf(COMMON_POOL_DATA))
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)

        given(
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = any(),
                xorChange = any(),
            )
        ).willReturn(false)
    }

    @Test
    fun `init viewModel EXPECT initial button state text`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        assertEquals("Enter amount", viewModel.removeState.btnState.text)
    }

    @Test
    fun `repeated confirmation removes liquidity only once while pending`() = runTest {
        val submitted = CompletableDeferred<TransactionOutcome>()
        val poolUpdates = MutableStateFlow(COMMON_POOL_DATA)
        given(poolsInteractor.subscribePoolCacheOfCurAccount(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(poolUpdates)
        doSuspendableAnswer { submitted.await() }.whenever(poolsInteractor)
            .removeLiquidity(any(), any(), any(), any(), any(), any(), any())
        setUpViewModel()
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("0.5"))
        advanceUntilIdle()
        // The screen is in front, so the transaction page opens when the result arrives.
        pageOpens { true }
        viewModel.onScreenResumed()

        viewModel.onConfirmClick()
        viewModel.onConfirmClick()
        advanceUntilIdle()
        assertTrue(viewModel.removeState.confirm.btnState.loading)

        poolUpdates.value = COMMON_POOL_DATA.copy(
            user = COMMON_POOL_DATA.user!!.copy(poolProvidersBalance = BigDecimal("11")),
        )
        advanceUntilIdle()
        assertTrue(viewModel.removeState.confirm.btnState.loading)
        assertFalse(viewModel.removeState.confirm.btnState.enabled)
        viewModel.onConfirmClick()
        advanceUntilIdle()
        submitted.complete(TransactionOutcome.Confirmed("removal-hash", "0xblock"))
        advanceUntilIdle()

        verify(poolsInteractor, times(1))
            .removeLiquidity(any(), any(), any(), any(), any(), any(), any())
        verify(assetsRouter, times(1))
            .showTxResult("removal-hash", TransactionScreen.REMOVE_LIQUIDITY)
        assertFalse(viewModel.removeState.confirm.btnState.loading)
    }

    @Test
    fun `init viewModel EXPECT xor token setted`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        assertEquals("XOR", viewModel.removeState.assetState1?.token?.symbol)
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        viewModel.slippageChanged(1.0)
        assertEquals(1.0, viewModel.removeState.slippage, 0.1)
    }

    @Test
    fun `amount from changed EXPECT update toAssetAmount`() = runTest {
        setUpViewModel()
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal(0.5))
        advanceUntilIdle()

        assertEquals(BigDecimal(0.5), viewModel.removeState.assetState1?.amount)
        assertTrue(viewModel.removeState.assetState2?.amount?.equalTo(BigDecimal(0.5)) == true)
        assertEquals("Remove", viewModel.removeState.btnState.text)
        assertTrue(viewModel.removeState.btnState.enabled)
    }

    @Test
    fun `amount to changed EXPECT update fromAssetAmount`() = runTest {
        val amount = BigDecimal.valueOf(0.35)

        setUpViewModel()
        advanceUntilIdle()
        viewModel.onAmount2Change(BigDecimal("0.35"))
        advanceUntilIdle()

        assertTrue(viewModel.removeState.assetState1?.amount?.equalTo(amount) == true)
        assertEquals(BigDecimal("0.35"), viewModel.removeState.assetState2?.amount)
        assertTrue(viewModel.removeState.btnState.enabled)
    }

    @Test
    fun `amount from changed EXPECT button disabled and insufficient balance text added`() =
        runTest {
            val amount = BigDecimal.valueOf(0.5)

            given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
                flowOf(
                    listOf(
                        XOR_ASSET_ZERO_BALANCE,
                        VAL_ASSET
                    )
                )
            )
            setUpViewModel()
            advanceUntilIdle()
            viewModel.onAmount1Change(BigDecimal("0.5"))
            advanceUntilIdle()

            assertEquals(
                "Insufficient ${XOR_ASSET_ZERO_BALANCE.token.symbol} balance",
                viewModel.removeState.btnState.text,
            )
            assertFalse(viewModel.removeState.btnState.enabled)
        }

    @Test
    fun `WHEN user enters amount including one XOR EXPECT transaction reminder is checked`() =
        runTest {
            setUpViewModel(
                firstTokenId = TestAssets.xorAsset().token.id,
                secondTokenId = TestAssets.valAsset().token.id
            )

            advanceUntilIdle()

            viewModel.onAmount1Change(BigDecimal.TEN)

            advanceUntilIdle()

            verify(
                mock = assetsInteractor,
                mode = times(1)
            ).isNotEnoughXorLeftAfterTransaction(
                xorChange = -BigDecimal.ONE,
                networkFeeInXor = NETWORK_FEE,
            )
        }

    // --- audit A5: one truthful result for every outcome of Confirm -------------------------

    private val txHash = "0x" + "ab".repeat(32)

    private suspend fun TestScope.readyToConfirm() {
        setUpViewModel()
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal(0.5))
        advanceUntilIdle()
        // The screen is the screen in front, as it is when Confirm is tapped, and the router
        // opens the transaction page when asked.
        pageOpens { true }
        viewModel.onScreenResumed()
    }

    /** What the router answers when asked to open the transaction page. */
    private fun pageOpens(answer: () -> Boolean) {
        org.mockito.Mockito.lenient()
            .`when`(assetsRouter.showTxResult(any(), any()))
            .thenAnswer { answer() }
    }

    private suspend fun answers(confirmedDuplicate: Boolean, answer: () -> TransactionOutcome) {
        given(
            poolsInteractor.removeLiquidity(
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

    private fun shownBadge() = viewModel.removeState.confirm.confirmResult

    private fun confirmButton() = viewModel.removeState.confirm.btnState

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
        verify(assetsRouter).showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)
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
            verify(assetsRouter).showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)
            verify(router, never()).returnToHubFragment()
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
            verify(assetsRouter).showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)
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
            verify(assetsRouter, never()).showTxResult(any(), any())
            // The dialog is on this screen, so the screen stays and offers Confirm again.
            verify(router, never()).returnToHubFragment()
            assertTrue(confirmButton().enabled)
            assertFalse(confirmButton().loading)
        }

    @Test
    fun `a network that needs a newer app says so and offers the update instead of another try`() =
        runTest {
            listOf(
                R.string.network_fee_unavailable_short,
                R.string.network_fee_needs_app_update,
                R.string.common_update,
                R.string.common_close,
            ).forEach { id ->
                given(resourceManager.getString(id)).willReturn("text-$id")
            }
            // What the signing runtime raises for a live signed extension the app cannot write.
            given(
                poolsInteractor.fetchRemoveLiquidityNetworkFee(XOR_ASSET.token, VAL_ASSET.token)
            ).willSuspendableAnswer {
                throw IllegalStateException(
                    "SORA2_FEE_QUOTE_FAILED",
                    IllegalStateException(NetworkFeeUnavailable.APP_UPDATE_REQUIRED_CODE),
                )
            }

            setUpViewModel()
            advanceUntilIdle()

            // Before the fix: "can't be checked right now, try again in a moment", for ever.
            val question = checkNotNull(viewModel.confirmDialogLiveData.value)
            assertEquals("text-${R.string.network_fee_needs_app_update}", question.message)
            assertEquals("text-${R.string.common_update}", question.confirmText)
            assertEquals("text-${R.string.common_close}", question.dismissText)
            assertEquals(null, viewModel.openAppStoreLiveData.value)

            question.onConfirm()
            advanceUntilIdle()

            // The store page of the app is asked for, the screen that cannot load is left, and
            // the fee is not asked again.
            assertEquals(Unit, viewModel.openAppStoreLiveData.value)
            assertEquals(Unit, viewModel.navigationPop.value)
            verify(poolsInteractor, times(1))
                .fetchRemoveLiquidityNetworkFee(XOR_ASSET.token, VAL_ASSET.token)
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
        verify(assetsRouter, never()).showTxResult(any(), any())
        assertTrue(confirmButton().enabled)

        question.onConfirm()
        advanceUntilIdle()
        verify(assetsRouter).showTxResult("0x" + "cd".repeat(32), TransactionScreen.REMOVE_LIQUIDITY)
    }

    // --- the result arrives while the screen is not in front -------------------------------

    @Test
    fun `a result that arrives in the background opens its page on return and not before`() =
        runTest {
            readyToConfirm()
            answers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

            confirm()
            runCurrent()
            // Home was pressed (or the phone locked) while the result mark was shown.
            viewModel.onScreenPaused()
            advanceUntilIdle()

            // Before the fix the page was opened from the coroutine at this moment. With the
            // activity stopped that navigation is dropped, and with the PIN lock on top it
            // removes the PIN lock.
            verify(assetsRouter, never()).showTxResult(any(), any())
            // Until the page opens the confirm screen says what happened and Confirm is off.
            assertEquals(TransactionResultBadge.CONFIRMED, shownBadge())
            assertFalse(confirmButton().enabled)
            assertFalse(confirmButton().loading)

            viewModel.onScreenResumed()

            verify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)

            // Shown once: a later return to the front opens nothing more.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            verify(assetsRouter, times(1)).showTxResult(any(), any())
        }

    @Test
    fun `a sent transaction cannot be confirmed a second time while its page has not opened`() =
        runTest {
            readyToConfirm()
            var sent = 0
            answers(false) {
                sent += 1
                TransactionOutcome.Waiting(txHash, acceptedByNode = true)
            }

            confirm()
            runCurrent()
            viewModel.onScreenPaused()
            advanceUntilIdle()

            confirm()
            viewModel.onReviewClick()
            confirm()
            advanceUntilIdle()

            assertEquals(1, sent)
            assertFalse(confirmButton().enabled)
        }

    @Test
    fun `a result is kept while the PIN lock is on top and shown when the screen is back`() =
        runTest {
            readyToConfirm()
            // The router opens the page only when this screen is the current destination.
            var pinLockOnTop = true
            pageOpens { !pinLockOnTop }
            answers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

            confirm()
            runCurrent()
            viewModel.onScreenPaused()
            advanceUntilIdle()

            // The app returns after more than five minutes: the activity resumes this screen
            // for a moment while the PIN lock is already the current destination.
            viewModel.onScreenResumed()
            verify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)
            assertEquals(TransactionResultBadge.SUBMITTED, shownBadge())
            assertFalse(confirmButton().enabled)
            viewModel.onScreenPaused()

            // The PIN was entered and the PIN lock removed itself.
            pinLockOnTop = false
            viewModel.onScreenResumed()
            verify(assetsRouter, times(2)).showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)

            // Opened: nothing more is asked of the router.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            verify(assetsRouter, times(2)).showTxResult(any(), any())
        }

    @Test
    fun `a failure that arrives in the background is explained and opens nothing`() = runTest {
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
        viewModel.onScreenPaused()
        advanceUntilIdle()
        viewModel.onScreenResumed()

        // The dialog is held by the view model and delivered when the screen is shown again.
        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_busy}",
            viewModel.alertDialogLiveData.value,
        )
        verify(assetsRouter, never()).showTxResult(any(), any())
        assertEquals(null, shownBadge())
        assertTrue(confirmButton().enabled)
    }

    @Test
    fun `an update of the pool during the wait does not bring Confirm back`() = runTest {
        readyToConfirm()
        val result = kotlinx.coroutines.CompletableDeferred<TransactionOutcome>()
        var sent = 0
        given(
            poolsInteractor.removeLiquidity(any(), any(), any(), any(), any(), any(), eq(false))
        ).willSuspendableAnswer {
            sent += 1
            result.await()
        }

        confirm()
        runCurrent()
        assertFalse(confirmButton().enabled)
        assertTrue(confirmButton().loading)

        // The pool is read again with every block and the buttons are rebuilt each time. The
        // same happens here through a change of the amount.
        viewModel.onAmount1Change(BigDecimal(0.25))
        advanceUntilIdle()

        // Before the fix Confirm was back, enabled and without its progress mark, while the
        // first withdrawal was still being followed.
        assertFalse(confirmButton().enabled)
        assertTrue(confirmButton().loading)
        confirm()
        runCurrent()
        assertEquals(1, sent)

        result.complete(TransactionOutcome.Confirmed(txHash, "0xblock"))
        advanceUntilIdle()

        verify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.REMOVE_LIQUIDITY)
        // The page takes this screen's place; nothing more is sent from here.
        viewModel.onAmount1Change(BigDecimal(0.5))
        advanceUntilIdle()
        assertFalse(confirmButton().enabled)
        confirm()
        runCurrent()
        assertEquals(1, sent)
    }

    @Test fun `removal hints and toolbar follow the active step`() = runTest {
        setUpViewModel(); advanceUntilIdle()
        assertEquals(LiquidityRemoveRoutes.start, viewModel.startScreen())
        viewModel.setCurDestination(LiquidityRemoveRoutes.start)
        viewModel.onNavIcon(); assertTrue(viewModel.removeState.hintVisible)
        viewModel.dismissHint(); assertFalse(viewModel.removeState.hintVisible)
        for ((route, title) in listOf(LiquidityRemoveRoutes.start to R.string.remove_liquidity_title,
            LiquidityRemoveRoutes.slippage to R.string.slippage, LiquidityRemoveRoutes.confirm to R.string.remove_pool_confirmation_title)) {
            viewModel.setCurDestination(route); assertEquals(title, viewModel.toolbarState.value!!.basic.title)
        }
        viewModel.onNavIcon(); org.junit.Assert.assertNotNull(viewModel.navigationPop.value)
        viewModel.onMenuItem(jp.co.soramitsu.ui_core.component.toolbar.Action.Close())
    }
    @Test fun `percentage controls remove the selected fraction of both pool assets`() = runTest {
        setUpViewModel(); advanceUntilIdle()
        for (percent in listOf(25, 75, 100)) {
            viewModel.onPercentClick(percent); advanceUntilIdle()
            val factor = BigDecimal(percent).divide(BigDecimal(100))
            assertEquals(0, COMMON_POOL_DATA.user!!.basePooled.multiply(factor).compareTo(viewModel.removeState.assetState1!!.amount))
            assertEquals(0, COMMON_POOL_DATA.user!!.targetPooled.multiply(factor).compareTo(viewModel.removeState.assetState2!!.amount))
        }
        viewModel.onSlippageClick(); viewModel.slippageChanged(2.0)
        assertEquals(2.0, viewModel.removeState.slippage, 0.0)
        assertTrue(viewModel.removeState.btnState.enabled)
    }
    @Test fun `output entry is capped at the owned amount and derives matching input`() = runTest {
        setUpViewModel(); advanceUntilIdle()
        viewModel.onAmount2Change(COMMON_POOL_DATA.user!!.targetPooled.multiply(BigDecimal.TEN)); advanceUntilIdle()
        assertEquals(0, COMMON_POOL_DATA.user!!.targetPooled.compareTo(viewModel.removeState.assetState2!!.amount))
        assertEquals(0, COMMON_POOL_DATA.user!!.basePooled.compareTo(viewModel.removeState.assetState1!!.amount))
    }
}
