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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.anyNonNull
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.network.BoundedHttpTextException
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap.SwapViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.test_data.PolkaswapTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify as kVerify
import org.mockito.kotlin.willSuspendableAnswer

@FlowPreview
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SwapViewModelTest {

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
    private lateinit var swapInteractor: SwapInteractor

    private val numbersFormatter: NumbersFormatter = NumbersFormatter()

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: SwapViewModel

    private lateinit var assets: List<Asset>

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    private val networkFee = BigDecimal.TEN

    /** When set, the fee query fails with it (for example while the SORA endpoint is down). */
    private var feeFailure: Throwable? = null

    private val assetsListItems: List<AssetItemCardState> by lazy {
        mapAssetsToCardState(assets, numbersFormatter)
    }

    private suspend fun initViewModel(
        xorBalance: BigDecimal = BigDecimal.ONE,
        valBalance: BigDecimal = BigDecimal.ONE,
        pswapBalance: BigDecimal = BigDecimal.ONE,
        firstTokenId: String? = null,
        secondTokenId: String? = null,
        emptyCatalog: Boolean = false,
        poolUpdates: Flow<String> = flowOf(""),
    ) = runTest {
        assets = if (emptyCatalog) emptyList() else listOf(
            TestAssets.xorAsset(xorBalance),
            TestAssets.valAsset(valBalance),
            TestAssets.pswapAsset(pswapBalance)
        )
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(flowOf(assets))
        given(
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = any(),
                xorChange = anyOrNull(),
            )
        ).willReturn(false)

        setUpAfterViewModelInit(poolUpdates)
        viewModel = SwapViewModel(
            assetsInteractor,
            walletInteractor,
            swapInteractor,
            numbersFormatter,
            resourceManager,
            assetsRouter,
            firstTokenId ?: TestAssets.xorAsset().token.id,
            secondTokenId ?: "",
        )
    }

    private fun setUpAfterViewModelInit(poolUpdates: Flow<String>) = runTest {
        given(swapInteractor.observeSwap()).willReturn(
            flow {
                emit(true)
            }
        )
        given(swapInteractor.observeSelectedMarket()).willReturn(
            flow {
                emit(Market.TBC)
            }
        )
        given(swapInteractor.observePoolReserves()).willReturn(poolUpdates)
        assets.forEach {
            given(assetsInteractor.getAssetOrThrow(it.token.id)).willReturn(it)
        }
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)
        given(swapInteractor.fetchSwapNetworkFee(anyNonNull())).willAnswer {
            feeFailure?.let { throw it }
            networkFee
        }
        given(swapInteractor.getPolkaswapDisclaimerVisibility()).willReturn(flowOf(true))
        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose token")
        given(resourceManager.getString(R.string.wallet_assets_unavailable)).willReturn("Assets unavailable")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.polkaswap_pool_not_created)).willReturn("Pool not created")
        given(resourceManager.getString(R.string.review)).willReturn("Review")
        given(resourceManager.getString(R.string.polkaswap_insufficient_balance)).willReturn("Insufficient balance")
        given(resourceManager.getString(R.string.polkaswap_insufficient_liqudity)).willReturn("Insufficient liquidity")
    }

    @Test
    fun `missing asset catalog keeps swap visible and disabled`() = runTest {
        initViewModel(emptyCatalog = true)
        advanceUntilIdle()
        val state = viewModel.swapMainState.value
        assertEquals(null, state.tokenFromState)
        assertEquals(null, state.tokenToState)
        org.junit.Assert.assertTrue(state.assetCatalogUnavailable)
        assertEquals("Assets unavailable", state.swapButtonState.text)
        assertFalse(state.swapButtonState.enabled)
        assertFalse(state.confirmButtonState.enabled)
        kVerify(assetsInteractor, org.mockito.kotlin.never()).getAssetOrThrow(any())
    }

    @Test
    fun init() = runTest {
        initViewModel()
        advanceUntilIdle()
        val buttonState = ButtonState(text = "Choose token", enabled = false, loading = false)
        viewModel.swapMainState.value.slippage.let {
            assertEquals(0.5, it, 0.1)
        }
        viewModel.navigationDisclaimerEvent.getOrAwaitValue()

        assertEquals(buttonState, viewModel.swapMainState.value.swapButtonState)
    }

    @Test
    fun `preselected VAL to XOR loads markets and can review a quote`() = runTest {
        given(swapInteractor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
            .willReturn(setOf(Market.SMART, Market.XYK))
        given(swapInteractor.calcDetails(any(), any(), any(), any(), any(), anyDouble()))
            .willReturn(
                SwapDetails(
                    BigDecimal.TEN,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    networkFee,
                    PoolDex(0, TestTokens.xorToken.id, "XOR"),
                    null,
                )
            )

        initViewModel(
            xorBalance = BigDecimal("100"),
            valBalance = BigDecimal("100"),
            firstTokenId = TestTokens.valToken.id,
            secondTokenId = TestTokens.xorToken.id,
        )
        advanceUntilIdle()

        kVerify(swapInteractor).fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id)
        assertEquals("Enter amount", viewModel.swapMainState.value.swapButtonState.text)

        viewModel.onFromAmountChange(BigDecimal.ONE)
        advanceUntilIdle()

        assertEquals(BigDecimal.TEN, viewModel.swapMainState.value.tokenToState?.amount)
        assertEquals("Review", viewModel.swapMainState.value.swapButtonState.text)
        assertTrue(viewModel.swapMainState.value.swapButtonState.enabled)
    }

    @Test
    fun `repeated confirmation submits a swap only once while pending`() = runTest {
        val submitted = CompletableDeferred<TransactionOutcome>()
        doSuspendableAnswer { submitted.await() }.whenever(swapInteractor)
            .swap(any(), any(), any(), any(), any(), any(), any(), any(), any())
        given(swapInteractor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
            .willReturn(setOf(Market.SMART, Market.XYK))
        given(swapInteractor.calcDetails(any(), any(), any(), any(), any(), anyDouble()))
            .willReturn(
                SwapDetails(
                    BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    networkFee, PoolDex(0, TestTokens.xorToken.id, "XOR"), null,
                )
            )
        initViewModel(
            xorBalance = BigDecimal("100"),
            valBalance = BigDecimal("100"),
            firstTokenId = TestTokens.valToken.id,
            secondTokenId = TestTokens.xorToken.id,
        )
        advanceUntilIdle()
        viewModel.onFromAmountChange(BigDecimal.ONE)
        advanceUntilIdle()
        assertTrue(viewModel.swapMainState.value.confirmButtonState.enabled)
        // The swap screen is in front, so the transaction page opens when the result arrives.
        pageOpens { true }
        viewModel.onScreenResumed()

        viewModel.onConfirmClicked()
        viewModel.onConfirmClicked()
        advanceUntilIdle()
        assertTrue(viewModel.swapMainState.value.confirmButtonState.loading)
        submitted.complete(TransactionOutcome.Confirmed("swap-hash", "0xblock"))
        advanceUntilIdle()

        kVerify(swapInteractor, times(1))
            .swap(any(), any(), any(), any(), any(), any(), any(), any(), any())
        kVerify(assetsRouter, times(1)).showTxResult("swap-hash", TransactionScreen.SWAP)
        assertFalse(viewModel.swapMainState.value.confirmButtonState.loading)
    }

    // A swap that was not sent (the store version returned no transaction hash for it) leaves
    // Confirm available again after the quote is refreshed.
    @Test
    fun `swap that was not sent can be retried after refreshing the quote`() = runTest {
        stubOutcomeTexts()
        var attempts = 0
        given(swapInteractor.swap(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willAnswer {
                attempts += 1
                if (attempts == 1) throw BoundedHttpTextException("BOUNDED_HTTP_IO")
                TransactionOutcome.Confirmed("retry-hash", "0xblock")
            }
        given(swapInteractor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
            .willReturn(setOf(Market.SMART, Market.XYK))
        given(swapInteractor.calcDetails(any(), any(), any(), any(), any(), anyDouble()))
            .willReturn(
                SwapDetails(
                    BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    networkFee, PoolDex(0, TestTokens.xorToken.id, "XOR"), null,
                )
            )
        initViewModel(
            xorBalance = BigDecimal("100"),
            valBalance = BigDecimal("100"),
            firstTokenId = TestTokens.valToken.id,
            secondTokenId = TestTokens.xorToken.id,
        )
        advanceUntilIdle()
        viewModel.onFromAmountChange(BigDecimal.ONE)
        advanceUntilIdle()
        pageOpens { true }
        viewModel.onScreenResumed()
        viewModel.onConfirmClicked()
        advanceUntilIdle()
        kVerify(assetsRouter, org.mockito.kotlin.never()).showTxResult(any(), any())

        viewModel.onFromAmountChange(BigDecimal("2"))
        advanceUntilIdle()
        assertTrue(viewModel.swapMainState.value.confirmButtonState.enabled)
        viewModel.onConfirmClicked()
        advanceUntilIdle()

        kVerify(swapInteractor, times(2))
            .swap(any(), any(), any(), any(), any(), any(), any(), any(), any())
        kVerify(assetsRouter).showTxResult("retry-hash", TransactionScreen.SWAP)
    }

    @Test
    fun `reversing XOR to VAL reloads markets for VAL to XOR`() = runTest {
        given(swapInteractor.fetchAvailableSources(TestTokens.xorToken.id, TestTokens.valToken.id))
            .willReturn(setOf(Market.SMART, Market.XYK))
        given(swapInteractor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
            .willReturn(setOf(Market.SMART, Market.TBC))
        initViewModel(
            firstTokenId = TestTokens.xorToken.id,
            secondTokenId = TestTokens.valToken.id,
        )
        advanceUntilIdle()

        viewModel.onTokensSwapClick()
        advanceUntilIdle()

        kVerify(swapInteractor).fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id)
        viewModel.onMarketClick()
        assertEquals(
            listOf(Market.SMART, Market.TBC),
            viewModel.swapMainState.value.selectMarketState?.second,
        )
        assertEquals("Enter amount", viewModel.swapMainState.value.swapButtonState.text)
    }

    @Test
    fun `empty markets are retried on the next block after pool sync`() = runTest {
        val poolUpdates = MutableSharedFlow<String>()
        given(swapInteractor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
            .willReturn(null)
        initViewModel(
            firstTokenId = TestTokens.valToken.id,
            secondTokenId = TestTokens.xorToken.id,
            poolUpdates = poolUpdates,
        )
        advanceUntilIdle()
        assertEquals("Pool not created", viewModel.swapMainState.value.swapButtonState.text)

        given(swapInteractor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
            .willReturn(setOf(Market.SMART, Market.XYK))
        clearInvocations(swapInteractor)
        poolUpdates.emit("next block")
        advanceUntilIdle()

        kVerify(swapInteractor)
            .fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id)
        assertEquals("Enter amount", viewModel.swapMainState.value.swapButtonState.text)
    }

    @Test
    fun fromAndToAssetsSelected() = runTest {
        initViewModel()
        advanceUntilIdle()
        val fromAsset = assetsListItems.first()
        val toAsset = assetsListItems[1]

        viewModel.fromAssetSelected(fromAsset.tokenId)
        advanceUntilIdle()
        assertEquals(viewModel.swapMainState.value.tokenFromState?.token, assets[0].token)
        assertFalse(viewModel.swapMainState.value.swapButtonState.enabled)

        viewModel.toAssetSelected(toAsset.tokenId)
        advanceUntilIdle()
        assertEquals(viewModel.swapMainState.value.tokenToState?.token, assets[1].token)
        assertFalse(viewModel.swapMainState.value.swapButtonState.enabled)
    }

    @Test
    fun fromInputPercentCalledWithLowBalance() = runTest {
        initViewModel()
        given(
            swapInteractor.fetchAvailableSources(
                assetsListItems.first().tokenId,
                assetsListItems.last().tokenId,
            )
        ).willReturn(
            setOf(Market.SMART)
        )
        given(
            swapInteractor.checkSwapBalances(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).willReturn(TestTokens.xorToken)
        given(
            swapInteractor.calcDetails(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyDouble(),
            )
        ).willReturn(
            SwapDetails(
                BigDecimal("0.02"),
                BigDecimal("12.2"),
                BigDecimal("18.3"),
                BigDecimal("22.33"),
                networkFee,
                PoolDex(0, assetsListItems.first().tokenId, assetsListItems.last().tokenId),
                null,
            )
        )
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first().tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(100)
        advanceUntilIdle()
        assertEquals(BigDecimal.ONE, viewModel.swapMainState.value.tokenFromState?.amount)
        val btn = viewModel.swapMainState.value.swapButtonState
        assertEquals("Insufficient balance", btn.text)
        assertEquals(false, btn.enabled)
    }

    @Test
    fun fromInputPercentCalledWithXor() = runTest {
        initViewModel(xorBalance = 100.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first().tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(100)
        assertEquals(BigDecimal(90.0), viewModel.swapMainState.value.tokenFromState?.amount)
    }

    @Test
    fun fromInputPercentHalfCalledWithXor() = runTest {
        initViewModel(xorBalance = 100.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first().tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(50)
        assertEquals(BigDecimal(50.0).setScale(18), viewModel.swapMainState.value.tokenFromState?.amount)
    }

    @Test
    fun `percent click 50 balance 16 amount less then fee`() = runTest {
        initViewModel(xorBalance = 16.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first().tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(50)
        assertEquals(BigDecimal(8.0).setScale(18), viewModel.swapMainState.value.tokenFromState?.amount)
    }

    @Test
    fun `percent click 50 balance 50`() = runTest {
        initViewModel(xorBalance = 50.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first().tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(50)
        assertEquals(BigDecimal(25).setScale(18), viewModel.swapMainState.value.tokenFromState?.amount)
    }

    @Test
    fun `percent click 50 balance val 100`() = runTest {
        initViewModel(xorBalance = 100.toBigDecimal(), valBalance = 100.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems[1].tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(100)
        assertEquals(BigDecimal(100.0), viewModel.swapMainState.value.tokenFromState?.amount)
    }

    @Test
    fun `onTokensSwapClicked called`() = runTest {
        initViewModel(xorBalance = 100.toBigDecimal(), valBalance = 100.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems[1].tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.onTokensSwapClick()
        assertEquals(assetsListItems.last().tokenId, viewModel.swapMainState.value.tokenFromState?.token?.id)
        assertEquals(assetsListItems[1].tokenId, viewModel.swapMainState.value.tokenToState?.token?.id)
    }

    @Test
    fun `WHEN user enters amount starting with XOR EXPECT transaction reminder is checked`() =
        runTest {
            initViewModel(
                firstTokenId = PolkaswapTestData.XOR_ASSET.token.id,
                secondTokenId = PolkaswapTestData.VAL_ASSET.token.id
            )

            advanceUntilIdle()

            viewModel.onFromAmountChange(BigDecimal.ONE)

            advanceUntilIdle()

            kVerify(
                assetsInteractor,
                atLeastOnce()
            ).isNotEnoughXorLeftAfterTransaction(
                xorChange = BigDecimal.ONE,
                networkFeeInXor = networkFee,
            )

            viewModel.onToAmountChange(BigDecimal.ONE)

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                atLeast(1)
            ).isNotEnoughXorLeftAfterTransaction(
                xorChange = BigDecimal.ONE,
                networkFeeInXor = networkFee,
            )

            viewModel.fromAssetSelected(TestAssets.pswapAsset().token.id)

            advanceUntilIdle()

            viewModel.onFromAmountChange(BigDecimal.TEN)

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                atLeast(1)
            ).isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = networkFee,
                xorChange = null,
            )
        }

    @Test
    fun `a failed local read while recalculating neither ends the app nor leaves a spinner`() =
        runTest {
            initViewModel(
                firstTokenId = PolkaswapTestData.XOR_ASSET.token.id,
                secondTokenId = PolkaswapTestData.VAL_ASSET.token.id
            )
            // The XOR reminder reads the account's XOR row; that read can fail. Before, the
            // exception left viewModelScope uncaught (runTest reports that), which ends the app.
            given(
                assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                    networkFeeInXor = any(),
                    xorChange = anyOrNull(),
                )
            ).willAnswer { throw NoSuchElementException("0x0200 not found") }
            advanceUntilIdle()

            viewModel.onFromAmountChange(BigDecimal.ONE)
            advanceUntilIdle()

            assertEquals(false, viewModel.swapMainState.value.swapButtonState.loading)
            // The collector is still alive: a later amount is recalculated as well.
            viewModel.onFromAmountChange(BigDecimal.TEN)
            advanceUntilIdle()
            kVerify(assetsInteractor, atLeast(2)).isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = any(),
                xorChange = anyOrNull(),
            )
        }

    @Test
    fun `WHEN user enters amount starting without XOR EXPECT transaction reminder is checked`() =
        runTest {
            initViewModel(
                firstTokenId = TestAssets.pswapAsset().token.id,
                secondTokenId = TestAssets.valAsset().token.id
            )

            advanceUntilIdle()

            viewModel.onFromAmountChange(BigDecimal.ONE)

            advanceUntilIdle()

            viewModel.onToAmountChange(BigDecimal.ONE)

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                atLeast(1)
            ).isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = networkFee,
                xorChange = null,
            )

            viewModel.fromAssetSelected(TestAssets.xorAsset().token.id)

            advanceUntilIdle()

            viewModel.onFromAmountChange(BigDecimal.TEN)

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                times(1)
            ).isNotEnoughXorLeftAfterTransaction(
                xorChange = BigDecimal.TEN,
                networkFeeInXor = networkFee,
            )
        }

    @Test
    fun `unreachable SORA endpoint shows the fee notice and Try again recovers`() = runTest {
        // 3.9.0 asks the reviewed endpoint for fees; the node chosen in More > Nodes is not used.
        feeFailure = IllegalStateException(
            "SORA2_RUNTIME_REFRESH_FAILED",
            BoundedHttpTextException("BOUNDED_HTTP_IO"),
        )
        given(resourceManager.getString(R.string.network_fee_unavailable_short))
            .willReturn("Network fee unavailable")
        initViewModel(xorBalance = 100.toBigDecimal())
        given(
            swapInteractor.fetchAvailableSources(
                assetsListItems.first().tokenId,
                assetsListItems.last().tokenId,
            )
        ).willReturn(setOf(Market.SMART))
        advanceUntilIdle()
        // The balance subscription survived the failure and the cause is named calmly.
        assertEquals(
            NetworkFeeUnavailable.NETWORK_UNREACHABLE,
            viewModel.swapMainState.value.feeUnavailable,
        )

        viewModel.fromAssetSelected(assetsListItems.first().tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.onFromAmountChange(BigDecimal.ONE)
        advanceUntilIdle()

        var state = viewModel.swapMainState.value
        assertEquals(NetworkFeeUnavailable.NETWORK_UNREACHABLE, state.feeUnavailable)
        assertEquals("Network fee unavailable", state.swapButtonState.text)
        assertFalse(state.swapButtonState.enabled)
        assertFalse(state.swapButtonState.loading)
        assertEquals(null, viewModel.errorFromResourceLiveData.value)

        // The endpoint is back.
        feeFailure = null
        viewModel.onRetryFee()
        advanceUntilIdle()

        state = viewModel.swapMainState.value
        assertEquals(null, state.feeUnavailable)
        org.junit.Assert.assertNotEquals("Network fee unavailable", state.swapButtonState.text)
        kVerify(swapInteractor, atLeastOnce()).calcDetails(
            anyNonNull(),
            anyNonNull(),
            anyNonNull(),
            anyNonNull(),
            anyNonNull(),
            anyDouble(),
        )
    }

    // --- audit A5: one truthful result for every outcome of Confirm -------------------------

    private val txHash = "0x" + "ab".repeat(32)

    /** Brings the screen to the reviewed state in which Confirm is available. */
    private suspend fun TestScope.readyToConfirmSwap() {
        initViewModel(xorBalance = 100.toBigDecimal())
        given(resourceManager.getString(R.string.review)).willReturn("Review")
        given(
            swapInteractor.fetchAvailableSources(
                assetsListItems.first().tokenId,
                assetsListItems.last().tokenId,
            )
        ).willReturn(setOf(Market.SMART))
        given(
            swapInteractor.calcDetails(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyDouble(),
            )
        ).willReturn(
            SwapDetails(
                BigDecimal("0.02"),
                BigDecimal("12.2"),
                BigDecimal("18.3"),
                BigDecimal("22.33"),
                networkFee,
                PoolDex(0, assetsListItems.first().tokenId, assetsListItems.last().tokenId),
                null,
            )
        )
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first().tokenId)
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last().tokenId)
        advanceUntilIdle()
        viewModel.onFromAmountChange(BigDecimal.ONE)
        advanceUntilIdle()
        // The swap screen is the screen in front, as it is when Confirm is tapped, and the
        // router opens the transaction page when asked.
        pageOpens { true }
        viewModel.onScreenResumed()
    }

    /** What the router answers when asked to open the transaction page. */
    private fun pageOpens(answer: () -> Boolean) {
        org.mockito.Mockito.lenient()
            .`when`(assetsRouter.showTxResult(any(), any()))
            .thenAnswer { answer() }
    }

    private suspend fun swapAnswers(confirmedDuplicate: Boolean, answer: () -> TransactionOutcome) {
        given(
            swapInteractor.swap(
                any(),
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

    private fun stubOutcomeTexts() {
        listOf(
            R.string.tx_outcome_refused_title,
            R.string.tx_outcome_note_refused_fee,
            R.string.tx_outcome_not_sent_title,
            R.string.tx_outcome_not_sent_network,
            R.string.tx_outcome_duplicate_title,
            R.string.tx_outcome_duplicate_message,
            R.string.tx_outcome_duplicate_send_again,
            R.string.tx_outcome_duplicate_wait,
        ).forEach { id ->
            given(resourceManager.getString(id)).willReturn("text-$id")
        }
    }

    @Test
    fun `a failed quote drops the earlier one so its limit is never signed for another amount`() =
        runTest {
            readyToConfirmSwap()
            given(resourceManager.getString(R.string.tx_outcome_quote_unavailable))
                .willReturn("Price unavailable")
            var state = viewModel.swapMainState.value
            assertEquals("Review", state.swapButtonState.text)
            assertEquals(true, state.swapButtonState.enabled)
            // The quote for 1 XOR is on screen with its minimum received.
            assertEquals(true, state.details.minmaxValue.isNotEmpty())

            // The amount changes and the request for its price fails (the connection dropped).
            var priceCanBeRead = false
            given(
                swapInteractor.calcDetails(
                    anyNonNull(),
                    anyNonNull(),
                    anyNonNull(),
                    anyNonNull(),
                    anyNonNull(),
                    anyDouble(),
                )
            ).willAnswer {
                if (!priceCanBeRead) throw java.io.IOException("socket closed")
                SwapDetails(
                    BigDecimal("0.04"),
                    BigDecimal("12.2"),
                    BigDecimal("18.3"),
                    BigDecimal("44.66"),
                    networkFee,
                    PoolDex(0, assetsListItems.first().tokenId, assetsListItems.last().tokenId),
                    null,
                )
            }
            viewModel.onFromAmountChange(BigDecimal.TEN)
            advanceUntilIdle()

            // Before (also in the store version): the error was shown, the quote for 1 XOR
            // stayed, Review stayed on, and Confirm signed 10 XOR with the limit of 1 XOR.
            state = viewModel.swapMainState.value
            assertEquals("Price unavailable", state.swapButtonState.text)
            assertFalse(state.swapButtonState.enabled)
            assertFalse(state.swapButtonState.loading)
            assertFalse(state.confirmButtonState.enabled)
            assertEquals("", state.details.minmaxValue)
            viewModel.onConfirmClicked()
            advanceUntilIdle()
            kVerify(swapInteractor, org.mockito.kotlin.never()).swap(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )

            // The price can be read again: the next change brings a quote and Review is back.
            priceCanBeRead = true
            viewModel.onFromAmountChange(BigDecimal("2"))
            advanceUntilIdle()

            state = viewModel.swapMainState.value
            assertEquals("Review", state.swapButtonState.text)
            assertEquals(true, state.swapButtonState.enabled)
            assertEquals(true, state.details.minmaxValue.isNotEmpty())
        }

    @Test
    fun `a confirmed swap shows confirmed and opens its transaction page`() = runTest {
        readyToConfirmSwap()
        swapAnswers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

        viewModel.onConfirmClicked()
        runCurrent()

        assertEquals(TransactionResultBadge.CONFIRMED, viewModel.swapMainState.value.confirmResult)
        advanceUntilIdle()
        kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.SWAP)
        assertEquals(null, viewModel.alertDialogLiveData.value)
    }

    @Test
    fun `a swap that is still waiting is shown as submitted and never as rejected`() = runTest {
        readyToConfirmSwap()
        // What a failed status read or an unfinished finality wait gives after a broadcast.
        swapAnswers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

        viewModel.onConfirmClicked()
        runCurrent()

        val badge = viewModel.swapMainState.value.confirmResult
        assertEquals(TransactionResultBadge.SUBMITTED, badge)
        assertEquals(R.string.wallet_transaction_submitted_1, badge?.text)
        advanceUntilIdle()
        // The transaction page carries "waiting for confirmation" and follows the result.
        kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.SWAP)
        assertEquals(null, viewModel.alertDialogLiveData.value)
        assertEquals(null, viewModel.errorFromResourceLiveData.value)
        // Confirm stays off: the swap is on its way and must not be sent again from here.
        assertFalse(viewModel.swapMainState.value.confirmButtonState.enabled)
    }

    @Test
    fun `a swap that failed on chain shows failed and opens its transaction page`() = runTest {
        readyToConfirmSwap()
        swapAnswers(false) {
            TransactionOutcome.FailedOnChain(
                txHash = txHash,
                blockHash = "0xblock",
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "LiquidityProxy.SlippageNotTolerated",
            )
        }

        viewModel.onConfirmClicked()
        runCurrent()

        assertEquals(TransactionResultBadge.FAILED, viewModel.swapMainState.value.confirmResult)
        advanceUntilIdle()
        kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.SWAP)
    }

    @Test
    fun `a swap the node refused is a failure with its reason and is not submitted`() = runTest {
        readyToConfirmSwap()
        stubOutcomeTexts()
        swapAnswers(false) {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees",
            )
        }

        viewModel.onConfirmClicked()
        runCurrent()

        assertEquals(TransactionResultBadge.NOT_SENT, viewModel.swapMainState.value.confirmResult)
        advanceUntilIdle()
        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_fee}",
            viewModel.alertDialogLiveData.value,
        )
        kVerify(assetsRouter, org.mockito.kotlin.never()).showTxResult(any(), any())
        // Nothing was sent, so the same screen offers Confirm again.
        org.junit.Assert.assertTrue(viewModel.swapMainState.value.confirmButtonState.enabled)
        assertFalse(viewModel.swapMainState.value.confirmButtonState.loading)
    }

    @Test
    fun `a swap that could not be prepared says nothing was sent`() = runTest {
        readyToConfirmSwap()
        stubOutcomeTexts()
        swapAnswers(false) { throw BoundedHttpTextException("BOUNDED_HTTP_IO") }

        viewModel.onConfirmClicked()
        advanceUntilIdle()

        // Before the fix this path showed "Transaction rejected" and no explanation.
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_network}",
            viewModel.alertDialogLiveData.value,
        )
        kVerify(assetsRouter, org.mockito.kotlin.never()).showTxResult(any(), any())
        org.junit.Assert.assertTrue(viewModel.swapMainState.value.confirmButtonState.enabled)
    }

    @Test
    fun `the same swap still waiting needs a deliberate second confirmation`() = runTest {
        readyToConfirmSwap()
        stubOutcomeTexts()
        swapAnswers(false) { throw DuplicateWaitingTransactionException(txHash, 1L) }
        swapAnswers(true) { TransactionOutcome.Waiting("0x" + "cd".repeat(32), true) }

        viewModel.onConfirmClicked()
        advanceUntilIdle()

        // The user is asked; nothing is sent and no result is claimed.
        val question = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_duplicate_title}", question.title)
        assertEquals("text-${R.string.tx_outcome_duplicate_send_again}", question.confirmText)
        assertEquals(null, viewModel.swapMainState.value.confirmResult)
        kVerify(assetsRouter, org.mockito.kotlin.never()).showTxResult(any(), any())
        org.junit.Assert.assertTrue(viewModel.swapMainState.value.confirmButtonState.enabled)

        // Dismissing changes nothing; only the explicit answer sends it again.
        question.onDismiss()
        advanceUntilIdle()
        kVerify(swapInteractor, org.mockito.kotlin.never())
            .swap(any(), any(), any(), any(), any(), any(), any(), any(), eq(true))

        question.onConfirm()
        advanceUntilIdle()
        kVerify(swapInteractor)
            .swap(any(), any(), any(), any(), any(), any(), any(), any(), eq(true))
        kVerify(assetsRouter).showTxResult("0x" + "cd".repeat(32), TransactionScreen.SWAP)
    }

    // --- the result arrives while the screen is not in front -------------------------------

    @Test
    fun `a result that arrives in the background opens its page on return and not before`() =
        runTest {
            readyToConfirmSwap()
            swapAnswers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

            viewModel.onConfirmClicked()
            runCurrent()
            // Home was pressed (or the phone locked) while the result mark was shown.
            viewModel.onScreenPaused()
            advanceUntilIdle()

            // Before the fix the page was opened from the coroutine at this moment. With the
            // activity stopped that navigation is dropped, and with the PIN lock on top it
            // removes the PIN lock.
            kVerify(assetsRouter, org.mockito.kotlin.never()).showTxResult(any(), any())
            // Until the page opens the confirm screen says what happened and Confirm is off.
            val state = viewModel.swapMainState.value
            assertEquals(TransactionResultBadge.CONFIRMED, state.confirmResult)
            assertFalse(state.confirmButtonState.enabled)
            assertFalse(state.confirmButtonState.loading)

            viewModel.onScreenResumed()

            kVerify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.SWAP)

            // Shown once: a later return to the front opens nothing more.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(1)).showTxResult(any(), any())
        }

    @Test
    fun `a sent swap cannot be confirmed a second time while its page has not opened`() =
        runTest {
            readyToConfirmSwap()
            swapAnswers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

            viewModel.onConfirmClicked()
            runCurrent()
            viewModel.onScreenPaused()
            advanceUntilIdle()

            viewModel.onConfirmClicked()
            advanceUntilIdle()

            kVerify(swapInteractor, times(1))
                .swap(any(), any(), any(), any(), any(), any(), any(), any(), any())
            assertFalse(viewModel.swapMainState.value.confirmButtonState.enabled)
        }

    @Test
    fun `a result is kept while the PIN lock is on top and shown when the screen is back`() =
        runTest {
            readyToConfirmSwap()
            // The router opens the page only when the swap screen is the current destination.
            var pinLockOnTop = true
            pageOpens { !pinLockOnTop }
            swapAnswers(false) {
                TransactionOutcome.FailedOnChain(
                    txHash = txHash,
                    blockHash = "0xblock",
                    reason = TransactionChainFailure.EXECUTION_FAILED,
                    detail = "LiquidityProxy.SlippageNotTolerated",
                )
            }

            viewModel.onConfirmClicked()
            runCurrent()
            viewModel.onScreenPaused()
            advanceUntilIdle()

            // The app returns after more than five minutes: the activity resumes this screen
            // for a moment while the PIN lock is already the current destination.
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.SWAP)
            assertEquals(TransactionResultBadge.FAILED, viewModel.swapMainState.value.confirmResult)
            viewModel.onScreenPaused()

            // The PIN was entered and the PIN lock removed itself.
            pinLockOnTop = false
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(2)).showTxResult(txHash, TransactionScreen.SWAP)

            // Opened: nothing more is asked of the router.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(2)).showTxResult(any(), any())
        }

    @Test
    fun `a failure that arrives in the background is explained and opens nothing`() = runTest {
        readyToConfirmSwap()
        stubOutcomeTexts()
        swapAnswers(false) {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees",
            )
        }

        viewModel.onConfirmClicked()
        runCurrent()
        viewModel.onScreenPaused()
        advanceUntilIdle()
        viewModel.onScreenResumed()

        // The dialog is held by the view model and delivered when the screen is shown again.
        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_fee}",
            viewModel.alertDialogLiveData.value,
        )
        kVerify(assetsRouter, org.mockito.kotlin.never()).showTxResult(any(), any())
        assertEquals(null, viewModel.swapMainState.value.confirmResult)
        org.junit.Assert.assertTrue(viewModel.swapMainState.value.confirmButtonState.enabled)
    }

    @Test
    fun `the same swap cannot be reviewed and confirmed again during the wait`() = runTest {
        readyToConfirmSwap()
        org.mockito.Mockito.lenient()
            .`when`(resourceManager.getString(R.string.polkaswap_output_estimated))
            .thenReturn("At least %s")
        val result = kotlinx.coroutines.CompletableDeferred<TransactionOutcome>()
        var sent = 0
        given(
            swapInteractor.swap(any(), any(), any(), any(), any(), any(), any(), any(), eq(false))
        ).willSuspendableAnswer {
            sent += 1
            result.await()
        }

        viewModel.onConfirmClicked()
        runCurrent()
        // Back to the amounts, Review and Confirm again while the first swap is followed.
        viewModel.swapClicked()
        // Before the fix Review switched Confirm on again and a tap sent the swap once more.
        assertFalse(viewModel.swapMainState.value.confirmButtonState.enabled)
        org.junit.Assert.assertTrue(viewModel.swapMainState.value.confirmButtonState.loading)
        viewModel.onConfirmClicked()
        runCurrent()

        assertEquals(1, sent)

        result.complete(TransactionOutcome.Confirmed(txHash, "0xblock"))
        advanceUntilIdle()
        kVerify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.SWAP)
    }
}
