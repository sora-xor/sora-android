package jp.co.soramitsu.sora.splash

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.presentation.compose.neon.safemode.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.debug.SafeModePreviewData
import jp.co.soramitsu.sora.splash.presentation.recovery.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import jp.co.soramitsu.common.R as CommonR

/** Recovery UI privacy, authentication and action routing with public synthetic display data only. */
@RunWith(AndroidJUnit4::class)
class RecoveryRevealInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val base = SafeModePreviewData.panelState(SafeModePreviewData.recovery)
    private val state = mutableStateOf(base)
    private val calls = mutableListOf<String>()
    private val entry = PhraseEntry("fixture-id", "public-address-1234567890", "Fixture wallet", false)
    private val detail = TechnicalDetail("Fixture code", "PUBLIC_FIXTURE_CODE")
    private val seed = "public-fixture-seed-no-wallet"
    private val callbacks = SafeModeCallbacks(onAction = { calls += "action:$it" }, onCopyDetail = { calls += "detail:${it.value}" },
        onCloseSheet = { calls += "close-sheet" }, onConfirmReadOnly = { calls += "read-only" }, onConfirmRestore = { calls += "restore" },
        onCreateExport = { calls += "export" }, onEmailSupport = { calls += "email" }, onCopySupport = { calls += "copy-support" },
        onPinDigit = { calls += "digit:$it" }, onPinDelete = { calls += "delete" }, onBiometric = { calls += "authenticate" },
        onRetryAuth = { calls += "retry-auth" }, onOpenEntry = { calls += "entry:${it.id}" }, onReveal = { calls += "reveal:$it" },
        onRevealBack = { calls += "back" }, onRevealClose = { calls += "close-reveal" })
    private fun show(reveal: RevealUi? = null) {
        state.value = state.value.copy(reveal = reveal)
        compose.setContent { SoraAppTheme { SplashSafeMode(state.value, listOf(detail), callbacks) } }
    }
    private fun text(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun click(id: Int) = text(id).performScrollTo().performClick()
    private fun shown(secret: RevealedSecret, revealed: Boolean = false, back: Boolean = true) = RevealUi.Shown(entry, secret, revealed, back)
    @Test fun unavailableAuthenticationOffersRetryWithoutPinPad() {
        show(RevealUi.Auth(loading = false, error = AuthError.UNAVAILABLE))
        compose.onNode(hasText(compose.activity.getString(R.string.recovery_try_again)) and hasAnyAncestor(hasTestTag(RecoveryTags.REVEAL)))
            .performScrollTo().performClick()
        compose.onNodeWithTag(RecoveryTags.PIN_DOTS).assertDoesNotExist(); assertEquals(listOf("retry-auth"), calls)
    }
    @Test fun noPinUsesScreenLockAuthenticationOnly() {
        show(RevealUi.Auth(loading = false, noPin = true)); click(R.string.recovery_auth_screen_lock)
        compose.onNodeWithTag(RecoveryTags.pinKey('1')).assertDoesNotExist(); assertEquals(listOf("authenticate"), calls)
    }
    @Test fun missingScreenLockNeverOffersAuthentication() {
        show(RevealUi.Auth(loading = false, noPin = true, error = AuthError.NO_SCREEN_LOCK))
        text(R.string.recovery_auth_no_screen_lock).assertExists(); text(R.string.recovery_auth_screen_lock).assertDoesNotExist()
    }
    @Test fun wrongPinExplainsFailureAndKeepsRetryInput() {
        show(RevealUi.Auth(loading = false, error = AuthError.WRONG)); text(R.string.recovery_auth_wrong).assertExists()
        compose.onNodeWithTag(RecoveryTags.pinKey('2')).performScrollTo().performClick(); assertEquals(listOf("digit:2"), calls)
    }
    @Test fun lockoutDisablesDigitsAndShowsRoundedCountdown() {
        show(RevealUi.Auth(loading = false, error = AuthError.LOCKED, lockedForMillis = 61_001))
        compose.onNodeWithText(compose.activity.getString(R.string.recovery_auth_locked, "1:02")).assertExists()
        compose.onNodeWithTag(RecoveryTags.pinKey('2')).performScrollTo().assertIsNotEnabled().performClick(); assertTrue(calls.isEmpty())
    }
    @Test fun verificationInProgressDisablesPinInput() {
        show(RevealUi.Auth(loading = false, checking = true))
        compose.onNodeWithTag(RecoveryTags.pinKey('1')).performScrollTo().assertIsNotEnabled()
    }
    @Test fun biometricActionUsesSeparateAuthenticationCallback() {
        show(RevealUi.Auth(loading = false, biometric = true))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.recovery_auth_biometric)).performScrollTo().performClick()
        assertEquals(listOf("authenticate"), calls)
    }
    @Test fun deletePinUsesDeleteCallbackWithoutDigit() {
        show(RevealUi.Auth(loading = false, entered = 2))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.recovery_pin_delete)).performScrollTo().performClick()
        assertEquals(listOf("delete"), calls)
    }
    @Test fun zeroKeyRemainsAvailableOnBottomRow() {
        show(RevealUi.Auth(loading = false)); compose.onNodeWithTag(RecoveryTags.pinKey('0')).performScrollTo().performClick()
        assertEquals(listOf("digit:0"), calls)
    }
    @Test fun pinProgressExposesCountWithoutEnteredDigits() {
        show(RevealUi.Auth(loading = false, entered = 3, pinLength = 6))
        compose.onNodeWithTag(RecoveryTags.PIN_DOTS).assertContentDescriptionEquals(compose.activity.getString(R.string.recovery_pin_progress, 3, 6))
    }
    @Test fun authenticationCancelUsesCloseHandler() {
        show(RevealUi.Auth(loading = false)); click(CommonR.string.common_cancel); assertEquals(listOf("close-reveal"), calls)
    }
    @Test fun emptyWalletListSaysNothingIsSavedOnlyWhenKnown() {
        show(RevealUi.Wallets(emptyList())); text(R.string.recovery_phrase_none).assertExists(); click(R.string.recovery_done)
        assertEquals(listOf("close-reveal"), calls)
    }
    @Test fun incompleteEmptyListSaysUnreadableInsteadOfAbsent() {
        show(RevealUi.Wallets(emptyList(), incomplete = true)); text(R.string.recovery_wallets_unreadable).assertExists(); text(R.string.recovery_phrase_none).assertDoesNotExist()
    }
    @Test fun withheldEmptyListExplainsMissingPermission() {
        show(RevealUi.Wallets(emptyList(), withheld = true)); text(R.string.recovery_wallets_withheld).assertExists(); text(R.string.recovery_phrase_none).assertDoesNotExist()
    }
    @Test fun walletSelectionPassesExactEntry() {
        show(RevealUi.Wallets(listOf(entry))); compose.onNodeWithText(entry.name!!).performScrollTo().performClick()
        assertEquals(listOf("entry:${entry.id}"), calls)
    }
    @Test fun walletLoadingDisablesRepeatedSelection() {
        show(RevealUi.Wallets(listOf(entry), loading = entry)); compose.onNodeWithText(entry.name!!).performScrollTo().assertIsNotEnabled().performClick()
        assertTrue(calls.isEmpty())
    }
    @Test fun olderWalletEntryUsesOlderVersionLabel() {
        show(RevealUi.Wallets(listOf(entry.copy(olderVersion = true)))); text(R.string.recovery_wallet_older).assertExists()
    }
    @Test fun unnamedWalletEntryUsesFallbackName() {
        show(RevealUi.Wallets(listOf(entry.copy(name = null, address = "short")))); text(R.string.recovery_wallet_unnamed).assertExists()
        compose.onNodeWithText("short").assertExists()
    }
    @Test fun partialListKeepsAvailableWalletAlongsideWarning() {
        show(RevealUi.Wallets(listOf(entry), incomplete = true)); text(R.string.recovery_wallets_unreadable).assertExists()
        compose.onNodeWithText(entry.name!!).assertExists()
    }
    @Test fun seedIsAbsentFromSemanticsUntilExplicitReveal() {
        show(shown(RevealedSecret.Seed(seed))); compose.onNodeWithText(seed).assertDoesNotExist()
        click(CommonR.string.safe_mode_tap_to_reveal); assertEquals(listOf("reveal:true"), calls)
    }
    @Test fun revealedSeedShowsExactValueAndReveilingRemovesIt() {
        show(shown(RevealedSecret.Seed(seed), revealed = true)); compose.onNodeWithText(seed).assertExists()
        compose.runOnIdle { state.value = state.value.copy(reveal = shown(RevealedSecret.Seed(seed))) }
        compose.onNodeWithText(seed).assertDoesNotExist()
    }
    @Test fun unconfirmedSeedClearlyExplainsItIsNotVerified() {
        show(shown(RevealedSecret.Seed(seed, checked = false))); text(R.string.recovery_seed_unchecked).assertExists()
        compose.onNodeWithText(seed).assertDoesNotExist()
    }
    @Test fun seedForDifferentWalletNeverClaimsToBeThisBackup() {
        show(shown(RevealedSecret.Seed(seed, otherWallet = true))); text(R.string.recovery_seed_other_wallet_title).assertExists()
        text(R.string.recovery_seed_other_wallet).assertExists(); text(R.string.recovery_seed_unchecked).assertDoesNotExist()
    }
    @Test fun noSavedSecretShowsAbsence() { show(shown(RevealedSecret.None)); text(R.string.recovery_phrase_none).assertExists() }
    @Test fun temporarilyUnavailableSecretIsDistinctFromAbsent() { show(shown(RevealedSecret.Unavailable)); text(R.string.recovery_phrase_unavailable).assertExists(); text(R.string.recovery_phrase_none).assertDoesNotExist() }
    @Test fun damagedSecretIsDistinctFromTemporaryFailure() { show(shown(RevealedSecret.Unreadable)); text(R.string.recovery_phrase_unreadable).assertExists(); text(R.string.recovery_phrase_unavailable).assertDoesNotExist() }
    @Test fun multipleWalletRevealOffersBackAndDoneSeparately() {
        show(shown(RevealedSecret.None)); click(R.string.recovery_back); click(R.string.recovery_done)
        assertEquals(listOf("back", "close-reveal"), calls)
    }
    @Test fun singleWalletRevealHidesBackButton() { show(shown(RevealedSecret.None, back = false)); text(R.string.recovery_back).assertDoesNotExist() }
    @Test fun hardwareBackDuringRevealUsesRevealNavigation() {
        show(RevealUi.Wallets(listOf(entry))); compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf("back"), calls)
    }
    @Test fun supportEmailAndCopyUseSeparateCallbacks() {
        state.value = base.copy(sheet = SafeSheetKind.SUPPORT); show(); click(R.string.recovery_support_email); click(R.string.recovery_support_copy)
        assertEquals(listOf("email", "copy-support"), calls); compose.onNodeWithText(detail.value).assertExists()
    }
    @Test fun missingMailAppDisplaysFallbackAddress() {
        state.value = base.copy(sheet = SafeSheetKind.SUPPORT, supportNoEmail = true); show()
        compose.onNodeWithText(compose.activity.getString(R.string.recovery_support_no_email, SafeModeViewModel.SUPPORT_EMAIL)).assertExists()
    }
    @Test fun readOnlyConfirmationUsesSpecificHandler() {
        state.value = base.copy(sheet = SafeSheetKind.READ_ONLY); show(); compose.onNodeWithTag(RecoveryTags.CONFIRM_READ_ONLY).performScrollTo().performClick()
        assertEquals(listOf("read-only"), calls)
    }
    @Test fun exportCreateUsesSpecificHandlerWithoutRestoring() {
        state.value = base.copy(sheet = SafeSheetKind.EXPORT); show(); click(R.string.recovery_export_create); assertEquals(listOf("export"), calls)
    }
    @Test fun completedExportDisplaysCreationAcknowledgement() {
        state.value = base.copy(sheet = SafeSheetKind.EXPORT, exportCreated = true); show(); text(R.string.recovery_export_done).assertExists()
    }
    @Test fun revealTakesPrecedenceOverAnyOpenSupportSheet() {
        state.value = base.copy(sheet = SafeSheetKind.SUPPORT); show(RevealUi.Auth(loading = false))
        text(R.string.recovery_support_email).assertDoesNotExist(); compose.onNodeWithTag(RecoveryTags.REVEAL).assertExists()
    }
    @Test fun technicalDetailsRequireExpansionBeforeCopying() {
        show(); compose.onNodeWithText(detail.value).assertDoesNotExist(); click(CommonR.string.safe_mode_technical_details)
        compose.onNodeWithText(detail.value).assertExists()
        compose.onNodeWithContentDescription(compose.activity.getString(CommonR.string.safe_mode_copy_item, detail.label)).performScrollTo().performClick()
        assertEquals(listOf("detail:${detail.value}"), calls)
    }
}
