package jp.co.soramitsu.feature_wallet_impl.data.nexus

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.nexus.NexusAssetBalance
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusToriiException
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    val sendCapabilities: NexusSendCapabilities = NexusSendCapabilities.unavailable(),
    val networkLive: Boolean = true,
    val historyHasMore: Boolean = false,
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
    private val requests = MutableStateFlow(PortfolioRequest())

    fun refresh() {
        requests.update { PortfolioRequest(refreshGeneration = it.refreshGeneration + 1) }
    }

    fun loadMoreHistory(walletId: String, networkId: WalletNetworkId) {
        if (networkId !in NexusNetworks.liveIds) return
        requests.update { request ->
            val key = walletId to networkId
            request.copy(historyPageCounts = request.historyPageCounts +
                (key to Math.addExact(request.historyPageCounts[key] ?: 1, 1)))
        }
    }

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
        // Cache only within this wallet subscription and this complete read context. Replacing
        // the map on invalidation also isolates a cancelled request that finishes late.
        var readScope: PortfolioInputs? = null
        var readCache = ConcurrentHashMap<jp.co.soramitsu.core_db.model.NetworkAccountLocal, CachedRead>()
        var pageCountBaseline = emptyMap<Pair<String, WalletNetworkId>, Int>()
        var refreshedGeneration: Long? = null
        emitAll(
            combine(
                database.walletIdentityDao().observeEnabledNetworkAccounts(),
                featureManager.observeState(),
                database.walletIdentityDao().observePendingTransactions(walletId),
                requests.mapLatest { request ->
                    if (refreshedGeneration != request.refreshGeneration) {
                        // Foreground/manual refresh updates both capabilities and read data.
                        try {
                            featureManager.getState()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // Preserve the last complete read capability snapshot while offline.
                        }
                        refreshedGeneration = request.refreshGeneration
                    }
                    request
                },
            ) { accounts, featureState, pending, request ->
                val pendingRecoveryRequired =
                    NexusPendingJournalPresentationContract.requiresRecovery(
                        transactions = pending,
                        networkAccounts = accounts,
                        maximumTransactions = MAX_PENDING_TRANSACTIONS,
                    )
                PortfolioInputs(
                    accounts = accounts.filter {
                        val networkId = WalletNetworkId.fromWireId(it.networkId)
                        featureState.nexusAvailable && it.walletId == walletId &&
                            networkId != null &&
                            NexusNetworks.find(networkId) != null &&
                            (networkId != WalletNetworkId.TAIRA || featureState.tairaVisible)
                    },
                    pending = if (pendingRecoveryRequired) emptyList() else pending,
                    pendingRecoveryRequired = pendingRecoveryRequired,
                    featureState = featureState,
                    historyPageCounts = request.historyPageCounts,
                    refreshGeneration = request.refreshGeneration,
                )
            }.mapLatest { inputs ->
                check(
                    inputs.accounts.map { it.walletId to it.networkId }.distinct().size ==
                        inputs.accounts.size
                ) { "NEXUS_DUPLICATE_NETWORK_ACCOUNT" }
                val scope = inputs.copy(historyPageCounts = emptyMap())
                if (scope != readScope) {
                    readScope = scope
                    readCache = ConcurrentHashMap()
                    pageCountBaseline = inputs.historyPageCounts
                }
                val scopedCache = readCache
                val scopedBaseline = pageCountBaseline
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
                            if (!NexusNetworks.isLive(network)) {
                                return@async NexusPortfolioBalance(
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
                                    historyErrorCode = null,
                                    errorCode = null,
                                    networkLive = false,
                                )
                            }
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
                                val balance = scopedCache[account]?.balance
                                    ?: torii.getXorBalance(network, account.address)
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
                                var cached = scopedCache[account] ?: CachedRead(balance)
                                currentCoroutineContext().ensureActive()
                                scopedCache[account] = cached
                                val history = try {
                                    val key = walletId to id
                                    val requestedPages = 1 + (inputs.historyPageCounts[key] ?: 1) -
                                        (scopedBaseline[key] ?: 1)
                                    while (cached.pages < requestedPages && cached.hasMore) {
                                        val slice = torii.committedXorTransferPage(
                                            network, account.address, canonicalAssetDefinitionId, cached.nextCursor,
                                        )
                                        check(slice.sourceItemIds.none(cached.sourceIds::contains)) {
                                            "NEXUS_HISTORY_DUPLICATE_ITEM"
                                        }
                                        check(slice.nextCursor == null || slice.nextCursor !in cached.cursors) {
                                            "NEXUS_HISTORY_REPEATED_PAGE"
                                        }
                                        val next = cached.copy(
                                            items = cached.items + slice.items,
                                            sourceIds = cached.sourceIds + slice.sourceItemIds,
                                            cursors = cached.cursors + listOfNotNull(slice.nextCursor),
                                            nextCursor = slice.nextCursor,
                                            hasMore = slice.nextCursor != null,
                                            pages = cached.pages + 1,
                                        )
                                        // Publish only a whole validated page; cancellation cannot advance a cursor.
                                        currentCoroutineContext().ensureActive()
                                        scopedCache[account] = next
                                        cached = next
                                    }
                                    HistoryResult(cached.items, null, cached.hasMore)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Throwable) {
                                    HistoryResult(
                                        items = cached.items,
                                        errorCode = error.diagnosticCode("NEXUS_HISTORY_UNAVAILABLE"),
                                        hasMore = cached.hasMore,
                                    )
                                }
                                val sendCapabilities = sendQualification.capabilitiesFor(network)
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
                                            inputs.featureState.sendsEnabledFor(network.id) &&
                                            sendCapabilities.supportsSend,
                                    pendingTransactions = if (pendingRecoveryRequired) {
                                        emptyList()
                                    } else {
                                        boundPending
                                    },
                                    confirmedTransfers = history.items,
                                    historyErrorCode = history.errorCode,
                                    historyHasMore = history.hasMore,
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
                                    sendCapabilities = sendCapabilities,
                                )
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
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
                                    historyErrorCode = error.diagnosticCode(
                                        "NEXUS_HISTORY_UNAVAILABLE"
                                    ),
                                    errorCode = if (pendingRecoveryRequired) {
                                        "NEXUS_PENDING_RECOVERY_REQUIRED"
                                    } else {
                                        error.diagnosticCode("NEXUS_BALANCE_UNAVAILABLE")
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
        val featureState: jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureState,
        val historyPageCounts: Map<Pair<String, WalletNetworkId>, Int>,
        val refreshGeneration: Long,
    )

    private data class PortfolioRequest(
        val refreshGeneration: Long = 0,
        val historyPageCounts: Map<Pair<String, WalletNetworkId>, Int> = emptyMap(),
    )

    private data class CachedRead(
        val balance: NexusAssetBalance,
        val items: List<NexusTransferHistoryItem> = emptyList(),
        val sourceIds: Set<String> = emptySet(),
        val cursors: Set<String> = emptySet(),
        val nextCursor: String? = null,
        val hasMore: Boolean = true,
        val pages: Int = 0,
    )

    private data class HistoryResult(
        val items: List<NexusTransferHistoryItem>,
        val errorCode: String?,
        val hasMore: Boolean = false,
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

    private fun Throwable.diagnosticCode(fallback: String): String =
        (this as? NexusToriiException)?.safeCode ?: fallback

    private companion object {
        const val MAX_PENDING_TRANSACTIONS = 500
        val PUBLIC_KEY = Regex("^[0-9a-fA-F]{64}$")
    }
}
