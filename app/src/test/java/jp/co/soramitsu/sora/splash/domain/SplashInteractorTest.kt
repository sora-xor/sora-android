/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var migrationManager: MigrationManager

    private lateinit var splashInteractor: SplashInteractor

    @Before
    fun setUp() {
        splashInteractor = SplashInteractor(userRepository, migrationManager)
    }

    @Test
    fun `getRegistrationState calls userRepository getRegistrationState`() = runTest {
        splashInteractor.getRegistrationState()

        verify(userRepository).getRegistrationState()
    }

    @Test
    fun `saveInviteCode calls userRepository saveInviteCode`() = runTest {
        val inviteCode = "inviteCode"

        splashInteractor.saveInviteCode(inviteCode)

        verify(userRepository).saveParentInviteCode(inviteCode)
    }
}