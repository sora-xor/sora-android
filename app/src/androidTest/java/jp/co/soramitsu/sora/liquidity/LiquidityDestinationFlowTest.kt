@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package jp.co.soramitsu.sora.liquidity

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
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Actual supply/review/slippage destinations; all estimation and transaction actions remain inert. */
@RunWith(AndroidJUnit4::class)
class LiquidityDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun input(symbol: String, amount: String) = AssetAmountInputState(
        Token(symbol, symbol, symbol, 18, true, null, 1.0, 0.0, "$"), "100 $symbol", BigDecimal(amount), amount, true)
    private val state = MutableStateFlow(LiquidityAddState(ButtonState("Review supply", true), input("XOR", "2.5"), input("VAL", "5"),
        LiquidityAddEstimatedState("XOR", "2.5", "VAL", "5", "10%"),
        LiquidityAddPricesState("XOR/VAL", "2", "VAL/XOR", "0.5", "12%", "0.01 XOR"),
        LiquidityAddConfirmState("Review the exact amounts", null, ButtonState("Confirm supply", true)),
        pairNotExist = false, hintVisible = false, shouldTransactionReminderInsufficientWarningBeShown = false))
    private val slippage = MutableStateFlow(0.5)
    private val readOnly = mutableStateOf(false)
    private val vm = mockk<LiquidityAddViewModel>(relaxed = true) {
        every { addState } returns state
        every { stateSlippage } returns slippage
    }
    private lateinit var nav: NavHostController
    private fun show(route: String = LiquidityAddRoutes.start) {
        val fragment = LiquidityAddFragment()
        LiquidityAddFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
            .set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
            nav = rememberNavController()
            val scroll = rememberScrollState()
            NavHost(nav, startDestination = route) { with(fragment) { content(scroll, nav) } }
        } } }
    }
    private fun click(text: String) = compose.onNodeWithText(text).performClick()
    @Test fun reviewNavigatesOnlyAfterReviewHandlerAndDoesNotSubmit() {
        show(); click("Review supply")
        compose.runOnIdle { assertEquals(LiquidityAddRoutes.confirm, nav.currentDestination?.route)
            verify(exactly = 1) { vm.onReviewClick() }; verify(exactly = 0) { vm.onConfirmClick() } }
    }
    @Test fun confirmationShowsExactAmountsAndUsesSeparateHandler() {
        show(LiquidityAddRoutes.confirm)
        compose.onNodeWithText("Review the exact amounts").assertExists(); click("Confirm supply")
        compose.runOnIdle { verify(exactly = 1) { vm.onConfirmClick() } }
    }
    @Test fun disabledReviewCannotNavigateToConfirmation() {
        state.value = state.value.copy(btnState = ButtonState("Review supply", false)); show()
        compose.onNodeWithText("Review supply").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(LiquidityAddRoutes.start, nav.currentDestination?.route); verify(exactly = 0) { vm.onReviewClick() } }
    }
    @Test fun readOnlyTransitionDisablesReviewOnSameDestination() {
        show(); compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Review supply").assertIsNotEnabled()
        compose.runOnIdle { verify(exactly = 0) { vm.onReviewClick() } }
    }
    @Test fun readOnlyTransitionDisablesPendingConfirmation() {
        show(LiquidityAddRoutes.confirm); compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Confirm supply").assertIsNotEnabled()
        compose.runOnIdle { verify(exactly = 0) { vm.onConfirmClick() } }
    }
    @Test fun amountOneEditsRouteToFirstAsset() {
        show(); compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("3.125")
        compose.runOnIdle { verify(exactly = 1) { vm.onAmount1Change(match { it.compareTo(BigDecimal("3.125")) == 0 }) }
            verify(exactly = 0) { vm.onAmount2Change(any()) } }
    }
    @Test fun amountTwoEditsRouteToSecondAsset() {
        show(); compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("7.25")
        compose.runOnIdle { verify(exactly = 1) { vm.onAmount2Change(match { it.compareTo(BigDecimal("7.25")) == 0 }) }
            verify(exactly = 0) { vm.onAmount1Change(any()) } }
    }
    @Test fun focusingFirstAmountSelectsItsEstimationDirection() {
        show(); compose.onAllNodes(hasSetTextAction())[0].performClick()
        compose.runOnIdle { verify(atLeast = 1) { vm.onAmount1Focused() } }
    }
    @Test fun focusingSecondAmountSelectsItsEstimationDirection() {
        show(); compose.onAllNodes(hasSetTextAction())[1].performClick()
        compose.runOnIdle { verify(atLeast = 1) { vm.onAmount2Focused() } }
    }
    @Test fun slippageActionOpensEditorAndSignalsViewModel() {
        show(); click("0.5%")
        compose.runOnIdle { assertEquals(LiquidityAddRoutes.slippage, nav.currentDestination?.route); verify(exactly = 1) { vm.onSlippageClick() } }
    }
    @Test fun slippageDoneReturnsToSupplyAndPreservesExactValue() {
        show(); click("0.5%")
        compose.onNode(hasSetTextAction()).performTextReplacement("1.25")
        click(compose.activity.getString(R.string.common_done))
        compose.runOnIdle { assertEquals(LiquidityAddRoutes.start, nav.currentDestination?.route); verify(exactly = 1) { vm.slippageChanged(1.25) } }
    }
    @Test fun newPairHintRequiresExplicitDismissal() {
        state.value = state.value.copy(hintVisible = true, pairNotExist = true); show()
        compose.onNodeWithText(compose.activity.getString(R.string.add_liquidity_alert_text)).assertIsDisplayed()
        click(compose.activity.getString(R.string.common_ok))
        compose.runOnIdle { verify(exactly = 1) { vm.dismissHint() }; verify(exactly = 0) { vm.onConfirmClick() } }
    }
    @Test fun liveConfirmationStateCanDisableStaleReadyButton() {
        show(LiquidityAddRoutes.confirm)
        compose.runOnIdle { state.value = state.value.copy(confirm = state.value.confirm.copy(btnState = ButtonState("Confirm supply", false))) }
        compose.onNodeWithText("Confirm supply").assertIsNotEnabled()
    }
}
