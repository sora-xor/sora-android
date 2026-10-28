package jp.co.soramitsu.feature_wallet_api.data

import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.WalletNetworkId
import kotlinx.coroutines.flow.Flow

/** Qualified activity for the selected wallet's live network accounts. */
interface NetworkActivityRepository {
    fun observeCurrentWallet(): Flow<List<NetworkAccountActivity>>
    fun refresh()
    fun loadMore(walletId: String, networkId: WalletNetworkId)
}

data class NetworkAccountActivity(
    val walletId: String,
    val networkId: WalletNetworkId,
    val networkName: String,
    val isTestnet: Boolean,
    val address: String,
    val confirmedTransfers: List<NexusTransferHistoryItem>,
    val pendingTransactions: List<NetworkPendingActivity>,
    val errorCode: String?,
    val hasMore: Boolean,
)

data class NetworkPendingActivity(
    val localId: String,
    val transactionHash: String?,
    val amount: String,
    val recipient: String,
    val state: String,
    val submissionIsAmbiguous: Boolean,
)
