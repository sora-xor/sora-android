package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A repeated transaction or date header must never crash the Activity list. Before the fix,
 * LazyColumn threw "Key ... was already used" for these items.
 */
@RunWith(AndroidJUnit4::class)
class TxHistoryListRepeatedItemsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun sent(hash: String, amount: String) =
        EventUiModel.EventTxUiModel.EventTransferOutUiModel(
            hash = hash,
            tokenIcon = DEFAULT_ICON_URI,
            peerAddress = "cnRuoXdU9t5bv5EQAiXT2gQozAvrVawqZkT2AQS1Msr8T8ZZu",
            dateTime = "12:00",
            timestamp = 0L,
            amountFormatted = amount,
            fiatFormatted = "",
            status = TransactionStatus.COMMITTED,
        )

    @Test
    fun repeatedTransactionsAndHeadersRenderWithoutCrashing() {
        val repeated = sent("0x" + "aa".repeat(32), "1 XOR")
        val events = listOf(
            EventUiModel.EventTimeSeparatorUiModel("Oct 7"),
            repeated,
            EventUiModel.EventTimeSeparatorUiModel("Today"),
            sent("0x" + "bb".repeat(32), "2 XOR"),
            EventUiModel.EventTimeSeparatorUiModel("Oct 7"),
            repeated,
            sent("0x" + "cc".repeat(32), "3 XOR"),
        )

        composeRule.setContent {
            SoraAppTheme {
                TxHistoryScreenContainer(
                    modifier = Modifier.fillMaxSize(),
                    historyState = HistoryState.History(endReached = false, events = events),
                    onRefreshClick = {},
                    onHistoryItemClick = {},
                    onMoreHistoryItemRequested = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("1 XOR", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("3 XOR", substring = true).assertCountEquals(1)
    }
}
