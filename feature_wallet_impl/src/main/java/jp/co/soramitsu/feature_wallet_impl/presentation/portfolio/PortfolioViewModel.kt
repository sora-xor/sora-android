package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.Optional
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.LivePortfolioSource
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PinnedTilt
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioDebugHooks
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioPreferences
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioSource
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.SavedPicture
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FanSlice
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FreshnessKind
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioCalculator
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFreshness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioGroup
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioLens
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioReadiness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioScope
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSlots
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioTotals
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletFreshness
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Why the total is a dash. */
enum class NoTotal { PRICE, TESTNET, READING, FAILED }

/** Everything the dashboard draws, computed from one [PortfolioSnapshot]. */
data class PortfolioModel(
    val snapshot: PortfolioSnapshot,
    val sample: Boolean,
    val nowMillis: Long,
    val scope: PortfolioScope,
    val walletIds: List<String>,
    val totals: PortfolioTotals,
    val freshness: WalletFreshness?,
    /** One group per token held in scope, largest counted value first. */
    val groups: List<PortfolioGroup>,
    val fan: List<FanSlice>,
    /** Slot per token for the active scope. */
    val slots: Map<String, Int>,
    /** Slot per wallet id: a wallet's colour everywhere. */
    val walletSlots: Map<String, Int>,
    val wallets: List<WalletSummary>,
    val allTotals: PortfolioTotals,
    val allFreshness: WalletFreshness?,
    /** The token picked on the ring or its legend, if any. */
    val highlighted: String?,
    /** The list's rows opened to their holdings. */
    val expanded: Set<String>,
    val swapDisclaimerPending: Boolean,
) {
    val selectedWallet get() = snapshot.selectedWallet
    val allWalletsAvailable: Boolean get() = snapshot.wallets.size >= 2

    /** Set while the total is valued with old prices: the time of the "Prices as of" note. */
    val pricesAsOf: Long?
        get() = snapshot.pricesReadAtMillis?.takeIf {
            totals.known && PortfolioFreshness.pricesAreOld(it, nowMillis)
        }

    /**
     * Every wallet in scope was read and really holds nothing: "Your wallet is ready". A wallet
     * that has not been read yet, or a network that could not be reached, is not empty.
     */
    val empty: Boolean
        get() = !holdsAnything &&
            walletIds.all { id -> snapshot.sync[id]?.let { !it.unread && it.unreachable.isEmpty() } ?: true }

    /** Something is held in scope, hidden or not. */
    val holdsAnything: Boolean
        get() = snapshot.holdings.any { it.walletId in walletIds }

    /**
     * Why there is no total to show: never a zero. A wallet that could not be read says so first,
     * since its balance is unknown; while it is still being read the list says that instead.
     */
    val noTotal: NoTotal?
        get() {
            val failed = freshness?.kind == FreshnessKind.OFFLINE || freshness?.unreachable?.isNotEmpty() == true
            return when {
                totals.known -> null
                failed && (totals.unread > 0 || !holdsAnything) -> NoTotal.FAILED
                totals.unpriced > 0 -> NoTotal.PRICE
                totals.testnet > 0 -> NoTotal.TESTNET
                totals.unread > 0 -> NoTotal.READING
                // Everything held is hidden, which the caption says; or the empty state shows.
                else -> null
            }
        }

    fun holding(key: String): PortfolioHolding? = snapshot.holdings.firstOrNull { it.key == key }

    fun wallet(id: String) = snapshot.wallet(id)
}

/** Sheets of the dashboard (portfolio-view SPEC §3.2, §3.3, §7). */
sealed interface PortfolioSheet {
    data object Wallets : PortfolioSheet
    data object HowWeCount : PortfolioSheet
    data object Sync : PortfolioSheet
    data class Move(val holdingKey: String?) : PortfolioSheet
    data class Swap(val holdingKey: String?) : PortfolioSheet
    data class PickWallet(val action: WalletAction) : PortfolioSheet
}

