package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.feature_multiaccount_impl.presentation.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.*
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual onboarding destination graph; mocked handlers cannot import, create or export any account. */
@RunWith(AndroidJUnit4::class)
class OnboardingDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val tutorial = MutableLiveData(TutorialScreenState())
    private val create = MutableLiveData(CreateAccountState(InputTextState(label = "Wallet name")))
    private val recoveryName = MutableLiveData(CreateAccountState(InputTextState(label = "Imported wallet name")))
    private val recovery = MutableLiveData(RecoveryState(RecoveryType.PASSPHRASE, R.string.recovery_enter_passphrase_title,
        InputTextState(label = "Recovery input"), true))
    private val phrase = MutableLiveData(BackupScreenState(List(12) { "fixture$it" }, isCreatingFlow = true))
    private val confirmation = MutableLiveData(MnemonicConfirmationState(3, listOf("fixture2", "fixture7", "fixture8"), 1))
    private val backup = MutableLiveData(CreateBackupPasswordState(InputTextState(label = "Backup password"),
        InputTextState(label = "Confirm password"), true, true))
    private val selected = BackupAccountMetaWithIcon(BackupAccountMeta("Cloud wallet", "public-cloud-account"), null)
    private val accounts = MutableLiveData(ImportAccountListScreenState(listOf(selected)))
    private val password = MutableLiveData(ImportAccountPasswordState(selected, InputTextState(label = "Import password"), true, true))
    private val skip = MutableLiveData(false)
    private val found = MutableStateFlow<OnboardingViewModel.FoundWalletsState?>(null)
    private val notice = MutableStateFlow<OnboardingViewModel.BatchImportNotice?>(null)
    private val recoveryDialog = MutableStateFlow(false)
    private val vm = mockk<OnboardingViewModel>(relaxed = true) {
        every { tutorialScreenState } returns tutorial
        every { createAccountCardState } returns create
        every { recoveryAccountNameCardState } returns recoveryName
        every { recoveryState } returns recovery
        every { passphraseCardState } returns phrase
        every { passphraseConfirmationState } returns confirmation
        every { createBackupPasswordState } returns backup
        every { importAccountListState } returns accounts
        every { importAccountPasswordState } returns password
        every { skipDialogState } returns skip
        every { foundWallets } returns found
        every { batchImportNotice } returns notice
        every { repairPrompt } returns MutableStateFlow(null)
        every { recoveryDialog } returns this@OnboardingDestinationFlowTest.recoveryDialog
        every { disclaimerCardState } returns MutableLiveData(ExportProtectionScreenState(R.string.common_continue,
            R.string.onboarding_create_account_description, emptyList(), true))
    }
    private lateinit var host: OnboardingActivity
    private val nav: NavController get() = OnboardingActivity::class.java.getDeclaredField("navController").apply { isAccessible = true }.get(host) as NavController
    private fun show(route: String = OnboardingFeatureRoutes.TUTORIAL) {
        compose.runOnUiThread {
            host = OnboardingActivity()
            OnboardingActivity::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(host, lazyOf(vm))
            host.debounceClickHandler = DebounceClickHandler()
        }
        compose.setContent { SoraAppTheme { host.Content(PaddingValues(), rememberScrollState()) } }
        if (route != OnboardingFeatureRoutes.TUTORIAL) compose.runOnIdle { nav.navigate(route) }
    }
    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick()
    private fun route(expected: String) = compose.runOnIdle { assertEquals(expected, nav.currentDestination?.route) }
    @Test fun createChoiceUsesCreateHandler() { show(); click(R.string.create_account_title); compose.runOnIdle { verify { vm.onCreateAccountClicked(nav) } } }
    @Test fun importChoiceOpensSourceChoice() { show(); click(R.string.recovery_title); compose.runOnIdle { verify { vm.onRecoveryDialogShow() } } }
    @Test fun accountNameChangesOnlyCreateDraft() {
        show(OnboardingFeatureRoutes.CREATE_ACCOUNT); compose.onNode(hasSetTextAction()).performTextReplacement("Savings")
        compose.runOnIdle { verify { vm.onAccountNameChanged(match { it.text == "Savings" }) }; verify(exactly = 0) { vm.onRecoveryAccountChanged(any()) } }
    }
    @Test fun createContinueUsesNavigationAwareHandler() { show(OnboardingFeatureRoutes.CREATE_ACCOUNT); click(R.string.common_continue); compose.runOnIdle { verify { vm.onCreateAccountContinueClicked(nav) } } }
    @Test fun disabledCreateCannotAdvance() {
        compose.runOnUiThread { create.value = create.value!!.copy(btnEnabled = false) }; show(OnboardingFeatureRoutes.CREATE_ACCOUNT)
        compose.onNodeWithText(compose.activity.getString(R.string.common_continue)).assertIsNotEnabled()
    }
    @Test fun phraseContinueRequestsConfirmationWithoutCommitting() {
        show(OnboardingFeatureRoutes.PASSPHRASE); click(R.string.safe_mode_tap_to_reveal); click(R.string.common_continue)
        compose.runOnIdle { verify { vm.onPassphraseContinueClicked(nav) }; verify(exactly = 0) { vm.onConfirmationButtonPressed(any(), any()) } }
    }
    @Test fun phraseCloudBackupUsesSignInBoundary() {
        show(OnboardingFeatureRoutes.PASSPHRASE); click(R.string.account_options_backup_google)
        compose.runOnIdle { verify { vm.onGoogleSignin(nav, any()) } }
    }
    @Test fun alreadyCloudBackedPhraseHidesDuplicateSignIn() {
        compose.runOnUiThread { phrase.value = phrase.value!!.copy(isViaGoogleDrive = true) }; show(OnboardingFeatureRoutes.PASSPHRASE)
        compose.onNodeWithText(compose.activity.getString(R.string.account_options_backup_google)).assertDoesNotExist()
    }
    @Test fun selectedMnemonicWordReachesConfirmationHandler() {
        show(OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION); compose.onNodeWithText("fixture7").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onConfirmationButtonPressed(host, "fixture7") } }
    }
    @Test fun recoveryInputUsesImportValidationHandler() {
        show(OnboardingFeatureRoutes.RECOVERY); compose.onNode(hasSetTextAction()).performTextReplacement("invalid public phrase")
        compose.runOnIdle { verify { vm.onRecoveryInputChanged(match { it.text == "invalid public phrase" }) } }
    }
    @Test fun recoveryContinueOpensNameWithoutImporting() {
        show(OnboardingFeatureRoutes.RECOVERY); click(R.string.common_continue); route(OnboardingFeatureRoutes.RECOVERY_ACCOUNT_NAME)
        compose.runOnIdle { verify(exactly = 0) { vm.recoveryNextClicked(any(), any()) } }
    }
    @Test fun recoveryNameUsesSeparateHandler() {
        show(OnboardingFeatureRoutes.RECOVERY_ACCOUNT_NAME); compose.onNode(hasSetTextAction()).performTextReplacement("Imported")
        compose.runOnIdle { verify { vm.onRecoveryAccountChanged(match { it.text == "Imported" }) }; verify(exactly = 0) { vm.onAccountNameChanged(any()) } }
    }
    @Test fun recoveryNameContinueStartsExplicitImport() {
        show(OnboardingFeatureRoutes.RECOVERY_ACCOUNT_NAME); click(R.string.common_continue)
        compose.runOnIdle { verify(exactly = 1) { vm.recoveryNextClicked(nav, host) } }
    }
    @Test fun backupPasswordAndConfirmationUseSeparateInputs() {
        show(OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD)
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("fixture password")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("fixture confirmation")
        compose.runOnIdle { verify { vm.onBackupPasswordChanged(match { it.text == "fixture password" }); vm.onBackupPasswordConfirmationChanged(match { it.text == "fixture confirmation" }) } }
    }
    @Test fun backupSaveRequiresExplicitAction() {
        show(OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD); click(R.string.create_backup_password_button_text)
        compose.runOnIdle { verify(exactly = 1) { vm.onSetBackupPasswordClicked(host) } }
    }
    @Test fun cloudSelectionUsesExactAccount() {
        show(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST); compose.onNodeWithText("Cloud wallet").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onImportAccountSelected(nav, selected) } }
    }
    @Test fun cloudListCanReturnToCreateFlow() {
        show(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST); click(R.string.create_new_account_title); route(OnboardingFeatureRoutes.CREATE_ACCOUNT)
    }
    @Test fun importPasswordUsesCloudInputHandler() {
        show(OnboardingFeatureRoutes.IMPORT_ACCOUNT_PASSWORD); compose.onNode(hasSetTextAction()).performTextReplacement("public fixture password")
        compose.runOnIdle { verify { vm.onImportPasswordChanged(match { it.text == "public fixture password" }) } }
    }
    @Test fun cloudPasswordContinueUsesDebouncedHandler() {
        show(OnboardingFeatureRoutes.IMPORT_ACCOUNT_PASSWORD); click(R.string.common_continue)
        compose.runOnIdle { verify(exactly = 1) { vm.onImportContinueClicked(nav) } }
    }
    @Test fun completedImportContinuesToWallet() {
        show(OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS); click(R.string.common_continue)
        compose.runOnIdle { verify(exactly = 1) { vm.onImportFinished(host) } }
    }
    @Test fun completedImportCanRequestMoreAccounts() {
        show(OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS); click(R.string.import_more_title)
        compose.runOnIdle { verify(exactly = 1) { vm.onImportMoreClicked(nav) } }
    }
    @Test fun noAdditionalCloudAccountsHidesImportMore() {
        compose.runOnUiThread { password.value = password.value!!.copy(isImportMoreAvailable = false) }; show(OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS)
        compose.onNodeWithText(compose.activity.getString(R.string.import_more_title)).assertDoesNotExist()
    }
    @Test fun skipBackupWarningCancelDoesNotCommit() {
        compose.runOnUiThread { skip.value = true }; show(); compose.onNodeWithText(compose.activity.getString(R.string.common_cancel)).performClick()
        compose.runOnIdle { verify { vm.skipDialogDismiss() }; verify(exactly = 0) { vm.skipDialogConfirm(any()) } }
    }
    @Test fun skipBackupWarningConfirmUsesExplicitHandler() {
        compose.runOnUiThread { skip.value = true }; show(); compose.onNodeWithText(compose.activity.getString(R.string.import_account_not_backed_up_alert_action_title)).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.skipDialogConfirm(host) } }
    }
    @Test fun batchImportNoticeAcknowledgesCurrentActivity() {
        notice.value = OnboardingViewModel.BatchImportNotice("Imported one fixture account", true); show()
        compose.onNodeWithTag("BatchImportNoticeOk").performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onBatchImportNoticeDismissed(host) } }
    }
    @Test fun eachDestinationNotifiesViewModelForToolbarUpdates() {
        show(OnboardingFeatureRoutes.CREATE_ACCOUNT); compose.runOnIdle { verify { vm.onDestinationChanged(OnboardingFeatureRoutes.CREATE_ACCOUNT) } }
    }
}
