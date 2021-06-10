package jp.co.soramitsu.feature_main_impl.presentation.parliament

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ParliamentViewModeTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var router: MainRouter

    private lateinit var parliamentViewModel: ParliamentViewModel

    @Before
    fun setUp() {
        parliamentViewModel = ParliamentViewModel(router)
    }

    @Test
    fun `referenda clicked`() {
        parliamentViewModel.onReferendaCardClicked()
        verify(router).showReferenda()
    }
}