package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_wallet_api.data.NetworkAccountActivity
import jp.co.soramitsu.feature_wallet_api.data.NetworkPendingActivity

data class NetworkActivityState(
    val walletId: String? = null,
    val accounts: List<NetworkAccountActivity> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

internal sealed class AccountActivityEntry(val key: String, val timestamp: Long) {
    class Sora2(val event: EventUiModel.EventTxUiModel) :
        AccountActivityEntry("sora2:${event.txHash}", event.timestamp)

    class Transfer(val account: NetworkAccountActivity, val transfer: NexusTransferHistoryItem, index: Int) :
        AccountActivityEntry("${account.walletId}:${account.networkId}:${transfer.transactionHash}:$index", transfer.timestampMillis)

    class Pending(val account: NetworkAccountActivity, val pending: NetworkPendingActivity) :
        AccountActivityEntry("${account.walletId}:${account.networkId}:pending:${pending.localId}", Long.MAX_VALUE)
}

internal fun accountActivityEntries(
    history: HistoryState,
    networks: List<NetworkAccountActivity>,
): List<AccountActivityEntry> = buildList {
    (history as? HistoryState.History)?.events
        ?.filterIsInstance<EventUiModel.EventTxUiModel>()
        ?.distinctBy { it.txHash }
        ?.forEach { add(AccountActivityEntry.Sora2(it)) }
    networks.forEach { account ->
        val confirmedHashes = account.confirmedTransfers.map { it.transactionHash }.toSet()
        account.pendingTransactions.filterNot { it.transactionHash in confirmedHashes }.forEach {
            add(AccountActivityEntry.Pending(account, it))
        }
        // One transaction may contain several transfers, including identical batch entries.
        account.confirmedTransfers.forEachIndexed { index, transfer ->
            add(AccountActivityEntry.Transfer(account, transfer, index))
        }
    }
}.sortedByDescending { it.timestamp }