/** Quick actions that ask for a wallet first in All wallets. */
enum class WalletAction { SEND, RECEIVE }

/** One-off events for the host fragment. */
sealed interface PortfolioEffect {
    data class OpenWhereItLives(val tokenId: String) : PortfolioEffect

    /** Hands off to the app's existing Nexus send for the selected wallet's [network] account. */
    data class OpenNexusSend(
        val walletId: String,
        val network: WalletNetworkId,
        val address: String,
        val recipient: String?,
        val amount: String?,
    ) : PortfolioEffect

    /** Opens a network's XOR page for the wallet the dashboard just selected. */
    data class OpenNexusPage(
        val walletId: String,
        val network: WalletNetworkId,
        val address: String,
    ) : PortfolioEffect

    /** Send or Receive after the wallet was picked: which network, if the wallet has a choice. */
    data class ContinueAction(val action: WalletAction, val walletId: String) : PortfolioEffect

    data class Message(@param:StringRes val text: Int, val argument: String? = null) : PortfolioEffect
}

/**
 * The Portfolio view on the Wallet tab (portfolio-view SPEC). It reads every wallet's balances
 * read-only and only hands off: Move and Swap open the app's existing Send, Nexus send and
 * Polkaswap screens with the parameters filled in. It never builds or signs a transaction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val liveSource: LivePortfolioSource,
    private val preferences: PortfolioPreferences,
    debugHooks: Optional<PortfolioDebugHooks>,
    private val walletRouter: WalletRouter,
    private val assetsRouter: AssetsRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val mainRouter: MainRouter,
    swapInteractor: SwapInteractor,
) : BaseViewModel() {

    private val hooks: PortfolioDebugHooks? = debugHooks.orElse(null)

    private val source: StateFlow<PortfolioSource> = (hooks?.sampleSource ?: MutableStateFlow(null))
        .map { it ?: liveSource }
        .stateIn(viewModelScope, SharingStarted.Eagerly, liveSource)

    /** Debug builds only: a deflection pinned for screenshots. Always null in production. */
    val pinnedTilt: StateFlow<PinnedTilt?> = hooks?.pinnedTilt ?: MutableStateFlow(null)

    /** The last saved picture: there for the very first frame, before anything has answered. */
    private val savedPicture: SavedPicture? = if (hooks?.sampleSource?.value == null) liveSource.savedPicture() else null

    private val snapshot: StateFlow<PortfolioSnapshot?> = source
        .flatMapLatest { it.observe() }
        // Each wallet's reads already fall back on their own; this is the account list itself.
        // It is recorded and read again a little later; the saved picture stays meanwhile.
        .retryWhen { error, attempt ->
            if (error is CancellationException) return@retryWhen false
            onBackgroundError(error)
            delay(minOf(RETRY_MAX_MS, RETRY_BASE_MS shl attempt.coerceAtMost(4L).toInt()))
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), savedPicture?.snapshot)

    /** The stored scope; the outer null means it has not been read yet. */
    private val scopeRequest = MutableStateFlow(savedPicture?.let { Optional.ofNullable(it.scope) })
    private val slotMaps = MutableStateFlow<Map<String, Map<String, Int>>?>(null)
    private val highlight = MutableStateFlow<String?>(null)
    private val expandedRows = MutableStateFlow<Set<String>>(emptySet())
    private val visible = MutableStateFlow(false)
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val disclaimerPending: StateFlow<Boolean> = swapInteractor.getPolkaswapDisclaimerVisibility()
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    private val _sheet = MutableStateFlow<PortfolioSheet?>(null)
    val sheet: StateFlow<PortfolioSheet?> = _sheet.asStateFlow()

    private val _effects = Channel<PortfolioEffect>(Channel.BUFFERED)
    val effects: Flow<PortfolioEffect> = _effects.receiveAsFlow()

    /** The wallet re-read in flight, if any; one at a time. */
    private var refreshJob: Job? = null

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private data class Request(
        val scope: Optional<PortfolioScope>?,
        val slots: Map<String, Map<String, Int>>?,
        val highlight: String?,
        val expanded: Set<String>,
    )

    private data class Built(val model: PortfolioModel, val slotUpdates: Map<String, Map<String, Int>>)

    val model: StateFlow<PortfolioModel?> = combine(
        combine(snapshot, source) { snapshot, source -> snapshot?.let { it to source.isSample } },
        combine(scopeRequest, slotMaps, highlight, expandedRows, ::Request),
        clock,
        disclaimerPending,
    ) { data, request, now, disclaimer ->
        // Without stored slot maps the seed applies: the saved balances never wait for them.
        if (data == null || request.scope == null) {
            null
        } else {
            build(data.first, data.second, request, now, disclaimer)
        }
    }
        // The debug sample never writes the device's slot maps.
        .onEach { built ->
            val updates = built?.slotUpdates.orEmpty()
            if (built != null && !built.model.sample && updates.isNotEmpty()) storeSlots(updates)
        }
        .map { it?.model }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            savedPicture?.let { picture ->
                val request = Request(Optional.ofNullable(picture.scope), null, null, emptySet())
                build(picture.snapshot, false, request, clock.value, disclaimer = true).model
            },
        )

    init {
        viewModelScope.launch {
            val stored = preferences.scope()
            scopeRequest.value = Optional.ofNullable(stored)
            liveSource.rememberScope(stored)
        }
        viewModelScope.launch { slotMaps.value = preferences.slotMaps() }
        viewModelScope.launch {
            // While the dashboard is visible: tick the freshness ages and re-read wallets that are
            // due (each wallet at most once a minute), starting as soon as there is a picture to
            // refresh. Nothing runs while it is hidden.
            visible.collectLatest { shown ->
                if (!shown) {
                    refreshJob?.cancel()
                    return@collectLatest
                }
                snapshot.first { it != null && !it.saved }
                while (true) {
                    clock.value = System.currentTimeMillis()
                    // A read that hangs never holds the clock back: the ages keep counting.
                    if (refreshJob?.isActive != true) refreshJob = launch { refreshDue(force = false) }
                    delay(TICK_MS)
                }
            }
        }
        viewModelScope.launch {
            // For as long as the home exists, also behind other screens: a wallet that is removed
            // takes its saved balances with it at once.
            try {
                liveSource.pruneRemovedWallets()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onBackgroundError(error)
            }
        }
    }

    private fun build(
        snapshot: PortfolioSnapshot,
        sample: Boolean,
        request: Request,
        now: Long,
        disclaimer: Boolean,
    ): Built {
        val maps = request.slots.orEmpty()
        val scope = PortfolioCalculator.effectiveScope(request.scope?.orElse(null), snapshot.wallets.size)
        val walletIds = PortfolioCalculator.scopeWalletIds(snapshot, scope)
        val groups = PortfolioCalculator.groups(snapshot, walletIds, PortfolioLens.TOKENS)
        val updates = mutableMapOf<String, Map<String, Int>>()

        // A wallet's colour: one map over every wallet, largest first.
        val walletKey = PortfolioSlots.mapKey(PortfolioScope.ALL, null, PortfolioLens.WALLETS)
        val allIds = snapshot.wallets.map { it.id }
        val walletRanking = PortfolioCalculator
            .groups(snapshot, allIds, PortfolioLens.WALLETS)
            .map { it.key }
        val walletSeed = PortfolioSlots.seed(PortfolioLens.WALLETS)
        val walletSlots = PortfolioSlots.assign(maps[walletKey], walletRanking, walletSeed)
        if (snapshot.wallets.isNotEmpty() && walletSlots != maps[walletKey]) updates[walletKey] = walletSlots

        val key = PortfolioSlots.mapKey(scope, snapshot.selectedWallet?.id, PortfolioLens.TOKENS)
        val ranked = groups.filter { it.countedFiat > 0.0 }.map { it.key }
        val slots = PortfolioSlots.assign(maps[key], ranked, PortfolioSlots.seed(PortfolioLens.TOKENS)).also {
            if (ranked.isNotEmpty() && it != maps[key]) updates[key] = it
        }
        val model = PortfolioModel(
            snapshot = snapshot,
            sample = sample,
            nowMillis = now,
            scope = scope,
            walletIds = walletIds,
            totals = PortfolioCalculator.scopeTotals(snapshot, walletIds),
            freshness = PortfolioCalculator.worstFreshness(snapshot, walletIds, now),
            groups = groups,
            fan = PortfolioCalculator.fan(groups),
            slots = slots,
            walletSlots = walletSlots,
            wallets = PortfolioCalculator.walletSummaries(snapshot, now),
            allTotals = PortfolioCalculator.scopeTotals(snapshot, allIds),
            allFreshness = PortfolioCalculator.worstFreshness(snapshot, allIds, now),
            highlighted = request.highlight?.takeIf { key -> groups.any { it.key == key } },
            expanded = request.expanded,
            swapDisclaimerPending = disclaimer,
        )
        // The stored colours have not been read yet: nothing to change in them, or to overwrite.
        return Built(model, if (request.slots == null) emptyMap() else updates)
    }

    private fun storeSlots(updates: Map<String, Map<String, Int>>) {
        val merged = slotMaps.value.orEmpty() + updates
        slotMaps.value = merged
        viewModelScope.launch { preferences.setSlotMaps(merged) }
    }

    /** The host calls this from onStart / onStop: reads and ticks run only while visible. */
    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    private suspend fun refreshDue(force: Boolean) {
        val current = source.value
        val snap = snapshot.value ?: return
        val scope = PortfolioCalculator.effectiveScope(scopeRequest.value?.orElse(null), snap.wallets.size)
        val wanted = if (scope == PortfolioScope.ALL || _sheet.value == PortfolioSheet.Wallets) {
            snap.wallets.map { it.id }
        } else {
            listOfNotNull(snap.selectedWallet?.id)
        }
        val now = System.currentTimeMillis()
        val due = wanted.filter { force || PortfolioFreshness.isDue(current.lastReads.value[it], now) }
        if (due.isNotEmpty()) {
            try {
                current.refresh(due)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Each wallet keeps its last read and shows its age.
            }
        }
    }

    // ── Scope and legend ──────────────────────────────────────────────────────────────────────

    fun openSheet(sheet: PortfolioSheet) {
        _sheet.value = sheet
    }

    fun closeSheet() {
        _sheet.value = null
    }

    fun selectAllWallets() {
        scopeRequest.value = Optional.of(PortfolioScope.ALL)
        highlight.value = null
        closeSheet()
        viewModelScope.launch {
            preferences.setScope(PortfolioScope.ALL)
            liveSource.rememberScope(PortfolioScope.ALL)
        }
    }

    /** Picking one wallet shows it alone; a wallet that is not selected is switched to first. */
    fun selectWallet(walletId: String) {
        viewModelScope.launch {
            if (!ensureSelected(walletId)) return@launch
            highlight.value = null
            closeSheet()
            // With one wallet there is nothing to choose: a second wallet still opens All wallets.
            if ((snapshot.value?.wallets?.size ?: 0) < 2) return@launch
            scopeRequest.value = Optional.of(PortfolioScope.SELECTED)
            preferences.setScope(PortfolioScope.SELECTED)
            liveSource.rememberScope(PortfolioScope.SELECTED)
        }
    }

    /** The first tap picks a token on the ring; a second tap opens its "Where it lives". */
    fun onLegendTap(key: String) {
        when {
            key == OTHER_KEY -> highlight.value = null
            highlight.value == key -> {
                highlight.value = null
                _effects.trySend(PortfolioEffect.OpenWhereItLives(key))
            }
            else -> highlight.value = key
        }
    }

    fun toggleRow(key: String) {
        expandedRows.update { if (key in it) it - key else it + key }
    }

    fun refreshAll() {
        clock.value = System.currentTimeMillis()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { refreshDue(force = true) }
    }

    fun manageWallets() {
        closeSheet()
        mainRouter.showAccountList()
    }

    fun manageAssets() {
        closeSheet()
        assetsRouter.showFullAssetsSettings()
    }

    // ── Quick actions ─────────────────────────────────────────────────────────────────────────

    fun onQuickSend() {
        val current = model.value ?: return
        if (current.scope == PortfolioScope.ALL) {
            openSheet(PortfolioSheet.PickWallet(WalletAction.SEND))
        } else {
            assetsRouter.showContacts(AssetHolder.XOR_ASSET_ID)
        }
    }

    fun onQuickReceive() {
        val current = model.value ?: return
        if (current.scope == PortfolioScope.ALL) {
            openSheet(PortfolioSheet.PickWallet(WalletAction.RECEIVE))
        } else {
            walletRouter.openQrCodeFlow()
        }
    }

    fun onPickWallet(action: WalletAction, walletId: String) {
        viewModelScope.launch {
            if (!ensureSelected(walletId)) return@launch
            closeSheet()
            _effects.send(PortfolioEffect.ContinueAction(action, walletId))
        }
    }

    /** SORA2's own Send or Receive for the selected wallet. */
    fun openSora2Action(action: WalletAction) {
        when (action) {
            WalletAction.SEND -> assetsRouter.showContacts(AssetHolder.XOR_ASSET_ID)
            WalletAction.RECEIVE -> walletRouter.openQrCodeFlow()
        }
    }

    /** Move from the quick action starts with the largest holding that can move, if any. */
    fun onQuickMove() {
        val current = model.value ?: return
        val snapshot = current.snapshot
        val first = defaultHolding(current) { PortfolioReadiness.moveBlock(snapshot, it) == null }
        openSheet(PortfolioSheet.Move(first?.key))
    }

    /** Swap from the quick action starts with the largest swap-ready holding, if any. */
    fun onQuickSwap() {
        val current = model.value ?: return
        val first = defaultHolding(current) { PortfolioReadiness.swapBlock(it) == null }
        openSheet(PortfolioSheet.Swap(first?.key))
    }

    private fun defaultHolding(model: PortfolioModel, usable: (PortfolioHolding) -> Boolean): PortfolioHolding? {
        val visible = PortfolioCalculator.sortHoldings(PortfolioCalculator.visibleHoldings(model.snapshot, model.walletIds))
        val selectedId = model.selectedWallet?.id
        val inSelected = visible.firstOrNull { it.walletId == selectedId && usable(it) }
        return inSelected ?: visible.firstOrNull(usable)
    }

    // ── Hand-offs (never a transaction here) ─────────────────────────────────────────────────

    fun openSwap(holdingKey: String, targetTokenId: String) {
        viewModelScope.launch {
            val holding = model.value?.holding(holdingKey) ?: return@launch
            if (PortfolioReadiness.swapBlock(holding) != null) return@launch
            if (!ensureSelected(holding.walletId)) return@launch
            closeSheet()
            polkaswapRouter.showSwap(tokenFromId = holding.token.id, tokenToId = targetTokenId)
        }
    }

    /**
     * The Polkaswap card's call to action: the Swap sheet for the token's SORA2 holding, or, when
     * the scope holds none on SORA2, the swap screen with the token as the one to get.
     */
    fun openPolkaswapFor(tokenId: String, sora2HoldingKey: String?) {
        if (sora2HoldingKey != null) {
            openSheet(PortfolioSheet.Swap(sora2HoldingKey))
        } else {
            polkaswapRouter.showSwap(tokenToId = tokenId)
        }
    }

    /** "Review in Send": the app's Send flow with the destination wallet and the asset filled in. */
    fun reviewMove(holdingKey: String, destinationWalletId: String, amount: BigDecimal?) {
        viewModelScope.launch {
            val current = model.value ?: return@launch
            val holding = current.holding(holdingKey) ?: return@launch
            if (PortfolioReadiness.moveBlock(current.snapshot, holding) != null) return@launch
            if (amount != null && (amount.signum() <= 0 || amount > holding.transferable)) return@launch
            val recipient = current.snapshot.addresses[destinationWalletId]?.get(holding.network)
            if (recipient.isNullOrBlank() || destinationWalletId == holding.walletId) return@launch
            if (current.sample) {
                // Sample wallets have sample addresses: never hand those to a real Send flow.
                _effects.send(PortfolioEffect.Message(R.string.portfolio_sample_handoff_off))
                return@launch
            }
            if (!ensureSelected(holding.walletId)) return@launch
            closeSheet()
            val amountText = amount?.takeIf { it.signum() > 0 }?.stripTrailingZeros()?.toPlainString()
            when (holding.network) {
                WalletNetworkId.SORA2 -> walletRouter.showValTransferAmount(recipient, holding.token.id, amountText)
                WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA -> _effects.send(
                    PortfolioEffect.OpenNexusSend(holding.walletId, holding.network, holding.address, recipient, amountText)
                )
            }
        }
    }

    /** Send within the holding's own network, through the app's existing Send or Nexus send. */
    fun send(holdingKey: String) {
        viewModelScope.launch {
            val current = model.value ?: return@launch
            val holding = current.holding(holdingKey) ?: return@launch
            if (!holding.sendAvailable) return@launch
            if (current.sample && holding.network != WalletNetworkId.SORA2) {
                _effects.send(PortfolioEffect.Message(R.string.portfolio_sample_handoff_off))
                return@launch
            }
            if (!ensureSelected(holding.walletId)) return@launch
            when (holding.network) {
                WalletNetworkId.SORA2 -> assetsRouter.showContacts(holding.token.id)
                WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA -> _effects.send(
                    PortfolioEffect.OpenNexusSend(holding.walletId, holding.network, holding.address, null, null)
                )
            }
        }
    }

    /** "Receive on SORA2": the app's receive screen for the holding's wallet. */
    fun receiveOnSora2(walletId: String) {
        viewModelScope.launch {
            if (!ensureSelected(walletId)) return@launch
            closeSheet()
            walletRouter.openQrCodeFlow()
        }
    }

    /** The holding's full page: SORA2's asset page, or that network's XOR page. */
    fun openAssetDetails(holdingKey: String) {
        viewModelScope.launch {
            val holding = model.value?.holding(holdingKey) ?: return@launch
            if (!ensureSelected(holding.walletId)) return@launch
            when (holding.network) {
                WalletNetworkId.SORA2 -> assetsRouter.showAssetDetails(holding.token.id)
                WalletNetworkId.MINAMOTO, WalletNetworkId.TAIRA -> _effects.send(
                    PortfolioEffect.OpenNexusPage(holding.walletId, holding.network, holding.address)
                )
            }
        }
    }

    /**
     * Acting on a holding of a wallet that is not selected switches to it first, with the app's
     * existing account switch. Returns false (and says why) when the switch did not happen.
     */
    private suspend fun ensureSelected(walletId: String): Boolean {
        // The saved picture only shows: acting needs what the app says is selected now.
        val current = withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
            snapshot.first { it != null && !it.saved }
        } ?: return false
        if (current.selectedWallet?.id == walletId) return true
        if (_busy.value) return false
        _busy.value = true
        return try {
            source.value.select(walletId)
            withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
                snapshot.first { it?.selectedWallet?.id == walletId }
            } != null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _effects.send(PortfolioEffect.Message(R.string.portfolio_switch_failed))
            false
        } finally {
            _busy.value = false
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 5_000L
        private const val SWITCH_TIMEOUT_MS = 5_000L
        private const val RETRY_BASE_MS = 2_000L
        private const val RETRY_MAX_MS = 30_000L
    }
}
