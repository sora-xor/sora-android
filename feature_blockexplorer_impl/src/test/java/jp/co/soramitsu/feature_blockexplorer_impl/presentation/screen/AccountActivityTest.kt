package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.data.NetworkAccountActivity
import jp.co.soramitsu.feature_wallet_api.data.NetworkPendingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountActivityTest {
    private val transfer = NexusTransferHistoryItem("hash", 300L, "2", "sender", "account")
    private val network = NetworkAccountActivity("wallet", WalletNetworkId.TAIRA, "Taira", true,
        "account", listOf(transfer), emptyList(), null, false)

    @Test
    fun `Taira history stays visible when SORA2 is empty or unavailable`() {
        listOf(HistoryState.NoData, HistoryState.Error, HistoryState.Loading).forEach { sora ->
            val rows = accountActivityEntries(sora, listOf(network))
            assertEquals(1, rows.size)
            assertEquals(transfer, (rows.single() as AccountActivityEntry.Transfer).transfer)
        }
    }

    @Test
    fun `merges newest first without confusing identical hashes on different chains`() {
        val sora = EventUiModel.EventTxUiModel.EventTransferInUiModel("hash", 200L,
            TransactionStatus.COMMITTED, "", "sender", "", "1 XOR", "")
        val history = HistoryState.History(true, listOf(EventUiModel.EventTimeSeparatorUiModel("Today"), sora))
        val rows = accountActivityEntries(history, listOf(network.copy(
            confirmedTransfers = listOf(transfer.copy(timestampMillis = 100L), transfer))))
        assertEquals(listOf(300L, 200L, 100L), rows.map { it.timestamp })
        assertEquals(3, rows.map { it.key }.distinct().size)
    }

    @Test
    fun `retains all batch transfers and removes confirmed pending duplicates`() {
        val pending = NetworkPendingActivity("local", "hash", "2", "recipient", "SUBMITTED", false)
        val rows = accountActivityEntries(HistoryState.NoData, listOf(network.copy(
            confirmedTransfers = listOf(transfer, transfer),
            pendingTransactions = listOf(pending, pending.copy(localId = "new", transactionHash = null)),
        )))
        assertEquals(3, rows.size)
        assertTrue(rows.first() is AccountActivityEntry.Pending)
        assertEquals(2, rows.filterIsInstance<AccountActivityEntry.Transfer>().size)
        assertEquals(3, rows.map { it.key }.distinct().size)
    }

    @Test
    fun `later page errors preserve qualified transfers`() {
        val rows = accountActivityEntries(HistoryState.NoData, listOf(network.copy(
            errorCode = "NEXUS_HISTORY_UNAVAILABLE", hasMore = true)))
        assertEquals(transfer, (rows.single() as AccountActivityEntry.Transfer).transfer)
    }
}
