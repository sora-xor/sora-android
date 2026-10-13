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

package jp.co.soramitsu.feature_multiaccount_impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.navigation.NavController
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryType
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.xbackup.BackupService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

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
    private lateinit var multiAccInteractor: MultiaccountInteractor

    @Mock
    private lateinit var mainStarter: MainStarter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var backupService: BackupService

    @Mock
    private lateinit var navController: NavController

    @Mock
    private lateinit var avatarGenerator: AccountAvatarGenerator

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var onboardingViewModel: OnboardingViewModel

    @Before
    fun setUp() = runTest {
        onboardingViewModel = OnboardingViewModel(
            invitationHandler,
            multiAccInteractor,
            mainStarter,
            resourceManager,
            jp.co.soramitsu.common.backup.CloudBackupProvider(true) { backupService },
            avatarGenerator,
            coroutineManager,
        )
    }

    @Test
    fun `unconfigured Google backup does not initialize SDK or prevent local create`() = runTest {
        val unavailable = OnboardingViewModel(invitationHandler, multiAccInteractor, mainStarter,
            resourceManager, jp.co.soramitsu.common.backup.CloudBackupProvider(false) { error("Must remain lazy") },
            avatarGenerator, coroutineManager)
        assertEquals(false, unavailable.tutorialScreenState.value?.isGoogleBackupAvailable)
        unavailable.onCreateAccountClicked(navController)
        org.mockito.kotlin.verify(navController).navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.CREATE_ACCOUNT)
        unavailable.onGoogleSignin(navController, org.mockito.kotlin.mock())
        assertEquals(false, unavailable.tutorialScreenState.value?.isGoogleSigninLoading)
    }

    @Test
    fun `cancelled Google authorization always settles loading`() = runTest {
        val launcher = org.mockito.kotlin.mock<androidx.activity.result.ActivityResultLauncher<android.content.Intent>>()
        whenever(backupService.authorize(launcher)).thenReturn(false)
        onboardingViewModel.onGoogleSignin(navController, launcher)
        testScheduler.advanceUntilIdle()
        assertEquals(false, onboardingViewModel.tutorialScreenState.value?.isGoogleSigninLoading)
        org.mockito.kotlin.verify(backupService).logout()
    }

    @Test
    fun `unexpected Google authorization error always settles loading`() = runTest {
        val launcher = org.mockito.kotlin.mock<androidx.activity.result.ActivityResultLauncher<android.content.Intent>>()
        whenever(backupService.authorize(launcher)).thenThrow(IllegalStateException("SDK error"))
        onboardingViewModel.onGoogleSignin(navController, launcher)
        testScheduler.advanceUntilIdle()
        assertEquals(false, onboardingViewModel.tutorialScreenState.value?.isGoogleSigninLoading)
    }

    @Test
    fun `configured Google authorization continues to backup lookup and local creation`() = runTest {
        val launcher = org.mockito.kotlin.mock<androidx.activity.result.ActivityResultLauncher<android.content.Intent>>()
        whenever(backupService.authorize(launcher)).thenReturn(true)
        whenever(backupService.getBackupAccounts()).thenReturn(emptyList())
        onboardingViewModel.onGoogleSignin(navController, launcher)
        testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verify(backupService).getBackupAccounts()
        org.mockito.kotlin.verify(navController).navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.CREATE_ACCOUNT)
        assertEquals(false, onboardingViewModel.tutorialScreenState.value?.isGoogleSigninLoading)
    }

    @Test
    fun `onRecoveryClicked() called test`() = runTest {
        whenever(resourceManager.getString(R.string.recovery_mnemonic_passphrase)).thenReturn("passphrase label")
        val passphraseRecoveryState =
            RecoveryState(
                title = R.string.recovery_enter_passphrase_title,
                recoveryType = RecoveryType.PASSPHRASE,
                recoveryInputState = InputTextState(
                    label = resourceManager.getString(R.string.recovery_mnemonic_passphrase)
                )
            )
        onboardingViewModel.onRecoveryClicked(navController, 1)
        assertEquals(onboardingViewModel.recoveryState?.value, passphraseRecoveryState)

        whenever(resourceManager.getString(R.string.recovery_input_raw_seed_hint)).thenReturn("raw label")
        val seedRecoveryState =
            RecoveryState(
                title = R.string.recovery_enter_seed_title,
                recoveryType = RecoveryType.SEED,
                recoveryInputState = InputTextState(
                    label = resourceManager.getString(R.string.recovery_input_raw_seed_hint)
                )
            )
        onboardingViewModel.onRecoveryClicked(navController, 2)
        assertEquals(onboardingViewModel?.recoveryState?.value, seedRecoveryState)
        onboardingViewModel.onRecoveryClicked(navController, 0)
        assertNull(onboardingViewModel?.recoveryState?.value)
    }
}
