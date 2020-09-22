package jp.co.soramitsu.feature_onboarding_impl.presentation.verification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleTransformer
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class VerificationViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: OnboardingInteractor
    @Mock private lateinit var router: OnboardingRouter
    @Mock private lateinit var progress: WithProgress
    @Mock private lateinit var timer: TimerWrapper
    @Mock private lateinit var numbersFormatter: NumbersFormatter

    private lateinit var verificationViewModel: VerificationViewModel

    private var countryIso = "RU"

    @Before fun setUp() {
        given(progress.progressCompose<Int>()).willReturn(SingleTransformer { upstream -> upstream })

        verificationViewModel = VerificationViewModel(interactor, router, progress, timer, numbersFormatter)
        verificationViewModel.setCountryIso(countryIso)
    }

    @Test fun `success verification code entered`() {
        given(interactor.verifySmsCode(anyString())).willReturn(Completable.complete())

        verificationViewModel.onVerify("1234")

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(timer).cancel()
        verify(router).showPersonalInfo(countryIso)
    }

    @Test fun `requesting new code will set new timer after success request`() {
        val expectedMinute = "01"
        val expectedSecond = "02"
        given(numbersFormatter.formatIntegerToTwoDigits(1)).willReturn(expectedMinute)
        given(numbersFormatter.formatIntegerToTwoDigits(2)).willReturn(expectedSecond)
        val lockTime = 62
        given(interactor.requestNewCode()).willReturn(Single.just(lockTime))

        given(timer.start(anyLong(), anyLong())).willReturn(Observable.just(62000))

        verificationViewModel.requestNewCode()

        verificationViewModel.timerLiveData.observeForever {
            assertEquals(expectedMinute, it.first)
            assertEquals(expectedSecond, it.second)
        }

        verify(interactor).requestNewCode()
    }

    @Test fun `backPressed calls interactor changePersonalData() and router onBackButtonPressed()`() {
        given(interactor.changePersonalData()).willReturn(Completable.complete())

        verificationViewModel.backPressed()

        verify(interactor).changePersonalData()
        verify(router).onBackButtonPressed()
    }
}