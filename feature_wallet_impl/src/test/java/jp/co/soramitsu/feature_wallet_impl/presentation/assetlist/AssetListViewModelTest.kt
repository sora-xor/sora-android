package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.assetlist.adapter.AssetListItemModel
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.Mockito.verify
import org.mockito.BDDMockito.given
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class AssetListViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var ethInteractor: EthereumInteractor

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: AssetListViewModel

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
                    )
                )
            )
        )
    }

    @Test
    fun `test back click`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.RECEIVE
        )
        viewModel.backClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `test router receive mode`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.RECEIVE
        )
        viewModel.itemClicked(AssetListItemModel(0, "title", "1", "sora", 1, "id"))
        verify(router).showReceive(ReceiveAssetModel("id", "sora", "title", 0))
    }

    @Test
    fun `test router send mode`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.SEND
        )
        viewModel.itemClicked(AssetListItemModel(0, "title", "1", "sora", 1, "id"))
        verify(router).showContacts("id")
    }

    @Test
    fun `set filter value`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.SEND
        )
        viewModel.searchAssets("so")
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(1, it.size)
        }
    }

    @Test
    fun `set filter value empty`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.SEND
        )
        viewModel.searchAssets("")
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(2, it.size)
        }
    }

    @Test
    fun `test init send`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.SEND
        )
        viewModel.title.observeForever {
            Assert.assertEquals(R.string.common_choose_asset, it)
        }
    }

    @Test
    fun `test init receive`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.RECEIVE
        )
        viewModel.title.observeForever {
            Assert.assertEquals(R.string.common_receive, it)
        }
    }

    @Test
    fun `test init receive asset`() {
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.RECEIVE
        )
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(2, it.size)
        }
    }

    @Test(expected = ExceptionInInitializerError::class)
    fun `check exception`() {
        val e = IllegalArgumentException("")
        given(numbersFormatter.formatBigDecimal(anyNonNull(), ArgumentMatchers.anyInt()))
            .willThrow(e)
        viewModel = AssetListViewModel(
            walletInteractor,
            ethInteractor,
            numbersFormatter,
            router,
            resourceManager,
            AssetListMode.RECEIVE
        )
    }
}