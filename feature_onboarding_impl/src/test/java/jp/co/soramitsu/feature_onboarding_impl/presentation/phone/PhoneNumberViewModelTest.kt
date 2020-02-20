/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.phone

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_account_api.domain.model.UserCreatingCase
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
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PhoneNumberViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: OnboardingInteractor
    @Mock private lateinit var router: OnboardingRouter
    @Mock private lateinit var progress: WithProgress

    private lateinit var phoneNumberViewModel: PhoneNumberViewModel

    private val countryIso = "countryIso"
    private val phoneCode = "code"
    private val phoneNumber = "number"

    @Before fun setUp() {
        phoneNumberViewModel = PhoneNumberViewModel(interactor, router, progress)
    }

    @Test fun `back button clicked`() {
        phoneNumberViewModel.backButtonClick()

        verify(router).onBackButtonPressed()
    }

    @Test fun `on phone entered called verified`() {
        given(interactor.createUser(anyString())).willReturn(Single.just(UserCreatingCase(true, 0)))

        phoneNumberViewModel.setCountryIso(countryIso)
        phoneNumberViewModel.onPhoneEntered(phoneCode, phoneNumber)

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showPersonalInfo(countryIso)
    }

    @Test fun `on phone entered called not verified`() {
        val blockingTime = 10
        given(interactor.createUser(anyString())).willReturn(Single.just(UserCreatingCase(false, blockingTime)))

        phoneNumberViewModel.setCountryIso(countryIso)
        phoneNumberViewModel.onPhoneEntered(phoneCode, phoneNumber)

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showVerification(countryIso, blockingTime)
    }
}