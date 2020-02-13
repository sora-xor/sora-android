package jp.co.soramitsu.feature_onboarding_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.InvitationHandler
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
class OnboardingViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var invitationHandler: InvitationHandler

    private lateinit var onboardingViewModel: OnboardingViewModel

    @Before fun setUp() {
        onboardingViewModel = OnboardingViewModel(invitationHandler)
    }

    @Test fun `success verification code entered`() {
        onboardingViewModel.startedWithInviteAction()

        verify(invitationHandler).invitationApplied()
    }
}