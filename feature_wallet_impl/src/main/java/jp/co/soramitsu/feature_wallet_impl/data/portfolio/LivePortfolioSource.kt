package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import java.math.BigDecimal
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioRepository
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFreshness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioToken
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioWallet
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletSync
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the first frame shows: the last saved picture, and the person's scope. */
data class SavedPicture(val snapshot: PortfolioSnapshot, val scope: PortfolioScope?)

/**
 * Every wallet's real holdings.
 *
 * A cold launch shows what was read last ([PortfolioReadStore]) at once, for every wallet and
 * network, before any flow below has answered; nothing here waits for the network to emit. Then:
 * - SORA2: the selected wallet comes from the app's own rows, which its live subscription keeps
 *   current. Other wallets are read from the chain, read-only, by address
 *   ([AssetsRepository.fetchBalance], [AssetsRepository.getXORBalance]); nothing is written to the
 *   app's rows for them, and their rows may not even exist. Each wallet's read arrives on its own.
 * - SORA Nexus and Taira: [NexusPortfolioRepository], which already applies this build's network
 *   visibility (Nexus availability, Taira visibility), one independent flow per wallet.
 *
 * Never touches keys, seeds, signing or account storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePortfolioSource @Inject constructor(
    private val userRepository: UserRepository,
    private val assetsRepository: AssetsRepository,
    private val nexusPortfolioRepository: NexusPortfolioRepository,
    private val connectionManager: ConnectionManager,
    private val readStore: PortfolioReadStore,
) : PortfolioSource {

    internal var clock: () -> Long = System::currentTimeMillis

    /** Mapping every wallet's token rows is not the main thread's work. */
    internal var workContext: CoroutineContext = Dispatchers.Default

    private data class ReadBalance(
        val token: PortfolioToken,
        val total: BigDecimal,
        val transferable: BigDecimal,
        val hidden: Boolean = false,
    )

    /**
     * A wallet's SORA2 balances as read (from the chain, or saved). [balances] holds what it has;
     * null means the app's own rows are newer than any read (it was selected until [atMillis]).
     */
    private data class WalletRead(val atMillis: Long?, val balances: Map<String, ReadBalance>?)

    /** The app's rows for one wallet. Null while they are loading, or when they cannot be read. */
    private data class WalletRows(
        val assets: List<Asset>?,
        val failed: Boolean = false,
        // Initial zero rows can precede an import's balance subscription. Once the subscription
        // has held funds, a later zero is authoritative too: those funds were spent.
        val confirmed: Boolean = false,
        val remembered: Boolean = false,
    )

    private data class WalletRaw(
        val account: SoraAccount,
        val rows: WalletRows,
        /** Null until the wallet's Nexus flow has answered once. */
        val nexus: List<NexusPortfolioBalance>?,
        val emittedAtMillis: Long,
    )

    /** [up]: the node answers now. [offline]: it has been gone for longer than a reconnect takes. */
    private data class NodeState(val up: Boolean, val offline: Boolean)

    /** The app's priced tokens by id, and when the newest of those prices was read. */
    private data class PricedTokens(val tokens: Map<String, PortfolioToken>, val readAtMillis: Long?)

    private val reads = MutableStateFlow<Map<String, WalletRead>>(emptyMap())
    private val savedNetworks = MutableStateFlow<Map<String, List<SavedNetworkBalance>>>(emptyMap())
    private val rememberedRows = MutableStateFlow<Map<String, WalletRows>>(emptyMap())
    private val failedReads = MutableStateFlow<Set<String>>(emptySet())
    private val lastReadsState = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val readPermits = Semaphore(MAX_PARALLEL_READS)
    private var lastSelectedId: String? = null
    private var savedFiatSymbol: String? = null
    private var savedXor: PortfolioToken? = null
    private var savedPricesReadAt: Long? = null
    private var tokenFiatSymbol: String? = null
    private var restored = false

    override val isSample: Boolean = false

    override val lastReads: StateFlow<Map<String, Long>> = lastReadsState.asStateFlow()

    override fun observe(): Flow<PortfolioSnapshot> = flow {
        restoreSavedReads()
        emitAll(liveSnapshots())
    }.transformLatest { snapshot ->
        emit(snapshot)
        // Quietly, once the picture has settled for a moment.
        delay(SAVE_DELAY_MS)
        saveReads(snapshot)
    }.flowOn(workContext)

    private fun liveSnapshots(): Flow<PortfolioSnapshot> =
        userRepository.flowSoraAccountsList()
            .distinctUntilChanged()
            .flatMapLatest { accounts ->
                val ids = accounts.map { it.substrateAddress }.toSet()
                rememberedRows.update { rows -> rows.filterKeys { it in ids } }
                if (accounts.isEmpty()) {
                    flowOf(PortfolioSnapshot.EMPTY)
                } else {
                    combine(
                        combine(accounts.map(::observeWallet)) { it.toList() },
                        combine(selectedWalletId(), reads, ::Pair),
                        combine(savedNetworks, failedReads, ::Pair),
                        observeTokens(),
                        observeNode(),
                    ) { raws, (selectedId, freshReads), (saved, failed), tokens, node ->
                        build(accounts, selectedId, raws, freshReads, saved, failed, tokens, node)
                    }
                }
            }

    /**
     * Each wallet is observed by its own address, whichever is selected: a switch restarts no read,
     * and every wallet keeps its numbers and its networks' addresses through it.
     */
    private fun selectedWalletId(): Flow<String> =
        userRepository.flowCurSoraAccount()
            .map { it.substrateAddress }
            .distinctUntilChanged()
            .onEach(::onSelectionChanged)

    /**
     * The picture as it was last saved, for the very first frame: read from disk right now, so it
     * is there before anything else has answered. It offers no Send and no Move target, because
     * nothing has been confirmed yet.
     *
     * Null without a saved picture, and when the selected wallet is not in it: whatever removed
     * the wallets it shows (Forget all, then a new wallet), they must not flash on the new home.
     */
    fun savedPicture(): SavedPicture? {
        val selectedId = userRepository.curSoraAccountIfLoaded()?.substrateAddress ?: return null
        val saved = readStore.loadNow() ?: return null
        if (selectedId !in saved.wallets) return null
        val xorToken = saved.xor?.toToken() ?: FALLBACK_XOR
        val oldPrices = PortfolioFreshness.pricesAreOld(saved.pricesReadAtMillis, clock())
        return SavedPicture(
            snapshot = PortfolioSnapshot(
                wallets = saved.wallets.map { (id, wallet) ->
                    PortfolioWallet(id, wallet.name, id == selectedId)
                },
                holdings = saved.wallets.flatMap { (id, wallet) ->
                    sora2Holdings(id, false, null, wallet.toRead(), priced = { it.withChangeIf(!oldPrices) }) +
                        nexusHoldings(id, null, wallet.networks, xorToken.withChangeIf(!oldPrices))
                },
                sync = saved.wallets.mapValues { (_, wallet) ->
                    WalletSync(wallet.readAtMillis, unread = !wallet.read)
                },
                connected = true,
                addresses = saved.wallets.mapValues { (id, _) -> mapOf(WalletNetworkId.SORA2 to id) },
                fiatSymbol = saved.fiatSymbol ?: OptionsProvider.fiatSymbol,
                saved = true,
                pricesReadAtMillis = saved.pricesReadAtMillis,
            ),
            scope = PortfolioScope.fromKey(saved.scope),
        )
    }

    /** Stores the person's scope with the saved picture, so the first frame has the right one. */
    suspend fun rememberScope(scope: PortfolioScope?) = readStore.saveScope(scope?.key)

    /**
     * The saved reads come first and stay until something newer replaces them. A read taken since
     * this source started is never replaced by what was saved before it.
     */
    private suspend fun restoreSavedReads() {
        if (restored) return
        restored = true
        val saved = try {
            readStore.load()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return
        savedFiatSymbol = saved.fiatSymbol
        savedXor = saved.xor?.toToken()
        savedPricesReadAt = saved.pricesReadAtMillis
        reads.update { current ->
            saved.wallets.filterValues { it.read }.mapValues { (_, wallet) -> wallet.toRead() } + current
        }
        savedNetworks.value = saved.wallets.filterValues { it.read }.mapValues { it.value.networks }
    }

    private fun SavedToken.toToken() = PortfolioToken(id, symbol, name, iconUri, precision, price, change)

    private fun PortfolioToken.toSaved() = SavedToken(id, symbol, name, iconUri, precision, price, change24h)

    /** An old price says nothing about the last 24 hours. */
    private fun PortfolioToken.withChangeIf(current: Boolean) = if (current) this else copy(change24h = null)

    private fun SavedWallet.toRead() = WalletRead(
        atMillis = readAtMillis,
        balances = sora2.mapNotNull { saved ->
            val total = saved.total.toBigDecimalOrNull() ?: return@mapNotNull null
            val transferable = saved.transferable.toBigDecimalOrNull() ?: total
            saved.token.id to ReadBalance(saved.token.toToken(), total, transferable, saved.hidden)
        }.toMap(),
    )

    private suspend fun saveReads(snapshot: PortfolioSnapshot) {
        if (snapshot.wallets.isEmpty()) {
            readStore.clear()
            return
        }
        // Only the wallets this app still has: a removed wallet's balances go with it.
        val wallets = snapshot.wallets.associate { wallet ->
            val sync = snapshot.sync[wallet.id]
            val holdings = snapshot.holdings.filter { it.walletId == wallet.id }
            wallet.id to SavedWallet(
                name = wallet.name,
                read = sync?.unread != true,
                readAtMillis = sync?.refreshedAtMillis,
                sora2 = holdings.filter { it.network == WalletNetworkId.SORA2 }.map { holding ->
                    SavedBalance(
                        token = holding.token.toSaved(),
                        total = holding.total.toPlainString(),
                        transferable = holding.transferable.toPlainString(),
                        hidden = holding.hidden,
                    )
                },
                networks = holdings.filter { it.network != WalletNetworkId.SORA2 }.map { holding ->
                    SavedNetworkBalance(holding.network.wireId, holding.address, holding.total.toPlainString())
                },
            )
        }
        try {
            readStore.save(
                SavedPortfolio(
                    fiatSymbol = snapshot.fiatSymbol,
                    xor = snapshot.holdings.firstOrNull { it.token.id == AssetHolder.XOR_ASSET_ID }?.token?.toSaved(),
                    wallets = wallets,
                    pricesReadAtMillis = snapshot.pricesReadAtMillis,
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A display cache: the next launch simply reads everything again.
        }
    }

    /**
     * A wallet that was selected until now keeps its rows, which are newer than any earlier read,
     * so the switch time becomes its last sync. The newly selected wallet keeps its last read until
     * its rows hold something.
     */
    private fun onSelectionChanged(selectedId: String) {
        val previous = lastSelectedId
        lastSelectedId = selectedId
        if (previous == null || previous == selectedId) return
        val now = clock()
        reads.update { current -> current + (previous to WalletRead(now, null)) }
    }

    private fun observeWallet(account: SoraAccount): Flow<WalletRaw> {
        val id = account.substrateAddress
        // Every whitelisted row, whatever its visibility: all the wallet holds. A row flow can wait
        // for the app's remote configuration, so it never holds the first picture back.
        // A screen subscription or wallet-list change restarts this flow. Preserve a confirmed
        // zero across that restart, without calling the remembered first frame live.
        var lastRows = rememberedRows.value[id]?.copy(remembered = true) ?: WalletRows(null)
        val rows: Flow<WalletRows> = assetsRepository.subscribeAssetsOfWhitelist(id)
            .scan(lastRows) { previous, assets ->
                WalletRows(assets, confirmed = previous.confirmed || assets.any { it.balance.total.signum() > 0 })
            }
            .onEach {
                lastRows = it
                if (it.confirmed && !it.remembered) rememberedRows.update { rows -> rows + (id to it) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                // A database observation failure cannot bring back a balance from before a spend.
                emit(lastRows.copy(failed = true))
            }
        // Balances only: the Portfolio view shows no history, so it never asks Torii for one.
        val nexus: Flow<List<NexusPortfolioBalance>?> =
            nexusPortfolioRepository.observeWallet(id, history = false)
                .onEach { rememberNetworks(id, it) }
                .map<List<NexusPortfolioBalance>, List<NexusPortfolioBalance>?> { it }
                .onStart { emit(null) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(emptyList())
                }
        return combine(rows, nexus) { r, n -> WalletRaw(account, r, n, clock()) }
    }

    /** A later failed read keeps the latest successful amount, including a confirmed zero. */
    private fun rememberNetworks(walletId: String, rows: List<NexusPortfolioBalance>) {
        savedNetworks.update { saved ->
            val previous = saved[walletId].orEmpty().associateBy { it.network }
            val latest = rows.mapNotNull { row ->
                if (!row.networkLive) return@mapNotNull null
                val quantity = row.xorQuantity?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
                if (quantity != null) {
                    SavedNetworkBalance(row.networkId.wireId, row.address, quantity.toPlainString())
                } else {
                    previous[row.networkId.wireId]?.takeIf { it.address == row.address }
                }
            }
            saved + (walletId to latest)
        }
    }

    /** The whitelist's tokens as the app prices them now; empty until they have loaded. */
    private fun observeTokens(): Flow<PricedTokens> = combine(
        assetsRepository.subscribeTokensList()
            .map { tokens ->
                tokens.firstNotNullOfOrNull { it.fiatSymbol }?.let { tokenFiatSymbol = it }
                tokens.associate { it.id to portfolioToken(it) }
            }
            .onStart { emit(emptyMap()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyMap())
            },
        assetsRepository.subscribeNewestPriceMillis()
            // New prices come every 20 s, and their time only matters once they stop coming.
            .distinctUntilChanged { old, new ->
                old != null && new != null && new >= old && new - old < PRICE_TIME_STEP_MS
            }
            .onStart { emit(null) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(null)
            },
        ::PricedTokens,
    )

    /**
     * A socket that is still connecting (every cold launch) is not "offline": saved values show
     * their age meanwhile, and OFFLINE only appears when the node stays away.
     */
    private fun observeNode(): Flow<NodeState> =
        connectionManager.connectionState
            .onStart { emit(connectionManager.isConnected) }
            .distinctUntilChanged()
            .transformLatest { up ->
                if (up) {
                    emit(NodeState(up = true, offline = false))
                } else {
                    emit(NodeState(up = false, offline = false))
                    delay(OFFLINE_AFTER_MS)
                    emit(NodeState(up = false, offline = true))
                }
            }

    private fun build(
        accounts: List<SoraAccount>,
        selectedId: String,
        raws: List<WalletRaw>,
        freshReads: Map<String, WalletRead>,
        saved: Map<String, List<SavedNetworkBalance>>,
        failed: Set<String>,
        pricedTokens: PricedTokens,
        node: NodeState,
    ): PortfolioSnapshot {
        val tokens = pricedTokens.tokens
        val currentPrices = !PortfolioFreshness.pricesAreOld(pricedTokens.readAtMillis, clock())
        // One token, one price, in every wallet and on every network.
        val priced = { token: PortfolioToken -> (tokens[token.id] ?: token).withChangeIf(currentPrices) }
        val xorToken = (
            tokens[AssetHolder.XOR_ASSET_ID]
                ?: raws.asSequence()
                    .flatMap { it.rows.assets.orEmpty().asSequence() }
                    .firstOrNull { it.token.id == AssetHolder.XOR_ASSET_ID }
                    ?.token?.let(::portfolioToken)
                ?: freshReads.values.asSequence()
                    .mapNotNull { it.balances?.get(AssetHolder.XOR_ASSET_ID)?.token }
                    .firstOrNull()
                ?: savedXor
                ?: FALLBACK_XOR
            ).withChangeIf(currentPrices)
        val holdings = mutableListOf<PortfolioHolding>()
        val sync = mutableMapOf<String, WalletSync>()
        val addresses = mutableMapOf<String, Map<WalletNetworkId, String>>()
        raws.forEach { raw ->
            val id = raw.account.substrateAddress
            val read = freshReads[id]
            val rows = raw.rows.assets
            val sora2 = sora2Holdings(id, id == selectedId, rows, read, priced, raw.rows.confirmed)
            val nexus = nexusHoldings(id, raw.nexus, saved[id].orEmpty(), xorToken)
            holdings += sora2
            holdings += nexus
            val sora2Known = read != null || raw.rows.confirmed
            val unread = !sora2Known ||
                (sora2.isEmpty() && nexus.isEmpty() && raw.nexus == null && id !in saved)
            val unreachable = buildSet {
                if (raw.rows.failed || (!sora2Known && id in failed)) add(WalletNetworkId.SORA2)
                raw.nexus.orEmpty().filter { it.networkLive && it.xorQuantity == null }.forEach { add(it.networkId) }
            }
            val live = id == selectedId && raw.rows.confirmed && !raw.rows.remembered && !raw.rows.failed && node.up
            sync[id] = WalletSync(
                refreshedAtMillis = if (live) maxOf(raw.emittedAtMillis, read?.atMillis ?: 0L) else read?.atMillis,
                unreachable = unreachable,
                live = live,
                unread = unread,
            )
            addresses[id] = mapOf(WalletNetworkId.SORA2 to id) + raw.nexus.orEmpty().associate { it.networkId to it.address }
        }
        return PortfolioSnapshot(
            wallets = accounts.map {
                PortfolioWallet(it.substrateAddress, it.accountTitle(), it.substrateAddress == selectedId)
            },
            holdings = holdings,
            sync = sync,
            connected = !node.offline,
            addresses = addresses,
            fiatSymbol = tokenFiatSymbol ?: savedFiatSymbol ?: OptionsProvider.fiatSymbol,
            pricesReadAtMillis = pricedTokens.readAtMillis ?: savedPricesReadAt,
        )
    }

    /**
     * Which SORA2 balances a wallet shows. The selected wallet shows its rows once they hold
     * something; until then (its rows have not caught up after an import, or are still loading)
     * its last read. Any other wallet shows its last read when it has one.
     */
    private fun sora2Holdings(
        id: String,
        selected: Boolean,
        rows: List<Asset>?,
        read: WalletRead?,
        priced: (PortfolioToken) -> PortfolioToken,
        rowsConfirmed: Boolean = false,
    ): List<PortfolioHolding> {
        val balances = read?.balances
        val rowsHold = rows.orEmpty().any { it.balance.total.signum() > 0 }
        val useRead = balances != null && when {
            rows == null -> true
            selected -> !rowsConfirmed && !rowsHold && balances.isNotEmpty()
            else -> true
        }
        if (useRead) {
            val rowById = rows.orEmpty().associateBy { it.token.id }
            return checkNotNull(balances).values.filter { it.total.signum() > 0 }.map { balance ->
                val hidden = rowById[balance.token.id]?.let(::hiddenByUser) ?: balance.hidden
                PortfolioHolding(
                    walletId = id,
                    network = WalletNetworkId.SORA2,
                    token = priced(balance.token),
                    total = balance.total,
                    transferable = balance.transferable.min(balance.total).max(BigDecimal.ZERO),
                    hidden = hidden,
                    address = id,
                )
            }
        }
        return rows.orEmpty().filter { it.balance.total.signum() > 0 }.map { asset ->
            PortfolioHolding(
                walletId = id,
                network = WalletNetworkId.SORA2,
                token = priced(portfolioToken(asset.token)),
                total = asset.balance.total,
                transferable = asset.balance.transferable.min(asset.balance.total).max(BigDecimal.ZERO),
                hidden = hiddenByUser(asset),
                address = id,
            )
        }
    }

    /**
     * The known tokens start visible, so one turned off is the user's choice. Any other whitelisted
     * token starts off and stays off until someone turns it on, so "off" says nothing about it:
     * a token the wallet really holds is listed.
     */
    private fun hiddenByUser(asset: Asset): Boolean =
        !asset.visibility && asset.token.isHidable && AssetHolder.isKnownAsset(asset.token.id)

    /**
     * A network's answer wins. While it has not answered, or when its read failed, the last saved
     * balance stays, without Send (the network has not confirmed anything yet). A network that
     * answered and no longer lists the wallet (Taira turned off) shows nothing.
     */
    private fun nexusHoldings(
        id: String,
        rows: List<NexusPortfolioBalance>?,
        saved: List<SavedNetworkBalance>,
        xorToken: PortfolioToken,
    ): List<PortfolioHolding> {
        val answered = rows.orEmpty().associateBy { it.networkId }
        val live = rows.orEmpty().mapNotNull { balance ->
            val quantity = balance.xorQuantity?.toBigDecimalOrNull()
            if (!balance.networkLive || quantity == null || quantity.signum() <= 0) {
                null
            } else {
                PortfolioHolding(
                    walletId = id,
                    network = balance.networkId,
                    token = xorToken,
                    total = quantity,
                    transferable = quantity,
                    hidden = false,
                    address = balance.address,
                    sendAvailable = balance.sendAvailable,
                )
            }
        }
        val remembered = saved.mapNotNull { entry ->
            val network = WalletNetworkId.fromWireId(entry.network) ?: return@mapNotNull null
            val answer = answered[network]
            val waiting = rows == null || (answer != null && answer.networkLive && answer.xorQuantity == null)
            val quantity = entry.quantity.toBigDecimalOrNull()
            if (!waiting || quantity == null || quantity.signum() <= 0) {
                null
            } else {
                PortfolioHolding(
                    walletId = id,
                    network = network,
                    token = xorToken,
                    total = quantity,
                    transferable = quantity,
                    hidden = false,
                    address = entry.address,
                    sendAvailable = false,
                )
            }
        }
        return live + remembered
    }

    /**
     * Reads every wallet at once, each on its own: a slow wallet or node never holds back the
     * others, and each answer shows as soon as it arrives.
     */
    override suspend fun refresh(walletIds: Collection<String>) {
        // Nexus balances are re-read for every observer; the repository keeps its own safety checks.
        nexusPortfolioRepository.refresh()
        withContext(workContext) {
            walletIds.distinct().map { id ->
                async { readPermits.withPermit { readWallet(id) } }
            }.awaitAll()
        }
    }

    /**
     * Reads one wallet's SORA2 balances from the chain, read-only. A read that does not answer
     * within [READ_TIMEOUT_MS] (no node, a stalled socket) counts as failed: the wallet keeps its
     * last values and shows their age.
     */
    private suspend fun readWallet(id: String) {
        val read = try {
            withTimeoutOrNull(READ_TIMEOUT_MS) { readBalances(id) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val now = clock()
        if (read != null) {
            reads.update { it + (id to WalletRead(now, read)) }
            failedReads.update { it - id }
        } else {
            failedReads.update { it + id }
        }
        lastReadsState.update { it + (id to now) }
    }

    private suspend fun readBalances(id: String): Map<String, ReadBalance> {
        // The app's token list as stored; only a first launch has to wait for it to be fetched.
        val tokens = assetsRepository.subscribeTokensList().first().ifEmpty { assetsRepository.tokensList() }
        val others = tokens.filter { it.id != AssetHolder.XOR_ASSET_ID }
        val raw = if (others.isEmpty()) emptyList() else assetsRepository.fetchBalance(id, others.map { it.id })
        check(raw.size == others.size) { "PORTFOLIO_BALANCE_READ_MISMATCH" }
        val balances = others.zip(raw).mapNotNull { (token, value) ->
            val amount = mapBalance(value, token.precision)
            if (amount.signum() > 0) token.id to ReadBalance(portfolioToken(token), amount, amount) else null
        }.toMap(linkedMapOf())
        val xor = tokens.firstOrNull { it.id == AssetHolder.XOR_ASSET_ID }
        val balance = assetsRepository.getXORBalance(id, xor?.precision ?: OptionsProvider.defaultScale)
        if (balance.totalBalance.signum() > 0) {
            balances[AssetHolder.XOR_ASSET_ID] = ReadBalance(
                xor?.let(::portfolioToken) ?: FALLBACK_XOR,
                balance.totalBalance,
                balance.transferable,
            )
        }
        return balances
    }

    /**
     * Keeps the saved balances to the wallets this app has. Never returns: it follows the account
     * list for as long as the caller lives, whether or not the view is on screen.
     */
    suspend fun pruneRemovedWallets() {
        userRepository.flowSoraAccountsList().collect { accounts ->
            readStore.retain(accounts.mapTo(HashSet()) { it.substrateAddress })
        }
    }

    override suspend fun select(walletId: String) {
        val account = userRepository.soraAccountsList().firstOrNull { it.substrateAddress == walletId }
            ?: throw IllegalStateException("PORTFOLIO_WALLET_NOT_FOUND")
        userRepository.setCurSoraAccount(account)
    }

    companion object {
        /** How long one wallet's read may take before it counts as failed. */
        internal const val READ_TIMEOUT_MS = 20_000L

        /** Well under [PortfolioFreshness.PRICES_OLD_AFTER_MS], so prices that keep coming are never old. */
        internal const val PRICE_TIME_STEP_MS = 5 * 60_000L

        /** Wallets read at the same moment; the rest follow as soon as one finishes. */
        internal const val MAX_PARALLEL_READS = 4

        /** How long the picture must hold still before it is saved. */
        internal const val SAVE_DELAY_MS = 2_000L

        /** How long the node may be away before the view says it is offline. */
        internal const val OFFLINE_AFTER_MS = 15_000L

        private val FALLBACK_XOR = PortfolioToken(
            id = AssetHolder.XOR_ASSET_ID,
            symbol = "XOR",
            name = "XOR",
            iconUri = DEFAULT_ICON_URI,
            precision = OptionsProvider.defaultScale,
            price = null,
            change24h = null,
        )

        internal fun portfolioToken(token: Token): PortfolioToken = PortfolioToken(
            id = token.id,
            symbol = token.symbol,
            name = token.name,
            iconUri = token.iconUri(),
            precision = token.precision,
            price = token.fiatPrice,
            change24h = token.fiatPriceChange,
        )
    }
}
