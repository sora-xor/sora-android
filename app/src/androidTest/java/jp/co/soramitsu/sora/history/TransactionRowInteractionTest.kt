@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.history

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    @Test fun dateSeparatorsNeverOpenATransaction() {
        val clicked = mutableListOf<String>()
        compose.setContent { SoraAppTheme { TxHistoryListItem(Modifier.testTag("row"), clicked::add, EventUiModel.EventTimeSeparatorUiModel("Yesterday")) } }
        compose.onNodeWithText("YESTERDAY").assertIsDisplayed().assertHasNoClickAction()
        compose.runOnIdle { assertTrue(clicked.isEmpty()) }
    }
}
