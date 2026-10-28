/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.splash.domain

import android.content.Context
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import io.mockk.coEvery
import io.mockk.mockk
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.core_db.SetUpAgainScope
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationManager
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

    @Mock
    private lateinit var context: Context

    private lateinit var splashInteractor: SplashInteractor

    @Before
    fun setUp() {
        WalletRecoveryCapabilityGate.enterNormal()
        val manager = mock<CoroutineManager>()
        whenever(manager.applicationScope).thenReturn(CoroutineScope(SupervisorJob() + Dispatchers.Main))
        whenever(manager.io).thenReturn(Dispatchers.Main)
        splashInteractor = SplashInteractor(userRepository, migrationManager, context, manager)
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
    fun `failure code that cannot be read from the database falls back to the code in memory`() =
        runTest {
            // While startup is blocked the database refuses every reader.
            WalletUpgradeBackup.noteTransientVerificationFailure("KEYSTORE_UNAVAILABLE")
            try {
                given(migrationManager.failureCode())
                    .willThrow(IllegalStateException("WALLET_UPGRADE_BACKUP_BLOCKED"))

                assertEquals("KEYSTORE_UNAVAILABLE", splashInteractor.getMigrationFailureCode())
            } finally {
                WalletUpgradeBackup.noteMigrationVerified()
            }
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
        assertTrue(splashInteractor.getMigrationDoneAsync().isCancelled)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.NORMAL,
            WalletRecoveryCapabilityGate.mode(),
        )
    }

    @Test
    fun `recovery options follow the recorded state, and never offer a restore it would refuse`() {
        WalletUpgradeBackup.noteMigrationVerified()
        try {
            // Nothing recorded (for example an unexpected error while routing): no restore.
            val fallback = splashInteractor.recoveryOptions("WALLET_VERIFICATION_FAILED")
            assertFalse(fallback.canRestoreFromPhrase || fallback.canRestoreFromDrive || fallback.canSetUpAgain)
            assertEquals(SetUpAgainScope.NONE, fallback.setUpAgainScope)
            assertTrue(fallback.canRetry && fallback.canContactSupport)

            // A verification that failed in this process: the same options requestSetUpAgain reads.
            WalletUpgradeBackup.noteMigrationRecoveryRequired("POST_MIGRATION_MISMATCH")
            val recorded = splashInteractor.recoveryOptions("POST_MIGRATION_MISMATCH")
            assertEquals(WalletUpgradeBackup.recoveryOptions(), recorded)
            assertEquals(SetUpAgainScope.DATABASE_ONLY, recorded.setUpAgainScope)
            assertTrue(recorded.canRestoreFromPhrase)
        } finally {
            WalletUpgradeBackup.noteMigrationVerified()
        }
    }

    @Test
    fun `normal later start replaces a completed result before navigation`() = runTest {
        given(migrationManager.start()).willReturn(false, true)
        splashInteractor.checkMigration()
        val prior = splashInteractor.getMigrationDoneAsync()
        val next = splashInteractor.startMigration()
        assertFalse(prior === next)
        assertTrue(next.await())
        verify(migrationManager, times(2)).start()
    }

    @Test
    fun `losing a caller does not cancel verification and another screen joins the same run`() = runTest {
        val finish = CompletableDeferred<Boolean>()
        val migrations = mockk<MigrationManager>()
        coEvery { migrations.start() } coAnswers { finish.await() }
        val manager = mock<CoroutineManager>()
        whenever(manager.applicationScope).thenReturn(backgroundScope)
        whenever(manager.io).thenReturn(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val interactor = SplashInteractor(userRepository, migrations, context, manager)
        val caller = launch { interactor.checkMigration() }
        runCurrent()
        val current = interactor.getMigrationDoneAsync()
        caller.cancel()
        assertFalse(current.isCompleted)
        assertTrue(current === interactor.startMigration())
        finish.complete(true)
        assertTrue(current.await())
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
