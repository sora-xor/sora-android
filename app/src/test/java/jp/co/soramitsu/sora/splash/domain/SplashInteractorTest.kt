/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
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
class SplashInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    private lateinit var splashInteractor: SplashInteractor

    @Before
    fun setUp() {
        splashInteractor = SplashInteractor(userRepository, credentialsRepository)
    }

    @Test
    fun `getRegistrationState calls userRepository getRegistrationState`() {
        splashInteractor.getRegistrationState()

        verify(userRepository).getRegistrationState()
    }

    @Test
    fun `saveRegistrationState calls userRepository saveRegistrationState`() = runBlockingTest {
        val state = OnboardingState.INITIAL

        splashInteractor.saveRegistrationState(state)

        verify(userRepository).saveRegistrationState(state)
    }

    @Test
    fun `saveInviteCode calls userRepository saveInviteCode`() {
        val inviteCode = "inviteCode"

        splashInteractor.saveInviteCode(inviteCode)

        verify(userRepository).saveParentInviteCode(inviteCode)
    }
}