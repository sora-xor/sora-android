@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RepairAccountDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic PIN inputs; this tests consent and dispatch, never writes wallet material. */
@RunWith(AndroidJUnit4::class)
class RepairAccountDialogContractTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private fun prompt(pin: Boolean = false) = OnboardingViewModel.RepairPrompt(accountExists = true, pinRequired = pin)
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun confirm() = compose.onNodeWithTag("RepairConfirm")
    private fun pin() = compose.onNodeWithTag("RepairPin")
    @Composable private fun Content(state: State<OnboardingViewModel.RepairPrompt>, lock: Boolean) {
        SoraAppTheme {
            RepairAccountDialog(state.value, lock, { events += "confirm:$it" }, { events += "lock" }, { events += "dismiss" })
        }
    }
    private fun show(value: OnboardingViewModel.RepairPrompt = prompt(), lock: Boolean = true): MutableState<OnboardingViewModel.RepairPrompt> {
        val state = mutableStateOf(value)
        compose.setContent { Content(state, lock) }
        return state
    }
    @Test fun phraseRepairExplainsExistingAccountAndRequiresExplicitConfirmation() {
        show()
        node(R.string.account_repair_title).assertIsDisplayed()
        node(R.string.account_repair_body_account).assertExists()
        pin().assertDoesNotExist(); compose.onNodeWithTag("RepairScreenLock").assertDoesNotExist()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        confirm().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm:"), events) }
    }
    @Test fun retainedKeysWithoutAnAccountUseTheKeyRepairExplanation() {
        show(prompt().copy(accountExists = false))
        node(R.string.account_repair_body_keys).assertExists()
        node(R.string.account_repair_body_account).assertDoesNotExist()
    }
    @Test fun seedAndJsonRepairUseTheBackupTitle() {
        show(prompt().copy(fromPhrase = false))
        node(R.string.account_repair_title_backup).assertIsDisplayed()
        node(R.string.account_repair_title).assertDoesNotExist()
    }
    @Test fun batchListsEveryAffectedAccountBeforeApproval() {
        show(prompt().copy(batchAccounts = listOf("First • cnFirst", "Second • cnSecond")))
        node(R.string.account_repair_title_batch).assertIsDisplayed()
        node(R.string.account_repair_body_batch).assertExists()
        compose.onNodeWithTag("RepairBatchAccounts").assertTextEquals("First • cnFirst\nSecond • cnSecond")
        node(R.string.account_repair_confirm_batch).performClick()
        compose.runOnIdle { assertEquals(listOf("confirm:"), events) }
    }
    @Test fun emptyPinPreventsConfirmation() {
        show(prompt(pin = true)); confirm().assertIsNotEnabled()
        pin().performTextReplacement("123456"); confirm().assertIsEnabled()
    }
    @Test fun localizedDigitsBecomeAsciiAndOtherCharactersAreRemoved() {
        show(prompt(pin = true)); pin().performTextReplacement("١٢a۳۴-५६")
        confirm().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm:123456"), events) }
    }
    @Test fun nonDigitsDoNotEnablePinConfirmation() {
        show(prompt(pin = true)); pin().performTextReplacement("ab-c •")
        confirm().assertIsNotEnabled(); compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun typedPinHasABoundedLength() {
        show(prompt(pin = true)); pin().performTextReplacement("12345678901234567890")
        confirm().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm:123456789012"), events) }
    }
    @Test fun confirmingClearsThePinBeforeAnotherAttempt() {
        show(prompt(pin = true)); pin().performTextReplacement("123456"); confirm().performClick()
        confirm().assertIsNotEnabled()
        pin().performTextReplacement("654321"); confirm().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm:123456", "confirm:654321"), events) }
    }
    @Test fun wrongPinExplainsRetryWithoutLosingThePrompt() {
        val state = show(prompt(pin = true)); pin().performTextReplacement("123456"); confirm().performClick()
        compose.runOnIdle { state.value = state.value.copy(wrongPin = true) }
        node(R.string.account_repair_pin_wrong).assertExists(); confirm().assertIsNotEnabled()
    }
    @Test fun screenLockTakesPrecedenceOverTheUnreadableAppPin() {
        show(prompt(pin = true).copy(screenLockRequired = true))
        pin().assertDoesNotExist(); node(R.string.account_repair_screen_lock_body).assertExists()
        confirm().performClick()
        compose.runOnIdle { assertEquals(listOf("lock"), events) }
    }
    @Test fun absentPhoneLockPreventsRepairAndExplainsHowToContinue() {
        show(prompt(pin = true).copy(screenLockRequired = true), lock = false)
        node(R.string.account_repair_screen_lock_missing).assertExists(); confirm().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun workingRepairDisablesPinAndBothActionsUntilItFinishes() {
        val state = show(prompt(pin = true)); pin().performTextReplacement("123456")
        compose.runOnIdle { state.value = state.value.copy(working = true) }
        pin().assertIsNotEnabled(); confirm().assertIsNotEnabled(); node(R.string.common_cancel).assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(working = false) }
        pin().assertIsEnabled(); confirm().assertIsEnabled(); node(R.string.common_cancel).assertIsEnabled()
    }
    @Test fun cancelDispatchesOnlyDismissal() {
        show(prompt(pin = true)); pin().performTextReplacement("123456")
        node(R.string.common_cancel).performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss"), events) }
    }
    @Test fun pinIsNeverRestoredFromSavedInstanceState() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(prompt(pin = true))
        restoration.setContent { Content(state, true) }
        pin().performTextReplacement("123456"); confirm().assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        confirm().assertIsNotEnabled(); compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
}
