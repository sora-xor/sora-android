/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PersonalInfoViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var interactor: OnboardingInteractor
    @Mock
    private lateinit var router: OnboardingRouter
    @Mock
    private lateinit var progress: WithProgress
    @Mock
    private lateinit var invitationHandler: InvitationHandler

    private lateinit var personalInfoViewModel: PersonalInfoViewModel

    private val countryIso = "countryIso"
    private val invitationCode = "code"
    private val accountName = "accountName"

    @Before
    fun setUp() {
//        given(invitationHandler.observeInvitationApplies()).willReturn(Observable.just(invitationCode))
//        given(interactor.getParentInviteCode()).willReturn(Single.just(invitationCode))

        personalInfoViewModel = PersonalInfoViewModel(interactor, router, progress)
    }

    @Test
    fun `back button clicked`() {
        personalInfoViewModel.backButtonClick()

        verify(router).onBackButtonPressed()
    }
}