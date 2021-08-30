package jp.co.soramitsu.feature_main_impl.presentation.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
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
class MainViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var interactor: MainInteractor

    @Mock
    private lateinit var router: MainRouter

    private lateinit var mainViewModel: MainViewModel

    @Before
    fun setUp() = runBlockingTest {
        mainViewModel = MainViewModel(interactor, router)
    }

    @Test
    fun `click on votes calls router showVotesScreen() function`() {
        mainViewModel.votesClick()

        verify(router).showVotesHistory()
    }
}