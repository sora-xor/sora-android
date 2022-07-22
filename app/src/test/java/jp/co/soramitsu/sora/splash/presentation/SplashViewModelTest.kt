/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private lateinit var splashViewModel: SplashViewModel

    @Before
    fun setUp() {
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log("Splash next screen true") } returns Unit
        splashViewModel = SplashViewModel(interactor)
    }

    @Test
    fun `nextScreen with REGISTRATION_FINISHED`() = runTest {
        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
        given(interactor.getRegistrationState()).willReturn(OnboardingState.REGISTRATION_FINISHED)
        splashViewModel.nextScreen()
        advanceUntilIdle()
        val r = splashViewModel.showMainScreen.getOrAwaitValue()
        assertEquals(Unit, r)
    }

    @Test
    fun `nextScreen with INITIAL`() = runTest {
        val state = OnboardingState.INITIAL

        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
        given(interactor.getRegistrationState()).willReturn(state)

        splashViewModel.nextScreen()
        advanceUntilIdle()
        val r = splashViewModel.showOnBoardingScreen.getOrAwaitValue()
        assertEquals(OnboardingState.INITIAL, r)
    }

    @Test
    fun `handleDeepLink before registration called`() = runTest {
        val state = OnboardingState.INITIAL
        val invitationCode = "INVITATION_CODE"

        given(interactor.getRegistrationState()).willReturn(state)

        splashViewModel.handleDeepLink(invitationCode)
        advanceUntilIdle()
        verify(interactor).saveInviteCode(invitationCode)
        val r = splashViewModel.showOnBoardingScreenViaInviteLink.getOrAwaitValue()
        assertEquals(Unit, r)
    }

    @Test
    fun `handleDeepLink after registration called`() = runTest {
        val state = OnboardingState.REGISTRATION_FINISHED
        val invitationCode = "INVITATION_CODE"

        given(interactor.getRegistrationState()).willReturn(state)

        splashViewModel.handleDeepLink(invitationCode)
        advanceUntilIdle()
        verify(interactor).saveInviteCode(invitationCode)
        val r = splashViewModel.showMainScreenFromInviteLink.getOrAwaitValue()
        assertEquals(Unit, r)
    }
}