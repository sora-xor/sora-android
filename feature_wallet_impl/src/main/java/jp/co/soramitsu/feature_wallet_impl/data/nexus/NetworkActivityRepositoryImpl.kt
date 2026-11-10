package jp.co.soramitsu.feature_wallet_impl.data.nexus

import javax.inject.Inject
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_wallet_api.data.NetworkAccountActivity
import jp.co.soramitsu.feature_wallet_api.data.NetworkActivityRepository
import jp.co.soramitsu.feature_wallet_api.data.NetworkPendingActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map

/** Reuses the portfolio's identity, history, and pending-transaction validation. */
class NetworkActivityRepositoryImpl @Inject constructor(
    private val portfolioRepository: NexusPortfolioRepository,
) : NetworkActivityRepository {
    // Activity is a history: it takes a read once its history is read, as it always has, and
    // not the earlier rows that only bring the balance and Send forward.
    override fun observeCurrentWallet(): Flow<List<NetworkAccountActivity>> =
        portfolioRepository.observeCurrentWallet().filterNot { balances ->
            balances.any { it.historyLoading }
        }.map { balances ->
            balances.filter { it.networkLive }.map { balance ->
                NetworkAccountActivity(
                    walletId = balance.walletId,
                    networkId = balance.networkId,
                    networkName = balance.networkName,
                    isTestnet = balance.isTestnet,
                    address = balance.address,
                    confirmedTransfers = balance.confirmedTransfers,
                    pendingTransactions = balance.pendingTransactions.map { pending ->
                        NetworkPendingActivity(
                            localId = pending.localId,
                            transactionHash = pending.transactionHash,
                            amount = pending.amount,
                            recipient = pending.recipient,
                            state = pending.state,
                            submissionIsAmbiguous = pending.submissionIsAmbiguous,
                        )
                    },
                    errorCode = balance.historyErrorCode ?: balance.errorCode
                        ?: "NEXUS_RECOVERY_REQUIRED".takeIf {
                            balance.recoveryPendingTransactions.isNotEmpty()
                        },
                    hasMore = balance.historyHasMore,
                )
            }
        }

    override fun refresh() = portfolioRepository.refresh()

    override fun loadMore(walletId: String, networkId: WalletNetworkId) =
        portfolioRepository.loadMoreHistory(walletId, networkId)
}
