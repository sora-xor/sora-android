package jp.co.soramitsu.sora.splash

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.safemode.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.presentation.compose.theme.neon.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Public safety surfaces remain actionable and accurately describe incomplete checks. */
@RunWith(AndroidJUnit4::class)
class SafetySurfaceInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent { SoraAppTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalFx provides FxPolicy(FxTier.MINIMAL, true, true, true)) {
                Box(Modifier.size(320.dp, 560.dp)) { content() }
            }
        } }
    }
    @Test fun readOnlyBannerKeepsRetryAndExplanationSeparate() {
        show { SafeModeScreen { ReadOnlyBanner({ events += "retry" }, { events += "learn" }) } }
        node(R.string.safe_mode_try_upgrade_again).performScrollTo().performClick()
        node(R.string.safe_mode_learn_more).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("retry", "learn"), events) }
    }
    @Test fun largeTextBannerActionsRemainReachable() {
        show(2f) { SafeModeScreen { ReadOnlyBanner({ events += "retry" }, { events += "learn" }) } }
        node(R.string.safe_mode_try_upgrade_again).performScrollTo().assertIsDisplayed().performClick()
        node(R.string.safe_mode_learn_more).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("retry", "learn"), events) }
    }
    @Test fun readOnlyExplanationCanCloseWithoutRetrying() {
        show { ReadOnlyLearnMoreSheet({ events += "retry" }, { events += "close" }) }
        node(R.string.safe_mode_read_only_details).assertExists(); node(R.string.common_close).performClick()
        compose.runOnIdle { assertEquals(listOf("close"), events) }
    }
    @Test fun readOnlyExplanationRetryIsAnExplicitAction() {
        show { ReadOnlyLearnMoreSheet({ events += "retry" }, { events += "close" }) }
        node(R.string.safe_mode_try_upgrade_again).performClick()
        compose.runOnIdle { assertEquals(listOf("retry"), events) }
    }
    @Test fun largeTextExplanationKeepsCloseAndRetryVisible() {
        show(2f) { ReadOnlyLearnMoreSheet({ events += "retry" }, { events += "close" }) }
        node(R.string.common_close).assertIsDisplayed(); node(R.string.safe_mode_try_upgrade_again).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("retry"), events) }
    }
    @Test fun legacyPinUpgradeExplainsChangeBeforeContinuing() {
        show { SafeModeScreen { LegacyPinUpgradePanel({ events += "continue" }) } }
        node(R.string.safe_mode_new_pin_title).assertExists(); node(R.string.safe_mode_new_pin_body).assertExists()
        compose.runOnIdle { assertTrue(events.isEmpty()) }; node(R.string.common_continue).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("continue"), events) }
    }
    @Test fun pendingCheckOffersOnlyConfiguredRecoveryAction() {
        show { PendingCheckCard(onOptions = { events += "options" }) }
        node(R.string.safe_mode_pending_check).assertExists(); node(R.string.wallet_backup_options).performClick()
        compose.runOnIdle { assertEquals(listOf("options"), events) }
    }
    @Test fun pendingCheckWithoutHandlerHasNoInertOptionsButton() {
        show { PendingCheckCard() }; node(R.string.safe_mode_pending_check).assertExists()
        node(R.string.wallet_backup_options).assertDoesNotExist(); compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
    private val checks = mutableStateOf(UpgradeCompleteChecks(false, false, false, true, 3, 1))
    private fun upgrade() = show { UpgradeCompleteSheet(checks.value, { events += "backup" }, { events += "close" }) }
    @Test fun upgradeNeverClaimsUnfinishedAccountOrPinChecksSucceeded() {
        upgrade(); node(R.string.safe_mode_whats_new_accounts).assertDoesNotExist(); node(R.string.safe_mode_whats_new_pin).assertDoesNotExist()
        node(R.string.safe_mode_whats_new_balances_loading).assertExists(); node(R.string.safe_mode_whats_new_balances).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.safe_mode_whats_new_accounts_some_read_only, "1", "3")).assertExists()
    }
    @Test fun finishingChecksRemovesStaleLoadingAndReadOnlyWarnings() {
        upgrade(); compose.runOnIdle { checks.value = UpgradeCompleteChecks(true, true, true, false, 1, 1) }
        node(R.string.safe_mode_whats_new_balances_loading).assertDoesNotExist(); node(R.string.safe_mode_whats_new_balances).assertExists()
        node(R.string.safe_mode_whats_new_batch_export).assertDoesNotExist(); node(R.string.safe_mode_back_up_now).assertDoesNotExist()
        node(R.string.common_continue).performClick(); compose.runOnIdle { assertEquals(listOf("close"), events) }
    }
    @Test fun backupChoiceNeverRunsUntilExplicitlyTapped() {
        upgrade(); compose.runOnIdle { assertTrue(events.isEmpty()) }; node(R.string.safe_mode_back_up_now).performClick()
        compose.runOnIdle { assertEquals(listOf("backup"), events) }
    }
    @Test fun postponingBackupClosesWithoutStartingExport() {
        upgrade(); node(R.string.safe_mode_later).performClick(); compose.runOnIdle { assertEquals(listOf("close"), events) }
    }
    @Test fun oddLengthPhraseRemainsPrivateUntilRevealAndUsesLeftToRightColumns() {
        val shown = mutableStateOf(false); val words = listOf("fixture-a", "fixture-b", "fixture-c", "fixture-d", "fixture-e")
        show { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            PhraseGrid(words, shown.value, { shown.value = true; events += "reveal" })
        } }
        words.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithTag(SafeModeTags.PHRASE_VEIL).performClick()
        words.forEach { compose.onNodeWithText(it).assertExists() }
        val first = compose.onNodeWithText("fixture-a").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText("fixture-c").fetchSemanticsNode().boundsInRoot
        val fourth = compose.onNodeWithText("fixture-d").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left < fourth.left); assertTrue(third.top > first.top)
        compose.runOnIdle { assertEquals(listOf("reveal"), events) }
    }
}
