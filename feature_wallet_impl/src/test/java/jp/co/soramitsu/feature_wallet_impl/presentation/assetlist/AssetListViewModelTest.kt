package jp.co.soramitsu.feature_wallet_impl.presentation.assetlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetListMode
import jp.co.soramitsu.feature_wallet_api.domain.model.ReceiveAssetModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

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
    private lateinit var ethInteractor: EthereumInteractor

    @Mock
    private lateinit var numbersFormatter: NumbersFormatter

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: AssetListViewModel

    @Before
    fun setUp() = runBlockingTest {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), ArgumentMatchers.anyInt()))
            .willReturn("0.6")
        given(walletInteractor.getVisibleAssets()).willReturn(
            listOf(
                Asset(
                    Token("token_id", "token name", "token symbol", 18, true, 0),
                    true,
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
        )
    }

    @Test
    fun `test back click`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.RECEIVE
        )
        viewModel.backClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `test router receive mode`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.RECEIVE
        )
        viewModel.itemClicked(AssetListItemModel(0, "title", "1", "sora", 1, "id"))
        verify(router).showReceive(ReceiveAssetModel("id", "sora", "title", 0))
    }

    @Test
    fun `test router send mode`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.SEND
        )
        viewModel.itemClicked(AssetListItemModel(0, "title", "1", "sora", 1, "id"))
        verify(router).showContacts("id")
    }

    @Test
    fun `set filter value`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.SEND
        )
        viewModel.searchAssets("token name")
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(1, it.size)
        }
    }

    @Test
    fun `set filter value empty`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.SEND
        )
        viewModel.searchAssets("")
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(2, it.size)
        }
    }

    @Test
    fun `test init send`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.SEND
        )
        viewModel.title.observeForever {
            Assert.assertEquals(R.string.common_choose_asset, it)
        }
    }

    @Test
    fun `test init receive`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.RECEIVE
        )
        viewModel.title.observeForever {
            Assert.assertEquals(R.string.common_receive, it)
        }
    }

    @Test
    fun `test init receive asset`() = runBlockingTest {
        viewModel = AssetListViewModel(
            walletInteractor,
            numbersFormatter,
            router,
            AssetListMode.RECEIVE
        )
        viewModel.displayingAssetsLiveData.observeForever {
            Assert.assertEquals(2, it.size)
        }
    }
}