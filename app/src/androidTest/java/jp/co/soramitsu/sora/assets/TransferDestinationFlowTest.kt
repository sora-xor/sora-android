@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.assets

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.*
import jp.co.soramitsu.feature_assets_impl.presentation.states.SendState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real send/review graph with inert transfer actions; no key, signer or network operation. */
@RunWith(AndroidJUnit4::class)
class TransferDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val address = "public-recipient-fixture"
    private val state = MutableStateFlow(SendState(address, ColorDrawable(Color.BLUE), reviewEnabled = true,
        input = AssetAmountInputState(Token("xor", "XOR", "SORA", 18, true, null, 1.0, 0.0, "$"),
            "100 XOR", BigDecimal("1.25"), "$1.25", true), fee = "0.007 XOR", feeFiat = "$0.007", feeLoading = false))
    private val readOnly = mutableStateOf(false)
    private val vm = mockk<TransferAmountViewModel>(relaxed = true) { every { sendState } returns state }
    private lateinit var nav: NavHostController
    @Before fun setup() { WalletRecoveryCapabilityGate.enterNormal() }
    @After fun cleanup() { WalletRecoveryCapabilityGate.enterNormal() }
    private fun show(route: String = SendRoutes.start) {
        val fragment = TransferAmountFragment()
        TransferAmountFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) {
            nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = route) { with(fragment) { content(scroll, nav) } }
        } } }
    }
    private fun button(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    @Test fun reviewShowsConfirmationWithoutSubmitting() {
        show(); button(R.string.review).performClick()
        compose.runOnIdle { assertEquals(SendRoutes.confirm, nav.currentDestination?.route)
            verify(exactly = 1) { vm.onReviewClick() }; verify(exactly = 0) { vm.onConfirmClick() } }
    }
    @Test fun confirmInvokesOnlyExplicitSubmissionHandler() {
        show(SendRoutes.confirm); button(R.string.common_confirm).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onConfirmClick() }; verify(exactly = 0) { vm.onReviewClick() } }
    }
    @Test fun amountEntryPreservesExactDecimal() {
        show(); compose.onNode(hasSetTextAction()).performTextReplacement("0.00000001")
        compose.runOnIdle { verify(exactly = 1) { vm.amountChanged(match { it.compareTo(BigDecimal("0.00000001")) == 0 }) } }
    }
    @Test fun recipientTapReturnsToRecipientSelection() {
        show(); compose.onNodeWithText(address).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onBackPressed() }; verify(exactly = 0) { vm.copyAddress() } }
    }
    @Test fun recipientLongPressCopiesOnlyTheAddress() {
        show(); compose.onNodeWithText(address).performTouchInput { longClick() }
        compose.runOnIdle { verify(exactly = 1) { vm.copyAddress() }; verify(exactly = 0) { vm.onBackPressed() } }
    }
    @Test fun confirmationAddressTapCopiesWithoutChangingRecipient() {
        show(SendRoutes.confirm); compose.onNodeWithText(address).performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.copyAddress() }; verify(exactly = 0) { vm.onBackPressed() } }
    }
    @Test fun unavailableReviewCannotAdvance() {
        state.value = state.value.copy(reviewEnabled = false); show(); button(R.string.review).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(SendRoutes.start, nav.currentDestination?.route); verify(exactly = 0) { vm.onReviewClick() } }
    }
    @Test fun readOnlyAccountTransitionDisablesReview() {
        show(); compose.runOnIdle { readOnly.value = true }; button(R.string.review).assertIsNotEnabled()
    }
    @Test fun readOnlyAccountTransitionDisablesConfirmation() {
        show(SendRoutes.confirm); compose.runOnIdle { readOnly.value = true }; button(R.string.common_confirm).assertIsNotEnabled()
    }
    @Test fun staleReadyConfirmationRespondsToBalanceChange() {
        show(SendRoutes.confirm); compose.runOnIdle { state.value = state.value.copy(reviewEnabled = false) }
        button(R.string.common_confirm).assertIsNotEnabled()
    }
    @Test fun liveRecipientChangeUpdatesConfirmation() {
        show(SendRoutes.confirm); compose.runOnIdle { state.value = state.value.copy(address = "replacement-recipient") }
        compose.onNodeWithText(address).assertDoesNotExist(); compose.onNodeWithText("replacement-recipient").assertExists()
    }
    @Test fun lockedBalanceExplanationUsesCurrentState() {
        show(); compose.runOnIdle { state.value = state.value.copy(lockedNote = "9 XOR is locked by staking") }
        compose.onNodeWithText("9 XOR is locked by staking").assertExists()
    }
    @Test fun confirmationShowsExactNetworkFee() {
        show(SendRoutes.confirm); compose.onNodeWithText("0.007 XOR").assertExists(); compose.onNodeWithText("$0.007").assertExists()
    }
}
