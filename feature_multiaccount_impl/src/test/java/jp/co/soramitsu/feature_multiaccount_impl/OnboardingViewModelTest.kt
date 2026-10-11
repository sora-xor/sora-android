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
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
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
            .thenReturn(MultiaccountInteractor.JsonImportResult(listOf(imported), 0))

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
    fun `a chosen backup file is kept out of the text field and is not parsed again per keystroke`() =
        runTest {
            whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
            whenever(multiAccInteractor.isJsonBackupValid("{file}")).thenReturn(true)
            onboardingViewModel.onRecoveryClicked(navController, 3)

            onboardingViewModel.onRecoveryFileChosen("{file}", "sora-backup.json")
            testScheduler.advanceUntilIdle()
            val chosen = requireNotNull(onboardingViewModel.recoveryState.value)
            // The screen gets the file's name; the text field stays empty.
            assertEquals("sora-backup.json", chosen.chosenFileName)
            assertEquals("", chosen.recoveryInputState.value.text)

            "file password".indices.forEach { end ->
                onboardingViewModel.onRecoveryPasswordChanged(
                    androidx.compose.ui.text.input.TextFieldValue("file password".take(end + 1))
                )
            }
            testScheduler.advanceUntilIdle()
            assertEquals(true, onboardingViewModel.recoveryState.value?.isButtonEnabled)
            org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.times(1))
                .isJsonBackupValid(org.mockito.kotlin.any())

            // Typing or pasting replaces the chosen file.
            whenever(multiAccInteractor.isJsonBackupValid("{")).thenReturn(false)
            onboardingViewModel.onRecoveryInputChanged(
                androidx.compose.ui.text.input.TextFieldValue("{")
            )
            testScheduler.advanceUntilIdle()
            assertNull(onboardingViewModel.recoveryState.value?.chosenFileName)
            assertEquals(false, onboardingViewModel.recoveryState.value?.isButtonEnabled)
        }

    @Test
    fun `a chosen file that is not a backup is refused and never shown`() = runTest {
        whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
        whenever(multiAccInteractor.isJsonBackupValid("time,level,message\n")).thenReturn(false)
        onboardingViewModel.onRecoveryClicked(navController, 3)

        onboardingViewModel.onRecoveryFileChosen("time,level,message\n", "app.log")
        testScheduler.advanceUntilIdle()

        val state = requireNotNull(onboardingViewModel.recoveryState.value)
        assertNull(state.chosenFileName)
        assertEquals("", state.recoveryInputState.value.text)
        assertEquals(false, state.isButtonEnabled)
        assertEquals(
            R.string.recovery_json_file_unreadable,
            onboardingViewModel.errorFromResourceLiveData.value?.second,
        )
    }

    @Test
    fun `accounts of a batch file that need repair are listed and repaired after one confirmation`() =
        runTest {
            val context = org.mockito.kotlin.mock<android.content.Context>()
            val imported = jp.co.soramitsu.common.account.SoraAccount("cnFromFile", "Restored")
            whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
            whenever(multiAccInteractor.isJsonBackupValid("{batch}")).thenReturn(true)
            whenever(multiAccInteractor.importFromJson("{batch}", "pw", "", null))
                .thenReturn(batchResult(imported))
            whenever(
                multiAccInteractor.repairFromJsonBatch(
                    "{batch}",
                    "pw",
                    "",
                    "135790",
                    listOf("cnBrokenAccountAddress0001", "cnOrphan"),
                    false,
                )
            ).thenReturn(emptyList())

            importBatch(context)

            // Nothing opens and nothing is replaced until the person has confirmed.
            org.mockito.kotlin.verify(mainStarter, org.mockito.kotlin.never()).start(context)
            val prompt = requireNotNull(onboardingViewModel.repairPrompt.value)
            assertEquals(
                listOf("Broken (cnBroken…ress0001)", "cnOrphan"),
                prompt.batchAccounts,
            )
            assertEquals(true, prompt.pinRequired)
            assertEquals(false, prompt.fromPhrase)

            onboardingViewModel.onRepairConfirmed("135790")
            testScheduler.advanceUntilIdle()

            org.mockito.kotlin.verify(multiAccInteractor).repairFromJsonBatch(
                "{batch}",
                "pw",
                "",
                "135790",
                listOf("cnBrokenAccountAddress0001", "cnOrphan"),
                false,
            )
            assertNull(onboardingViewModel.repairPrompt.value)
            assertNull(onboardingViewModel.batchImportNotice.value)
            org.mockito.kotlin.verify(mainStarter).start(context)
        }

    @Test
    fun `cancelling the batch repair changes nothing and says what was added`() = runTest {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        val imported = jp.co.soramitsu.common.account.SoraAccount("cnFromFile", "Restored")
        whenever(resourceManager.getString(org.mockito.kotlin.any()))
            .thenReturn("Added %1\$s. Not changed %2\$s.")
        whenever(multiAccInteractor.isJsonBackupValid("{batch}")).thenReturn(true)
        whenever(multiAccInteractor.importFromJson("{batch}", "pw", "", null))
            .thenReturn(batchResult(imported))

        importBatch(context)
        onboardingViewModel.onRepairDismissed()

        assertNull(onboardingViewModel.repairPrompt.value)
        org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never()).repairFromJsonBatch(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
        // The wallet opens only after the person has read what was not changed.
        org.mockito.kotlin.verify(mainStarter, org.mockito.kotlin.never()).start(context)
        assertEquals("Added 1. Not changed 2.", onboardingViewModel.batchImportNotice.value?.message)
        onboardingViewModel.onBatchImportNoticeDismissed(context)
        org.mockito.kotlin.verify(mainStarter).start(context)
        assertNull(onboardingViewModel.batchImportNotice.value)
    }

    @Test
    fun `conflicting stored keys offer the repair and the right PIN repairs from a phrase`() = runTest {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
        whenever(multiAccInteractor.isMnemonicValid("the phrase")).thenReturn(true)
        whenever(multiAccInteractor.importFromMnemonic("the phrase", "Name")).thenAnswer {
            throw repairOffer(accountExists = true, pinRequired = true)
        }
        whenever(multiAccInteractor.repairFromMnemonic("the phrase", "Name", "000000", false))
            .thenAnswer {
                throw jp.co.soramitsu.common.domain.SoraException.businessError(
                    jp.co.soramitsu.common.domain.ResponseCode.WRONG_PIN_CODE
                )
            }
        whenever(multiAccInteractor.repairFromMnemonic("the phrase", "Name", "135790", false))
            .thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBroken", "Name"))

        enterAndContinue(kind = 1, input = "the phrase", context = context)

        // The import wrote nothing and opened nothing: it offers the repair.
        val prompt = requireNotNull(onboardingViewModel.repairPrompt.value)
        assertEquals(true, prompt.accountExists)
        assertEquals(true, prompt.pinRequired)
        assertEquals(true, prompt.fromPhrase)
        assertEquals(false, prompt.screenLockRequired)
        org.mockito.kotlin.verify(mainStarter, org.mockito.kotlin.never()).start(context)

        // A wrong PIN keeps the prompt open and says so; nothing was changed.
        onboardingViewModel.onRepairConfirmed("000000")
        testScheduler.advanceUntilIdle()
        assertEquals(true, onboardingViewModel.repairPrompt.value?.wrongPin)
        assertEquals(false, onboardingViewModel.repairPrompt.value?.working)
        org.mockito.kotlin.verify(mainStarter, org.mockito.kotlin.never()).start(context)

        onboardingViewModel.onRepairConfirmed("135790")
        testScheduler.advanceUntilIdle()
        assertNull(onboardingViewModel.repairPrompt.value)
        org.mockito.kotlin.verify(mainStarter).start(context)
    }

    @Test
    fun `the repair of a raw seed import calls the raw seed repair`() = runTest {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
        whenever(multiAccInteractor.isRawSeedValid("the seed")).thenReturn(true)
        whenever(multiAccInteractor.importFromRawSeed("the seed", "Name")).thenAnswer {
            throw repairOffer(accountExists = false, pinRequired = false)
        }
        whenever(multiAccInteractor.repairFromRawSeed("the seed", "Name", "", false))
            .thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBroken", "Name"))

        enterAndContinue(kind = 2, input = "the seed", context = context)

        val prompt = requireNotNull(onboardingViewModel.repairPrompt.value)
        assertEquals(false, prompt.accountExists)
        assertEquals(false, prompt.pinRequired)
        assertEquals(false, prompt.fromPhrase)

        onboardingViewModel.onRepairConfirmed("")
        testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verify(multiAccInteractor).repairFromRawSeed("the seed", "Name", "", false)
        org.mockito.kotlin.verify(mainStarter).start(context)
    }

    @Test
    fun `the repair of a one-account backup file calls the file repair with its password`() = runTest {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
        whenever(multiAccInteractor.isJsonBackupValid("{file}")).thenReturn(true)
        whenever(multiAccInteractor.importFromJson("{file}", "file password", "Name", null))
            .thenAnswer { throw repairOffer(accountExists = true, pinRequired = true) }
        whenever(
            multiAccInteractor.repairFromJson("{file}", "file password", "Name", "135790", null, false)
        ).thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBroken", "Name"))

        enterAndContinue(kind = 3, input = "{file}", password = "file password", context = context)
        assertEquals(false, onboardingViewModel.repairPrompt.value?.fromPhrase)

        onboardingViewModel.onRepairConfirmed("135790")
        testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verify(multiAccInteractor)
            .repairFromJson("{file}", "file password", "Name", "135790", null, false)
        org.mockito.kotlin.verify(mainStarter).start(context)
    }

    @Test
    fun `too many wrong PINs close the repair with nothing changed and say how long it is locked`() =
        runTest {
            val context = org.mockito.kotlin.mock<android.content.Context>()
            whenever(resourceManager.getString(org.mockito.kotlin.any()))
                .thenReturn("Locked for about %1\$s min")
            whenever(multiAccInteractor.isMnemonicValid("the phrase")).thenReturn(true)
            whenever(multiAccInteractor.importFromMnemonic("the phrase", "Name")).thenAnswer {
                throw repairOffer(accountExists = true, pinRequired = true)
            }
            whenever(multiAccInteractor.repairFromMnemonic("the phrase", "Name", "000000", false))
                .thenAnswer {
                    throw jp.co.soramitsu.feature_account_api.domain.model.RepairPinLockedException(
                        remainingMillis = 4 * 60_000L + 1,
                    )
                }

            enterAndContinue(kind = 1, input = "the phrase", context = context)
            onboardingViewModel.onRepairConfirmed("000000")
            testScheduler.advanceUntilIdle()

            assertNull(onboardingViewModel.repairPrompt.value)
            // Rounded up to whole minutes.
            assertEquals(
                "Locked for about 5 min",
                onboardingViewModel.alertDialogLiveData.value?.second,
            )
            org.mockito.kotlin.verify(mainStarter, org.mockito.kotlin.never()).start(context)

            // The prompt is closed: another confirmation does nothing.
            onboardingViewModel.onRepairConfirmed("135790")
            testScheduler.advanceUntilIdle()
            org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never())
                .repairFromMnemonic("the phrase", "Name", "135790", false)
        }

    @Test
    fun `a stored PIN that cannot be read makes the repair ask for the screen lock`() = runTest {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
        whenever(multiAccInteractor.isMnemonicValid("the phrase")).thenReturn(true)
        whenever(multiAccInteractor.importFromMnemonic("the phrase", "Name")).thenAnswer {
            throw jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException(
                address = "cnBroken",
                accountExists = true,
                pinRequired = false,
                screenLockRequired = true,
            )
        }
        whenever(multiAccInteractor.repairFromMnemonic("the phrase", "Name", "", true))
            .thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBroken", "Name"))

        enterAndContinue(kind = 1, input = "the phrase", context = context)
        val prompt = requireNotNull(onboardingViewModel.repairPrompt.value)
        assertEquals(true, prompt.screenLockRequired)
        assertEquals(false, prompt.pinRequired)

        // The screen confirmed the phone's screen lock.
        onboardingViewModel.onRepairConfirmed(pin = "", screenLockConfirmed = true)
        testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verify(multiAccInteractor).repairFromMnemonic("the phrase", "Name", "", true)
        org.mockito.kotlin.verify(mainStarter).start(context)
    }

    @Test
    fun `cancelling the repair of one account changes nothing and stays on the import`() = runTest {
        val context = org.mockito.kotlin.mock<android.content.Context>()
        whenever(resourceManager.getString(org.mockito.kotlin.any())).thenReturn("label")
        whenever(multiAccInteractor.isMnemonicValid("the phrase")).thenReturn(true)
        whenever(multiAccInteractor.importFromMnemonic("the phrase", "Name")).thenAnswer {
            throw repairOffer(accountExists = true, pinRequired = true)
        }

        enterAndContinue(kind = 1, input = "the phrase", context = context)
        onboardingViewModel.onRepairDismissed()

        assertNull(onboardingViewModel.repairPrompt.value)
        assertNull(onboardingViewModel.batchImportNotice.value)
        org.mockito.kotlin.verify(mainStarter, org.mockito.kotlin.never()).start(context)
        // The import can be started again: the button is enabled and nothing is loading.
        assertEquals(true, onboardingViewModel.recoveryAccountNameCardState.value?.btnEnabled)
        assertEquals(false, onboardingViewModel.recoveryAccountNameCardState.value?.loading)
    }

    @Test
    fun `a Google backup repaired after confirmation still offers the other backups`() = runTest {
        val backup = googleBackup(
            types = listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountType.JSON),
            phrase = "",
            seed = "",
        )
        whenever(coroutineManager.io)
            .thenReturn(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        whenever(backupService.importBackupAccount("cnBackedUp", "backup password"))
            .thenReturn(backup)
        // Two more backups are on the drive.
        whenever(backupService.getBackupAccounts()).thenReturn(
            listOf(
                jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Second", "cnSecond"),
                jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Third", "cnThird"),
            )
        )
        whenever(multiAccInteractor.isJsonBackupValid("{backup json}")).thenReturn(true)
        whenever(multiAccInteractor.isAddressValid(org.mockito.kotlin.any())).thenReturn(true)
        // Neither of the two is on this phone yet.
        whenever(multiAccInteractor.backupRestores(listOf("cnSecond", "cnThird"))).thenReturn(
            mapOf(
                "cnSecond" to MultiaccountInteractor.BackupRestore.ADDS_ACCOUNT,
                "cnThird" to MultiaccountInteractor.BackupRestore.ADDS_ACCOUNT,
            )
        )
        whenever(
            multiAccInteractor.importFromJson("{backup json}", "backup password", "Backed up", "cnBackedUp")
        ).thenAnswer { throw repairOffer(accountExists = true, pinRequired = true) }
        whenever(
            multiAccInteractor.repairFromJson(
                "{backup json}",
                "backup password",
                "Backed up",
                "135790",
                "cnBackedUp",
                false,
            )
        ).thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBackedUp", "Backed up"))
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

        // The restore stopped at the repair prompt: no success screen yet, nothing loading.
        requireNotNull(onboardingViewModel.repairPrompt.value)
        assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isLoading)

        onboardingViewModel.onRepairConfirmed("135790")
        testScheduler.advanceUntilIdle()

        assertNull(onboardingViewModel.repairPrompt.value)
        // The success screen offers "Import more": two other backups remain.
        assertEquals(true, onboardingViewModel.importAccountPasswordState.value?.isImportMoreAvailable)
        assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isLoading)
    }

    @Test
    fun `the Google backup of a read-only account is listed, restored and repaired after the PIN`() =
        runTest {
            val works = jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Works", "cnWorks")
            val readOnly =
                jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Backed up", "cnBackedUp")
            val notHere =
                jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Not here", "cnNotHere")
            whenever(coroutineManager.io)
                .thenReturn(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            whenever(backupService.getBackupAccounts()).thenReturn(listOf(works, readOnly, notHere))
            whenever(multiAccInteractor.isAddressValid(org.mockito.kotlin.any())).thenReturn(true)
            // On this phone: cnWorks can sign, cnBackedUp is listed but read-only, cnNotHere is
            // not listed. After the repair cnBackedUp can sign too.
            whenever(
                multiAccInteractor.backupRestores(listOf("cnWorks", "cnBackedUp", "cnNotHere"))
            ).thenReturn(
                mapOf(
                    "cnBackedUp" to
                        MultiaccountInteractor.BackupRestore.REPAIRS_READ_ONLY_ACCOUNT,
                    "cnNotHere" to MultiaccountInteractor.BackupRestore.ADDS_ACCOUNT,
                )
            ).thenReturn(
                mapOf("cnNotHere" to MultiaccountInteractor.BackupRestore.ADDS_ACCOUNT)
            )

            onboardingViewModel.onSuccessfulGoogleSignin(navController)
            testScheduler.advanceUntilIdle()

            // The list of backups opens. The read-only account is in it and marked; only the
            // account that already works is left out.
            org.mockito.kotlin.verify(navController)
                .navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
            val list = requireNotNull(onboardingViewModel.importAccountListState.value).accountList
            assertEquals(
                listOf("cnBackedUp" to true, "cnNotHere" to false),
                list.map { it.backupAccountMeta.address to it.readOnlyOnThisPhone },
            )

            // Its backup is opened with the backup password. The keys stored for the account on
            // this phone cannot be used, so the restore offers the repair and writes nothing.
            whenever(backupService.importBackupAccount("cnBackedUp", "backup password")).thenReturn(
                googleBackup(
                    types = listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountType.PASSPHRASE),
                    phrase = "own phrase",
                    seed = "",
                )
            )
            whenever(multiAccInteractor.isMnemonicValid("own phrase")).thenReturn(true)
            whenever(multiAccInteractor.addressOfMnemonic("own phrase")).thenReturn("cnBackedUp")
            whenever(multiAccInteractor.importFromMnemonic("own phrase", "Backed up")).thenAnswer {
                throw repairOffer(accountExists = true, pinRequired = true)
            }
            whenever(multiAccInteractor.repairFromMnemonic("own phrase", "Backed up", "135790", false))
                .thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBackedUp", "Kept name"))
            onboardingViewModel.onImportAccountSelected(navController, list.first())
            onboardingViewModel.onImportPasswordChanged(
                androidx.compose.ui.text.input.TextFieldValue("backup password")
            )
            onboardingViewModel.onImportContinueClicked(navController)
            testScheduler.advanceUntilIdle()

            val prompt = requireNotNull(onboardingViewModel.repairPrompt.value)
            assertEquals(true, prompt.accountExists)
            assertEquals(true, prompt.pinRequired)
            assertEquals(true, prompt.fromPhrase)
            org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never()).repairFromMnemonic(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
            )

            onboardingViewModel.onRepairConfirmed("135790")
            testScheduler.advanceUntilIdle()

            org.mockito.kotlin.verify(multiAccInteractor)
                .repairFromMnemonic("own phrase", "Backed up", "135790", false)
            assertNull(onboardingViewModel.repairPrompt.value)
            // The success screen offers the backup that is left; the repaired account is not
            // offered again.
            assertEquals(true, onboardingViewModel.importAccountPasswordState.value?.isImportMoreAvailable)
            assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isLoading)
            onboardingViewModel.onImportMoreClicked(navController)
            testScheduler.advanceUntilIdle()
            assertEquals(
                listOf("cnNotHere" to false),
                onboardingViewModel.importAccountListState.value?.accountList
                    ?.map { it.backupAccountMeta.address to it.readOnlyOnThisPhone },
            )
        }

    @Test
    fun `a read-only account whose only backup is on Google is offered there, not Create account`() =
        runTest {
            whenever(coroutineManager.io)
                .thenReturn(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
            whenever(backupService.getBackupAccounts()).thenReturn(
                listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Backed up", "cnBackedUp"))
            )
            whenever(multiAccInteractor.isAddressValid("cnBackedUp")).thenReturn(true)
            whenever(multiAccInteractor.backupRestores(listOf("cnBackedUp"))).thenReturn(
                mapOf(
                    "cnBackedUp" to MultiaccountInteractor.BackupRestore.REPAIRS_READ_ONLY_ACCOUNT
                )
            ).thenReturn(emptyMap())

            onboardingViewModel.onSuccessfulGoogleSignin(navController)
            testScheduler.advanceUntilIdle()

            org.mockito.kotlin.verify(navController)
                .navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
            org.mockito.kotlin.verify(navController, org.mockito.kotlin.never())
                .navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.CREATE_ACCOUNT)
            val entry = requireNotNull(onboardingViewModel.importAccountListState.value)
                .accountList.single()
            assertEquals(true, entry.readOnlyOnThisPhone)

            // Only values were missing on the phone (for example the nonce): the restore adds
            // them and the account works again, with no repair to confirm.
            whenever(backupService.importBackupAccount("cnBackedUp", "backup password")).thenReturn(
                googleBackup(
                    types = listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountType.SEED),
                    phrase = "",
                    seed = "own seed",
                )
            )
            whenever(multiAccInteractor.isRawSeedValid("own seed")).thenReturn(true)
            whenever(multiAccInteractor.addressOfRawSeed("own seed")).thenReturn("cnBackedUp")
            whenever(multiAccInteractor.importFromRawSeed("own seed", "Backed up"))
                .thenReturn(jp.co.soramitsu.common.account.SoraAccount("cnBackedUp", "Kept name"))
            onboardingViewModel.onImportAccountSelected(navController, entry)
            onboardingViewModel.onImportPasswordChanged(
                androidx.compose.ui.text.input.TextFieldValue("backup password")
            )
            onboardingViewModel.onImportContinueClicked(navController)
            testScheduler.advanceUntilIdle()

            org.mockito.kotlin.verify(multiAccInteractor).importFromRawSeed("own seed", "Backed up")
            assertNull(onboardingViewModel.repairPrompt.value)
            // Nothing is left to restore: the account works and is not offered again.
            assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isImportMoreAvailable)
            assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isLoading)
        }

    @Test
    fun `Google backups of accounts that all work here say so and do not open Create account`() =
        runTest {
            whenever(backupService.getBackupAccounts()).thenReturn(
                listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta("Works", "cnWorks"))
            )
            whenever(multiAccInteractor.isAddressValid("cnWorks")).thenReturn(true)
            whenever(multiAccInteractor.backupRestores(listOf("cnWorks"))).thenReturn(emptyMap())

            onboardingViewModel.onSuccessfulGoogleSignin(navController)
            testScheduler.advanceUntilIdle()

            // Nothing is offered again, and the screen stays where it is: opening Create account
            // would look as if this Google account held no backup.
            org.mockito.kotlin.verify(navController, org.mockito.kotlin.never())
                .navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
            org.mockito.kotlin.verify(navController, org.mockito.kotlin.never())
                .navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.CREATE_ACCOUNT)
            assertEquals(
                emptyList<Any>(),
                onboardingViewModel.importAccountListState.value?.accountList,
            )
            // The person is told what was found and what they can do next.
            assertEquals(
                R.string.google_backups_all_here_title to R.string.google_backups_all_here,
                onboardingViewModel.errorFromResourceLiveData.value,
            )
            assertEquals(false, onboardingViewModel.tutorialScreenState.value?.isGoogleSigninLoading)
        }

    @Test
    fun `a Google account with no backup still opens Create account without a message`() = runTest {
        whenever(backupService.getBackupAccounts()).thenReturn(emptyList())

        onboardingViewModel.onSuccessfulGoogleSignin(navController)
        testScheduler.advanceUntilIdle()

        org.mockito.kotlin.verify(navController)
            .navigate(jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingFeatureRoutes.CREATE_ACCOUNT)
        assertNull(onboardingViewModel.errorFromResourceLiveData.value)
        org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never())
            .backupRestores(org.mockito.kotlin.any())
    }

    @Test
    fun `a wrong password for a Google backup that holds only JSON is a Google backup message`() =
        runTest {
            // Nothing is decrypted before the JSON import for such a backup, so the wrong
            // password is found by the import, with the code of a backup file.
            importFromGoogle(
                googleBackup(
                    types = listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountType.JSON),
                    phrase = "",
                    seed = "",
                ),
                importResult = {
                    throw SoraException.businessError(ResponseCode.JSON_BACKUP_WRONG_PASSWORD)
                },
            )

            // The text of the Google restore screen, not of "the file you exported".
            assertEquals(
                ResponseCode.GOOGLE_BACKUP_DECRYPTION_FAILED.messageResource,
                onboardingViewModel.errorFromResourceLiveData.value?.second,
            )
            // The screen stays on the password, ready for another try.
            assertNull(onboardingViewModel.repairPrompt.value)
            assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isLoading)
        }

    @Test
    fun `a Google backup whose JSON cannot restore the account says so in Google backup words`() =
        runTest {
            importFromGoogle(
                googleBackup(
                    types = listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountType.JSON),
                    phrase = "",
                    seed = "",
                ),
                importResult = { throw SoraException.businessError(ResponseCode.JSON_BACKUP_INVALID) },
            )

            assertEquals(
                R.string.google_backup_unreadable_title to R.string.google_backup_unreadable,
                onboardingViewModel.errorFromResourceLiveData.value,
            )
            assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isLoading)
        }

    @Test
    fun `a Google backup that holds nothing usable says so and what to do next`() = runTest {
        whenever(coroutineManager.io)
            .thenReturn(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        // The backup opens with its password, but its phrase is not a valid phrase and it
        // holds no seed and no JSON.
        whenever(backupService.importBackupAccount("cnBackedUp", "backup password")).thenReturn(
            googleBackup(
                types = listOf(jp.co.soramitsu.xbackup.domain.models.BackupAccountType.PASSPHRASE),
                phrase = "not a phrase",
                seed = "",
            )
        )
        whenever(multiAccInteractor.isMnemonicValid("not a phrase")).thenReturn(false)
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

        // Not "Something went wrong": nothing was changed, and Import account is the way on.
        assertEquals(
            R.string.google_backup_unreadable_title to R.string.google_backup_unreadable,
            onboardingViewModel.errorFromResourceLiveData.value,
        )
        org.mockito.kotlin.verify(multiAccInteractor, org.mockito.kotlin.never())
            .importFromMnemonic(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        assertEquals(false, onboardingViewModel.importAccountPasswordState.value?.isLoading)
    }

    @Test
    fun `a phone without a file picker says that the backup text can be pasted`() = runTest {
        onboardingViewModel.onRecoveryFilePickerUnavailable()

        assertEquals(
            R.string.recovery_json_no_file_picker,
            onboardingViewModel.errorFromResourceLiveData.value?.second,
        )
    }

    private fun repairOffer(accountExists: Boolean, pinRequired: Boolean) =
        jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException(
            address = "cnBroken",
            accountExists = accountExists,
            pinRequired = pinRequired,
        )

    private fun batchResult(imported: jp.co.soramitsu.common.account.SoraAccount) =
        MultiaccountInteractor.JsonImportResult(
            imported = listOf(imported),
            alreadyHere = 1,
            needRepair = listOf(
                MultiaccountInteractor.RepairCandidate(
                    address = "cnBrokenAccountAddress0001",
                    name = "Broken",
                    accountExists = true,
                ),
                MultiaccountInteractor.RepairCandidate(
                    address = "cnOrphan",
                    name = "",
                    accountExists = false,
                ),
            ),
            repairPinRequired = true,
        )

    private fun kotlinx.coroutines.test.TestScope.importBatch(context: android.content.Context) {
        onboardingViewModel.onRecoveryClicked(navController, 3)
        onboardingViewModel.onRecoveryFileChosen("{batch}", "batch.json")
        onboardingViewModel.onRecoveryPasswordChanged(
            androidx.compose.ui.text.input.TextFieldValue("pw")
        )
        testScheduler.advanceUntilIdle()
        onboardingViewModel.recoveryNextClicked(navController, context)
        testScheduler.advanceUntilIdle()
    }

    /** Import account: choose the kind (1 phrase, 2 raw seed, 3 backup file), enter, Continue. */
    private fun kotlinx.coroutines.test.TestScope.enterAndContinue(
        kind: Int,
        input: String,
        context: android.content.Context,
        password: String = "",
    ) {
        onboardingViewModel.onRecoveryClicked(navController, kind)
        onboardingViewModel.onRecoveryInputChanged(
            androidx.compose.ui.text.input.TextFieldValue(input)
        )
        if (kind == 3) {
            onboardingViewModel.onRecoveryPasswordChanged(
                androidx.compose.ui.text.input.TextFieldValue(password)
            )
        }
        onboardingViewModel.onRecoveryAccountChanged(
            androidx.compose.ui.text.input.TextFieldValue("Name")
        )
        testScheduler.advanceUntilIdle()
        onboardingViewModel.recoveryNextClicked(navController, context)
        testScheduler.advanceUntilIdle()
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
        importResult: () -> MultiaccountInteractor.JsonImportResult = {
            MultiaccountInteractor.JsonImportResult(
                listOf(jp.co.soramitsu.common.account.SoraAccount("cnBackedUp", "Backed up")),
                0,
            )
        },
    ) {
        whenever(coroutineManager.io)
            .thenReturn(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        whenever(backupService.importBackupAccount("cnBackedUp", "backup password"))
            .thenReturn(backup)
        // Asked for only after a restore that succeeded ("Import more").
        org.mockito.Mockito.lenient().`when`(backupService.getBackupAccounts())
            .thenReturn(emptyList())
        whenever(multiAccInteractor.isJsonBackupValid("{backup json}")).thenReturn(true)
        whenever(multiAccInteractor.isAddressValid("cnBackedUp")).thenReturn(true)
        whenever(
            multiAccInteractor.importFromJson(
                "{backup json}",
                "backup password",
                "Backed up",
                "cnBackedUp",
            )
        ).thenAnswer { importResult() }
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
