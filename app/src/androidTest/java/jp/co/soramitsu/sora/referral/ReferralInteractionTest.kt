package jp.co.soramitsu.sora.referral

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_referral_impl.presentation.*
import jp.co.soramitsu.feature_referral_impl.presentation.screens.ReferralWelcomePageScreen
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedTextState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferralInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val readOnly = mutableStateOf(false)
    private val referrer = "cnT65V7vJnPkR2Y2AxXwV1Bpcbab7vNaHGd3ZxXjrTpPuxvj2"
    private val invitation = "https://polkaswap.io/#/referral/$referrer"
    private fun common() = ReferralCommonState(null, true, false, "0.005 XOR", "0.002 XOR", "$0.01")
    private fun bond() = ReferralBondState(true, 2, "0.010 XOR", "100 XOR")
    private fun invitations() = ReferralInvitationsCardState("Available invitations", 2,
        WrappedTextState(title = "Invitation link", text = invitation), "0.010 XOR", ReferralsCardState())
    private fun program() = ReferralProgramState(common(), bond(), InputTextState(), invitations())
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        } }
    }
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun bondButton() = node(R.string.referral_bond_button_title)
    private fun unbondButton() = node(R.string.referral_unbond_button_title)

    @Test fun bondingShowsExactAmountsAndRequiresAnExplicitAction() {
        show { ReferralBondXor(common(), bond(), {}, {}, {}, { events += "bond" }) }
        compose.onNodeWithText("0.010 XOR").assertExists()
        compose.onNodeWithText("0.002 XOR").assertExists()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        bondButton().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("bond"), events) }
    }
    @Test fun bondingRequiresAPositiveInvitationCountAndReadyState() {
        val state = mutableStateOf(bond().copy(invitationsCount = 0))
        val fees = mutableStateOf(common())
        show { ReferralBondXor(fees.value, state.value, {}, {}, {}, { events += "bond" }) }
        bondButton().performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = bond(); fees.value = common().copy(activate = false) }
        bondButton().assertIsNotEnabled()
        compose.runOnIdle { fees.value = common() }
        bondButton().assertIsEnabled()
    }
    @Test fun readOnlyAccountCannotBondEvenWithReadyState() {
        show { ReferralBondXor(common(), bond(), {}, {}, {}, { events += "bond" }) }
        compose.runOnIdle { readOnly.value = true }
        bondButton().performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun bondingForwardsTheEditedInvitationCount() {
        val counts = mutableListOf<Int>()
        show { ReferralBondXor(common(), bond(), counts::add, {}, {}, {}) }
        compose.onNode(hasSetTextAction()).performTextReplacement("7")
        compose.runOnIdle { assertEquals(listOf(7), counts) }
    }
    @Test fun invitationInputDoesNotOverflowOrThrowForPastedInvalidText() {
        val counts = mutableListOf<Int>()
        show { InvitationsEnterField("100 XOR", "0.010 XOR", 2, {}, {}, counts::add) }
        for (value in listOf("99999999999999999999999", "invalid", "")) {
            compose.onNode(hasSetTextAction()).performTextReplacement(value)
        }
        compose.runOnIdle { assertEquals(listOf(0, 0, 0), counts) }
    }
    @Test fun invitationStepperHasAccessibleNamesAndSeparateActions() {
        show { InvitationsEnterField("100 XOR", "0.010 XOR", 2, { events += "minus" }, { events += "plus" }, {}) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.common_remove)).assertHasClickAction().performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.referral_add_invitations_title)).assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(listOf("minus", "plus"), events) }
    }
    @Test fun bondingWarnsWhenTheRemainingBalanceWouldBeTooSmall() {
        show { ReferralBondXor(common(), bond().copy(shouldTransactionReminderInsufficientWarningBeShown = true), {}, {}, {}, {}) }
        compose.onNodeWithText(compose.activity.getString(R.string.swap_confirmation_screen_warning_balance_afterwards_transaction_is_too_small, "0.002 XOR"))
            .performScrollTo().assertIsDisplayed()
    }
    @Test fun unbondingShowsExactAmountsAndRequiresAnExplicitAction() {
        show { ReferralUnbondXor(common(), bond(), {}, {}, {}, { events += "unbond" }) }
        compose.onNodeWithText("0.010 XOR").assertExists()
        compose.onNodeWithText("0.002 XOR").assertExists()
        unbondButton().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("unbond"), events) }
    }
    @Test fun unbondingRequiresPositiveCountAndWritableAccount() {
        val state = mutableStateOf(bond().copy(invitationsCount = 0))
        show { ReferralUnbondXor(common(), state.value, {}, {}, {}, { events += "unbond" }) }
        unbondButton().performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = bond(); readOnly.value = true }
        unbondButton().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun unbondingForwardsTheEditedCountAndShowsBalanceWarning() {
        val counts = mutableListOf<Int>()
        show { ReferralUnbondXor(common(), bond().copy(shouldTransactionReminderInsufficientWarningBeShown = true), counts::add, {}, {}, {}) }
        compose.onNode(hasSetTextAction()).performTextReplacement("1")
        compose.onNodeWithText(compose.activity.getString(R.string.swap_confirmation_screen_warning_balance_afterwards_transaction_is_too_small, "0.002 XOR"))
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(1), counts) }
    }
    @Test fun availableInvitationsRouteShareGetMoreAndUnbondSeparately() {
        show { ReferralAvailableInvitationsCard(invitations(), Modifier, { events += "more" }, { events += "unbond" }, { events += "share" }) }
        compose.onNodeWithText(invitation).performScrollTo().performClick()
        node(R.string.referral_get_more_invitation_button_title).performScrollTo().performClick()
        unbondButton().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("share", "more", "unbond"), events) }
    }
    @Test fun emptyInvitationsHideAnUnusableLinkAndUnbondAction() {
        show { ReferralAvailableInvitationsCard(invitations().copy(invitationsCount = 0), Modifier, { events += "more" }, {}, {}) }
        compose.onNodeWithText(invitation).assertDoesNotExist()
        unbondButton().assertDoesNotExist()
        node(R.string.referral_get_more_invitation_button_title).performClick()
        compose.runOnIdle { assertEquals(listOf("more"), events) }
    }
    @Test fun removedReferrerReplacesOldAddressWithTheEnterLinkAction() {
        val state = mutableStateOf(common().copy(referrer = referrer))
        show { YourReferrerCard(state.value, Modifier) { events += "enter" } }
        compose.onNodeWithText(referrer).assertIsDisplayed()
        node(R.string.referral_enter_link_title).assertDoesNotExist()
        compose.runOnIdle { state.value = common() }
        compose.onNodeWithText(referrer).assertDoesNotExist()
        node(R.string.referral_enter_link_title).performClick()
        compose.runOnIdle { assertEquals(listOf("enter"), events) }
    }
    @Test fun referralRewardsPreserveExactAmountsAndCanBeCollapsed() {
        val state = mutableStateOf(ReferralsCardState(listOf(ReferralModel("Alice", "1.25 XOR"), ReferralModel("Bob", "2.5 XOR")), "3.75 XOR", true))
        show { YourReferralsCard(state.value, Modifier) { state.value = state.value.copy(isExpanded = !state.value.isExpanded) } }
        compose.onNodeWithText("1.25 XOR").assertIsDisplayed()
        compose.onNodeWithText("2.5 XOR").assertIsDisplayed()
        compose.onNodeWithText("3.75 XOR").assertIsDisplayed()
        node(R.string.referral_your_referrals).performClick()
        compose.onNodeWithText("Alice").assertDoesNotExist()
        compose.onNodeWithText("Bob").assertDoesNotExist()
        compose.onNodeWithText("3.75 XOR").assertIsDisplayed()
    }
    @Test fun welcomePageRoutesInviteAndEnterLinkAndUpdatesReferrerLabel() {
        val state = mutableStateOf(program())
        show { ReferralWelcomePageScreen(state.value, { events += "invite" }, { events += "referrer" }) }
        node(R.string.referral_start_inviting).performScrollTo().performClick()
        node(R.string.referral_enter_link_title).performScrollTo().performClick()
        compose.runOnIdle { state.value = program().copy(common = common().copy(referrer = referrer)) }
        node(R.string.referral_enter_link_title).assertDoesNotExist()
        node(R.string.referral_your_referrer).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("invite", "referrer", "referrer"), events) }
    }
    @Test fun programOnlyShowsRewardHistoryWhenThereAreReferrals() {
        val state = mutableStateOf(program())
        show { ReferralProgramPage(state.value, {}, {}, {}, { events += "header" }, {}) }
        node(R.string.referral_your_referrals).assertDoesNotExist()
        compose.runOnIdle { state.value = program().copy(referralInvitationsCardState = invitations().copy(
            referrals = ReferralsCardState(listOf(ReferralModel("Alice", "1.25 XOR")), "1.25 XOR", true))) }
        compose.onNodeWithText("Alice").performScrollTo().assertIsDisplayed()
        node(R.string.referral_your_referrals).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("header"), events) }
    }
}
