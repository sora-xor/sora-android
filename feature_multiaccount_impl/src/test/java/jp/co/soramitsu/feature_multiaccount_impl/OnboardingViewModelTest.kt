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

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.navigation.NavController
import io.mockk.mockk
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryType
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
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
    private lateinit var multiAccInteractor: MultiaccountInteractor

    @Mock
    private lateinit var mainStarter: MainStarter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var backupService: BackupService

    @Mock
    private lateinit var avatarGenerator: AccountAvatarGenerator

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var accountResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var onboardingViewModel: OnboardingViewModel

    private val account = SoraAccount("address", "accountName")

    @Before
    fun setUp() = runTest {
        onboardingViewModel = OnboardingViewModel(
            invitationHandler,
            multiAccInteractor,
            mainStarter,
            resourceManager,
            backupService,
            avatarGenerator,
            coroutineManager,
        )
    }

    @Test
    fun `onRecoveryClicked() called test`() = runTest {
        given(resourceManager.getString(R.string.recovery_mnemonic_passphrase)).willReturn("passphrase label")
        val passphraseRecoveryState =
            RecoveryState(
                title = R.string.recovery_enter_passphrase_title,
                recoveryType = RecoveryType.PASSPHRASE,
                recoveryInputState = InputTextState(
                    label = resourceManager.getString(R.string.recovery_mnemonic_passphrase)
                )
            )

        val navController: NavController = mockk(relaxed = true)

        onboardingViewModel.onRecoveryClicked(navController, 1)

        assertEquals(onboardingViewModel?.recoveryState?.value, passphraseRecoveryState)


        given(resourceManager.getString(R.string.recovery_input_raw_seed_hint)).willReturn("raw label")
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