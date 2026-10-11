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

    @Test
    fun `backing out after Continue stores no key material`() = runTest {
        val draft = testDraft()
        whenever(multiAccInteractor.createDraft(org.mockito.kotlin.any())).thenReturn(draft)

        onboardingViewModel.onCreateAccountContinueClicked(navController)
        testScheduler.advanceUntilIdle()

        org.mockito.kotlin.verify(multiAccInteractor).createDraft(org.mockito.kotlin.any())
        org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never())
            .createUserFromDraft(org.mockito.kotlin.any())
        assertEquals(
            draft.mnemonicWords,
            onboardingViewModel.passphraseCardState.value?.mnemonicWords,
        )
    }

    @Test
    fun `finishing the create flow commits the draft once and opens the wallet`() = runTest {
        val draft = testDraft()
        val context = org.mockito.kotlin.mock<android.content.Context>()
        whenever(multiAccInteractor.createDraft(org.mockito.kotlin.any())).thenReturn(draft)
        whenever(multiAccInteractor.createUserFromDraft(draft))
            .thenReturn(jp.co.soramitsu.common.account.SoraAccount(draft.address, draft.accountName))

        onboardingViewModel.onCreateAccountContinueClicked(navController)
        testScheduler.advanceUntilIdle()
        onboardingViewModel.skipDialogConfirm(context)
        testScheduler.advanceUntilIdle()

        org.mockito.kotlin.verify(multiAccInteractor).createUserFromDraft(draft)
        org.mockito.kotlin.verify(mainStarter).start(context)
    }

    @Test
    fun `an encrypted backup file is imported with its password and opens the wallet`() = runTest {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        val imported = jp.co.soramitsu.common.account.SoraAccount("cnFromFile", "Restored")
        whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
        whenever(multiAccInteractor.isJsonBackupValid("{file}")).thenReturn(true)
        whenever(multiAccInteractor.importFromJson("{file}", "file password", "Restored", null))
            .thenReturn(MultiaccountInteractor.JsonImportResult(listOf(imported), 0, 0))

        onboardingViewModel.onRecoveryClicked(navController, 3)
        assertEquals(RecoveryType.JSON, onboardingViewModel.recoveryState.value?.recoveryType)
        // The file text alone is not enough: the password is needed too.
        onboardingViewModel.onRecoveryFileChosen("{file}")
        testScheduler.advanceUntilIdle()
        assertEquals(false, onboardingViewModel.recoveryState.value?.isButtonEnabled)
        onboardingViewModel.onRecoveryPasswordChanged(
            androidx.compose.ui.text.input.TextFieldValue("file password")
        )
        testScheduler.advanceUntilIdle()
        assertEquals(true, onboardingViewModel.recoveryState.value?.isButtonEnabled)
        onboardingViewModel.onRecoveryAccountChanged(
            androidx.compose.ui.text.input.TextFieldValue("Restored")
        )

        onboardingViewModel.recoveryNextClicked(navController, context)
        testScheduler.advanceUntilIdle()

        org.mockito.kotlin.verify(multiAccInteractor)
            .importFromJson("{file}", "file password", "Restored", null)
        org.mockito.kotlin.verify(mainStarter).start(context)
        assertNull(onboardingViewModel.batchImportNotice.value)
    }

    @Test
    fun `accounts of a batch backup file that were skipped are named before the wallet opens`() =
        runTest {
            val context = org.mockito.kotlin.mock<android.content.Context>()
            val imported = jp.co.soramitsu.common.account.SoraAccount("cnFromFile", "Restored")
            whenever(resourceManager.getString(org.mockito.kotlin.any()))
                .thenReturn("Added %1\$s. Skipped %2\$s.")
            whenever(multiAccInteractor.isJsonBackupValid("{batch}")).thenReturn(true)
            whenever(multiAccInteractor.importFromJson("{batch}", "pw", "", null))
                .thenReturn(MultiaccountInteractor.JsonImportResult(listOf(imported), 1, 2))

            onboardingViewModel.onRecoveryClicked(navController, 3)
            onboardingViewModel.onRecoveryFileChosen("{batch}")
            onboardingViewModel.onRecoveryPasswordChanged(
                androidx.compose.ui.text.input.TextFieldValue("pw")
            )
            testScheduler.advanceUntilIdle()
            onboardingViewModel.recoveryNextClicked(navController, context)
            testScheduler.advanceUntilIdle()

            // Nothing opens until the person has read which accounts were not added.
            org.mockito.kotlin.verify(mainStarter, org.mockito.kotlin.never()).start(context)
            assertEquals("Added 1. Skipped 2.", onboardingViewModel.batchImportNotice.value?.message)

            onboardingViewModel.onBatchImportNoticeDismissed(context)
            org.mockito.kotlin.verify(mainStarter).start(context)
            assertNull(onboardingViewModel.batchImportNotice.value)
        }

    @Test
    fun `a Google backup that holds only the encrypted JSON restores the account from it`() =
        runTest {
            val backup = googleBackup(
                types = listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountType.JSON),
                phrase = "",
                seed = "",
            )
            importFromGoogle(backup)

            org.mockito.kotlin.verify(multiAccInteractor)
                .importFromJson("{backup json}", "backup password", "Backed up", "cnBackedUp")
            org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never())
                .importFromMnemonic(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }

    @Test
    fun `a Google backup is restored from the form that derives the account it names`() = runTest {
        val all = listOf(
            jp.co.soramitsu.xbackup.domain.models.BackupAccountType.PASSPHRASE,
            jp.co.soramitsu.xbackup.domain.models.BackupAccountType.SEED,
            jp.co.soramitsu.xbackup.domain.models.BackupAccountType.JSON,
        )
        // An old backup: the phrase belongs to another wallet, the seed is this account's.
        whenever(multiAccInteractor.isMnemonicValid("other phrase")).thenReturn(true)
        whenever(multiAccInteractor.isRawSeedValid("own seed")).thenReturn(true)
        whenever(multiAccInteractor.addressOfMnemonic("other phrase")).thenReturn("cnOther")
        whenever(multiAccInteractor.addressOfRawSeed("own seed")).thenReturn("cnBackedUp")
        whenever(multiAccInteractor.importFromRawSeed("own seed", "Backed up"))
            .thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBackedUp", "Backed up"))

        importFromGoogle(googleBackup(types = all, phrase = "other phrase", seed = "own seed"))

        org.mockito.kotlin.verify(multiAccInteractor).importFromRawSeed("own seed", "Backed up")
        org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never())
            .importFromMnemonic(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `a Google backup whose phrase and seed are another wallet's falls back to its JSON`() =
        runTest {
            val all = listOf(
                jp.co.soramitsu.xbackup.domain.models.BackupAccountType.PASSPHRASE,
                jp.co.soramitsu.xbackup.domain.models.BackupAccountType.SEED,
                jp.co.soramitsu.xbackup.domain.models.BackupAccountType.JSON,
            )
            whenever(multiAccInteractor.isMnemonicValid("other phrase")).thenReturn(true)
            whenever(multiAccInteractor.isRawSeedValid("other seed")).thenReturn(true)
            whenever(multiAccInteractor.addressOfMnemonic("other phrase")).thenReturn("cnOther")
            whenever(multiAccInteractor.addressOfRawSeed("other seed")).thenReturn("cnOther")

            importFromGoogle(googleBackup(types = all, phrase = "other phrase", seed = "other seed"))

            org.mockito.kotlin.verify(multiAccInteractor)
                .importFromJson("{backup json}", "backup password", "Backed up", "cnBackedUp")
        }

    private fun googleBackup(
        types: List<jp.co.soramitsu.xbackup.domain.models.BackupAccountType>,
        phrase: String,
        seed: String,
    ) = jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount(
        name = "Backed up",
        address = "cnBackedUp",
        mnemonicPhrase = phrase,
        cryptoType = jp.co.soramitsu.xbackup.domain.models.CryptoType.SR25519,
        backupAccountType = types,
        seed = jp.co.soramitsu.xbackup.domain.models.Seed(substrateSeed = seed),
        json = jp.co.soramitsu.xbackup.domain.models.Json(substrateJson = "{backup json}"),
    )

    private suspend fun kotlinx.coroutines.test.TestScope.importFromGoogle(
        backup: jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount,
    ) {
        whenever(coroutineManager.io)
            .thenReturn(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        whenever(backupService.importBackupAccount("cnBackedUp", "backup password"))
            .thenReturn(backup)
        whenever(backupService.getBackupAccounts()).thenReturn(emptyList())
        whenever(multiAccInteractor.isJsonBackupValid("{backup json}")).thenReturn(true)
        whenever(multiAccInteractor.isAddressValid("cnBackedUp")).thenReturn(true)
        whenever(
            multiAccInteractor.importFromJson(
                "{backup json}",
                "backup password",
                "Backed up",
                "cnBackedUp",
            )
        ).thenReturn(
            MultiaccountInteractor.JsonImportResult(
                listOf(jp.co.soramitsu.common.account.SoraAccount("cnBackedUp", "Backed up")),
                0,
                0,
            )
        )
        onboardingViewModel.onImportAccountSelected(
            navController,
            jp.co.soramitsu.feature_multiaccount_impl.presentation.BackupAccountMetaWithIcon(
                jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Backed up", "cnBackedUp"),
                null,
            ),
        )
        onboardingViewModel.onImportPasswordChanged(
            androidx.compose.ui.text.input.TextFieldValue("backup password")
        )
        onboardingViewModel.onImportContinueClicked(navController)
        testScheduler.advanceUntilIdle()
    }

    private fun testDraft() = jp.co.soramitsu.feature_account_api.domain.model.WalletDraft(
        address = "cnDraftWallet",
        accountName = "",
        mnemonic = List(24) { "word$it" }.joinToString(" "),
        seedHex = "00".repeat(32),
        keypair = jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair(
            ByteArray(64),
            ByteArray(32),
            ByteArray(32),
        ),
        source = jp.co.soramitsu.feature_account_api.domain.model.WalletDraft.Source.GENERATED,
        wipeKeypair = {},
    )
}
