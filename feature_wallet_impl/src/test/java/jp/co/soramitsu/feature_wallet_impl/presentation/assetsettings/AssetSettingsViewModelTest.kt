package jp.co.soramitsu.feature_wallet_impl.presentation.assetsettings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.AssetSettingsViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableModel
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.AssetListViewModel
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import jp.co.soramitsu.test_shared.getOrAwaitValue
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.anyList
import org.mockito.BDDMockito.anyMap
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.Mockito.verify
import org.mockito.BDDMockito.given
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.never
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class AssetSettingsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: AssetSettingsViewModel

    @Before
    fun setUp() {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), ArgumentMatchers.anyInt()))
            .willReturn("0.6")
        given(walletInteractor.getAssets()).willReturn(
            Single.just(
                listOf(
                    Asset(
                        "id",
                        "sora",
                        "val",
                        true,
                        true,
                        1,
                        4,
                        18,
                        BigDecimal.TEN,
                        0,
                        true
                    ),
                    Asset(
                        "id2",
                        "polkaswap",
                        "pswap",
                        true,
                        true,
                        2,
                        4,
                        18,
                        BigDecimal.TEN,
                        0,
                        true
                    ),
                    Asset(
                        "id3",
                        "sora",
                        "xor",
                        true,
                        true,
                        3,
                        4,
                        18,
                        BigDecimal.TEN,
                        0,
                        true
                    ),
                    Asset(
                        "id4",
                        "usoreu",
                        "usdt",
                        true,
                        true,
                        4,
                        4,
                        18,
                        BigDecimal.TEN,
                        0,
                        true
                    )
                )
            )
        )
        viewModel =
            AssetSettingsViewModel(walletInteractor, numbersFormatter, resourceManager, router)
    }

    @Test
    fun `init check`() {
        viewModel.assetsListLiveData.observeForever {
            assertEquals(4, it.size)
        }
    }

    @Test
    fun `change position`() {
        viewModel.assetPositionChanged(0, 1)
        viewModel.assetPositions.value?.let {
            assert(it.first == 0 && it.second == 1)
        }

        val res = viewModel.assetPositionChanged(1, 2)
        viewModel.assetPositions.value?.let {
            assert(it.first == 1 && it.second == 2)
        }
        assertEquals(true, res)
    }

    @Test
    fun `click back`() {
        viewModel.backClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `click done`() {
        given(walletInteractor.updateAssetPositions(anyMap())).willReturn(Completable.complete())
        given(walletInteractor.displayAssets(anyList())).willReturn(Completable.complete())
        given(walletInteractor.hideAssets(anyList())).willReturn(Completable.complete())
        viewModel.doneClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed`() {
        given(walletInteractor.updateAssetPositions(anyMap())).willReturn(Completable.complete())
        given(walletInteractor.displayAssets(listOf("id"))).willReturn(Completable.complete())
        given(walletInteractor.hideAssets(anyList())).willReturn(Completable.complete())
        val a = AssetConfigurableModel("id", "sora", "xor", 0, true, "1", true)
        viewModel.checkChanged(a, true)
        viewModel.doneClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed add remove`() {
        given(walletInteractor.updateAssetPositions(anyMap())).willReturn(Completable.complete())
        given(walletInteractor.displayAssets(emptyList())).willReturn(Completable.complete())
        given(walletInteractor.hideAssets(anyList())).willReturn(Completable.complete())
        val a = AssetConfigurableModel("id", "sora", "xor", 0, true, "1", true)
        viewModel.checkChanged(a, true)
        viewModel.checkChanged(a, false)
        viewModel.doneClicked()
        verify(router).popBackStackFragment()
    }
}