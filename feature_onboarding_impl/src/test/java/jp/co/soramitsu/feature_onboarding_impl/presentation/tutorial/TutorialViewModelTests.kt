package jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class TutorialViewModelTests {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: OnboardingInteractor
    @Mock private lateinit var router: OnboardingRouter
    @Mock private lateinit var progress: WithProgress

    private lateinit var tutorialViewModel: TutorialViewModel

    @Before fun setUp() {
        tutorialViewModel = TutorialViewModel(interactor, router, progress)
    }

    @Test fun `sign up clicked with supported version leads to countries screen`() {
        val appVersion = AppVersion(true, "")

        given(interactor.runRegisterFlow()).willReturn(Single.just(appVersion))

        tutorialViewModel.onSignUpClicked()

        verify(interactor).runRegisterFlow()
        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showCountries()
        verifyNoMoreInteractions(interactor, progress, router)
    }

    @Test fun `sign up clicked with unsupported version leads to unsupported version screen`() {
        val appVersion = AppVersion(false, "")

        given(interactor.runRegisterFlow()).willReturn(Single.just(appVersion))

        tutorialViewModel.onSignUpClicked()

        verify(interactor).runRegisterFlow()
        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showUnsupportedScreen(anyString())
        verifyNoMoreInteractions(interactor, progress, router)
    }

    @Test fun `recovery click open recover screen if version is supported`() {
        val appVersion = AppVersion(true, "")

        given(interactor.checkVersionIsSupported()).willReturn(Single.just(appVersion))

        tutorialViewModel.onRecoveryClicked()

        verify(interactor).checkVersionIsSupported()
        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showRecovery()
        verifyNoMoreInteractions(interactor, progress, router)
    }

    @Test fun `recovery click with unsupported version leads to unsupported version screen`() {
        val appVersion = AppVersion(false, "")

        given(interactor.checkVersionIsSupported()).willReturn(Single.just(appVersion))

        tutorialViewModel.onRecoveryClicked()

        verify(interactor).checkVersionIsSupported()
        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showUnsupportedScreen(anyString())
        verifyNoMoreInteractions(interactor, progress, router)
    }

    @Test fun `showTermsScreen() calls router showTermsScreen()`() {
        tutorialViewModel.showTermsScreen()

        verify(router).showTermsScreen()
        verifyNoMoreInteractions(router)
        verifyZeroInteractions(interactor, progress)
    }

    @Test fun `showPrivacyScreen() calls router showPrivacyScreen()`() {
        tutorialViewModel.showPrivacyScreen()

        verify(router).showPrivacyScreen()
        verifyNoMoreInteractions(router)
        verifyZeroInteractions(interactor, progress)
    }
}