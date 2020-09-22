package jp.co.soramitsu.feature_onboarding_impl.presentation.privacy

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
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
class PrivacyViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var router: OnboardingRouter

    private lateinit var privacyViewModel: PrivacyViewModel

    @Before fun setUp() {
        privacyViewModel = PrivacyViewModel(router)
    }

    @Test fun `on back pressed clicked`() {
        privacyViewModel.onBackPressed()

        verify(router).onBackButtonPressed()
    }
}