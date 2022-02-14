/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import jp.co.soramitsu.sora.splash.domain.SplashRouter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SplashViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: SplashInteractor

    @Mock
    private lateinit var router: SplashRouter

    @Mock
    private lateinit var runtime: RuntimeManager

    private lateinit var splashViewModel: SplashViewModel

    @Before
    fun setUp() {
        splashViewModel = SplashViewModel(interactor, router, runtime)
    }

    @Test
    fun `nextScreen with REGISTRATION_FINISHED`() = runBlockingTest {
        given(interactor.getRegistrationState()).willReturn(OnboardingState.REGISTRATION_FINISHED)

        splashViewModel.nextScreen()

        verify(router).showMainScreen()
    }

    @Test
    fun `nextScreen with INITIAL`() = runBlockingTest {
        val state = OnboardingState.INITIAL

        given(interactor.getRegistrationState()).willReturn(state)

        splashViewModel.nextScreen()

        verify(router).showOnBoardingScreen(state)
    }

    @Test
    fun `handleDeepLink before registration called`() = runBlockingTest {
        val state = OnboardingState.INITIAL
        val invitationCode = "INVITATION_CODE"

        given(interactor.getRegistrationState()).willReturn(state)

        splashViewModel.handleDeepLink(invitationCode)

        verify(interactor).saveInviteCode(invitationCode)
        verify(router).showOnBoardingScreenViaInviteLink()
    }

    @Test
    fun `handleDeepLink after registration called`() = runBlockingTest {
        val state = OnboardingState.REGISTRATION_FINISHED
        val invitationCode = "INVITATION_CODE"

        given(interactor.getRegistrationState()).willReturn(state)

        splashViewModel.handleDeepLink(invitationCode)

        verify(interactor).saveInviteCode(invitationCode)
        verify(router).showMainScreenFromInviteLink()
    }
}