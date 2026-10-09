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

package jp.co.soramitsu.sora.splash.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.RecoveryFacts
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.sora.splash.domain.PendingRecoveryStartupScheduler
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
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
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var pendingRecoveryStartupScheduler: PendingRecoveryStartupScheduler

    private lateinit var splashViewModel: SplashViewModel

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        mockkObject(FirebaseWrapper)
        every { FirebaseWrapper.log(any()) } returns Unit
        whenever(coroutineManager.io).thenReturn(this.coroutineContext[CoroutineDispatcher]!!)
        splashViewModel = SplashViewModel(
            interactor,
            coroutineManager,
            pendingRecoveryStartupScheduler,
        )
    }

    @Test
    fun `nextScreen with REGISTRATION_FINISHED`() = runTest {
        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
        given(interactor.getRegistrationState()).willReturn(OnboardingState.REGISTRATION_FINISHED)
        given(interactor.hasExistingWallet()).willReturn(true)
        splashViewModel.nextScreen()
        advanceUntilIdle()
        val r = splashViewModel.showMainScreen.getOrAwaitValue()
        assertEquals(Unit, r)
    }

    @Test
    fun `nextScreen never navigates before wallet migration completes`() = runTest {
        val migrationResult = CompletableDeferred<Boolean>()
        given(interactor.getMigrationDoneAsync()).willReturn(migrationResult)
        given(interactor.getRegistrationState()).willReturn(OnboardingState.REGISTRATION_FINISHED)
        given(interactor.hasExistingWallet()).willReturn(true)

        splashViewModel.nextScreen()
        advanceUntilIdle()

        verify(interactor, never()).getRegistrationState()

        migrationResult.complete(true)
        advanceUntilIdle()

        verify(interactor).getRegistrationState()
        verify(pendingRecoveryStartupScheduler).scheduleAfterWalletMigration()
        assertEquals(Unit, splashViewModel.showMainScreen.getOrAwaitValue())
    }

    @Test
    fun `finished registration without any account opens onboarding instead of an empty wallet`() =
        runTest {
            given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
            given(interactor.getRegistrationState())
                .willReturn(OnboardingState.REGISTRATION_FINISHED)
            given(interactor.hasExistingWallet()).willReturn(false)

            splashViewModel.nextScreen()
            advanceUntilIdle()

            assertEquals(
                OnboardingState.INITIAL,
                splashViewModel.showOnBoardingScreen.getOrAwaitValue(),
            )
        }

    @Test
    fun `nextScreen with INITIAL`() = runTest {
        val state = OnboardingState.INITIAL

        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
        given(interactor.getRegistrationState()).willReturn(state)
        given(interactor.hasExistingWallet()).willReturn(false)

        splashViewModel.nextScreen()
        advanceUntilIdle()
        val r = splashViewModel.showOnBoardingScreen.getOrAwaitValue()
        assertEquals(OnboardingState.INITIAL, r)
    }

    @Test
    fun `missing onboarding preference with existing wallet opens wallet`() = runTest {
        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(true))
        given(interactor.getRegistrationState()).willReturn(OnboardingState.INITIAL)
        given(interactor.hasExistingWallet()).willReturn(true)

        splashViewModel.nextScreen()
        advanceUntilIdle()

        assertEquals(Unit, splashViewModel.showMainScreen.getOrAwaitValue())
    }

    @Test
    fun `migration recovery never evaluates onboarding or implicit wallet creation`() = runTest {
        given(interactor.getMigrationDoneAsync()).willReturn(CompletableDeferred(false))
        given(interactor.getMigrationFailureCode()).willReturn("SECRET_DECRYPTION_FAILED")
        given(interactor.canContinueLegacyWallet()).willReturn(true)
        given(interactor.recoveryOptions("SECRET_DECRYPTION_FAILED"))
            .willReturn(options("SECRET_DECRYPTION_FAILED"))

        splashViewModel.nextScreen()
        advanceUntilIdle()

        assertEquals(
            WalletMigrationRecoveryUiState(
                code = "SECRET_DECRYPTION_FAILED",
                canContinueLegacyWallet = true,
                options = options("SECRET_DECRYPTION_FAILED"),
            ),
            splashViewModel.showMigrationRecovery.getOrAwaitValue(),
        )
        verify(interactor, never()).getRegistrationState()
        verify(interactor, never()).hasExistingWallet()
        verify(pendingRecoveryStartupScheduler, never()).scheduleAfterWalletMigration()
    }

    @Test
    fun `legacy continuation revalidates exact selection immediately before navigation`() = runTest {
        given(interactor.canContinueLegacyWallet()).willReturn(true)

        splashViewModel.continueLegacyWallet()
        advanceUntilIdle()

        assertEquals(Unit, splashViewModel.showMainScreen.getOrAwaitValue())
    }

    @Test
    fun `legacy continuation selection race stays on recovery screen`() = runTest {
        given(interactor.canContinueLegacyWallet()).willReturn(false)
        given(interactor.getMigrationFailureCode()).willReturn("SELECTED_ACCOUNT_MISMATCH")
        given(interactor.recoveryOptions("SELECTED_ACCOUNT_MISMATCH"))
            .willReturn(options("SELECTED_ACCOUNT_MISMATCH"))

        splashViewModel.continueLegacyWallet()
        advanceUntilIdle()

        assertEquals(
            WalletMigrationRecoveryUiState(
                code = "SELECTED_ACCOUNT_MISMATCH",
                canContinueLegacyWallet = false,
                options = options("SELECTED_ACCOUNT_MISMATCH"),
            ),
            splashViewModel.showMigrationRecovery.getOrAwaitValue(),
        )
    }

    @Test
    fun `explicit retry reruns migration and opens main only after verification`() = runTest {
        // The same ViewModel survives Activity recreation. A completed preflight failure must
        // be replaced by retryMigration rather than awaited again through nextScreen.
        val failedInitialMigration = CompletableDeferred(false)
        given(interactor.getMigrationDoneAsync()).willReturn(failedInitialMigration)
        given(interactor.getMigrationFailureCode()).willReturn("INSUFFICIENT_BACKUP_STORAGE")
        given(interactor.canContinueLegacyWallet()).willReturn(false)
        given(interactor.recoveryOptions("INSUFFICIENT_BACKUP_STORAGE"))
            .willReturn(options("INSUFFICIENT_BACKUP_STORAGE"))
        splashViewModel.nextScreen()
        advanceUntilIdle()
        assertEquals(
            WalletMigrationRecoveryUiState(
                "INSUFFICIENT_BACKUP_STORAGE",
                false,
                options("INSUFFICIENT_BACKUP_STORAGE"),
            ),
            splashViewModel.showMigrationRecovery.getOrAwaitValue(),
        )
        verify(pendingRecoveryStartupScheduler, never()).scheduleAfterWalletMigration()
        given(interactor.retryMigration()).willReturn(true)

        splashViewModel.retryWalletMigration()
        advanceUntilIdle()

        assertEquals(Unit, splashViewModel.showMainScreen.getOrAwaitValue())
        assertEquals(false, failedInitialMigration.await())
        verify(interactor).retryMigration()
        verify(interactor, never()).getRegistrationState()
        verify(interactor, never()).hasExistingWallet()
        verify(pendingRecoveryStartupScheduler).scheduleAfterWalletMigration()
    }

    @Test
    fun `failed explicit retry remains recovery and preserves legacy option`() = runTest {
        given(interactor.retryMigration()).willReturn(false)
        given(interactor.getMigrationFailureCode()).willReturn("KEY_UNAVAILABLE")
        given(interactor.canContinueLegacyWallet()).willReturn(true)
        given(interactor.recoveryOptions("KEY_UNAVAILABLE")).willReturn(options("KEY_UNAVAILABLE"))

        splashViewModel.retryWalletMigration()
        advanceUntilIdle()

        assertEquals(
            WalletMigrationRecoveryUiState(
                code = "KEY_UNAVAILABLE",
                canContinueLegacyWallet = true,
                options = options("KEY_UNAVAILABLE"),
            ),
            splashViewModel.showMigrationRecovery.getOrAwaitValue(),
        )
        verify(interactor, never()).getRegistrationState()
        verify(interactor, never()).hasExistingWallet()
        verify(pendingRecoveryStartupScheduler, never()).scheduleAfterWalletMigration()
    }

    @Test
    fun `every recovery state offers retry, phrase restore and support`() = runTest {
        listOf(
            "KEYSTORE_UNAVAILABLE",
            "KEYSTORE_KEY_UNAVAILABLE",
            "INSUFFICIENT_BACKUP_STORAGE",
            "WALLET_MIGRATION_RECOVERY_REQUIRED",
            "WALLET_DELETION_JOURNAL_INVALID",
        ).forEach { code ->
            val options = options(code)
            assertEquals(true, options.canRetry)
            assertEquals(true, options.canRestoreFromPhrase)
            assertEquals(true, options.canRestoreFromDrive)
            assertEquals(true, options.canContactSupport)
        }
        assertEquals(false, options("KEYSTORE_KEY_UNAVAILABLE").canShowPhraseAfterPin)
        assertEquals(true, options("KEYSTORE_UNAVAILABLE").retryAutomatically)
    }

    private fun options(code: String) = WalletRecoveryOptions.forState(
        code = code,
        transientFailure = false,
        facts = RecoveryFacts(
            hasWalletFiles = true,
            hasRecoveryPhraseOrSeed = true,
            databaseAtCurrentVersion = true,
        ),
    )
}
