package jp.co.soramitsu.feature_wallet_impl.presentation.assetsettings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.AssetSettingsViewModel
import jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.display.AssetConfigurableModel
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.anyNonNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
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
class AssetSettingsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

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
    fun setUp() = runBlockingTest {
        given(numbersFormatter.formatBigDecimal(anyNonNull(), ArgumentMatchers.anyInt()))
            .willReturn("0.6")
        given(walletInteractor.getWhitelistAssets()).willReturn(
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
                ),
                Asset(
                    Token("token3_id", "token3 name", "token2 symbol", 18, true, 0),
                    true,
                    true,
                    3,
                    AssetBalance(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE
                    )
                ),
                Asset(
                    Token("token4_id", "token2 name", "token4 symbol", 18, true, 0),
                    true,
                    true,
                    4,
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
    fun `change position`() = runBlockingTest {
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
    fun `click done`() = runBlockingTest {
        viewModel.doneClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed`() = runBlockingTest {
        val a = AssetConfigurableModel("id", "sora", "xor", 0, true, "1", true)
        viewModel.checkChanged(a, true)
        viewModel.doneClicked()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `check changed add remove`() = runBlockingTest {
        val a = AssetConfigurableModel("id", "sora", "xor", 0, true, "1", true)
        viewModel.checkChanged(a, true)
        viewModel.checkChanged(a, false)
        viewModel.doneClicked()
        verify(router).popBackStackFragment()
    }
}