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
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Actual supply/review/slippage destinations; all estimation and transaction actions remain inert. */
@RunWith(AndroidJUnit4::class)
class LiquidityRemovalDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun input(symbol: String, amount: String) = AssetAmountInputState(
        Token(symbol, symbol, symbol, 18, true, null, 1.0, 0.0, "$"), "100 $symbol", BigDecimal(amount), amount, true)
    private val state = mutableStateOf(LiquidityRemoveState(ButtonState("Review withdrawal", true), 0.5, input("XOR", "2.5"), input("VAL", "5"),
        LiquidityRemoveEstimatedState("XOR", "2.5", "VAL", "5", "10%"),
        LiquidityRemovePricesState("XOR/VAL", "2", "VAL/XOR", "0.5", "12%", "0.01 XOR"),
        LiquidityRemoveConfirmState("Review the exact amounts", null, ButtonState("Confirm withdrawal", true)),
        hintVisible = false, shouldTransactionReminderInsufficientWarningBeShown = false, poolInFarming = false))
    private val readOnly = mutableStateOf(false)
    private val vm = mockk<LiquidityRemoveViewModel>(relaxed = true) {
        every { removeState } answers { state.value }
    }
    private lateinit var nav: NavHostController
    private fun show(route: String = LiquidityRemoveRoutes.start) {
        val fragment = LiquidityRemoveFragment()
        LiquidityRemoveFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
            .set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
            nav = rememberNavController()
            val scroll = rememberScrollState()
            NavHost(nav, startDestination = route) { with(fragment) { content(scroll, nav) } }
        } } }
    }
    private fun click(text: String) = compose.onNodeWithText(text).performClick()
    @Test fun reviewNavigatesOnlyAfterReviewHandlerAndDoesNotSubmit() {
        show(); click("Review withdrawal")
        compose.runOnIdle { assertEquals(LiquidityRemoveRoutes.confirm, nav.currentDestination?.route)
            verify(exactly = 1) { vm.onReviewClick() }; verify(exactly = 0) { vm.onConfirmClick() } }
    }
    @Test fun confirmationShowsExactAmountsAndUsesSeparateHandler() {
        show(LiquidityRemoveRoutes.confirm)
        compose.onNodeWithText("Review the exact amounts").assertExists(); click("Confirm withdrawal")
        compose.runOnIdle { verify(exactly = 1) { vm.onConfirmClick() } }
    }
    @Test fun disabledReviewCannotNavigateToConfirmation() {
        state.value = state.value.copy(btnState = ButtonState("Review withdrawal", false)); show()
        compose.onNodeWithText("Review withdrawal").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(LiquidityRemoveRoutes.start, nav.currentDestination?.route); verify(exactly = 0) { vm.onReviewClick() } }
    }
    @Test fun readOnlyTransitionDisablesReviewOnSameDestination() {
        show(); compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Review withdrawal").assertIsNotEnabled()
        compose.runOnIdle { verify(exactly = 0) { vm.onReviewClick() } }
    }
    @Test fun readOnlyTransitionDisablesPendingConfirmation() {
        show(LiquidityRemoveRoutes.confirm); compose.runOnIdle { readOnly.value = true }
        compose.onNodeWithText("Confirm withdrawal").assertIsNotEnabled()
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
    @Test fun slippageActionOpensEditorWithoutSubmitting() {
        show(); click("0.5%")
        compose.runOnIdle { assertEquals(LiquidityRemoveRoutes.slippage, nav.currentDestination?.route); verify(exactly = 0) { vm.onConfirmClick() } }
    }
    @Test fun slippageDoneReturnsToSupplyAndPreservesExactValue() {
        show(); click("0.5%")
        compose.onNode(hasSetTextAction()).performTextReplacement("1.25")
        click(compose.activity.getString(R.string.common_done))
        compose.runOnIdle { assertEquals(LiquidityRemoveRoutes.start, nav.currentDestination?.route); verify(exactly = 1) { vm.slippageChanged(1.25) } }
    }
    @Test fun removalHintRequiresExplicitDismissal() {
        state.value = state.value.copy(hintVisible = true); show()
        compose.onNodeWithText(compose.activity.getString(R.string.remove_liquidity_info_text)).assertIsDisplayed()
        click(compose.activity.getString(R.string.common_ok))
        compose.runOnIdle { verify(exactly = 1) { vm.dismissHint() }; verify(exactly = 0) { vm.onConfirmClick() } }
    }
    @Test fun farmedPoolDisplaysWithdrawalWarning() {
        state.value = state.value.copy(poolInFarming = true); show()
        compose.onNodeWithText(compose.activity.getString(R.string.polkaswap_farming_pool_in_farming_hint)).assertExists()
    }
    @Test fun liveConfirmationStateCanDisableStaleReadyButton() {
        show(LiquidityRemoveRoutes.confirm)
        compose.runOnIdle { state.value = state.value.copy(confirm = state.value.confirm.copy(btnState = ButtonState("Confirm withdrawal", false))) }
        compose.onNodeWithText("Confirm withdrawal").assertIsNotEnabled()
    }
}
