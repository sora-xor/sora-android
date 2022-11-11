/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AssetListViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var polkaswapInteractor: PolkaswapInteractor

    @Mock
    private lateinit var router: WalletRouter

    private lateinit var viewModel: AssetListViewModel

    @Before
    fun setUp() = runTest {
        given(walletInteractor.getActiveAssets()).willReturn(AssetListTestData.ASSET_LIST)
    }

    @Test
    fun `test back click`() = runTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            polkaswapInteractor,
            NumbersFormatter(),
            router,
            AssetListMode.RECEIVE
        )
        viewModel.backClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `test router receive mode`() = runTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            polkaswapInteractor,
            NumbersFormatter(),
            router,
            AssetListMode.RECEIVE
        )
        viewModel.itemClicked(
            AssetListItemModel(
                0, "title", AssetBalanceData(
                    amount = "1",
                    style = AssetBalanceStyle(
                        R.style.TextAppearance_Soramitsu_Neu_Bold_15,
                        R.style.TextAppearance_Soramitsu_Neu_Bold_11
                    )
                ), "sora", 1, "id"
            )
        )
        verify(router).showReceive(ReceiveAssetModel("id", "sora", "title", 0))
    }

    @Test
    fun `test router send mode`() = runTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            polkaswapInteractor,
            NumbersFormatter(),
            router,
            AssetListMode.SEND
        )
        viewModel.itemClicked(
            AssetListItemModel(
                0, "title", AssetBalanceData(
                    amount = "1",
                    style = AssetBalanceStyle(
                        R.style.TextAppearance_Soramitsu_Neu_Bold_15,
                        R.style.TextAppearance_Soramitsu_Neu_Bold_11
                    )
                ), "sora", 1, "id"
            )
        )
        verify(router).showContacts("id")
    }

    @Test
    fun `test router select for liquidity mode`() = runTest {
        given(walletInteractor.getAssetOrThrow(AssetListTestData.SECOND_TOKEN.id)).willReturn(
            AssetListTestData.SECOND_ASSET
        )
        viewModel = AssetListViewModel(
            walletInteractor,
            polkaswapInteractor,
            NumbersFormatter(),
            router,
            AssetListMode.SELECT_FOR_LIQUIDITY
        )
        advanceUntilIdle()
        viewModel.itemClicked(AssetListTestData.SECOND_ASSET_LIST_ITEM_MODEL)
        advanceUntilIdle()
        verify(router).returnToAddLiquidity(
            null,
            AssetListTestData.SECOND_TOKEN
        )
    }

    @Test
    fun `set filter value`() = runTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            polkaswapInteractor,
            NumbersFormatter(),
            router,
            AssetListMode.SEND
        )
        advanceUntilIdle()
        viewModel.searchAssets("token name")
        advanceUntilIdle()
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(1, it.size)
        }
    }

    @Test
    fun `set filter value empty`() = runTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            polkaswapInteractor,
            NumbersFormatter(),
            router,
            AssetListMode.SEND
        )
        advanceUntilIdle()
        viewModel.searchAssets("")
        advanceUntilIdle()
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(2, it.size)
        }
    }

    @Test
    fun `test init receive asset`() = runTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            polkaswapInteractor,
            NumbersFormatter(),
            router,
            AssetListMode.RECEIVE
        )
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(2, it.size)
        }
    }
}