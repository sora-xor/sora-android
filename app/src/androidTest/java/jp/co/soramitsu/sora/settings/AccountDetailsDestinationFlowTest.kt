@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_multiaccount_impl.presentation.CreateBackupPasswordState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.*
import jp.co.soramitsu.ui_core.component.input.InputTextState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Account options destinations; all credential, cloud and account mutation handlers are inert. */
@RunWith(AndroidJUnit4::class)
class AccountDetailsDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val address = "public-fixture-account"
    private val detail = MutableLiveData(AccountDetailsScreenState(InputTextState(label = "Wallet name", value = TextFieldValue("Main wallet")),
        true, false, false, address))
    private val deletion = MutableLiveData(false)
    private val backup = MutableLiveData(CreateBackupPasswordState(
        password = InputTextState(label = "Backup password"), passwordConfirmation = InputTextState(label = "Confirm password")))
    private val vm = mockk<AccountDetailsViewModel>(relaxed = true) {
        every { accountDetailsScreenState } returns detail
        every { deleteDialogState } returns deletion
        every { createBackupPasswordState } returns backup
    }
    private lateinit var nav: NavHostController
    private fun show(route: String = AccountDetailsRoutes.ACCOUNT_DETAILS) {
        val fragment = AccountDetailsFragment()
        AccountDetailsFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
            .set(fragment, lazyOf(vm))
        fragment.debounceClickHandler = DebounceClickHandler()
        compose.setContent { SoraAppTheme {
            nav = rememberNavController()
            val scroll = rememberScrollState()
            NavHost(nav, startDestination = route) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun tag(suffix: String) = compose.onNode(SemanticsMatcher("tag ends with $suffix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith(suffix) == true
    })
    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick()
    private fun asReadOnly() = compose.runOnIdle {
        detail.value = detail.value!!.copy(readOnly = ReadOnlyAccountUi(UnavailableWalletReason.KEY_UNREADABLE, true, true, true, true))
    }
    @Test fun accountNameEditReachesTheSelectedAccountModel() {
        show(); compose.onNode(hasSetTextAction()).performTextReplacement("Savings wallet")
        compose.runOnIdle { verify(exactly = 1) { vm.onNameChange(match { it.text == "Savings wallet" }) } }
    }
    @Test fun addressCopyUsesAccountHandlerWithoutOpeningBackup() {
        show(); compose.onNodeWithText(address).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onAddressCopy() }; verify(exactly = 0) { vm.onShowPassphrase() } }
    }
    @Test fun phraseOptionStartsOnlyPhraseAuthentication() {
        show(); tag("ShowPassphrase").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onShowPassphrase() }; verify(exactly = 0) { vm.onShowRawSeed() } }
    }
    @Test fun rawSeedOptionUsesSeedHandler() {
        show(); tag("ShowRawSeed").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onShowRawSeed() } }
    }
    @Test fun jsonOptionUsesExportHandler() {
        show(); tag("ExportJson").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onExportJson() } }
    }
    @Test fun mnemonicDisappearsWhenCurrentAccountHasNoPhrase() {
        show(); compose.runOnIdle { detail.value = detail.value!!.copy(isMnemonicAvailable = false) }
        tag("ShowPassphrase").assertDoesNotExist(); tag("ExportJson").assertExists()
    }
    @Test fun googleBackupRoutesThroughDebouncedAccountAction() {
        show(); tag("GoogleLogin").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onBackupClicked(null) } }
    }
    @Test fun unavailableCloudHidesActionAndExplainsWhy() {
        show(); compose.runOnIdle { detail.value = detail.value!!.copy(isBackupAvailable = null, isCloudBackupAvailable = false) }
        tag("GoogleLogin").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.wallet_cloud_backup_unavailable)).performScrollTo().assertIsDisplayed()
    }
    @Test fun removalUsesLogoutHandlerOnlyAfterExplicitTap() {
        show(); compose.runOnIdle { verify(exactly = 0) { vm.onLogout() } }
        tag("ForgetAccount").performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onLogout() } }
    }
    @Test fun deleteDialogRequiresConfirmation() {
        show(); compose.runOnIdle { deletion.value = true }
        compose.onNodeWithText(compose.activity.getString(R.string.delete_backup_alert_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.common_delete)).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.deleteGoogleBackup() } }
    }
    @Test fun deleteDialogCancellationDoesNotDelete() {
        show(); compose.runOnIdle { deletion.value = true }; compose.onNodeWithText(compose.activity.getString(R.string.common_cancel)).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.deleteDialogDismiss() }; verify(exactly = 0) { vm.deleteGoogleBackup() } }
    }
    @Test fun readOnlyRepairActionsKeepTheirSeparateHandlers() {
        show(); asReadOnly()
        for (suffix in listOf(ReadOnlyAccountPanelTags.RESTORE_PHRASE, ReadOnlyAccountPanelTags.RESTORE_OTHER,
            ReadOnlyAccountPanelTags.RESTORE_GOOGLE, ReadOnlyAccountPanelTags.SHOW_BACKUP)) tag(suffix).performScrollTo().performClick()
        compose.runOnIdle { verify { vm.onRestoreKeysWithPhrase(); vm.onRestoreKeysAnotherWay(); vm.onRestoreKeysFromGoogle(); vm.onShowSavedBackup() } }
    }
    @Test fun readOnlyRemovalUsesSinglePanelAction() {
        show(); asReadOnly(); tag("ForgetAccount").assertDoesNotExist()
        tag(ReadOnlyAccountPanelTags.REMOVE).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onLogout() } }
    }
    @Test fun restoringAccountRemovesRepairPanelWithoutRecreatingRoute() {
        show(); asReadOnly(); tag(ReadOnlyAccountPanelTags.PANEL).assertExists()
        compose.runOnIdle { detail.value = detail.value!!.copy(readOnly = null) }
        tag(ReadOnlyAccountPanelTags.PANEL).assertDoesNotExist(); tag("ForgetAccount").assertExists()
    }
    @Test fun backupRouteForwardsPasswordAndConfirmationIndependently() {
        show(AccountDetailsRoutes.BACKUP_ACCOUNT)
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("fixture-password")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("fixture-confirmation")
        compose.runOnIdle { verify { vm.onBackupPasswordChanged(match { it.text == "fixture-password" });
            vm.onBackupPasswordConfirmationChanged(match { it.text == "fixture-confirmation" }) } }
    }
    @Test fun backupWarningUsesWarningHandler() {
        show(AccountDetailsRoutes.BACKUP_ACCOUNT)
        click(R.string.create_backup_password_warning_text)
        compose.runOnIdle { verify(exactly = 1) { vm.onWarningToggle() } }
    }
    @Test fun readyBackupFormHandsOffOnceOnExplicitTap() {
        show(AccountDetailsRoutes.BACKUP_ACCOUNT)
        compose.runOnIdle { backup.value = backup.value!!.copy(setPasswordButtonIsEnabled = true, warningIsSelected = true) }
        click(R.string.create_backup_password_button_text)
        compose.runOnIdle { verify(exactly = 1) { vm.onBackupPasswordClicked() } }
    }
    @Test fun unreadyBackupFormCannotInvokeSaveHandler() {
        show(AccountDetailsRoutes.BACKUP_ACCOUNT)
        compose.onNodeWithText(compose.activity.getString(R.string.create_backup_password_button_text)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { verify(exactly = 0) { vm.onBackupPasswordClicked() } }
    }
}
