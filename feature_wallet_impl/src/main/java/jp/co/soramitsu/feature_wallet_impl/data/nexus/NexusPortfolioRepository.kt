package jp.co.soramitsu.feature_wallet_impl.data.nexus

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusToriiReadClient
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

data class NexusPortfolioBalance(
    val walletId: String,
    val networkId: WalletNetworkId,
    val networkName: String,
    val isTestnet: Boolean,
    val address: String,
    val xorQuantity: String?,
    val assetDefinitionId: String?,
    val explorerBaseUrl: String,
    val sendAvailable: Boolean,
    val pendingTransactions: List<NexusPendingTransaction>,
    val confirmedTransfers: List<NexusTransferHistoryItem>,
    val historyErrorCode: String?,
    val errorCode: String?,
    val recoveryPendingTransactions: List<NexusPendingTransaction> = emptyList(),
)

data class NexusPendingTransaction(
    val localId: String,
    val transactionHash: String?,
    val assetDefinitionId: String,
    val amount: String,
    val recipient: String,
    val state: String,
    val submissionIsAmbiguous: Boolean,
)

@Singleton
class NexusPortfolioRepository @Inject constructor(
    private val database: AppDatabase,
    private val userRepository: UserRepository,
    private val torii: NexusToriiReadClient,
    private val sendQualification: NexusSendQualification,
    private val featureManager: ProductionFeatureManager,
) {
    fun observeCurrentWallet(): Flow<List<NexusPortfolioBalance>> =
        userRepository.flowCurSoraAccount().flatMapLatest { wallet ->
            observeWallet(wallet.substrateAddress).onStart {
                // Account selection is a privacy boundary. Never retain the
                // previous wallet's receive addresses, balances, pending
                // transactions, or history while the replacement flow loads.
                emit(emptyList())
            }
        }

    private fun observeWallet(walletId: String): Flow<List<NexusPortfolioBalance>> = flow {
        val featureState = featureManager.getState()
        if (!featureState.nexusAvailable) {
            emit(emptyList())
            return@flow
        }

        val migration = database.walletIdentityDao()
            .getMigrationJournal(WalletMigrationIds.NETWORK_ACCOUNTS_V1)
        val identity = database.walletIdentityDao().getWallet(walletId)
        val verified = if (migration == null) {
            identity?.migrationState == "VERIFIED"
        } else {
            migration.state == "VERIFIED" && identity?.migrationState == "VERIFIED"
        }
        if (!verified) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            combine(
                database.walletIdentityDao().observeEnabledNetworkAccounts(),
                featureManager.observeTairaVisible(),
                database.walletIdentityDao().observePendingTransactions(walletId),
            ) { accounts, tairaVisible, pending ->
                val pendingRecoveryRequired =
                    NexusPendingJournalPresentationContract.requiresRecovery(
                        transactions = pending,
                        networkAccounts = accounts,
                        maximumTransactions = MAX_PENDING_TRANSACTIONS,
                    )
                PortfolioInputs(
                    accounts = accounts.filter {
                        it.walletId == walletId &&
                            it.networkId != WalletNetworkId.SORA2.wireId &&
                            (it.networkId != WalletNetworkId.TAIRA.wireId || tairaVisible)
                    },
                    pending = if (pendingRecoveryRequired) emptyList() else pending,
                    pendingRecoveryRequired = pendingRecoveryRequired,
                )
            }.mapLatest { inputs ->
                check(
                    inputs.accounts.map { it.walletId to it.networkId }.distinct().size ==
                        inputs.accounts.size
                ) { "NEXUS_DUPLICATE_NETWORK_ACCOUNT" }
                coroutineScope {
                    inputs.accounts.map { account ->
                        async {
                            val id = checkNotNull(WalletNetworkId.fromWireId(account.networkId))
                            val network = NexusNetworks.require(id)
                            val parsedAccount = IrohaAddressCodec.parse(
                                account.address,
                                network.chainDiscriminant,
                            )
                            check(
                                PUBLIC_KEY.matches(account.publicKey) &&
                                    parsedAccount.publicKeyHex == account.publicKey.lowercase()
                            ) { "NEXUS_NETWORK_ACCOUNT_IDENTITY_MISMATCH" }
                            val projection =
                                NexusPendingOverlayValidator.projectForPortfolio(
                                    transactions = inputs.pending.filter {
                                        it.networkId == account.networkId
                                    },
                                    expectedWalletId = walletId,
                                    expectedNetworkId = account.networkId,
                                    expectedChainId = network.chainId,
                                    discriminant = network.chainDiscriminant,
                                )
                            val projectionRequiresRecovery =
                                projection ==
                                    NexusPendingOverlayValidator.Projection.RecoveryRequired
                            val pendingRecoveryRequired =
                                inputs.pendingRecoveryRequired || projectionRequiresRecovery
                            val portfolioAccess =
                                NexusPendingJournalPresentationContract.portfolioAccess(
                                    pendingRecoveryRequired
                                )
                            // Recovery evidence blocks mutation admission and is
                            // never projected. It does not make this qualified
                            // account's current balance or finalized chain
                            // history unsafe to read.
                            val pending = if (!portfolioAccess.projectsPending) {
                                emptyList()
                            } else {
                                (projection as NexusPendingOverlayValidator.Projection.Valid)
                                    .transactions
                            }
                            check(portfolioAccess.allowsQualifiedReads) {
                                "NEXUS_QUALIFIED_READS_DISABLED"
                            }
                            try {
                                val balance = torii.getXorBalance(network, account.address)
                                val canonicalBalance = canonicalNonNegativeQuantity(
                                    balance.quantity
                                )
                                val canonicalAssetDefinitionId =
                                    balance.assetId ?: balance.asset
                                val boundPending = pending.filter {
                                    it.assetDefinitionId == canonicalAssetDefinitionId
                                }
                                val recoveryPending = pending.filterNot {
                                    it.assetDefinitionId == canonicalAssetDefinitionId
                                }
                                val history = try {
                                    HistoryResult(
                                        items = torii.committedXorTransfers(
                                            network = network,
                                            accountId = account.address,
                                            assetDefinitionId = canonicalAssetDefinitionId,
                                        ),
                                        errorCode = null,
                                    )
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    HistoryResult(
                                        items = emptyList(),
                                        errorCode = "NEXUS_HISTORY_UNAVAILABLE",
                                    )
                                }
                                NexusPortfolioBalance(
                                    walletId = walletId,
                                    networkId = id,
                                    networkName = network.displayName,
                                    isTestnet = network.isTestnet,
                                    address = account.address,
                                    xorQuantity = canonicalBalance,
                                    assetDefinitionId = canonicalAssetDefinitionId,
                                    explorerBaseUrl = network.explorerBaseUrl,
                                    sendAvailable =
                                        portfolioAccess.allowsSends &&
                                            featureState.nexusSendsAvailable &&
                                            sendQualification.isQualifiedFor(network),
                                    pendingTransactions = if (pendingRecoveryRequired) {
                                        emptyList()
                                    } else {
                                        boundPending
                                    },
                                    confirmedTransfers = history.items,
                                    historyErrorCode = history.errorCode,
                                    errorCode = if (pendingRecoveryRequired) {
                                        "NEXUS_PENDING_RECOVERY_REQUIRED"
                                    } else {
                                        null
                                    },
                                    recoveryPendingTransactions =
                                        if (pendingRecoveryRequired) {
                                            emptyList()
                                        } else {
                                            recoveryPending
                                        },
                                )
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                NexusPortfolioBalance(
                                    walletId = walletId,
                                    networkId = id,
                                    networkName = network.displayName,
                                    isTestnet = network.isTestnet,
                                    address = account.address,
                                    xorQuantity = null,
                                    assetDefinitionId = null,
                                    explorerBaseUrl = network.explorerBaseUrl,
                                    sendAvailable = false,
                                    pendingTransactions = emptyList(),
                                    confirmedTransfers = emptyList(),
                                    historyErrorCode = "NEXUS_HISTORY_UNAVAILABLE",
                                    errorCode = if (pendingRecoveryRequired) {
                                        "NEXUS_PENDING_RECOVERY_REQUIRED"
                                    } else {
                                        "NEXUS_BALANCE_UNAVAILABLE"
                                    },
                                    recoveryPendingTransactions = pending,
                                )
                            }
                        }
                    }.awaitAll()
                }
            }
        )
    }

    private data class PortfolioInputs(
        val accounts: List<jp.co.soramitsu.core_db.model.NetworkAccountLocal>,
        val pending: List<jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal>,
        val pendingRecoveryRequired: Boolean,
    )

    private data class HistoryResult(
        val items: List<NexusTransferHistoryItem>,
        val errorCode: String?,
    )

    private fun canonicalNonNegativeQuantity(value: String): String {
        check(NexusQuantityContract.isWireQuantity(value)) {
            "NEXUS_INVALID_QUANTITY"
        }
        val decimal = runCatching { BigDecimal(value) }
            .getOrElse { throw IllegalArgumentException("NEXUS_INVALID_QUANTITY") }
        check(decimal.signum() >= 0) { "NEXUS_INVALID_QUANTITY" }
        return decimal.stripTrailingZeros().toPlainString()
    }

    private companion object {
        const val MAX_PENDING_TRANSACTIONS = 500
        val PUBLIC_KEY = Regex("^[0-9a-fA-F]{64}$")
    }
}
