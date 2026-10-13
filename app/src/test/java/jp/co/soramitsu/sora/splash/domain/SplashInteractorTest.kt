/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.splash.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given

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
        WalletRecoveryCapabilityGate.enterNormal()
        splashInteractor = SplashInteractor(userRepository, migrationManager)
    }

    @After
    fun tearDown() {
        WalletRecoveryCapabilityGate.enterNormal()
    }

    @Test
    fun `check migration called and migration done listened`() = runTest {
        given(migrationManager.start()).willReturn(true)

        splashInteractor.checkMigration()

        assertTrue(splashInteractor.getMigrationDoneAsync().await())
    }

    @Test
    fun `migration exception completes recovery decision instead of hanging splash`() = runTest {
        given(migrationManager.start()).willThrow(IllegalStateException("corrupt"))

        splashInteractor.checkMigration()

        assertFalse(splashInteractor.getMigrationDoneAsync().await())
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertEquals(
            "WALLET_RECOVERY_READ_ONLY",
            runCatching {
                WalletRecoveryCapabilityGate.requireUserMutationAllowed()
            }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `explicit retry replaces the completed failure decision`() = runTest {
        given(migrationManager.start()).willReturn(false, true)
        splashInteractor.checkMigration()
        assertFalse(splashInteractor.getMigrationDoneAsync().await())

        assertTrue(splashInteractor.retryMigration())

        assertTrue(splashInteractor.getMigrationDoneAsync().await())
        verify(migrationManager, times(2)).start()
    }

    @Test
    fun `migration cancellation remains cancellation and does not publish recovery`() = runTest {
        given(migrationManager.start()).willThrow(CancellationException("screen stopped"))
        var cancellationObserved = false

        try {
            splashInteractor.checkMigration()
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
        assertFalse(splashInteractor.getMigrationDoneAsync().isCompleted)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.NORMAL,
            WalletRecoveryCapabilityGate.mode(),
        )
    }

    @Test
    fun `getRegistrationState calls userRepository getRegistrationState`() = runTest {
        val expectedState = OnboardingState.REGISTRATION_FINISHED
        given(userRepository.getRegistrationState()).willReturn(expectedState)

        val state = splashInteractor.getRegistrationState()

        assertEquals(expectedState, state)
    }

    @Test
    fun `existing wallet is determined from durable account rows`() = runTest {
        given(userRepository.getSoraAccountsCount()).willReturn(1)

        assertTrue(splashInteractor.hasExistingWallet())
    }

    @Test
    fun `legacy continuation requires an exact existing selected wallet`() = runTest {
        given(userRepository.getCurSoraAccount()).willReturn(
            SoraAccount("cnRetainedWallet", "Retained")
        )

        assertTrue(splashInteractor.canContinueLegacyWallet())
    }

    @Test
    fun `missing legacy selection hides continuation instead of selecting a wallet`() = runTest {
        given(userRepository.getCurSoraAccount()).willThrow(
            IllegalStateException("SELECTED_WALLET_MISSING")
        )

        assertFalse(splashInteractor.canContinueLegacyWallet())
    }

    @Test
    fun `saveInviteCode calls userRepository saveInviteCode`() = runTest {
        val inviteCode = "inviteCode"

        splashInteractor.saveInviteCode(inviteCode)

        verify(userRepository).saveParentInviteCode(inviteCode)
    }
}
