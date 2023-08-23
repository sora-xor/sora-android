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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap.SwapViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.test_data.PolkaswapTestData
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.atMost
import org.mockito.kotlin.times
import java.math.BigDecimal
import org.mockito.kotlin.verify as kVerify

@FlowPreview
@ExperimentalCoroutinesApi
@OptIn(ExperimentalStdlibApi::class)
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
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var swapInteractor: SwapInteractor

    private val mockedUri = Mockito.mock(Uri::class.java)

    private val numbersFormatter: NumbersFormatter = NumbersFormatter()

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: SwapViewModel

    private lateinit var assets: List<Asset>

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var mainRouter: MainRouter

    private val networkFee = BigDecimal.TEN

    private val assetsListItems: List<AssetItemCardState> by lazy {
        mapAssetsToCardState(assets, numbersFormatter)
    }

    @Before
    fun setUp() = runTest {
        mockkStatic(Uri::parse)
        every { Uri.parse(any()) } returns mockedUri
    }

    private suspend fun initViewModel(
        xorBalance: BigDecimal = BigDecimal.ONE,
        valBalance: BigDecimal = BigDecimal.ONE,
        pswapBalance: BigDecimal = BigDecimal.ONE,
        firstTokenId: String? = null,
        secondTokenId: String? = null,
    ) = runTest {
        assets = listOf(
            TestAssets.xorAsset(xorBalance),
            TestAssets.valAsset(valBalance),
            TestAssets.pswapAsset(pswapBalance)
        )
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(flowOf(assets))
        given(
            assetsInteractor.isEnoughXorLeftAfterTransaction(
                primaryToken = any(),
                primaryTokenAmount = any(),
                secondaryToken = any(),
                secondaryTokenAmount = any(),
                networkFeeInXor = any()
            )
        ).willReturn(false)

        given(
            coroutineManager.io
        ).willReturn(this.coroutineContext[CoroutineDispatcher]!!)

        setUpAfterViewModelInit()
        viewModel = SwapViewModel(
            assetsInteractor,
            walletInteractor,
            swapInteractor,
            numbersFormatter,
            resourceManager,
            mainRouter,
            assetsRouter,
            coroutineManager,
            firstTokenId ?: TestAssets.xorAsset().token.id,
            secondTokenId ?: "",
            isLaunchedFromSoraCard = false
        )
    }

    private fun setUpAfterViewModelInit() = runTest {
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
        given(swapInteractor.observePoolReserves()).willReturn(
            flow {
                emit("")
            }
        )
        assets.forEach {
            given(assetsInteractor.getAssetOrThrow(it.token.id)).willReturn(it)
        }
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)
        given(swapInteractor.fetchSwapNetworkFee(anyNonNull())).willReturn(networkFee)
        given(swapInteractor.getPolkaswapDisclaimerVisibility()).willReturn(flowOf(true))
        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose token")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.polkaswap_pool_not_created)).willReturn("Pool not created")
        //given(resourceManager.getString(R.string.review)).willReturn("Review")
        given(resourceManager.getString(R.string.polkaswap_insufficient_balance)).willReturn("Insufficient balance")
        given(resourceManager.getString(R.string.polkaswap_insufficient_liqudity)).willReturn("Insufficient liquidity")
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
    fun fromAndToCardClicked() = runTest {
        initViewModel()
        advanceUntilIdle()
        viewModel.fromCardClicked()
        advanceUntilIdle()
        assertEquals(viewModel.swapMainState.value.selectSearchAssetState?.fullList, assetsListItems)
        viewModel.fromAssetSelected(assetsListItems[0].tokenId)

        viewModel.toCardClicked()
        assertEquals(
            SelectSearchAssetState(
                filter = "",
                fullList = assetsListItems.filter { it.tokenId != viewModel.swapMainState.value.tokenFromState?.token?.id }),
            viewModel.swapMainState.value.selectSearchAssetState,
        )
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
                BigDecimal("0.2"),
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
        assertEquals(BigDecimal(50.0).setScale(19), viewModel.swapMainState.value.tokenFromState?.amount)
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
        assertEquals(BigDecimal(8.0).setScale(19), viewModel.swapMainState.value.tokenFromState?.amount)
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
        assertEquals(BigDecimal(25).setScale(19), viewModel.swapMainState.value.tokenFromState?.amount)
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
            ).isEnoughXorLeftAfterTransaction(
                primaryToken = PolkaswapTestData.XOR_ASSET.token,
                primaryTokenAmount = BigDecimal.ONE,
                secondaryToken = PolkaswapTestData.VAL_ASSET.token,
                secondaryTokenAmount = BigDecimal.ZERO,
                networkFeeInXor = networkFee
            )

            viewModel.onToAmountChange(BigDecimal.ONE)

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                times(1)
            ).isEnoughXorLeftAfterTransaction(
                primaryToken = PolkaswapTestData.XOR_ASSET.token,
                primaryTokenAmount = BigDecimal.ONE,
                secondaryToken = PolkaswapTestData.VAL_ASSET.token,
                secondaryTokenAmount = BigDecimal.ONE,
                networkFeeInXor = networkFee
            )

            viewModel.fromAssetSelected(TestAssets.pswapAsset().token.id)

            advanceUntilIdle()

            viewModel.onFromAmountChange(BigDecimal.TEN)

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                atMost(1)
            ).isEnoughXorLeftAfterTransaction(
                primaryToken = TestAssets.pswapAsset().token,
                primaryTokenAmount = BigDecimal.TEN,
                secondaryToken = PolkaswapTestData.VAL_ASSET.token,
                secondaryTokenAmount = BigDecimal.ONE,
                networkFeeInXor = networkFee
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
                atMost(1)
            ).isEnoughXorLeftAfterTransaction(
                primaryToken = any(),
                primaryTokenAmount = any(),
                secondaryToken = any(),
                secondaryTokenAmount = any(),
                networkFeeInXor = any()
            )

            viewModel.fromAssetSelected(TestAssets.xorAsset().token.id)

            advanceUntilIdle()

            viewModel.onFromAmountChange(BigDecimal.TEN)

            advanceUntilIdle()

            kVerify(
                mock = assetsInteractor,
                times(1)
            ).isEnoughXorLeftAfterTransaction(
                primaryToken = TestAssets.xorAsset().token,
                primaryTokenAmount = BigDecimal.TEN,
                secondaryToken = PolkaswapTestData.VAL_ASSET.token,
                secondaryTokenAmount = BigDecimal.ONE,
                networkFeeInXor = networkFee
            )
        }
}
