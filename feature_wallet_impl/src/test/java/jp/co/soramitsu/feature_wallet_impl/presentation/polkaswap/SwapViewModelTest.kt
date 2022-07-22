/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.liquidity.model.ButtonState
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap.SwapViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.util.mapAssetToAssetModel
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens.valToken
import jp.co.soramitsu.test_data.TestTokens.xorToken
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.BDDMockito.anyFloat
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
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
    private lateinit var walletRouter: WalletRouter

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var polkaswapInteractor: PolkaswapInteractor

    private val numbersFormatter: NumbersFormatter = NumbersFormatter()

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: SwapViewModel

    private lateinit var assets: List<Asset>

    private val networkFee = BigDecimal.TEN

    private val assetsListItems: List<AssetListItemModel> by lazy {
        assets.map {
            it.mapAssetToAssetModel(
                numbersFormatter, AssetBalanceStyle(
                    R.style.TextAppearance_Soramitsu_Neu_Bold_15,
                    R.style.TextAppearance_Soramitsu_Neu_Bold_11
                )
            )
        }
    }

    private fun initViewModel(
        xorBalance: BigDecimal = BigDecimal.ONE,
        valBalance: BigDecimal = BigDecimal.ONE,
        pswapBalance: BigDecimal = BigDecimal.ONE
    ) {
        assets = listOf(
            TestAssets.xorAsset(xorBalance),
            TestAssets.valAsset(valBalance),
            TestAssets.pswapAsset(pswapBalance)
        )
        given(walletInteractor.subscribeActiveAssetsOfCurAccount()).willReturn(flow { emit(assets) })
        setUpAfterViewModelInit()
        viewModel = SwapViewModel(
            walletRouter,
            walletInteractor,
            polkaswapInteractor,
            numbersFormatter,
            resourceManager
        )
    }

    private fun setUpAfterViewModelInit() = runTest {
        given(polkaswapInteractor.fetchSwapNetworkFee(assets[0].token)).willReturn(networkFee)
        given(polkaswapInteractor.observeSwap()).willReturn(
            flow {
                emit(true)
            }
        )
        given(polkaswapInteractor.observeSelectedMarket()).willReturn(
            flow {
                emit(Market.TBC)
            }
        )
        given(polkaswapInteractor.observePoolReserves()).willReturn(
            flow {
                emit("")
            }
        )
        given(polkaswapInteractor.getPolkaswapDisclaimerVisibility()).willReturn(flowOf(false))
        given(resourceManager.getString(anyInt())).willReturn("Test string")
        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose token")
        given(
            polkaswapInteractor.calcDetails(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyFloat()
            )
        ).willReturn(
            SwapDetails(
                amount = BigDecimal.ZERO,
                per1 = BigDecimal.ZERO,
                per2 = BigDecimal.ZERO,
                minmax = BigDecimal.ZERO,
                liquidityFee = BigDecimal.ZERO,
                networkFee = networkFee,
            )
        )
    }

    @Test
    fun init() {
        initViewModel()
        val buttonState = ButtonState(text = "Choose token", enabled = false, loading = false)

        viewModel.slippageToleranceLiveData.observeForever {
            assertEquals(0.5f, it)
        }
        viewModel.disclaimerVisibilityLiveData.observeForever {
            assertEquals(it, false)
        }
        assertEquals(buttonState, viewModel.swapButtonState.value)
    }

    @Test
    fun setSwapDataCalled() = runTest {
        initViewModel()
        viewModel.setSwapData(xorToken, valToken, BigDecimal.ONE)

        advanceUntilIdle()
        assertEquals(viewModel.fromAssetLiveData.getOrAwaitValue(), assets[0])
        assertEquals(viewModel.toAssetLiveData.getOrAwaitValue(), assets[1])
        assertFalse(viewModel.swapButtonState.value.enabled)
        assertTrue(viewModel.detailsEnabledLiveData.getOrAwaitValue())
    }

    @Test
    fun infoClicked() {
        initViewModel()
        viewModel.infoClicked()

        verify(walletRouter).showPolkaswapInfoFragment()
    }

    @Test
    fun detailsClickedWithoutDetails() {
        initViewModel()
        viewModel.detailsClicked()
        assertFalse(viewModel.detailsShowLiveData.getOrAwaitValue())

        viewModel.detailsClicked()
        assertFalse(viewModel.detailsShowLiveData.getOrAwaitValue())
    }

    @Test
    fun detailsClickedWithDetails() = runTest {
        initViewModel()
        advanceUntilIdle()

        val fromAsset = assetsListItems.first()
        val toAsset = assetsListItems.last()

        viewModel.fromAssetSelected(fromAsset)
        advanceUntilIdle()
        viewModel.toAssetSelected(toAsset)
        advanceUntilIdle()
        viewModel.fromAmountChanged(BigDecimal.TEN)
        advanceUntilIdle()

        viewModel.detailsClicked()
        assertTrue(viewModel.detailsShowLiveData.getOrAwaitValue())

        viewModel.detailsClicked()
        assertFalse(viewModel.detailsShowLiveData.getOrAwaitValue())
    }

    @Test
    fun slippageToleranceClicked() {
        initViewModel()
        viewModel.slippageToleranceClicked()
        assertEquals(viewModel.showSlippageToleranceBottomSheet.getOrAwaitValue(), 0.5f)
    }

    @Test
    fun reverseButtonClicked() = runTest {
        initViewModel()
        advanceUntilIdle()
        val fromAsset = assetsListItems.first()
        val toAsset = assetsListItems[1]

        viewModel.fromAssetSelected(fromAsset)
        viewModel.toAssetSelected(toAsset)

        viewModel.reverseButtonClicked()

        assertEquals(viewModel.fromAssetLiveData.getOrAwaitValue(), assets[1])
        assertEquals(viewModel.toAssetLiveData.getOrAwaitValue(), assets[0])
    }

    @Test
    fun fromAndToAssetsSelected() = runTest {
        initViewModel()
        val fromAsset = assetsListItems.first()
        val toAsset = assetsListItems[1]

        viewModel.fromAssetSelected(fromAsset)
        advanceUntilIdle()
        assertEquals(viewModel.fromAssetLiveData.getOrAwaitValue(), assets[0])
        assertFalse(viewModel.swapButtonState.value.enabled)
        assertFalse(viewModel.detailsEnabledLiveData.getOrAwaitValue())

        viewModel.toAssetSelected(toAsset)
        advanceUntilIdle()
        assertEquals(viewModel.toAssetLiveData.getOrAwaitValue(), assets[1])
        assertFalse(viewModel.swapButtonState.value.enabled)
        assertTrue(viewModel.detailsEnabledLiveData.getOrAwaitValue())
    }

    @Test
    fun fromAndToCardClicked() = runTest {
        initViewModel()
        advanceUntilIdle()
        viewModel.fromCardClicked()
        advanceUntilIdle()
        assertEquals(viewModel.showFromAssetSelectBottomSheet.getOrAwaitValue(), assetsListItems)
        viewModel.fromAssetSelected(assetsListItems[0])

        viewModel.toCardClicked()
        assertEquals(
            viewModel.showToAssetSelectBottomSheet.getOrAwaitValue(),
            assetsListItems.filter { it.assetId != viewModel.fromAssetLiveData.getOrAwaitValue().token.id })
    }

    @Test
    fun fromInputPercentCalledWithLowBalance() = runTest {
        initViewModel()
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first())
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last())
        advanceUntilIdle()
        val expectedAmountFormatted = "1"
        viewModel.fromInputPercentClicked(100)
        assertEquals(expectedAmountFormatted, viewModel.fromAmountLiveData.value)
    }

    @Test
    fun fromInputPercentCalledWithXor() = runTest {
        initViewModel(xorBalance = 100.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first())
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last())
        advanceUntilIdle()
        val expectedAmountFormatted = "90"
        viewModel.fromInputPercentClicked(100)
        assertEquals(expectedAmountFormatted, viewModel.fromAmountLiveData.value)
    }

    @Test
    fun fromInputPercentHalfCalledWithXor() = runTest {
        initViewModel(xorBalance = 100.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first())
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last())
        advanceUntilIdle()
        val expectedAmountFormatted = "50"
        viewModel.fromInputPercentClicked(50)
        assertEquals(expectedAmountFormatted, viewModel.fromAmountLiveData.value)
    }

    @Test
    fun `percent click 50 balance 16 amount less then fee`() = runTest {
        initViewModel(xorBalance = 16.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first())
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last())
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(50)
        assertEquals("8", viewModel.fromAmountLiveData.value)
    }

    @Test
    fun `percent click 50 balance 50`() = runTest {
        initViewModel(xorBalance = 50.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems.first())
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last())
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(50)
        assertEquals("25", viewModel.fromAmountLiveData.value)
    }

    @Test
    fun `percent click 50 balance val 100`() = runTest {
        initViewModel(xorBalance = 100.toBigDecimal(), valBalance = 100.toBigDecimal())
        advanceUntilIdle()
        viewModel.fromAssetSelected(assetsListItems[1])
        advanceUntilIdle()
        viewModel.toAssetSelected(assetsListItems.last())
        advanceUntilIdle()
        viewModel.fromInputPercentClicked(100)
        assertEquals("100", viewModel.fromAmountLiveData.value)
    }
}