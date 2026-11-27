@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.history

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryListItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel.EventTxUiModel.*
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every transaction type preserves displayed amounts and routes taps to its own exact hash. */
@RunWith(AndroidJUnit4::class)
class TransactionRowInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val hash = "0x" + "ab".repeat(32)
    private val peer = "cnT65V7vJnPkR2Y2AxXwV1Bpcbab7vNaHGd3ZxXjrTpPuxvj2"
    private val now = 1_800_000_000_000L
    private fun checkRows(amounts: List<String>, make: (TransactionStatus) -> EventUiModel.EventTxUiModel) {
        val current = mutableStateOf<EventUiModel>(make(TransactionStatus.COMMITTED))
        val clicked = mutableListOf<String>()
        compose.setContent { SoraAppTheme { TxHistoryListItem(Modifier.testTag("row"), clicked::add, current.value) } }
        for (status in TransactionStatus.entries) {
            compose.runOnIdle { current.value = make(status) }
            for (amount in amounts) compose.onAllNodesWithText(amount, substring = true).onFirst().assertIsDisplayed()
            if (status != TransactionStatus.COMMITTED) {
                val label = compose.activity.getString(if (status == TransactionStatus.PENDING) R.string.status_pending else R.string.common_failed)
                compose.onNodeWithContentDescription(label).assertExists()
            }
            compose.onNodeWithTag("row").performClick()
        }
        compose.runOnIdle { assertEquals(List(3) { hash }, clicked) }
    }

    @Test fun receivedTransferKeepsAmountPeerAndOriginalHashForEveryStatus() = checkRows(listOf("2.5 XOR", peer)) {
        EventTransferInUiModel(hash, now, it, "", peer, "10:15", "2.5 XOR", "$5.00")
    }
    @Test fun sentTransferKeepsAmountPeerAndOriginalHashForEveryStatus() = checkRows(listOf("2.5 XOR", peer)) {
        EventTransferOutUiModel(hash, "", peer, "10:15", now, "2.5 XOR", "$5.00", it)
    }
    @Test fun bridgeTransferKeepsItsSoraHashInsteadOfItsRequestHash() = checkRows(listOf("2.5 XOR")) {
        EventEthTransfer(hash, now, it, "", "", "10:15", "2.5 XOR", "$5.00", "bridge-request", "0xrecipient")
    }
    @Test fun swapShowsBothAmountsAndOutputFiat() = checkRows(listOf("2.5 XOR", "5 VAL", "$5.00")) {
        EventLiquiditySwapUiModel(hash, "", "", "2.5 XOR", "5 VAL", "XOR", "VAL", "$5.00", "10:15", now, it)
    }
    @Test fun addedLiquidityShowsBothPoolAmounts() = checkRows(listOf("2.5 XOR", "5 VAL", "$5.00")) {
        EventLiquidityAddUiModel(hash, now, it, "10:15", "", "", "2.5 XOR", "5 VAL", "Added liquidity", "XOR", "VAL", "$5.00", true)
    }
    @Test fun removedLiquidityShowsBothPoolAmounts() = checkRows(listOf("2.5 XOR", "5 VAL", "$5.00")) {
        EventLiquidityAddUiModel(hash, now, it, "10:15", "", "", "2.5 XOR", "5 VAL", "Removed liquidity", "XOR", "VAL", "$5.00", false)
    }
    @Test fun referralBondRetainsAmountAndDescription() = checkRows(listOf("2.5 XOR", "Invitations bonded")) {
        EventReferralProgramUiModel(hash, now, it, "", R.string.referral_bond_button_title, "Invitations bonded", false, "10:15", "2.5 XOR")
    }
    @Test fun referralIncomeRetainsAmountAndDescription() = checkRows(listOf("2.5 XOR", "Referral reward")) {
        EventReferralProgramUiModel(hash, now, it, "", R.string.referral_bond_button_title, "Referral reward", true, "10:15", "2.5 XOR")
    }
    @Test fun farmingStakeRetainsPoolSymbolsAndHash() = checkRows(listOf("2.5", "XOR / VAL")) {
        EventDemeterStakeUiModel(hash, now, it, "", "", "", true, "2.5", "XOR / VAL")
    }
    @Test fun farmingWithdrawalRetainsPoolSymbolsAndHash() = checkRows(listOf("2.5", "XOR / VAL")) {
        EventDemeterStakeUiModel(hash, now, it, "", "", "", false, "2.5", "XOR / VAL")
    }
    @Test fun farmingRewardsRetainExactTokenAmount() = checkRows(listOf("1.23456789 PSWAP")) {
        EventDemeterRewardUiModel(hash, now, it, "", "1.23456789 PSWAP")
    }
    @Test fun adarIncomeRetainsExactTokenAmount() = checkRows(listOf("1.23456789 XOR")) {
        EventAdarIncomeUiModel(hash, now, it, "", "1.23456789 XOR", peer)
    }
    /**
     * A long amount on a 320 dp screen with text at twice its size, on a waiting entry (its mark takes room too), in a
     * card's 24 dp margins as Recent activity has them: every character laid out, none behind an ellipsis, no line
     * wider than the text's place nor below it, the place never zero wide and shown whole.
     */
    private fun assertLongAmountWhole(amount: String, row: EventUiModel) {
        compose.setContent {
            SoraAppTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp).padding(horizontal = 24.dp)) { TxHistoryListItem(Modifier, {}, row) }
                }
            }
        }
        val node = compose.onAllNodesWithText(amount, substring = true, useUnmergedTree = true).onFirst()
            .assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val text = layout.layoutInput.text
        val lines = layout.lineCount
        val seen = "\"$text\" in a ${node.size} place, $lines lines"
        assertTrue("the amount has room: $seen", node.size.width > 0)
        assertTrue("never cut with an ellipsis: $seen", (0 until lines).none(layout::isLineEllipsized))
        assertTrue("every character is laid out: $seen", layout.getLineEnd(lines - 1, visibleEnd = true) >= text.trimEnd().length)
        assertTrue(
            "no line is wider than its place: $seen",
            (0 until lines).all { layout.getLineRight(it) - layout.getLineLeft(it) <= node.size.width + 1f },
        )
        assertTrue("no line falls below its place: $seen", !layout.didOverflowHeight && layout.getLineBottom(lines - 1) <= node.size.height + 1f)
        val shown = node.boundsInRoot
        val origin = node.positionInRoot
        assertTrue(
            "the place shows whole, ${node.size} at $origin in $shown: $seen",
            origin.x >= shown.left - 1f && origin.x + node.size.width <= shown.right + 1f &&
                origin.y >= shown.top - 1f && origin.y + node.size.height <= shown.bottom + 1f,
        )
    }

    private val longAmount = "123\u00A0456\u00A0789.123456 XOR"

    @Test fun aLongSentAmountOnAWaitingEntryIsWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longAmount, EventTransferOutUiModel(hash, "", peer, "10:15", now, longAmount, "$5.00", TransactionStatus.PENDING))

    @Test fun aLongReceivedAmountIsWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longAmount, EventTransferInUiModel(hash, now, TransactionStatus.REJECTED, "", peer, "10:15", longAmount, "$5.00"))

    @Test fun aLongBridgedAmountIsWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longAmount, EventEthTransfer(hash, now, TransactionStatus.PENDING, "", "", "10:15", longAmount, "$5.00", "bridge-request", "0xrecipient"))

    @Test fun longSwappedAmountsAreWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longAmount, EventLiquiditySwapUiModel(hash, "", "", longAmount, "987\u00A0654.321 VAL", "XOR", "VAL", "$5.00", "10:15", now, TransactionStatus.PENDING))

    @Test fun longPoolAmountsAreWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longAmount, EventLiquidityAddUiModel(hash, now, TransactionStatus.PENDING, "10:15", "", "", longAmount, "987\u00A0654.321 VAL", "Added liquidity", "XOR", "VAL", "$5.00", true))

    private val longFarmAmount = "123\u00A0456\u00A0789.123456"

    @Test fun aLongStakedAmountIsWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longFarmAmount, EventDemeterStakeUiModel(hash, now, TransactionStatus.PENDING, "", "", "", true, longFarmAmount, "XOR / VAL"))

    @Test fun aLongWithdrawnFarmAmountIsWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longFarmAmount, EventDemeterStakeUiModel(hash, now, TransactionStatus.REJECTED, "", "", "", false, longFarmAmount, "XOR / VAL"))

    @Test fun aLongReferralBondIsWholeWithTheLargestTextOnANarrowScreen() =
        assertLongAmountWhole(longAmount, EventReferralProgramUiModel(hash, now, TransactionStatus.PENDING, "", R.string.referral_bond_button_title, "Invitations bonded", false, "10:15", longAmount))

    @Test fun dateSeparatorsNeverOpenATransaction() {
        val clicked = mutableListOf<String>()
        compose.setContent { SoraAppTheme { TxHistoryListItem(Modifier.testTag("row"), clicked::add, EventUiModel.EventTimeSeparatorUiModel("Yesterday")) } }
        compose.onNodeWithText("YESTERDAY").assertIsDisplayed().assertHasNoClickAction()
        compose.runOnIdle { assertTrue(clicked.isEmpty()) }
    }
}
