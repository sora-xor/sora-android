/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.eqNonNull
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PersonalInfoViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: OnboardingInteractor
    @Mock private lateinit var router: OnboardingRouter
    @Mock private lateinit var progress: WithProgress
    @Mock private lateinit var invitationHandler: InvitationHandler

    private lateinit var personalInfoViewModel: PersonalInfoViewModel

    private val countryIso = "countryIso"
    private val invitationCode = "code"
    private val firstName = "firstName"
    private val lastName = "lastName"

    @Before fun setUp() {
        given(invitationHandler.observeInvitationApplies()).willReturn(Observable.just(invitationCode))
        given(interactor.getParentInviteCode()).willReturn(Single.just(invitationCode))

        personalInfoViewModel = PersonalInfoViewModel(interactor, router, progress, invitationHandler)
    }

    @Test fun `init successfull`() {
        assertEquals(personalInfoViewModel.inviteCodeLiveData.value, invitationCode)
    }

    @Test fun `back button clicked`() {
        personalInfoViewModel.backButtonClick()

        verify(router).onBackButtonPressed()
    }

    @Test fun `register called successfully`() {
        given(interactor.register(anyString(), anyString(), anyString(), anyString())).willReturn(Single.just(true))

        personalInfoViewModel.setCountryIso(countryIso)
        personalInfoViewModel.register(firstName, lastName, invitationCode)

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showMnemonic()
    }

    @Test fun `register called with wrong code`() {
        given(interactor.register(anyString(), anyString(), anyString(), eqNonNull(invitationCode))).willReturn(Single.just(false))

        personalInfoViewModel.setCountryIso(countryIso)
        personalInfoViewModel.register(firstName, lastName, invitationCode)

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router, times(0)).showMnemonic()
        assertEquals(personalInfoViewModel.invitationNotValidEventLiveData.value?.peekContent(), Unit)
    }

    @Test fun `continue without invitation code called`() {
        given(interactor.register(anyString(), anyString(), anyString(), eqNonNull(""))).willReturn(Single.just(true))

        personalInfoViewModel.setCountryIso(countryIso)
        personalInfoViewModel.continueWithoutInvitationCodePressed(firstName, lastName)

        verify(progress).showProgress()
        verify(progress).hideProgress()
        verify(router).showMnemonic()
    }
}