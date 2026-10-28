package jp.co.soramitsu.sora.settings

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryType
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Drives the actual activity and its retained view model; no account is ever imported or saved. */
@RunWith(AndroidJUnit4::class)
class OnboardingEntryFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<OnboardingActivity>? = null
    private fun launch(entry: String = "WELCOME") {
        scenario = ActivityScenario.launch(Intent(context, OnboardingActivity::class.java)
            .putExtra("jp.co.soramitsu.feature_multiaccount_impl.extra.ENTRY", entry))
        compose.waitForIdle()
    }
    @After fun close() { scenario?.close() }
    private fun node(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun state(): RecoveryState {
        var value: RecoveryState? = null
        scenario!!.onActivity { value = it.viewModel.recoveryState.value }
        return requireNotNull(value)
    }
    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitForText(id: Int) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(context.getString(id)).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun unknownEntryFallsBackToTheWelcomeChoices() {
        launch("obsolete-entry"); waitForText(R.string.create_account_title)
        node(R.string.create_account_title).assertExists(); node(R.string.recovery_title).assertExists()
        compose.onNodeWithTag("RecoveryChooseFile").assertDoesNotExist()
    }
    @Test fun directPhraseEntryOpensTheRequestedImportSource() {
        launch("IMPORT_RECOVERY_PHRASE"); waitForText(R.string.recovery_enter_passphrase_title)
        assertEquals(RecoveryType.PASSPHRASE, state().recoveryType)
        node(R.string.common_continue).assertIsNotEnabled()
        compose.onNodeWithTag("RecoveryChooseFile").assertDoesNotExist()
    }
    @Test fun chooseSourceEntryLetsTheUserSelectRawSeed() {
        launch("IMPORT_CHOOSE_SOURCE"); waitForTag("RecoveryBackupFile")
        node(R.string.common_raw_seed).performClick(); waitForText(R.string.recovery_enter_seed_title)
        assertEquals(RecoveryType.SEED, state().recoveryType); node(R.string.common_continue).assertIsNotEnabled()
    }
    @Test fun chooseSourceEntryLetsTheUserSelectEncryptedBackup() {
        launch("IMPORT_CHOOSE_SOURCE"); waitForTag("RecoveryBackupFile")
        compose.onNodeWithTag("RecoveryBackupFile").performClick(); waitForTag("RecoveryChooseFile")
        assertEquals(RecoveryType.JSON, state().recoveryType)
        compose.onNodeWithTag("RecoveryFilePassword").assertExists(); node(R.string.common_continue).assertIsNotEnabled()
    }
    @Test fun welcomeImportOpensTheSourceDialogAndPhraseFlow() {
        launch(); waitForText(R.string.recovery_title); node(R.string.recovery_title).performScrollTo().performClick()
        waitForTag("RecoveryBackupFile"); node(R.string.common_passphrase_title).performClick()
        waitForText(R.string.recovery_enter_passphrase_title); assertEquals(RecoveryType.PASSPHRASE, state().recoveryType)
    }
    @Test fun changingSourceClearsOldInputAndValidation() {
        launch("IMPORT_RECOVERY_PHRASE"); waitForText(R.string.recovery_enter_passphrase_title)
        compose.onNode(hasSetTextAction()).performTextReplacement("invalid fixture phrase")
        node(R.string.common_raw_seed).performClick(); waitForText(R.string.recovery_enter_seed_title)
        assertEquals("", state().recoveryInputState.value.text); assertFalse(state().isButtonEnabled)
    }
    @Test fun invalidPhraseNeverEnablesImportContinuation() {
        launch("IMPORT_RECOVERY_PHRASE"); waitForText(R.string.recovery_enter_passphrase_title)
        compose.onNode(hasSetTextAction()).performTextReplacement("invalid fixture phrase")
        compose.waitForIdle(); node(R.string.common_continue).assertIsNotEnabled()
        assertFalse(state().isButtonEnabled)
    }
    @Test fun recreationDoesNotApplyTheOriginalEntryAgain() {
        launch("IMPORT_RECOVERY_PHRASE"); waitForText(R.string.recovery_enter_passphrase_title)
        node(R.string.common_raw_seed).performClick(); waitForText(R.string.recovery_enter_seed_title)
        compose.onNode(hasSetTextAction()).performTextReplacement("public-invalid-seed")
        scenario!!.recreate(); waitForText(R.string.recovery_enter_seed_title)
        assertEquals(RecoveryType.SEED, state().recoveryType)
        assertEquals("public-invalid-seed", state().recoveryInputState.value.text)
    }
    @Test fun switchingFromJsonClearsTheBackupPassword() {
        launch("IMPORT_CHOOSE_SOURCE"); waitForTag("RecoveryBackupFile")
        compose.onNodeWithTag("RecoveryBackupFile").performClick(); waitForTag("RecoveryFilePassword")
        compose.onNodeWithTag("RecoveryFilePassword").performTextReplacement("fixture-password")
        node(R.string.common_raw_seed).performClick(); waitForText(R.string.recovery_enter_seed_title)
        assertEquals("", state().passwordInputState.value.text)
        compose.onNodeWithTag("RecoveryFilePassword").assertDoesNotExist()
    }
    @Test fun createAccountNameSurvivesActivityRecreation() {
        launch(); waitForText(R.string.create_account_title); node(R.string.create_account_title).performScrollTo().performClick()
        waitForText(R.string.onboarding_create_account_description)
        compose.onNode(hasSetTextAction()).performTextReplacement("UI fixture wallet")
        scenario!!.recreate(); waitForText(R.string.onboarding_create_account_description)
        compose.onNode(hasSetTextAction()).assertTextContains("UI fixture wallet")
        scenario!!.onActivity { assertEquals("UI fixture wallet", it.viewModel.createAccountCardState.value!!.accountNameInputState.value.text) }
    }
    @Test fun backFromAccountNameReturnsToWelcomeWithoutCreatingAnAccount() {
        launch(); waitForText(R.string.create_account_title); node(R.string.create_account_title).performScrollTo().performClick()
        waitForText(R.string.onboarding_create_account_description)
        scenario!!.onActivity { it.onToolbarNavigation() }
        waitForText(R.string.recovery_title); node(R.string.create_account_title).assertExists()
    }
}
