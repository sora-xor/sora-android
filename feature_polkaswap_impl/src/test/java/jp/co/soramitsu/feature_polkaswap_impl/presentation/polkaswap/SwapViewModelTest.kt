/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkStatic
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap.SwapViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common_wallet.presentation.compose.components.SelectSearchAssetState
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapAssetsToCardState
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.getOrAwaitValue
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
import java.math.BigDecimal

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
        pswapBalance: BigDecimal = BigDecimal.ONE
    ) {
        assets = listOf(
            TestAssets.xorAsset(xorBalance),
            TestAssets.valAsset(valBalance),
            TestAssets.pswapAsset(pswapBalance)
        )
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(flowOf(assets))
        setUpAfterViewModelInit()
        viewModel = SwapViewModel(
            assetsInteractor,
            walletInteractor,
            swapInteractor,
            numbersFormatter,
            resourceManager,
            mainRouter,
            assetsRouter,
            TestAssets.xorAsset().token.id,
            "",
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
        viewModel.swapMainState.slippage.let {
            assertEquals(0.5, it, 0.1)
        }
        viewModel.navigationDisclaimerEvent.getOrAwaitValue()

        assertEquals(buttonState, viewModel.swapMainState.swapButtonState)
    }

    @Test
    fun fromAndToAssetsSelected() = runTest {
        initViewModel()
        advanceUntilIdle()
        val fromAsset = assetsListItems.first()
        val toAsset = assetsListItems[1]

        viewModel.fromAssetSelected(fromAsset.tokenId)
        advanceUntilIdle()
        assertEquals(viewModel.swapMainState.tokenFromState?.token, assets[0].token)
        assertFalse(viewModel.swapMainState.swapButtonState.enabled)

        viewModel.toAssetSelected(toAsset.tokenId)
        advanceUntilIdle()
        assertEquals(viewModel.swapMainState.tokenToState?.token, assets[1].token)
        assertFalse(viewModel.swapMainState.swapButtonState.enabled)
    }

    @Test
    fun fromAndToCardClicked() = runTest {
        initViewModel()
        advanceUntilIdle()
        viewModel.fromCardClicked()
        advanceUntilIdle()
        assertEquals(viewModel.swapMainState.selectSearchAssetState?.fullList, assetsListItems)
        viewModel.fromAssetSelected(assetsListItems[0].tokenId)

        viewModel.toCardClicked()
        assertEquals(
            SelectSearchAssetState(
                filter = "",
                fullList = assetsListItems.filter { it.tokenId != viewModel.swapMainState.tokenFromState?.token?.id }),
            viewModel.swapMainState.selectSearchAssetState,
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
        assertEquals(BigDecimal.ONE, viewModel.swapMainState.tokenFromState?.amount)
        val btn = viewModel.swapMainState.swapButtonState
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
        assertEquals(BigDecimal(90.0), viewModel.swapMainState.tokenFromState?.amount)
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
        assertEquals(BigDecimal(50.0).setScale(19), viewModel.swapMainState.tokenFromState?.amount)
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
        assertEquals(BigDecimal(8.0).setScale(19), viewModel.swapMainState.tokenFromState?.amount)
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
        assertEquals(BigDecimal(25).setScale(19), viewModel.swapMainState.tokenFromState?.amount)
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
        assertEquals(BigDecimal(100.0), viewModel.swapMainState.tokenFromState?.amount)
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
        assertEquals(assetsListItems.last().tokenId, viewModel.swapMainState.tokenFromState?.token?.id)
        assertEquals(assetsListItems[1].tokenId, viewModel.swapMainState.tokenToState?.token?.id)
    }
}
