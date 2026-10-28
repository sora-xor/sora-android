package jp.co.soramitsu.sora.history

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.PACKAGE_ID
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.*
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_impl.presentation.txdetails.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith

/** Actual transaction-detail graph, including compact-screen reachability; no external explorer. */
@RunWith(AndroidJUnit4::class)
class TransactionDetailsDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val txHash = "0x" + "ab".repeat(32)
    private val basic = BasicTxDetailsState(txHash, "public-block-hash", "public-sender", emptyList(),
        TransactionStatus.COMMITTED, "27 Oct 2026", "0.07 XOR", "$0.07", R.drawable.ic_arrow_down_24, "Fixture operation")
    private val screenState = MutableStateFlow(TxDetailsScreenState(basic, "1.25 XOR", "2.50 VAL", "$1.25",
        DEFAULT_ICON_URI, DEFAULT_ICON_URI, DEFAULT_ICON_URI, true, TxType.LIQUIDITY))
    private val vm = mockk<TxDetailsViewModel>(relaxed = true) { every { txDetailsScreenState } returns screenState }
    private fun show(type: TxType = TxType.LIQUIDITY, fontScale: Float = 1f) {
        screenState.value = screenState.value.copy(txType = type)
        val fragment = TxDetailsFragment()
        TxDetailsFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.size(320.dp, 520.dp)) {
                    val nav = rememberNavController(); val scroll = rememberScrollState()
                    NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
                }
            }
        } }
    }
    private fun amounts(type: TxType) {
        show(type); compose.onNodeWithText("1.25 XOR").assertExists(); compose.onNodeWithText("2.50 VAL").assertExists()
    }
    @Test fun liquidityShowsBothExactAmounts() = amounts(TxType.LIQUIDITY)
    @Test fun swapShowsBothExactAmounts() = amounts(TxType.SWAP)
    @Test fun farmingShowsBothExactAmounts() {
        show(TxType.DEMETER); compose.onNodeWithText("1.25 XOR 2.50 VAL").assertExists()
    }
    @Test fun transferShowsOnlyItsTransferredAmount() {
        show(TxType.REFERRAL_TRANSFER); compose.onNodeWithText("1.25 XOR").assertExists(); compose.onNodeWithText("2.50 VAL").assertDoesNotExist()
    }
    @Test fun compactLiquidityDetailsKeepCloseReachable() {
        show(); compose.onNodeWithTag("$PACKAGE_ID:id/CloseButton").performScrollTo().performClick(); compose.runOnIdle { verify(exactly = 1) { vm.onBackPressed() } }
    }
    @Test fun largeTextFarmingDetailsKeepCloseReachable() {
        show(TxType.DEMETER, 2f); compose.onNodeWithTag("$PACKAGE_ID:id/CloseButton").performScrollTo().performClick(); compose.runOnIdle { verify(exactly = 1) { vm.onBackPressed() } }
    }
    @Test fun copyingTransactionUsesUnshortenedHash() {
        show(); compose.onNodeWithText(compose.activity.getString(R.string.transaction_id)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onCopyClicked(txHash) } }
    }
    @Test fun copyingBlockUsesBlockIdentity() {
        show(); compose.onNodeWithText(compose.activity.getString(R.string.block_id)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onCopyClicked("public-block-hash") } }
    }
    @Test fun unavailableBlockNeverCopiesAnEmptyIdentity() {
        screenState.value = screenState.value.copy(basicTxDetailsState = basic.copy(blockHash = null)); show()
        compose.onNodeWithText(compose.activity.getString(R.string.block_id)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 0) { vm.onCopyClicked(any()) } }
    }
    @Test fun copyingSenderUsesSenderRatherThanTransaction() {
        show(); compose.onNodeWithText(compose.activity.getString(R.string.common_sender)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onCopyClicked("public-sender") } }
    }
    @Test fun stateReplacementRemovesPreviousOperationAmounts() {
        show(); compose.runOnIdle { screenState.value = screenState.value.copy(txType = TxType.REFERRAL_TRANSFER, amount1 = "3 PSWAP", amount2 = null, icon2 = null, icon3 = null) }
        compose.onNodeWithText("1.25 XOR").assertDoesNotExist(); compose.onNodeWithText("2.50 VAL").assertDoesNotExist(); compose.onNodeWithText("3 PSWAP").assertExists()
    }
    @Test fun pendingExplanationAndOptionalFeeFollowLatestState() {
        screenState.value = screenState.value.copy(basicTxDetailsState = basic.copy(networkFee = null, statusNote = "Waiting for finality; do not resend"))
        show(); compose.onNodeWithTag("$PACKAGE_ID:id/TransactionStatusNote").assertTextEquals("Waiting for finality; do not resend")
        compose.onNodeWithText("0.07 XOR").assertDoesNotExist(); compose.onNodeWithTag("$PACKAGE_ID:id/ViewOnExplorerButton").assertDoesNotExist()
    }
}
