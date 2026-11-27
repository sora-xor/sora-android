@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.liquidity

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.presentation.compose.neon.NeonBackground
import jp.co.soramitsu.sora.testutil.NativeDisplayCapture
import java.io.File
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap.*
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class SwapNavigationContractTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val vm = mockk<SwapViewModel>(relaxed = true)
    private lateinit var nav: NavHostController
    private val disclaimer = SingleLiveEvent<Unit>()
    private fun input(symbol: String, amount: String) = AssetAmountInputState(Token(symbol, symbol, symbol, 18, true, null, 1.0, 0.0, "$"), "100 $symbol", BigDecimal(amount), amount, true)
    private val state = MutableStateFlow(SwapMainState(input("XOR", "2.5"), input("VAL", "5"), 0.5, Market.XYK, Market.XYK to listOf(Market.XYK, Market.SMART),
        defaultSwapDetailsState().copy(transactionFee = "0.01 XOR", minmaxValue = "4.95 VAL", route = "XOR → VAL"),
        ButtonState("Review swap", true), ButtonState("Confirm swap", true), AnnotatedString("Review the exact swap"), null))
    @Before fun setup() {
        every { vm.swapMainState } returns state
        every { vm.navigationDisclaimerEvent } returns disclaimer
        val fragment = spyk(SwapFragment()) { every { getViewLifecycleOwner() } returns compose.activity }
        fragment.debounceClickHandler = DebounceClickHandler()
        SwapFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme { nav = rememberNavController(); val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
                NeonBackground()
                NavHost(nav, startDestination = SwapRoutes.start, modifier = Modifier.systemBarsPadding()) {
                    with(fragment) { content(scroll, nav) }
                }
            }
        } }
    }
    @Test fun selectedTokensAndAmountsUseTheWidthOfBothLabeledFields() {
        val context = compose.activity
        val pay = input("CERES", "0.12").copy(balance = "0.468", amountFiat = "")
        val receive = input("XOR", "0.25").let {
            it.copy(token = it.token.copy(id = AssetHolder.XOR_ASSET_ID), balance = "0.899", amountFiat = "")
        }
        compose.runOnIdle {
            state.value = state.value.copy(
                tokenFromState = pay,
                tokenToState = receive,
                swapButtonState = ButtonState(context.getString(R.string.review), true),
                details = defaultSwapDetailsState(),
            )
        }
        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals(2, fields.size)
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        listOf(R.string.swap_pay_label to pay, R.string.swap_receive_label to receive).forEachIndexed { index, (label, amount) ->
            val caption = compose.onNodeWithText(context.getString(label)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val number = fields[index].boundsInRoot
            compose.onNodeWithText(amount.token.symbol).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.asset_balance_value, amount.balance)).assertIsDisplayed()
            assertTrue("Amount must remain inside the screen", number.right <= width)
            assertTrue("The labelled field must span the phone", number.right - caption.left > width * 0.75f)
            assertTrue("The amount needs space of its own", number.width > width * 0.28f)
        }
        if (InstrumentationRegistry.getArguments().getString("swapCapture") == "true") {
            val file = File(context.getExternalFilesDir(null), "fixture-swap-pair.png")
            val bitmap = NativeDisplayCapture.capture()
            try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }
    @Test fun reviewingTheQuoteDoesNotConfirmItUntilTheSecondExplicitAction() {
        compose.onNodeWithText("Review swap").performClick()
        compose.onNodeWithText("Review the exact swap").assertExists()
        verify(exactly = 1) { vm.swapClicked() }; verify(exactly = 0) { vm.onConfirmClicked() }
        compose.onNodeWithText("Confirm swap").performClick()
        verify(exactly = 1) { vm.onConfirmClicked() }
        compose.runOnIdle { assertEquals(SwapRoutes.confirm, nav.currentDestination!!.route) }
    }
    @Test fun slippageSelectionReturnsToTheQuoteWithTheEnteredValue() {
        compose.onNodeWithText("0.5%").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.polkaswap_slippage_info)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.common_done), ignoreCase = true).performClick()
        verify(exactly = 1) { vm.onSlippageClick(); vm.slippageChanged(0.5) }
        compose.onNodeWithText("Review swap").assertExists()
    }
    @Test fun marketSelectionReturnsTheExactChosenMarketToTheViewModel() {
        compose.onNodeWithText(compose.activity.getString(Market.XYK.titleResource)).performClick()
        compose.onNodeWithText(compose.activity.getString(Market.SMART.titleResource)).performClick()
        verify(exactly = 1) { vm.onMarketClick(); vm.onMarketSelected(Market.SMART) }
        compose.onNodeWithText("Review swap").assertExists()
    }
    @Test fun nullMarketOptionsDoNotInventASelection() {
        compose.runOnIdle { state.value = state.value.copy(selectMarketState = null); nav.navigate(SwapRoutes.markets) }
        compose.onNodeWithText(compose.activity.getString(Market.SMART.titleResource)).assertDoesNotExist()
        verify(exactly = 0) { vm.onMarketSelected(any()) }
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Review swap").assertExists()
    }
}
