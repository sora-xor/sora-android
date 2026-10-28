@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.referral

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_referral_impl.presentation.*
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.component.wrappedtext.WrappedTextState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The fragment's actual destination graph and callbacks; transaction and share boundaries stay inert. */
@RunWith(AndroidJUnit4::class)
class ReferralDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val referrer = "public-fixture-referrer"
    private val link = "https://polkaswap.io/#/referral/$referrer"
    private val state = MutableStateFlow(ReferralProgramState(
        ReferralCommonState(null, true, false, "0.005 XOR", "0.002 XOR", "$0.01"),
        ReferralBondState(true, 2, "0.010 XOR", "100 XOR"), InputTextState(),
        ReferralInvitationsCardState("Available invitations", 2, WrappedTextState(title = "Invitation link", text = link),
            "0.010 XOR", ReferralsCardState(listOf(ReferralModel("Alice", "1.25 XOR")), "1.25 XOR", true))))
    private val readOnly = mutableStateOf(false)
    private val vm = mockk<ReferralViewModel>(relaxed = true) {
        every { referralScreenState } returns state
    }
    private lateinit var nav: NavHostController
    private fun show(route: String = ReferralFeatureRoutes.WELCOME_PAGE) {
        val fragment = ReferralFragment()
        ReferralFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
            .set(fragment, lazyOf(vm))
        compose.setContent {
            SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
                nav = rememberNavController()
                val scroll = rememberScrollState()
                NavHost(nav, startDestination = route) { with(fragment) { content(scroll, nav) } }
            } }
        }
    }
    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick()
    private fun destination(expected: String) = compose.runOnIdle { assertEquals(expected, nav.currentDestination?.route) }
    private fun navigate(route: String) = compose.runOnIdle { nav.navigate(route) }

    @Test fun welcomeStartsBondFormWithoutSubmitting() {
        show(); click(R.string.referral_start_inviting); destination(ReferralFeatureRoutes.BOND_XOR)
        compose.runOnIdle { verify(exactly = 1) { vm.openBond() }; verify(exactly = 0) { vm.onBondButtonClick() } }
    }
    @Test fun welcomeOpensReferrerInputWithoutActivating() {
        show(); click(R.string.referral_enter_link_title); destination(ReferralFeatureRoutes.REFERRER_INPUT)
        compose.runOnIdle { verify(exactly = 1) { vm.openReferrerInput() }; verify(exactly = 0) { vm.onActivateLinkClick() } }
    }
    @Test fun programGetMoreOpensBondForm() {
        show(ReferralFeatureRoutes.REFERRAL_PROGRAM); click(R.string.referral_get_more_invitation_button_title)
        destination(ReferralFeatureRoutes.BOND_XOR); compose.runOnIdle { verify(exactly = 1) { vm.openBond() } }
    }
    @Test fun programUnbondOpensWithdrawalForm() {
        show(ReferralFeatureRoutes.REFERRAL_PROGRAM); click(R.string.referral_unbond_button_title)
        destination(ReferralFeatureRoutes.UNBOND_XOR); compose.runOnIdle { verify(exactly = 1) { vm.openUnbond() } }
    }
    @Test fun programEnterLinkUsesReferrerRoute() {
        show(ReferralFeatureRoutes.REFERRAL_PROGRAM); click(R.string.referral_enter_link_title)
        destination(ReferralFeatureRoutes.REFERRER_INPUT); compose.runOnIdle { verify(exactly = 1) { vm.openReferrerInput() } }
    }
    @Test fun programShareUsesViewModelBoundary() {
        show(ReferralFeatureRoutes.REFERRAL_PROGRAM); compose.onNodeWithText(link).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onShareLink() } }; destination(ReferralFeatureRoutes.REFERRAL_PROGRAM)
    }
    @Test fun programRewardsHeaderTogglesOnlyRewardCard() {
        show(ReferralFeatureRoutes.REFERRAL_PROGRAM); click(R.string.referral_your_referrals)
        compose.runOnIdle { verify(exactly = 1) { vm.toggleReferralsCard() } }; destination(ReferralFeatureRoutes.REFERRAL_PROGRAM)
    }
    @Test fun bondEditsAndSteppersReachCorrectHandlers() {
        show(ReferralFeatureRoutes.BOND_XOR)
        compose.onNode(hasSetTextAction()).performTextReplacement("7")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.common_remove)).performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.referral_add_invitations_title)).performClick()
        compose.runOnIdle { verify { vm.onBondValueChange(7); vm.onBondMinus(); vm.onBondPlus() } }
    }
    @Test fun bondRequiresExplicitConfirmation() {
        show(ReferralFeatureRoutes.BOND_XOR)
        compose.runOnIdle { verify(exactly = 0) { vm.onBondButtonClick() } }
        click(R.string.referral_bond_button_title)
        compose.runOnIdle { verify(exactly = 1) { vm.onBondButtonClick() } }
    }
    @Test fun bondLosesPermissionWhenAccountBecomesReadOnly() {
        show(ReferralFeatureRoutes.BOND_XOR); compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText(compose.activity.getString(R.string.referral_bond_button_title)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { verify(exactly = 0) { vm.onBondButtonClick() } }
    }
    @Test fun unbondEditsAndSteppersReachWithdrawalHandlers() {
        show(ReferralFeatureRoutes.UNBOND_XOR)
        compose.onNode(hasSetTextAction()).performTextReplacement("1")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.common_remove)).performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.referral_add_invitations_title)).performClick()
        compose.runOnIdle { verify { vm.onUnbondValueChange(1); vm.onUnbondMinus(); vm.onUnbondPlus() } }
    }
    @Test fun unbondRequiresExplicitConfirmation() {
        show(ReferralFeatureRoutes.UNBOND_XOR); click(R.string.referral_unbond_button_title)
        compose.runOnIdle { verify(exactly = 1) { vm.onUnbondButtonClick() }; verify(exactly = 0) { vm.onBondButtonClick() } }
    }
    @Test fun referrerEntryForwardsExactTypedLink() {
        show(ReferralFeatureRoutes.REFERRER_INPUT); compose.onNode(hasSetTextAction()).performTextReplacement(link)
        compose.runOnIdle { verify(exactly = 1) { vm.onReferrerInputChange(match { it.text == link }) } }
    }
    @Test fun referrerActivationUsesCurrentRouteHandler() {
        show(ReferralFeatureRoutes.REFERRER_INPUT); click(R.string.referral_activate_button_title)
        compose.runOnIdle { verify(exactly = 1) { vm.onActivateLinkClick() } }
    }
    @Test fun readOnlyReferrerCannotActivateEvenWhenFormIsReady() {
        show(ReferralFeatureRoutes.REFERRER_INPUT); compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText(compose.activity.getString(R.string.referral_activate_button_title)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { verify(exactly = 0) { vm.onActivateLinkClick() } }
    }
    @Test fun filledReferrerClosesToProgramForInitializedWallet() {
        show(ReferralFeatureRoutes.REFERRAL_PROGRAM); navigate(ReferralFeatureRoutes.REFERRER_FILLED)
        click(R.string.common_close); destination(ReferralFeatureRoutes.REFERRAL_PROGRAM)
    }
    @Test fun filledReferrerClosesToWelcomeForUninitializedWallet() {
        state.value = state.value.copy(referralInvitationsCardState = state.value.referralInvitationsCardState.copy(
            invitationsCount = 0, referrals = ReferralsCardState()))
        show(); navigate(ReferralFeatureRoutes.REFERRER_FILLED); click(R.string.common_close)
        destination(ReferralFeatureRoutes.WELCOME_PAGE)
    }
    @Test fun backDuringBondUsesFragmentViewModelNavigation() {
        show(ReferralFeatureRoutes.BOND_XOR)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { verify(exactly = 1) { vm.onBackPressed() } }
    }
    @Test fun progressRouteDoesNotExposeSubmissionControls() {
        show(ReferralFeatureRoutes.WELCOME_PROGRESS)
        compose.onNodeWithText(compose.activity.getString(R.string.referral_bond_button_title)).assertDoesNotExist()
        compose.runOnIdle { verify(exactly = 0) { vm.onBondButtonClick(); vm.onUnbondButtonClick(); vm.onActivateLinkClick() } }
    }
}
