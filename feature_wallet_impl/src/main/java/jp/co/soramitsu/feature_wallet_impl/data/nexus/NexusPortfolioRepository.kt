package jp.co.soramitsu.feature_wallet_impl.data.nexus

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.nexus.NexusAssetBalance
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.nexus.IrohaAddressCodec
import jp.co.soramitsu.common.nexus.NexusNetwork
import jp.co.soramitsu.common.nexus.NexusNetworks
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusToriiException
import jp.co.soramitsu.common.nexus.NexusToriiReadClient
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
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
import kotlinx.coroutines.flow.transformLatest

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
    /**
     * The balance, Send and pending transfers are current, the history is being read: until it
     * arrives the row carries the history it showed before (none on a first read).
     */
    val historyLoading: Boolean = false,
)

data class NexusPendingTransaction(
    val localId: String,
    val transactionHash: String?,
    val assetDefinitionId: String,
    val amount: String,
    val recipient: String,
    val state: String,
    val submissionIsAmbiguous: Boolean,
    /** When this phone recorded the transfer, for its transaction page; 0 when not known. */
    val createdAtMillis: Long = 0L,
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

    /**
     * One wallet's Nexus rows, read-only, whether or not it is the selected wallet. The
     * Portfolio view reads every wallet this way; it never sends from a wallet that is not
     * selected (the home's Nexus send only takes [observeCurrentWallet] rows).
     *
     * A read is emitted twice. First every network's balance and Send, from the balance read and
     * the pending journal as stored, while the history is read ([NexusPortfolioBalance.historyLoading]);
     * then the same rows with their history. Until 2026-11-08 nothing was emitted before the
     * history page as well, and one page of Recent activity on Taira can take many requests.
     *
     * Without [history] only the balance, Send and journal are read: the Portfolio view shows no
     * history, and following Taira's empty ranges for every wallet on each of its refreshes would
     * only load the server.
     */
    internal fun observeWallet(
        walletId: String,
        history: Boolean = true,
    ): Flow<List<NexusPortfolioBalance>> = flow {
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
        var readCache = ConcurrentHashMap<NetworkAccountLocal, CachedRead>()
        var pageCountBaseline = emptyMap<Pair<String, WalletNetworkId>, Int>()
        var refreshedGeneration: Long? = null
        // The rows this subscription emitted last. Only their history is ever reused, and only
        // for the same wallet, network, address and asset.
        var shown = emptyList<NexusPortfolioBalance>()
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
            }.transformLatest<PortfolioInputs, List<NexusPortfolioBalance>> { inputs ->
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
                val shownBefore = shown
                val reads = coroutineScope {
                    inputs.accounts.map { account ->
                        async {
                            readBalance(
                                walletId, account, inputs, scopedCache, scopedBaseline,
                                shownBefore, history,
                            )
                        }
                    }.awaitAll()
                }
                // The balance and Send show at once. Only when a pending transfer has just left
                // the journal (and Send stays as available) do the rows wait for the history that
                // now holds it, so it never drops out of Recent activity or closes its page.
                if (reads.any { it.row.historyLoading } && reads.none { it.resolvedPending }) {
                    val rows = reads.map { it.row }
                    emit(rows)
                    shown = rows
                }
                val rows = coroutineScope {
                    reads.map { read -> async { readHistory(read, scopedCache) } }.awaitAll()
                }
                emit(rows)
                shown = rows
            }
        )
    }

    /**
     * A network's row as soon as its balance is read: Send is decided exactly as before, from the
     * journal as stored, the network's feature switch, the send capabilities and a verified XOR
     * balance read. The history, when [history] asks for it, is left to [readHistory].
     */
    private suspend fun readBalance(
        walletId: String,
        account: NetworkAccountLocal,
        inputs: PortfolioInputs,
        cache: ConcurrentHashMap<NetworkAccountLocal, CachedRead>,
        baseline: Map<Pair<String, WalletNetworkId>, Int>,
        shown: List<NexusPortfolioBalance>,
        history: Boolean,
    ): AccountRead {
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
            return AccountRead(
                account = account,
                network = network,
                row = NexusPortfolioBalance(
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
                ),
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
        return try {
            val balance = cache[account]?.balance
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
            val cached = cache[account] ?: CachedRead(balance)
            currentCoroutineContext().ensureActive()
            cache[account] = cached
            val key = walletId to id
            val requestedPages = if (history) {
                1 + (inputs.historyPageCounts[key] ?: 1) - (baseline[key] ?: 1)
            } else {
                0
            }
            val historyToRead = cached.pages < requestedPages && cached.hasMore
            val shownRow = shown.firstOrNull {
                it.walletId == walletId && it.networkId == id && it.address == account.address
            }
            // Until the history is read again the row keeps the history it showed, for this
            // very account and asset only; a first read shows none.
            val shownHistory = shownRow?.takeIf {
                cached.pages == 0 && it.assetDefinitionId == canonicalAssetDefinitionId
            }
            val sendCapabilities = sendQualification.capabilitiesFor(network)
            val sendAvailable = portfolioAccess.allowsSends &&
                inputs.featureState.sendsEnabledFor(network.id) &&
                sendCapabilities.supportsSend
            AccountRead(
                account = account,
                network = network,
                row = NexusPortfolioBalance(
                    walletId = walletId,
                    networkId = id,
                    networkName = network.displayName,
                    isTestnet = network.isTestnet,
                    address = account.address,
                    xorQuantity = canonicalBalance,
                    assetDefinitionId = canonicalAssetDefinitionId,
                    explorerBaseUrl = network.explorerBaseUrl,
                    sendAvailable = sendAvailable,
                    pendingTransactions = if (pendingRecoveryRequired) {
                        emptyList()
                    } else {
                        boundPending
                    },
                    confirmedTransfers = shownHistory?.confirmedTransfers ?: cached.items,
                    historyErrorCode = shownHistory?.historyErrorCode,
                    historyHasMore = shownHistory?.historyHasMore
                        ?: (cached.pages > 0 && cached.hasMore),
                    historyLoading = historyToRead,
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
                ),
                requestedPages = requestedPages,
                // Never when the journal needs recovery or Send has just been turned off: those
                // rows are shown at once.
                resolvedPending = !pendingRecoveryRequired && shownRow != null &&
                    (sendAvailable || !shownRow.sendAvailable) &&
                    shownRow.pendingTransactions.any { before ->
                        boundPending.none { it.localId == before.localId }
                    },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AccountRead(
                account = account,
                network = network,
                row = NexusPortfolioBalance(
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
                ),
            )
        }
    }

    /**
     * The pages of Recent activity [read] asks for. Taira scans an account's history newest first
     * in block ranges and answers most ranges with no rows and a cursor (24 of the faucet's 28 on
     * 2026-11-08), so one page of Recent activity follows up to [MAX_HISTORY_REQUESTS_PER_PAGE]
     * of them until it has transfers or the history ends; past that, Show more continues.
     */
    private suspend fun readHistory(
        read: AccountRead,
        cache: ConcurrentHashMap<NetworkAccountLocal, CachedRead>,
    ): NexusPortfolioBalance {
        val assetDefinitionId = read.row.assetDefinitionId
        if (!read.row.historyLoading || assetDefinitionId == null) return read.row
        var cached = checkNotNull(cache[read.account])
        val history = try {
            while (cached.pages < read.requestedPages && cached.hasMore) {
                var requests = 0
                do {
                    val slice = torii.committedXorTransferPage(
                        read.network, read.account.address, assetDefinitionId, cached.nextCursor,
                    )
                    check(slice.sourceItemIds.none(cached.sourceIds::contains)) {
                        "NEXUS_HISTORY_DUPLICATE_ITEM"
                    }
                    check(slice.nextCursor == null || slice.nextCursor !in cached.cursors) {
                        "NEXUS_HISTORY_REPEATED_PAGE"
                    }
                    requests += 1
                    val pageComplete = slice.items.isNotEmpty() || slice.nextCursor == null ||
                        requests >= MAX_HISTORY_REQUESTS_PER_PAGE
                    val next = cached.copy(
                        items = cached.items + slice.items,
                        sourceIds = cached.sourceIds + slice.sourceItemIds,
                        cursors = cached.cursors + listOfNotNull(slice.nextCursor),
                        nextCursor = slice.nextCursor,
                        hasMore = slice.nextCursor != null,
                        pages = if (pageComplete) cached.pages + 1 else cached.pages,
                    )
                    // Publish only a whole validated page; cancellation cannot advance a cursor.
                    currentCoroutineContext().ensureActive()
                    cache[read.account] = next
                    cached = next
                } while (!pageComplete)
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
        return read.row.copy(
            confirmedTransfers = history.items,
            historyErrorCode = history.errorCode,
            historyHasMore = history.hasMore,
            historyLoading = false,
        )
    }

    private class AccountRead(
        val account: NetworkAccountLocal,
        val network: NexusNetwork,
        val row: NexusPortfolioBalance,
        val requestedPages: Int = 0,
        /** A transfer the last emitted row listed as pending has left the journal. */
        val resolvedPending: Boolean = false,
    )

    private data class PortfolioInputs(
        val accounts: List<NetworkAccountLocal>,
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
        /** Taira history requests one page of Recent activity may take; matches iOS. */
        const val MAX_HISTORY_REQUESTS_PER_PAGE = 12
        val PUBLIC_KEY = Regex("^[0-9a-fA-F]{64}$")
    }
}
