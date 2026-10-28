@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.assets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common_wallet.presentation.compose.states.*
import jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails.AssetDetailsScreen
import jp.co.soramitsu.feature_assets_impl.presentation.states.*
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetDetailsInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val events = mutableListOf<String>()
    private val readOnly = mutableStateOf(false)
    private val txHash = "0x" + "ab".repeat(32)
    private fun state() = AssetCardStateData(
        tokenId = "xor-token-id", tokenName = "SORA", tokenIcon = "", tokenSymbol = "XOR", price = "$2.00", priceChange = "+4%",
        transferableBalance = "12.34567890123456789 XOR", transferableBalanceFiat = "$24.69", frozenBalance = "3 XOR", frozenBalanceFiat = "$6.00",
        poolsCardTitle = "My XOR pools", poolsState = PoolsListState(emptyList()), poolsSum = "$10.00",
        isTransferableBalanceAvailable = true, hasTokens = true, events = emptyList(), lockedNote = "3 XOR is locked for invitations")
    private fun node(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun show(current: androidx.compose.runtime.State<AssetCardStateData>) = compose.setContent {
        SoraAppTheme { CompositionLocalProvider(LocalSelectedAccountReadOnly provides readOnly.value) { Column {
            AssetDetailsScreen(current.value, rememberScrollState(), { events += "balance" }, { events += "send" },
                { events += "receive" }, { events += "swap" }, { events += "pool:${it.first}:${it.second}" },
                { events += "more" }, { events += "tx:$it" }, { events += "id" })
        } } }
    }
    @Before fun setup() { WalletRecoveryCapabilityGate.enterNormal() }
    @After fun cleanup() { WalletRecoveryCapabilityGate.enterNormal() }

    @Test fun exactBalancesPricesAndLockedReasonStayVisible() {
        show(mutableStateOf(state()))
        for (text in listOf("SORA", "XOR", "$2.00", "+4%", "12.34567890123456789 XOR", "$24.69", "3 XOR", "$6.00", "3 XOR is locked for invitations")) {
            compose.onNodeWithText(text).assertExists()
        }
    }
    @Test fun balanceActionsAndAssetIdentifierRouteIndependently() {
        show(mutableStateOf(state()))
        node(R.string.common_send).performScrollTo().performClick()
        node(R.string.common_receive).performScrollTo().performClick()
        node(R.string.polkaswap_swap_title).performScrollTo().performClick()
        compose.onNodeWithText("12.34567890123456789 XOR").performScrollTo().performClick()
        compose.onNodeWithText("xor-token-id").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("send", "receive", "swap", "balance", "id"), events) }
    }
    @Test fun zeroTransferableBalanceHidesSendButKeepsReceive() {
        show(mutableStateOf(state().copy(isTransferableBalanceAvailable = false, transferableBalance = "0 XOR", transferableBalanceFiat = "$0.00")))
        node(R.string.common_send).assertDoesNotExist()
        node(R.string.common_receive).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("receive"), events) }
    }
    @Test fun switchingToReadOnlyAccountDisablesSendAndSwapButKeepsReceive() {
        show(mutableStateOf(state()))
        compose.runOnIdle { readOnly.value = true }
        node(R.string.common_send).performScrollTo().assertIsNotEnabled()
        node(R.string.polkaswap_swap_title).assertIsNotEnabled()
        node(R.string.common_receive).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("receive"), events) }
    }
    @Test fun recoveryModeDisablesSigningActionsInAnAlreadyVisibleScreen() {
        show(mutableStateOf(state()))
        compose.runOnIdle { WalletRecoveryCapabilityGate.enterBlocked(); WalletRecoveryCapabilityGate.enterLegacyReadOnly() }
        node(R.string.common_send).performScrollTo().assertIsNotEnabled()
        node(R.string.polkaswap_swap_title).assertIsNotEnabled()
        node(R.string.common_receive).assertIsEnabled()
    }
    @Test fun emptiedPositionsRemoveTheirOldPoolAndHistoryCards() {
        val transaction = EventUiModel.EventTxUiModel.EventTransferInUiModel(txHash, 1_800_000_000_000, TransactionStatus.COMMITTED,
            "", "sender-address", "10:15", "1 XOR", "$2.00")
        val pool = PoolsListItemState("", "", "XOR", "VAL", "2 XOR - 5 VAL", "$14.00", "+1%", "xor" to "val", emptyList())
        val current = mutableStateOf(state().copy(poolsState = PoolsListState(listOf(pool)), events = listOf(transaction)))
        show(current)
        compose.onNodeWithText("2 XOR - 5 VAL").performScrollTo().performClick()
        compose.onNodeWithText("1 XOR").performScrollTo().performClick()
        node(R.string.show_more).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("pool:xor:val", "tx:$txHash", "more"), events); current.value = state() }
        compose.onNodeWithText("My XOR pools").assertDoesNotExist()
        compose.onNodeWithText("1 XOR").assertDoesNotExist()
        node(R.string.show_more).assertDoesNotExist()
    }
    @Test fun clearedFrozenBalanceRemovesStaleAmountsAndReason() {
        val current = mutableStateOf(state())
        show(current)
        compose.runOnIdle { current.value = state().copy(frozenBalance = null, frozenBalanceFiat = null, lockedNote = null) }
        compose.onNodeWithText("3 XOR").assertDoesNotExist()
        compose.onNodeWithText("$6.00").assertDoesNotExist()
        compose.onNodeWithText("3 XOR is locked for invitations").assertDoesNotExist()
    }
    @Test fun frozenBalanceBreakdownKeepsEachCategoryAndAmountDistinct() {
        val details = FrozenXorDetailsModel("15 XOR", "$30", "1 XOR", "$2", "2 XOR", "$4", "3 XOR", "$6", "4 XOR", "$8", "5 XOR", "$10")
        show(mutableStateOf(state().copy(xorBalance = details, frozenBalance = null, frozenBalanceFiat = null)))
        for (text in listOf("15 XOR", "1 XOR", "2 XOR", "3 XOR", "4 XOR", "5 XOR", "$30", "$2", "$4", "$6", "$8", "$10")) {
            compose.onNodeWithText(text).assertExists()
        }
    }
}
