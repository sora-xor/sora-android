package jp.co.soramitsu.feature_main_impl.presentation.faq

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
class FaqViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val schedulersRule = RxSchedulersRule()

    @Mock private lateinit var router: MainRouter

    private lateinit var faqViewModel: FaqViewModel

    @Before fun setUp() {
        faqViewModel = FaqViewModel(router)
    }

    @Test fun `back pressed`() {
        faqViewModel.onBackPressed()

        verify(router).popBackStack()
    }
}