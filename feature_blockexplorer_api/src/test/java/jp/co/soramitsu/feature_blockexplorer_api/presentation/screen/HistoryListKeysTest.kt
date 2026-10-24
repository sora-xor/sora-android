package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import com.google.common.truth.Truth.assertThat
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import org.junit.Test

class HistoryListKeysTest {

    private fun tx(hash: String) = EventUiModel.EventTxUiModel.EventTransferOutUiModel(
        hash = hash,
        tokenIcon = "",
        peerAddress = "",
        dateTime = "",
        timestamp = 0L,
        amountFormatted = "",
        fiatFormatted = "",
        status = TransactionStatus.COMMITTED,
    )

    private fun header(title: String) = EventUiModel.EventTimeSeparatorUiModel(title)

    @Test
    fun `distinct items keep their natural keys`() {
        val keys = historyListKeys(
            listOf(header("Today"), tx("0xaa"), tx("0xbb"), header("Yesterday"), tx("0xcc"), EventUiModel.EventUiLoading),
        )

        assertThat(keys).containsExactly(
            "header:Today",
            "tx:0xaa",
            "tx:0xbb",
            "header:Yesterday",
            "tx:0xcc",
            "loading",
        ).inOrder()
    }

    @Test
    fun `a repeated transaction or header still gets a unique key`() {
        val items = listOf(
            header("Oct 7"),
            tx("0xaa"),
            header("Today"),
            tx("0xbb"),
            header("Oct 7"),
            tx("0xaa"),
            tx("0xaa"),
        )

        val keys = historyListKeys(items)

        assertThat(keys).hasSize(items.size)
        assertThat(keys.toSet()).hasSize(items.size)
        assertThat(keys.first()).isEqualTo("header:Oct 7")
        assertThat(keys[1]).isEqualTo("tx:0xaa")
    }

    @Test
    fun `a suffixed key cannot collide with a hash that already looks suffixed`() {
        val keys = historyListKeys(listOf(tx("0xaa#1"), tx("0xaa"), tx("0xaa")))

        assertThat(keys.toSet()).hasSize(3)
    }
}
