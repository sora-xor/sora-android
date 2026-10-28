@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(androidx.compose.ui.unit.ExperimentalUnitApi::class)

package jp.co.soramitsu.sora.settings

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_main_impl.presentation.profile.ProfileItems
import jp.co.soramitsu.feature_main_impl.presentation.profile.ProfileScreenState
import jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings.AppSettingsScreen
import jp.co.soramitsu.feature_main_impl.presentation.profile.information.InformationScreen
import jp.co.soramitsu.feature_main_impl.presentation.profile.login.LoginSecurityScreen
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list.*
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.*
import jp.co.soramitsu.feature_select_node_impl.presentation.select.SelectNodeViewModel
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.*
import jp.co.soramitsu.feature_select_node_impl.presentation.select.ui.SelectNodeScreen
import jp.co.soramitsu.feature_select_node_impl.presentation.details.*
import jp.co.soramitsu.feature_wallet_impl.presentation.contacts.*
import jp.co.soramitsu.feature_wallet_impl.presentation.claim.*
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real UI with recorded navigation; no external links, account mutations, or network connections. */
@RunWith(AndroidJUnit4::class)
class SettingsAndAccountsInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val readOnly = mutableStateOf(false)
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun show(scroll: Boolean = true, content: @Composable () -> Unit) = compose.setContent {
        SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
            Column(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier) { content() }
        } }
    }
    private fun profile() = ProfileScreenState("SORA mainnet", true, true, true)
    private val addressA = "cnT65V7vJnPkR2Y2AxXwV1Bpcbab7vNaHGd3ZxXjrTpPuxvj2"
    private val addressB = "cnVyaue39dssBc2bReZycusLdys3vbeoz2irRF8BbwVcdCNmm"
    private fun accounts() = AccountListScreenState(false, listOf(
        ExportAccountData(true, false, ColorDrawable(Color.RED), SoraAccount(addressA, "Main wallet")),
        ExportAccountData(false, false, ColorDrawable(Color.BLUE), SoraAccount(addressB, "Read-only wallet"), true)))
    private fun chainNode(custom: Boolean = false) = ChainNode("SORA", if (custom) "Private node" else "Official node",
        if (custom) "wss://node.example.test" else "wss://ws.mof.sora.org", !custom, !custom)

    @Test fun profileRoutesEachVisibleDestinationIndependently() {
        show { ProfileItems(profile(), { events += "accounts" }, { events += "markets" }, { events += "nodes" },
            { events += "settings" }, { events += "login" }, { events += "referral" }, { events += "information" }, { events += "debug" }) }
        for (label in listOf(R.string.settings_crypto_accounts, R.string.polkamarkt_title, R.string.settings_nodes,
            R.string.settings_header_app, R.string.settings_login_title, R.string.settings_invite_title, R.string.settings_information_title)) {
            node(label).performScrollTo().performClick()
        }
        compose.onNodeWithText("Debug Menu").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("accounts", "markets", "nodes", "settings", "login", "referral", "information", "debug"), events) }
    }
    @Test fun profileRemovesUnavailableOptionalDestinationsAndUpdatesNodeName() {
        val state = mutableStateOf(profile())
        show { ProfileItems(state.value, {}, {}, {}, {}, {}, {}, {}, {}) }
        compose.runOnIdle { state.value = profile().copy(nodeName = "Offline node", nodeConnected = false, isDebugMenuAvailable = false, polkamarktVisible = false) }
        node(R.string.polkamarkt_title).assertDoesNotExist()
        compose.onNodeWithText("Debug Menu").assertDoesNotExist()
        compose.onNodeWithText("SORA mainnet").assertDoesNotExist()
        compose.onNodeWithText("Offline node").assertExists()
    }
    @Test fun informationRoutesEveryDocumentAndSocialDestination() {
        show { InformationScreen("Version 1.2.3", { events += "disclaimer" }, { events += "faq" }, { events += "email" },
            { events += "support" }, { events += "terms" }, { events += "privacy" }, { events += "announcements" },
            { events += "telegram" }, { events += "wiki" }, { events += "medium" }, { events += "instagram" },
            { events += "youtube" }, { events += "twitter" }, { events += "github" }, { events += "website" }) }
        compose.onNodeWithText("Version 1.2.3").assertExists()
        for (id in listOf(R.string.faq_title, R.string.about_ask_support, R.string.polkaswap_info_title_2, R.string.common_terms_title,
            R.string.about_privacy, R.string.about_website, R.string.about_source_code, R.string.about_twitter, R.string.about_youtube,
            R.string.about_instagram, R.string.about_medium, R.string.about_wiki, R.string.about_telegram, R.string.about_announcements, R.string.about_contact_us)) {
            node(id).performScrollTo().performClick()
        }
        compose.runOnIdle { assertEquals(listOf("faq", "support", "disclaimer", "terms", "privacy", "website", "github", "twitter",
            "youtube", "instagram", "medium", "wiki", "telegram", "announcements", "email"), events) }
    }
    @Test fun appearanceForwardsSystemDarkAndLightChoices() {
        show { AppSettingsScreen(true, false, false, false, { events += "system:$it" }, { events += "dark:$it" }, {}) }
        node(R.string.neon_theme_dark).performClick()
        node(R.string.neon_theme_light).performClick()
        node(R.string.neon_theme_system).performClick()
        node(R.string.test_networks).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("dark:true", "dark:false", "system:true"), events) }
    }
    @Test fun appearanceTogglesReducedEffectsAndTestNetworks() {
        show { AppSettingsScreen(false, true, true, false, {}, {}, { events += "test:$it" }, false, { events += "effects:$it" }) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.appearance_reduce_effects)).assertIsOff().performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.test_networks)).assertIsOff().performClick()
        compose.runOnIdle { assertEquals(listOf("effects:true", "test:true"), events) }
    }
    @Test fun loginSettingsRoutePinAndBiometryWithoutAuthenticating() {
        show { LoginSecurityScreen(true, false, { events += "pin" }, { events += "bio:$it" }) }
        node(R.string.change_pin).performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.profile_biometry_title)).performClick()
        compose.runOnIdle { assertEquals(listOf("pin", "bio:true"), events) }
    }
    @Test fun unavailableBiometryDoesNotInvokeToggle() {
        show { LoginSecurityScreen(false, false, {}, { events += "bio" }) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.profile_biometry_title)).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun accountRowsRouteTheirExactAddressAndKeepReadOnlyLabel() {
        val vm = mockk<AccountListViewModel>(relaxed = true)
        show(false) { AccountListScreen(accounts(), vm, rememberScrollState()) }
        node(R.string.account_read_only_list_label).assertIsDisplayed()
        compose.onNodeWithText("Main wallet").performClick()
        compose.onNodeWithText("Read-only wallet").performClick()
        verify(exactly = 1) { vm.onAccountClicked(addressA) }
        verify(exactly = 1) { vm.onAccountClicked(addressB) }
    }
    @Test fun accountLongPressCopiesTheAddressWithoutSwitching() {
        val vm = mockk<AccountListViewModel>(relaxed = true)
        show(false) { AccountListScreen(accounts(), vm, rememberScrollState()) }
        compose.onNodeWithText("Main wallet").performTouchInput { longClick() }
        verify(exactly = 1) { vm.onAccountLongClicked(addressA) }
        verify(exactly = 0) { vm.onAccountClicked(any()) }
    }
    @Test fun accountMenuKeepsSelectionAndOptionsBoundToItsRow() {
        val vm = mockk<AccountListViewModel>(relaxed = true)
        show(false) { AccountListScreen(accounts(), vm, rememberScrollState()) }
        compose.onAllNodesWithTag("jp.co.soramitsu.sora.develop:id/OpenAccountMenuItem")[1].performClick()
        node(R.string.export_account_options).performClick()
        verify(exactly = 1) { vm.onAccountOptionsClicked(addressB) }
        node(R.string.export_account_options).assertDoesNotExist()
        compose.onAllNodesWithTag("jp.co.soramitsu.sora.develop:id/OpenAccountMenuItem")[0].performClick()
        node(R.string.export_select_account).performClick()
        verify(exactly = 1) { vm.onAccountSelected(addressA) }
        node(R.string.export_select_account).assertDoesNotExist()
    }
    @Test fun exportSelectionModeDoesNotSwitchTheActiveWallet() {
        val vm = mockk<AccountListViewModel>(relaxed = true)
        show(false) { AccountListScreen(accounts().copy(isActionMode = true), vm, rememberScrollState()) }
        compose.onNodeWithText("Read-only wallet").performClick()
        verify(exactly = 1) { vm.onAccountSelected(addressB) }
        verify(exactly = 0) { vm.onAccountClicked(any()) }
    }
    @Test fun nodeListSeparatesDefaultsAndCustomNodesAndRoutesExactObjects() {
        val vm = mockk<SelectNodeViewModel>(relaxed = true)
        every { vm.state } returns SelectNodeState(listOf(chainNode()), listOf(chainNode(true)))
        show(false) { SelectNodeScreen(rememberScrollState(), vm) }
        compose.onNodeWithText("Official node").performClick()
        compose.onNodeWithText("Private node").performClick()
        node(R.string.select_node_add_custom_node).performClick()
        verify(exactly = 1) { vm.onNodeSelected(chainNode()) }
        verify(exactly = 1) { vm.onNodeSelected(chainNode(true)) }
        verify(exactly = 1) { vm.onAddCustomNode() }
    }
    @Test fun nodeRemovalRequiresExplicitConfirmationAndCanBeCanceled() {
        val vm = mockk<SelectNodeViewModel>(relaxed = true)
        every { vm.state } returns SelectNodeState(removeAlertState = RemoveNodeAlertState(chainNode(true)))
        show(false) { SelectNodeScreen(rememberScrollState(), vm) }
        node(R.string.common_cancel).performClick()
        verify(exactly = 1) { vm.onRemoveNodeCanceled() }
        verify(exactly = 0) { vm.onRemoveNodeConfirmed() }
        node(R.string.common_remove).performClick()
        verify(exactly = 1) { vm.onRemoveNodeConfirmed() }
    }
    @Test fun nodeSwitchExplainsTheChangeAndRequiresConfirmation() {
        val vm = mockk<SelectNodeViewModel>(relaxed = true)
        every { vm.state } returns SelectNodeState(switchNodeAlertState = SwitchNodeAlertState(chainNode(true)))
        show(false) { SelectNodeScreen(rememberScrollState(), vm) }
        node(R.string.select_node_switch_alert_description).assertIsDisplayed()
        node(R.string.common_cancel).performClick()
        verify(exactly = 1) { vm.onNodeSwitchCanceled() }
        verify(exactly = 0) { vm.onNodeSwitchConfirmed() }
        node(R.string.common_ok).performClick()
        verify(exactly = 1) { vm.onNodeSwitchConfirmed() }
    }
    @Test fun nodeDetailsForwardEditedFieldsAndRespectSubmitValidation() {
        val vm = mockk<NodeDetailsViewModel>(relaxed = true)
        val state = MutableStateFlow(NodeDetailsState(InputTextState(label = "Node name"), InputTextState(label = "Node address")))
        every { vm.state } returns state
        show { NodeDetailsScreen(vm) }
        node(R.string.select_node_add_custom_node).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Private node")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("wss://node.example.test")
        verify { vm.onNameChanged(match { it.text == "Private node" }) }
        verify { vm.onAddressChanged(match { it.text == "wss://node.example.test" }) }
        compose.runOnIdle { state.value = state.value.copy(submitButtonEnabled = true) }
        node(R.string.select_node_add_custom_node).performClick()
        node(R.string.select_node_how_to_run_node).performClick()
        verify(exactly = 1) { vm.onSubmit() }
        verify(exactly = 1) { vm.onHowToRunNode() }
    }
    @Test fun contactsForwardTheExactChosenAddressAndEditedSearch() {
        val edits = mutableListOf<String>()
        show(false) { ContactsScreen(InputTextState(label = "Find an address"), { edits += it.text }, R.string.address_not_found_1,
            listOf(ContactsListItem(addressA, ColorDrawable(Color.RED)), ContactsListItem(addressB, ColorDrawable(Color.BLUE))),
            isMyAddress = false, onScanClick = {}, onCloseSearchClick = {}, onAccountClick = { events += it }) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Alice")
        compose.onNodeWithTag("jp.co.soramitsu.sora.develop:id/SearchItemN1").performClick()
        compose.runOnIdle { assertEquals(listOf("Alice"), edits); assertEquals(listOf(addressB), events) }
    }
    @Test fun contactsExplainWhenTheRecipientIsTheCurrentWallet() {
        val same = mutableStateOf(true)
        show(false) { ContactsScreen(InputTextState(), {}, R.string.address_not_found_1, emptyList(),
            isMyAddress = same.value, onScanClick = {}, onCloseSearchClick = {}, onAccountClick = {}) }
        node(R.string.invoice_scan_error_match).assertExists()
        compose.runOnIdle { same.value = false }
        node(R.string.invoice_scan_error_match).assertDoesNotExist()
    }
    @Test fun legacyClaimRequiresExplicitConfirmationAndWritableWallet() {
        show { ClaimScreen(ClaimState(false), { events += "claim" }, {}) }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        node(R.string.common_confirm).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("claim"), events); readOnly.value = true }
        node(R.string.common_confirm).assertIsNotEnabled()
    }
    @Test fun pendingLegacyClaimDisplaysConfirmationStatus() {
        show { ClaimScreen(ClaimState(true), { events += "claim" }, {}) }
        node(R.string.claim_subtitle_confirmed).assertExists()
        node(R.string.claim_subtitle_confirmed_v2).assertExists()
        node(R.string.claim_subtitle_v2).assertDoesNotExist()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
}
