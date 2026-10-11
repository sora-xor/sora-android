@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.ux

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyNotice
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeTags
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_account_api.domain.model.UnavailableWalletReason
import jp.co.soramitsu.feature_main_impl.presentation.pincode.AccountRemovalScreen
import jp.co.soramitsu.feature_main_impl.presentation.pincode.AccountRemovalState
import jp.co.soramitsu.feature_main_impl.presentation.pincode.AccountRemovalTags
import jp.co.soramitsu.feature_main_impl.presentation.profile.login.LoginSecurityScreen
import jp.co.soramitsu.feature_main_impl.presentation.profile.login.RetainedWalletsTags
import jp.co.soramitsu.feature_main_impl.presentation.profile.login.RetainedWalletsUi
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.ReadOnlyAccountPanel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.ReadOnlyAccountPanelTags
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.ACCOUNT_ROW_READ_ONLY_TAG
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.SelectableAccountWithIcon
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ReadOnlyAccountUi
import jp.co.soramitsu.feature_multiaccount_impl.presentation.found_wallets.FoundWalletsScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.found_wallets.FoundWalletsTags
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.TopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screens of audit finding A2 on a device, rendered with the real composables: an account
 * that cannot sign is labelled where it is shown, its options say why and offer every way
 * forward (repair first), its removal shows the matching backup before anything is removed, and
 * wallets found on the phone are restored only behind the PIN or their own recovery phrase.
 */
