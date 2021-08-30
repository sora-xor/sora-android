package jp.co.soramitsu.feature_main_impl.presentation.terms

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class TermsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var router: MainRouter

    private lateinit var termsViewModel: TermsViewModel

    @Before
    fun setUp() {
        termsViewModel = TermsViewModel(router)
    }

    @Test
    fun `back button pressed`() {
        termsViewModel.onBackPressed()

        verify(router).popBackStack()
    }
}