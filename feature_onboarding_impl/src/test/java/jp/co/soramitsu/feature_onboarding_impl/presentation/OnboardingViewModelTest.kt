/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.navigation.NavController
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class OnboardingViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var invitationHandler: InvitationHandler

    @Mock
    private lateinit var multiaccountStarter: MultiaccountStarter

    @Mock
    private lateinit var navController: NavController

    private lateinit var onboardingViewModel: OnboardingViewModel

    @Before
    fun setUp() {
        onboardingViewModel = OnboardingViewModel(invitationHandler, multiaccountStarter)
    }

    @Test
    fun `success verification code entered`() {
        onboardingViewModel.startedWithInviteAction()

        verify(invitationHandler).invitationApplied()
    }

    @Test
    fun `sign up clicked EXPECT start create account flow`() {
        onboardingViewModel.onSignUpClicked(navController)

        verify(multiaccountStarter).startCreateAccount(navController)
    }

    @Test
    fun `recovery clicked EXPECT start recovery account flow`() {
        onboardingViewModel.onRecoveryClicked(navController)

        verify(multiaccountStarter).startRecoveryAccount(navController)
    }
}