@RunWith(AndroidJUnit4::class)
class ReadOnlyAccountScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun tagEndsWith(suffix: String) = SemanticsMatcher("test tag ends with $suffix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith(suffix) == true
    }

    private val phrase = listOf(
        "legal", "winner", "thank", "year", "wave", "sausage",
        "worth", "useful", "legal", "winner", "thank", "yellow",
    )

    // ---- Label: the main screen header and the account list ---------------------------------

    @Test
    fun theMainScreenLabelsAReadOnlyAccountSaysWhyAndOffersTheRepair() {
        var readOnly by mutableStateOf(true)
        var repairs = 0
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                Column {
                    TopBar(account = "Bravo", onAccountClick = {}, onQrClick = {}, readOnly = readOnly)
                    if (readOnly) AccountReadOnlyNotice(onRepair = { repairs++ })
                }
            }
        }

        rule.onNodeWithText("Bravo").assertIsDisplayed()
        // The chip next to the account's name, and the chip of the notice.
        rule.onAllNodesWithText(text(CommonR.string.safe_mode_read_only_chip)).assertCountEquals(2)
        rule.onNodeWithText(text(CommonR.string.account_read_only_title)).assertIsDisplayed()
        rule.onNodeWithText(text(CommonR.string.account_read_only_body)).assertIsDisplayed()
        rule.onNodeWithTag(AccountReadOnlyTags.REPAIR).assertIsDisplayed().performClick()
        assertEquals(1, repairs)

        // An account that signs has neither.
        readOnly = false
        rule.waitForIdle()
        rule.onAllNodesWithText(text(CommonR.string.safe_mode_read_only_chip)).assertCountEquals(0)
        rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
    }

    @Test
    fun theAccountListLabelsOnlyTheAccountThatCannotSign() {
        val icon = ColorDrawable(Color.GRAY)
        rule.setContent {
            SoraAppTheme(darkTheme = false) {
                Column {
                    SelectableAccountWithIcon(
                        modifier = Modifier,
                        address = "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m",
                        accountName = "Alpha",
                        accountIcon = icon,
                        selectableIcon = CommonR.drawable.ic_checkmark_24,
                        tint = androidx.compose.ui.graphics.Color.Green,
                        showSelectableIcon = true,
                        readOnly = false,
                    )
                    SelectableAccountWithIcon(
                        modifier = Modifier,
                        address = "cnTiG8p7LWNnLt5m8AuMe1ZsxwnT6Ss4YZXoVRodHftnzZSok",
                        accountName = "Bravo",
                        accountIcon = icon,
                        selectableIcon = CommonR.drawable.ic_checkmark_24,
                        tint = androidx.compose.ui.graphics.Color.Green,
                        showSelectableIcon = false,
                        readOnly = true,
                    )
                }
            }
        }

        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Bravo").assertIsDisplayed()
        rule.onAllNodes(tagEndsWith(ACCOUNT_ROW_READ_ONLY_TAG)).assertCountEquals(1)
        rule.onAllNodesWithText(text(CommonR.string.account_read_only_list_label)).assertCountEquals(1)
    }

    // ---- Account options: why, and every way forward ------------------------------------------

    @Test
    fun accountOptionsSayWhyAndOfferRepairBackupAndRemovalInThatOrder() {
        val clicked = mutableListOf<String>()
        var panel by mutableStateOf(
            ReadOnlyAccountUi(
                reason = UnavailableWalletReason.KEY_UNREADABLE,
                canRestoreWithPhrase = true,
                canRestoreFromGoogle = true,
                canShowBackup = true,
                canRemove = true,
            )
        )
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReadOnlyAccountPanel(
                        readOnly = panel,
                        onRestoreKeysWithPhrase = { clicked += "phrase" },
                        onRestoreKeysFromGoogle = { clicked += "google" },
                        onShowSavedBackup = { clicked += "backup" },
                        onRemove = { clicked += "remove" },
                    )
                }
            }
        }

        rule.onNodeWithText(text(CommonR.string.account_read_only_title)).assertIsDisplayed()
        rule.onNodeWithText(text(CommonR.string.account_read_only_reason_unreadable)).assertIsDisplayed()
        // Removal is never the repair, and the screen says so.
        rule.onNodeWithText(text(CommonR.string.account_read_only_restore_note)).performScrollTo()
            .assertIsDisplayed()
        listOf(
            ReadOnlyAccountPanelTags.RESTORE_PHRASE,
            ReadOnlyAccountPanelTags.RESTORE_GOOGLE,
            ReadOnlyAccountPanelTags.SHOW_BACKUP,
            ReadOnlyAccountPanelTags.REMOVE,
        ).forEach { tag ->
            rule.onNode(tagEndsWith(tag)).performScrollTo().assertIsDisplayed().assertIsEnabled()
                .performClick()
        }
        assertEquals(listOf("phrase", "google", "backup", "remove"), clicked)

        // Every reason has its own sentence.
        mapOf(
            UnavailableWalletReason.KEY_MISSING to CommonR.string.account_read_only_reason_missing,
            UnavailableWalletReason.KEY_INCOMPLETE to CommonR.string.account_read_only_reason_incomplete,
            UnavailableWalletReason.KEY_NOT_USABLE to CommonR.string.account_read_only_reason_not_usable,
        ).forEach { (reason, sentence) ->
            panel = panel.copy(reason = reason)
            rule.waitForIdle()
            rule.onNodeWithText(text(sentence)).performScrollTo().assertIsDisplayed()
        }

        // Only what is possible is offered: no Google build, nothing saved to show.
        panel = panel.copy(canRestoreFromGoogle = false, canShowBackup = false)
        rule.waitForIdle()
        rule.onNode(tagEndsWith(ReadOnlyAccountPanelTags.RESTORE_GOOGLE)).assertDoesNotExist()
        rule.onNode(tagEndsWith(ReadOnlyAccountPanelTags.SHOW_BACKUP)).assertDoesNotExist()
        rule.onNode(tagEndsWith(ReadOnlyAccountPanelTags.RESTORE_PHRASE)).assertExists()
    }

    // ---- Removal: the matching backup first ---------------------------------------------------

    private fun removalContent(initial: AccountRemovalState, events: MutableList<String>): () -> AccountRemovalState {
        var state by mutableStateOf(initial)
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                AccountRemovalScreen(
                    state = state,
                    onReveal = { state = state.copy(revealed = true) },
                    // The view model's rule: only what was shown can have been written down.
                    onWrittenDown = { if (state.revealed) state = state.copy(writtenDown = it) },
                    onUnderstoodNoBackup = { state = state.copy(understoodNoBackup = it) },
                    onExportJson = { events += "export" },
                    onRemove = { events += "remove" },
                    onCancel = { events += "cancel" },
                )
            }
        }
        return { state }
    }

    @Test
    fun removingTheOnlyAccountShowsItsPhraseBehindAVeilAndNeedsItWrittenDown() {
        val events = mutableListOf<String>()
        val state = removalContent(
            AccountRemovalState(
                accounts = listOf(
                    AccountRemovalState.Account(
                        address = "cnTiG8p7LWNnLt5m8AuMe1ZsxwnT6Ss4YZXoVRodHftnzZSok",
                        name = "Bravo",
                        phraseWords = phrase,
                    ),
                ),
                removesEverything = true,
                retainedKeySets = 2,
            ),
            events,
        )

        // It is the last account: the screen says what else goes and that the app starts again.
        rule.onNodeWithText(text(CommonR.string.account_removal_body_last)).assertIsDisplayed()
        rule.onNodeWithText("Bravo").assertIsDisplayed()
        // The phrase is veiled: its words are not composed at all.
        rule.onAllNodesWithText("sausage").assertCountEquals(0)
        // Nothing can be removed yet, and the acknowledgement cannot be given yet.
        rule.onNodeWithTag(AccountRemovalTags.REMOVE).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(AccountRemovalTags.WRITTEN_DOWN).performScrollTo().performClick()
        assertFalse(state().writtenDown)
        rule.onNodeWithTag(AccountRemovalTags.REMOVE).performScrollTo().assertIsNotEnabled()

        // What stays on the phone is said before anything is removed.
        rule.onNodeWithText(
            text(CommonR.string.account_removal_stays_found_wallets_count, "2")
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(CommonR.string.account_removal_stays_copies)).performScrollTo()
            .assertIsDisplayed()

        // Reveal, read, acknowledge: only then can it be removed.
        rule.onNodeWithTag(SafeModeTags.PHRASE_VEIL).performScrollTo().performClick()
        rule.onNodeWithText("sausage").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("yellow").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(AccountRemovalTags.REMOVE).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(AccountRemovalTags.WRITTEN_DOWN).performScrollTo().performClick()
        assertTrue(state().writtenDown)
        assertTrue(events.isEmpty())
        rule.onNodeWithTag(AccountRemovalTags.REMOVE).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("remove"), events)
    }

    @Test
    fun removingOneOfSeveralAccountsSaysThatTheOthersAndThePinStay() {
        val events = mutableListOf<String>()
        removalContent(
            AccountRemovalState(
                accounts = listOf(
                    AccountRemovalState.Account(address = "cnB", name = "Bravo", phraseWords = phrase),
                ),
                removesEverything = false,
            ),
            events,
        )

        rule.onNodeWithText(text(CommonR.string.account_removal_body)).assertIsDisplayed()
        rule.onNodeWithText(text(CommonR.string.account_removal_stays_other_accounts)).performScrollTo()
            .assertIsDisplayed()
        // Keeping the account is always one tap away and removes nothing.
        rule.onNodeWithTag(AccountRemovalTags.CANCEL).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun anAccountWithoutAMatchingBackupSaysSoAndOffersTheJsonExportFirst() {
        val events = mutableListOf<String>()
        val state = removalContent(
            AccountRemovalState(
                accounts = listOf(
                    AccountRemovalState.Account(address = "cnW", name = "Working", canExportJson = true),
                ),
                removesEverything = false,
            ),
            events,
        )

        rule.onNodeWithTag(AccountRemovalTags.NO_BACKUP).assertIsDisplayed()
        rule.onNodeWithText(text(CommonR.string.account_removal_no_backup)).assertIsDisplayed()
        // There is nothing to write down, so that is not what is asked.
        rule.onNodeWithTag(AccountRemovalTags.WRITTEN_DOWN).assertDoesNotExist()
        rule.onNodeWithTag(SafeModeTags.PHRASE_VEIL).assertDoesNotExist()
        rule.onNodeWithTag(AccountRemovalTags.EXPORT_JSON).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("export"), events)

        rule.onNodeWithTag(AccountRemovalTags.REMOVE).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(AccountRemovalTags.UNDERSTOOD_NO_BACKUP).performScrollTo().performClick()
        assertTrue(state().understoodNoBackup)
        rule.onNodeWithTag(AccountRemovalTags.REMOVE).performScrollTo().assertIsEnabled()
    }

    @Test
    fun aReadOnlyAccountWithoutABackupIsNotOfferedAnExportThatCannotBeMade() {
        removalContent(
            AccountRemovalState(
                accounts = listOf(
                    AccountRemovalState.Account(address = "cnR", name = "Read only", canExportJson = false),
                ),
                removesEverything = false,
            ),
            mutableListOf(),
        )

        rule.onNodeWithTag(AccountRemovalTags.NO_BACKUP).assertIsDisplayed()
        rule.onNodeWithTag(AccountRemovalTags.EXPORT_JSON).assertDoesNotExist()
        rule.onNodeWithTag(AccountRemovalTags.UNDERSTOOD_NO_BACKUP).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRawSeedIsShownOnlyAfterItIsRevealed() {
        val seed = "0x" + "ab".repeat(32)
        removalContent(
            AccountRemovalState(
                accounts = listOf(AccountRemovalState.Account(address = "cnS", name = "Seed", seed = seed)),
                removesEverything = false,
            ),
            mutableListOf(),
        )

        rule.onAllNodesWithText(seed).assertCountEquals(0)
        rule.onNodeWithTag(AccountRemovalTags.SEED_REVEAL).performScrollTo().performClick()
        rule.onNodeWithTag(AccountRemovalTags.SEED).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(seed).assertIsDisplayed()
    }

    // ---- Wallets found on this phone ----------------------------------------------------------

    @Test
    fun onboardingAsksForThePinBeforeRestoringWalletsFoundOnThisPhone() {
        val pins = mutableListOf<String>()
        var state by mutableStateOf(
            OnboardingViewModel.FoundWalletsState(
                addresses = listOf("cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"),
            )
        )
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                FoundWalletsScreen(state = state, onRestore = { pins += it })
            }
        }

        rule.onNodeWithText(text(CommonR.string.onboarding_found_wallets_body)).assertIsDisplayed()
        rule.onNodeWithText("cnTon6cV…wdUoRf4m").assertIsDisplayed()
        // Nothing is restored without a PIN.
        rule.onNodeWithTag(FoundWalletsTags.RESTORE).assertIsNotEnabled()
        rule.onNodeWithTag(FoundWalletsTags.PIN).performTextInput("135790")
        rule.onNodeWithTag(FoundWalletsTags.RESTORE).assertIsEnabled().performClick()
        assertEquals(listOf("135790"), pins)

        // A wrong PIN is said to be wrong, and that nothing was changed.
        state = state.copy(wrongPin = true)
        rule.waitForIdle()
        rule.onNodeWithTag(FoundWalletsTags.WRONG_PIN).assertIsDisplayed()
        rule.onNodeWithText(text(CommonR.string.account_repair_pin_wrong)).assertIsDisplayed()
    }

    @Test
    fun aWalletLeftByRemoveAllAccountsAsksForItsRecoveryPhraseAndCannotBeRestoredFromHere() {
        val restored = mutableListOf<String>()
        var imports = 0
        rule.setContent {
            SoraAppTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LoginSecurityScreen(
                        bioAvailable = false,
                        bioEnabled = false,
                        onChangePinClick = {},
                        onToggle = {},
                        retained = RetainedWalletsUi(
                            addresses = listOf("cnLeftByRemoveAll00000000000000", "cnStillBehindThePin0000000000000"),
                            needRecoveryPhrase = listOf("cnLeftByRemoveAll00000000000000"),
                        ),
                        onRestoreRetained = { restored += it },
                        onImportRecoveryPhrase = { imports++ },
                    )
                }
            }
        }

        rule.onNodeWithTag(RetainedWalletsTags.NEEDS_PHRASE).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(CommonR.string.safe_mode_retained_needs_phrase)).assertIsDisplayed()
        // Exactly one "Restore this wallet": for the key set the PIN still protects.
        rule.onAllNodesWithText(text(CommonR.string.safe_mode_retained_restore)).assertCountEquals(1)
        rule.onNodeWithTag(RetainedWalletsTags.RESTORE).performScrollTo().performClick()
        assertEquals(listOf("cnStillBehindThePin0000000000000"), restored)

        rule.onNodeWithTag(RetainedWalletsTags.IMPORT_PHRASE).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, imports)
    }
}
