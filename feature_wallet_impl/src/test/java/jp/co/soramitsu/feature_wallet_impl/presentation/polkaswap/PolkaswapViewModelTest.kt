package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PolkaswapViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletRouter: WalletRouter

    @Mock
    private lateinit var polkaswapInteractor: PolkaswapInteractor

    private lateinit var viewModel: PolkaSwapViewModel

    @Before
    fun setUp() = runBlockingTest {
        viewModel = PolkaSwapViewModel(walletRouter, polkaswapInteractor)
    }

    @Test
    fun `test back click`() {
        viewModel.backPressed()

        verify(walletRouter).popBackStackFragment()
    }
}