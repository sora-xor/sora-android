package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class RecoveryViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: OnboardingInteractor
    @Mock private lateinit var router: OnboardingRouter
    @Mock private lateinit var progress: WithProgress

    private lateinit var privacyViewModel: RecoveryViewModel

    @Before fun setUp() {
        privacyViewModel = RecoveryViewModel(interactor, router, progress)
    }

    @Test fun `on back pressed clicked`() {
        privacyViewModel.backButtonClick()

        verify(router).onBackButtonPressed()
    }

    @Test fun `btn next clicked`() {
        val mnemonic = "faculty soda zero quote reopen rubber jazz feed casual shed veteran badge grief squeeze apple"

        given(interactor.runRecoverFlow(mnemonic)).willReturn(Completable.complete())

        privacyViewModel.btnNextClick(mnemonic)

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showMainScreen()
    }

}