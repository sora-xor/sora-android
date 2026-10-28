@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeTags
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_multiaccount_impl.presentation.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.backup_password.BackupPasswordScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsScreenBasic
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.BackupScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json.BackupJsonScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password.ImportAccountPasswordScreen
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** All secrets in these UI fixtures are public, synthetic test strings. No backup is written. */
@RunWith(AndroidJUnit4::class)
class BackupAndRecoveryInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        SoraAppTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
    }
    private fun backup(state: State<BackupScreenState>, google: Boolean = false) = show {
        BackupScreen(state.value, { events += "primary" }, if (google) ({ events += "google" }) else null, { events += "json" })
    }
    private fun details() = AccountDetailsScreenState(InputTextState(TextFieldValue("My wallet"), label = "Account name"), true, false, false, "exact-account-address")
    private fun showDetails(state: State<AccountDetailsScreenState>) = show {
        AccountDetailsScreenBasic(state.value, { events += "name:${it.text}" }, { events += "phrase" }, { events += "seed" }, { events += "json" },
            { events += "google" }, { events += "remove" }, { events += "address" }, { events += "restore-phrase" }, { events += "restore-google" },
            { events += "saved-backup" }, { events += "restore-other" })
    }
    private fun password() = CreateBackupPasswordState(InputTextState(label = "New password"), InputTextState(label = "Repeat password"))
    private fun showPassword(state: State<CreateBackupPasswordState>) = show {
        BackupPasswordScreen(state.value, { events += "password:${it.text}" }, { events += "repeat:${it.text}" }, { events += "warning" }, { events += "save" })
    }
    private fun importState() = ImportAccountPasswordState(BackupAccountMetaWithIcon(BackupAccountMeta("Backup wallet", "backup-address"), ColorDrawable(Color.BLUE)), InputTextState(label = "Backup password"), isContinueButtonEnabled = true)
    private fun showImport(state: State<ImportAccountPasswordState>) = show {
        ImportAccountPasswordScreen(state.value, { events += "password:${it.text}" }, { events += "import" })
    }

    @Test fun creationRequiresRevealingThePhraseBeforeContinuing() {
        backup(mutableStateOf(BackupScreenState(mnemonicWords = phrase, isCreatingFlow = true)))
        node(R.string.common_continue).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("about").assertDoesNotExist()
        compose.onNodeWithTag(SafeModeTags.PHRASE_VEIL).performScrollTo().performClick()
        compose.onNodeWithText("about").assertExists()
        node(R.string.common_continue).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("primary"), events) }
    }
    @Test fun rawSeedCopyRemainsDisabledUntilExplicitReveal() {
        backup(mutableStateOf(BackupScreenState(seedString = "01".repeat(32))))
        node(R.string.copy_to_clipboard).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("01".repeat(32)).assertDoesNotExist()
        compose.onNodeWithTag("BackupSeedReveal").performScrollTo().performClick()
        compose.onNodeWithTag("BackupSeed").assertTextEquals("01".repeat(32))
        node(R.string.copy_to_clipboard).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("primary"), events) }
    }
    @Test fun changingTheSecretReveilsItAndRevokesCopy() {
        val state = mutableStateOf(BackupScreenState(seedString = "01".repeat(32))); backup(state)
        compose.onNodeWithTag("BackupSeedReveal").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(seedString = "02".repeat(32)) }
        compose.onNodeWithTag("BackupSeed").assertDoesNotExist()
        compose.onNodeWithTag("BackupSeedReveal").assertExists()
        node(R.string.copy_to_clipboard).performScrollTo().assertIsNotEnabled()
    }
    @Test fun googleBackupIsAnExplicitIndependentAction() {
        backup(mutableStateOf(BackupScreenState(mnemonicWords = phrase, isCreatingFlow = true)), google = true)
        node(R.string.account_options_backup_google).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("google"), events) }
        node(R.string.common_continue).performScrollTo().assertIsNotEnabled()
    }
    @Test fun unmatchedSecretOffersOnlyEligibleEncryptedExport() {
        backup(mutableStateOf(BackupScreenState(mnemonicWords = phrase, seedString = "01".repeat(32), noMatchingSecret = true, canExportJson = true)))
        node(R.string.backup_no_matching_secret).assertExists()
        compose.onNodeWithTag(SafeModeTags.PHRASE_VEIL).assertDoesNotExist()
        compose.onNodeWithTag("BackupSeedReveal").assertDoesNotExist()
        node(R.string.copy_to_clipboard).assertDoesNotExist()
        node(R.string.export_protection_json_title).performClick()
        compose.runOnIdle { assertEquals(listOf("json"), events) }
    }
    @Test fun unmatchedReadOnlySecretDoesNotOfferAnUnusableExport() {
        backup(mutableStateOf(BackupScreenState(noMatchingSecret = true, canExportJson = false)))
        node(R.string.backup_no_matching_secret_read_only).assertExists()
        node(R.string.export_protection_json_title).assertDoesNotExist()
    }
    @Test fun accountOptionsRouteEachBackupSourceAndExactAddress() {
        showDetails(mutableStateOf(details()))
        compose.onNodeWithText("exact-account-address").performScrollTo().performClick()
        node(R.string.export_account_details_show_passphrase).performScrollTo().performClick()
        node(R.string.export_account_details_show_raw_seed).performScrollTo().performClick()
        node(R.string.export_protection_json_title).performScrollTo().performClick()
        node(R.string.account_options_backup_google).performScrollTo().performClick()
        node(R.string.forget_account).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("address", "phrase", "seed", "json", "google", "remove"), events) }
    }
    @Test fun accountRenameForwardsTheExactEditedName() {
        showDetails(mutableStateOf(details()))
        compose.onNode(hasSetTextAction()).performTextReplacement("My second wallet")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(listOf("name:My second wallet"), events) }
    }
    @Test fun unavailableMnemonicAndCloudBackupRemoveTheirActions() {
        showDetails(mutableStateOf(details().copy(isMnemonicAvailable = false, isBackupAvailable = null, isCloudBackupAvailable = false)))
        node(R.string.export_account_details_show_passphrase).assertDoesNotExist()
        node(R.string.account_options_backup_google).assertDoesNotExist()
        node(R.string.account_options_delete_backup).assertDoesNotExist()
        node(R.string.wallet_cloud_backup_unavailable).performScrollTo().assertIsDisplayed()
    }
    @Test fun existingCloudBackupChangesTheActionToDelete() {
        val state = mutableStateOf(details()); showDetails(state)
        compose.runOnIdle { state.value = state.value.copy(isBackupAvailable = true) }
        node(R.string.account_options_backup_google).assertDoesNotExist()
        node(R.string.account_options_delete_backup).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("google"), events) }
    }
    @Test fun readOnlyRepairOptionsMatchTheirCapabilities() {
        val readOnly = ReadOnlyAccountUi(UnavailableWalletReason.KEY_MISSING, true, true, true, true)
        showDetails(mutableStateOf(details().copy(readOnly = readOnly)))
        node(R.string.account_read_only_reason_missing).assertExists()
        node(R.string.account_read_only_restore_phrase).performScrollTo().performClick()
        node(R.string.account_read_only_restore_other).performScrollTo().performClick()
        node(R.string.account_read_only_restore_google).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("restore-phrase", "restore-other", "restore-google"), events) }
    }
    @Test fun revokedReadOnlyRepairCapabilitiesRemoveStaleActions() {
        val state = mutableStateOf(details().copy(readOnly = ReadOnlyAccountUi(UnavailableWalletReason.KEY_MISSING, true, true, true, true)))
        showDetails(state)
        compose.runOnIdle { state.value = state.value.copy(readOnly = ReadOnlyAccountUi(UnavailableWalletReason.WATCH_ONLY, false, false, false, false)) }
        node(R.string.account_read_only_reason_watch_only).assertExists()
        node(R.string.account_read_only_restore_phrase).assertDoesNotExist()
        node(R.string.account_read_only_restore_google).assertDoesNotExist()
        node(R.string.account_read_only_restore_other).assertDoesNotExist()
    }
    @Test fun backupPasswordFieldsAndAcknowledgementHaveSeparateCallbacks() {
        showPassword(mutableStateOf(password()))
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("test-password-only")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("repeated-test-password")
        compose.onAllNodes(hasSetTextAction())[1].performImeAction()
        node(R.string.create_backup_password_warning_text).performScrollTo().performClick()
        node(R.string.create_backup_password_button_text).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf("password:test-password-only", "repeat:repeated-test-password", "warning"), events) }
    }
    @Test fun backupPasswordCanSubmitOnlyWhenTheValidatedStateIsReady() {
        val state = mutableStateOf(password()); showPassword(state)
        node(R.string.create_backup_password_button_text).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(setPasswordButtonIsEnabled = true, warningIsSelected = true) }
        node(R.string.create_backup_password_button_text).performClick()
        compose.runOnIdle { assertEquals(listOf("save"), events) }
    }
    @Test fun jsonExportPreservesTwoIndependentPasswordFieldsAndReadiness() {
        val state = mutableStateOf(BackupJsonScreenState(InputTextState(label = "New password"), InputTextState(label = "Repeat password")))
        show { BackupJsonScreen(state.value, { events += "password:${it.text}" }, { events += "repeat:${it.text}" }, { events += "download" }) }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("export-test-password")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("export-test-confirmation")
        val download = compose.onNodeWithText(compose.activity.getString(R.string.export_json_download_json).uppercase())
        download.performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(buttonEnabledState = true) }
        download.performClick()
        compose.runOnIdle { assertEquals(listOf("password:export-test-password", "repeat:export-test-confirmation", "download"), events) }
    }
    @Test fun importPasswordShowsTheSelectedBackupAndForwardsExactText() {
        showImport(mutableStateOf(importState()))
        compose.onNodeWithText("Backup wallet").assertExists()
        compose.onNodeWithText("backup-address").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("public-test-password")
        compose.onNode(hasSetTextAction()).performImeAction()
        node(R.string.common_continue).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("password:public-test-password", "import"), events) }
    }
    @Test fun importPasswordHonorsTheValidatedContinueState() {
        val state = mutableStateOf(importState().copy(isContinueButtonEnabled = false)); showImport(state)
        node(R.string.common_continue).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(isContinueButtonEnabled = true) }
        node(R.string.common_continue).performClick()
        compose.runOnIdle { assertEquals(listOf("import"), events) }
    }
    @Test fun switchingBackupRemovesThePreviousIdentityAndRepairNote() {
        val state = mutableStateOf(importState().copy(selectedAccount = importState().selectedAccount!!.copy(readOnlyOnThisPhone = true))); showImport(state)
        node(R.string.import_account_list_read_only_note).assertExists()
        compose.runOnIdle { state.value = state.value.copy(selectedAccount = BackupAccountMetaWithIcon(BackupAccountMeta("New backup", "new-backup-address"), null)) }
        compose.onNodeWithText("Backup wallet").assertDoesNotExist()
        compose.onNodeWithText("backup-address").assertDoesNotExist()
        node(R.string.import_account_list_read_only_note).assertDoesNotExist()
        compose.onNodeWithText("new-backup-address").assertExists()
    }
}
