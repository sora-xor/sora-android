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

package jp.co.soramitsu.sora.splash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteChecks
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteSheet
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.debug.SafeModePreview
import jp.co.soramitsu.sora.debug.SafeModePreviewData
import jp.co.soramitsu.sora.debug.SafeModePreviewState
import jp.co.soramitsu.sora.splash.presentation.recovery.PhraseEntry
import jp.co.soramitsu.sora.splash.presentation.recovery.RecoveryAction
import jp.co.soramitsu.sora.splash.presentation.recovery.RecoveryTags
import jp.co.soramitsu.sora.splash.presentation.recovery.RevealUi
import jp.co.soramitsu.sora.splash.presentation.recovery.RevealedSecret
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeCallbacks
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeUiState
import jp.co.soramitsu.sora.splash.presentation.recovery.SplashSafeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import jp.co.soramitsu.common.R as CommonR

/**
 * Design spec 9 on a device: every blocking state shows a forward action, each action reaches
 * its handler, the recovery phrase needs confirmation and stays veiled, and the progress screen
 * follows the 10 s / 60 s rule. Rendered with the real composables in both themes.
 */
@RunWith(AndroidJUnit4::class)
class SafeModeScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val blockingStates = listOf(
        SafeModePreviewData.panelState(SafeModePreviewData.recovery),
        SafeModePreviewData.panelState(SafeModePreviewData.transient),
        SafeModePreviewData.panelState(SafeModePreviewData.keysUnavailable),
        SafeModePreviewData.panelState(SafeModePreviewData.removal),
        SafeModePreviewData.panelState(SafeModePreviewData.storage),
        SafeModePreviewData.panelState(SafeModePreviewData.restorePending, restorePending = true),
    )

    @Test
    fun everyBlockingStateShowsAForwardActionInBothThemes() {
        var dark by mutableStateOf(true)
        var state by mutableStateOf(SafeModeUiState())
        val clicked = mutableListOf<RecoveryAction>()
        rule.setContent {
            SoraAppTheme(darkTheme = dark) {
                SplashSafeMode(state, SafeModePreviewData.details, SafeModeCallbacks(onAction = { clicked += it }))
            }
        }
        for (theme in listOf(true, false)) for (panelState in blockingStates) {
            dark = theme
            state = panelState
            rule.waitForIdle()
            val model = state.panel!!
            rule.onNodeWithTag(SafeModeTags.PANEL).assertExists()
            rule.onNodeWithTag(RecoveryTags.action(RecoveryAction.TRY_AGAIN)).performScrollTo().assertIsEnabled()
            assertTrue(model.code, model.forwardActionsBesidesRetry.isNotEmpty())
            model.forwardActionsBesidesRetry.forEach { action ->
                clicked.clear()
                rule.onNodeWithTag(RecoveryTags.action(action)).performScrollTo().assertIsDisplayed().assertIsEnabled()
                    .performClick()
                assertEquals("${model.code} $action", listOf(action), clicked)
            }
            // The recovery code is only under Technical details, never in the title or body.
            rule.onAllNodesWithText(model.code, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun onlyContactSupportStaysAvailableWhileAnActionRuns() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SplashSafeMode(
                    SafeModePreviewData.panelState(SafeModePreviewData.recovery).copy(running = RecoveryAction.TRY_AGAIN),
                    SafeModePreviewData.details,
                    SafeModeCallbacks(),
                )
            }
        }
        rule.onNodeWithTag(RecoveryTags.action(RecoveryAction.RESTORE_PHRASE)).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(RecoveryTags.action(RecoveryAction.READ_ONLY)).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(RecoveryTags.action(RecoveryAction.CONTACT_SUPPORT)).performScrollTo().assertIsEnabled()
    }

    @Test
    fun recoveryPhraseNeedsConfirmationAndStaysVeiledUntilTapped() {
        val word = SafeModePreviewData.samplePhrase.first()
        val entry = PhraseEntry("cnA", "cnA", "Main", false)
        val base = SafeModePreviewData.panelState(SafeModePreviewData.recovery)
        var state by mutableStateOf(base.copy(reveal = RevealUi.Auth(loading = false, pinLength = 6)))
        val digits = mutableListOf<Char>()
        var revealRequested = false
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SplashSafeMode(
                    state,
                    SafeModePreviewData.details,
                    SafeModeCallbacks(
                        onPinDigit = { digits += it },
                        onReveal = { revealRequested = it },
                    ),
                )
            }
        }
        // Confirmation first: a keypad, and no word of any phrase anywhere on screen.
        rule.onNodeWithTag(RecoveryTags.REVEAL).assertExists()
        rule.onNodeWithTag(RecoveryTags.PIN_DOTS).assertExists()
        rule.onNodeWithTag(RecoveryTags.pinKey('1')).performScrollTo().performClick()
        assertEquals(listOf('1'), digits)
        rule.onAllNodesWithText(word, substring = true).assertCountEquals(0)

        // After confirmation the phrase is veiled: the words are not even composed.
        state = base.copy(reveal = RevealUi.Shown(entry, RevealedSecret.Phrase(SafeModePreviewData.samplePhrase), canGoBack = false))
        rule.waitForIdle()
        rule.onAllNodesWithText(word, substring = true).assertCountEquals(0)
        rule.onNodeWithTag(SafeModeTags.PHRASE_VEIL).performScrollTo().performClick()
        assertTrue(revealRequested)

        state = base.copy(
            reveal = RevealUi.Shown(entry, RevealedSecret.Phrase(SafeModePreviewData.samplePhrase), revealed = true, canGoBack = false),
        )
        rule.waitForIdle()
        rule.onNodeWithText(word).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun progressFollowsTheTenAndSixtySecondRules() {
        var preview by mutableStateOf(SafeModePreviewState.U1_UPDATING)
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                SafeModePreview(preview)
            }
        }
        rule.onNodeWithTag(SafeModeTags.PROGRESS).assertExists()
        rule.onNodeWithTag(SafeModeTags.STILL_WORKING).assertDoesNotExist()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_get_help)).assertDoesNotExist()

        preview = SafeModePreviewState.U1_STILL_WORKING
        rule.waitForIdle()
        rule.onNodeWithTag(SafeModeTags.STILL_WORKING).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_get_help)).assertDoesNotExist()

        preview = SafeModePreviewState.U1_GET_HELP
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_keep_waiting)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_get_help)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun waitingForNetworkOffersChangeNode() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SafeModePreview(SafeModePreviewState.U2_WAITING_FOR_NETWORK)
            }
        }
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_step_network_waiting)).assertExists()
        rule.onNode(hasText(context.getString(CommonR.string.safe_mode_change_node)).and(hasClickAction()))
            .performScrollTo().assertIsEnabled()
    }

    @Test
    fun aRestoreWaitingForARestartOffersTheRestartAndNoOtherRestore() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SafeModePreview(SafeModePreviewState.U6_RESTORE_PENDING)
            }
        }
        rule.onNodeWithText(context.getString(R.string.recovery_restore_pending_title)).assertExists()
        rule.onNodeWithTag(RecoveryTags.action(RecoveryAction.TRY_AGAIN)).performScrollTo()
            .assertIsEnabled()
            .assert(hasText(context.getString(R.string.recovery_restart_continue)))
        rule.onNode(hasTestTag(RecoveryTags.action(RecoveryAction.RESTORE_PHRASE))).assertDoesNotExist()
        rule.onNode(hasTestTag(RecoveryTags.action(RecoveryAction.READ_ONLY))).assertDoesNotExist()
        rule.onNodeWithTag(RecoveryTags.action(RecoveryAction.CONTACT_SUPPORT)).performScrollTo().assertIsEnabled()
        // The old "usually passes in a moment" wording is not used for this state.
        rule.onNodeWithText(context.getString(R.string.recovery_transient_body)).assertDoesNotExist()
    }

    @Test
    fun aSheetActionThatFailedSaysSoAndKeepsAWayOut() {
        var preview by mutableStateOf(SafeModePreviewState.U6_READ_ONLY_UNAVAILABLE)
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SafeModePreview(preview)
            }
        }
        rule.onNodeWithText(context.getString(R.string.recovery_sheet_read_only_unavailable)).assertExists()
        // Confirming again would not help: only Close remains.
        rule.onNode(hasTestTag(RecoveryTags.CONFIRM_READ_ONLY)).assertDoesNotExist()
        rule.onNodeWithTag(RecoveryTags.SHEET_DISMISS)
            .assert(hasText(context.getString(CommonR.string.common_close)))
            .performScrollTo().assertIsEnabled()

        preview = SafeModePreviewState.U6_EXPORT_FAILED
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.recovery_sheet_export_failed)).assertExists()
        rule.onNode(hasText(context.getString(R.string.recovery_export_create)).and(hasClickAction()))
            .performScrollTo().assertIsEnabled()

        preview = SafeModePreviewState.U6_RESTORE_FAILED
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.recovery_sheet_restore_failed)).assertExists()
        rule.onNode(hasText(context.getString(R.string.recovery_restore_confirm)).and(hasClickAction()))
            .performScrollTo().assertIsEnabled()
    }

    @Test
    fun aPhraseNotProvenForThisWalletSaysSo() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SafeModePreview(SafeModePreviewState.U6_REVEAL_UNCHECKED)
            }
        }
        rule.onNodeWithText(context.getString(R.string.recovery_wallet_older)).assertExists()
        rule.onNodeWithText(context.getString(R.string.recovery_phrase_unchecked)).assertExists()
        // Still veiled until tapped.
        rule.onAllNodesWithText(SafeModePreviewData.samplePhrase.first(), substring = true).assertCountEquals(0)
    }

    @Test
    fun aPhraseProvenToOpenAnotherWalletIsNeverCalledUnconfirmed() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SafeModePreview(SafeModePreviewState.U6_REVEAL_OTHER_WALLET)
            }
        }
        rule.onNodeWithText(context.getString(R.string.recovery_phrase_other_wallet_title)).assertExists()
        rule.onNodeWithText(context.getString(R.string.recovery_phrase_other_wallet)).assertExists()
        // Not the "older version" title and not "couldn't confirm": both would be wrong here.
        rule.onNodeWithText(context.getString(R.string.recovery_wallet_older)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.recovery_phrase_unchecked)).assertDoesNotExist()
        rule.onAllNodesWithText(SafeModePreviewData.samplePhrase.first(), substring = true).assertCountEquals(0)
    }

    @Test
    fun upgradeSheetAsksPeopleWithSeveralAccountsForANewBatchBackupFile() {
        var accounts by mutableStateOf(2)
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                UpgradeCompleteSheet(
                    checks = UpgradeCompleteChecks(
                        accountsOnDevice = true,
                        protectedByPin = true,
                        balancesLoaded = true,
                        needsBackup = false,
                        accountCount = accounts,
                        signingAccountCount = accounts,
                    ),
                    onBackUpNow = {},
                    onClose = {},
                )
            }
        }
        val note = context.getString(CommonR.string.safe_mode_whats_new_batch_export)
        // Several accounts: a batch backup file made by an earlier version can be empty.
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_whats_new_title)).assertExists()
        rule.onNodeWithText(note).assertExists()
        rule.onNodeWithText(context.getString(CommonR.string.common_continue)).assertExists()

        // One account cannot have made such a file: nothing is asked.
        accounts = 1
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_whats_new_title)).assertExists()
        rule.onNodeWithText(note).assertDoesNotExist()
    }

    @Test
    fun offlineCheckOnThePhoneNeverSaysItWaitsForTheNetwork() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SafeModePreview(SafeModePreviewState.U1_PIN_CHECK_OFFLINE)
            }
        }
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_checking_title)).assertExists()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_step_network_waiting)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_waiting_network_body)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(CommonR.string.safe_mode_change_node)).assertDoesNotExist()
    }

    @Test
    fun storageScreenNeverOffersToClearData() {
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                SafeModePreview(SafeModePreviewState.U4_FREE_SPACE)
            }
        }
        rule.onNodeWithText(context.getString(R.string.recovery_storage_title)).assertExists()
        rule.onNodeWithText(context.getString(R.string.recovery_storage_body, "120")).assertExists()
        rule.onNodeWithTag(RecoveryTags.action(RecoveryAction.OPEN_STORAGE)).performScrollTo().assertIsEnabled()
        rule.onAllNodesWithText("clear data", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("reinstall", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onNode(hasTestTag(RecoveryTags.action(RecoveryAction.EXPORT))).assertDoesNotExist()
    }
}
