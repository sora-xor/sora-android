/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap.SwapViewModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
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
    private lateinit var walletRouter: WalletRouter

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var polkaswapInteractor: PolkaswapInteractor

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: SwapViewModel

    private val assets = listOf(
        Asset(
            Token("token_id", "token name", "token symbol", 18, true, 0),
            true,
            1,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            ),
        ),
        Asset(
            Token("token2_id", "token2 name", "token2 symbol", 18, true, 0),
            true,
            2,
            AssetBalance(
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE
            )
        )
    )

    private val assetFlow: Flow<List<Asset>> = flow {
        emit(assets)
    }

    private val assetsListItems = listOf(
        AssetListItemModel(0, "token name", AssetBalanceData(
            amount = "10.10",
            style = AssetBalanceStyle(
                R.style.TextAppearance_Soramitsu_Neu_Bold_15,
                R.style.TextAppearance_Soramitsu_Neu_Bold_11
            )), "token symbol", 1, "token_id"),
        AssetListItemModel(0, "token2 name", AssetBalanceData(
            amount = "10.10",
            style = AssetBalanceStyle(
                R.style.TextAppearance_Soramitsu_Neu_Bold_15,
                R.style.TextAppearance_Soramitsu_Neu_Bold_11
            )), "token2 symbol", 2, "token2_id")
    )

    @Before
    fun setUp() = runBlockingTest {
        given(walletInteractor.subscribeVisibleAssets()).willReturn(assetFlow)
        given(numbersFormatter.formatBigDecimal(anyNonNull(), eq(AssetHolder.ROUNDING))).willReturn(
            "10.10"
        )
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

        viewModel = SwapViewModel(
            walletRouter,
            walletInteractor,
            polkaswapInteractor,
            numbersFormatter,
            resourceManager
        )
    }

    @Test
    fun init() {
        viewModel.slippageToleranceLiveData.observeForever {
            assertEquals(0.5f, it)
        }
    }

    @Test
    fun infoClicked() {
        viewModel.infoClicked()

        verify(walletRouter).showPolkaswapInfoFragment()
    }

    @Test
    fun detailsClicked() {
        viewModel.detailsClicked()
        assertTrue(viewModel.detailsShowLiveData.getOrAwaitValue())

        viewModel.detailsClicked()
        assertFalse(viewModel.detailsShowLiveData.getOrAwaitValue())
    }

    @Test
    fun slippageToleranceClicked() {
        viewModel.slippageToleranceClicked()
        assertEquals(viewModel.showSlippageToleranceBottomSheet.getOrAwaitValue(), 0.5f)
    }

    @Test
    fun reverseButtonClicked() {
        val fromAsset = assetsListItems.first()
        val toAsset = assetsListItems.last()

        viewModel.fromAssetSelected(fromAsset)
        viewModel.toAssetSelected(toAsset)

        viewModel.reverseButtonClicked()

        assertEquals(viewModel.fromAssetLiveData.getOrAwaitValue(), assets[1])
        assertEquals(viewModel.toAssetLiveData.getOrAwaitValue(), assets[0])
    }

    @Test
    fun fromAndToAssetsSelected() {
        mainCoroutineRule.runBlockingTest {
            val fromAsset = assetsListItems.first()
            val toAsset = assetsListItems.last()

            viewModel.fromAssetSelected(fromAsset)
            delay(3000)

            assertEquals(viewModel.fromAssetLiveData.getOrAwaitValue(), assets[0])
            assertFalse(viewModel.swapButtonEnabledLiveData.getOrAwaitValue())
            assertFalse(viewModel.detailsEnabledLiveData.getOrAwaitValue())

            viewModel.toAssetSelected(toAsset)
            delay(3000)

            assertEquals(viewModel.toAssetLiveData.getOrAwaitValue(), assets[1])
            assertFalse(viewModel.swapButtonEnabledLiveData.getOrAwaitValue())
            assertTrue(viewModel.detailsEnabledLiveData.getOrAwaitValue())
        }
    }

    @Test
    fun fromAndToCardClicked() {
        viewModel.fromCardClicked()
        assertEquals(viewModel.showFromAssetSelectBottomSheet.getOrAwaitValue(), assetsListItems)
        viewModel.fromAssetSelected(assetsListItems[0])

        viewModel.toCardClicked()
        assertEquals(
            viewModel.showToAssetSelectBottomSheet.getOrAwaitValue(),
            assetsListItems.filter { it.assetId != viewModel.fromAssetLiveData.getOrAwaitValue().token.id })
    }
}