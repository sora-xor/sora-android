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
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.FanSlice
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

/** Everything the dashboard draws, computed from one [PortfolioSnapshot]. */
data class PortfolioModel(
    val snapshot: PortfolioSnapshot,
    val sample: Boolean,
    val nowMillis: Long,
    val scope: PortfolioScope,
    val walletIds: List<String>,
    val lens: PortfolioLens,
    val lenses: List<PortfolioLens>,
    val totals: PortfolioTotals,
    val freshness: WalletFreshness?,
    val groups: List<PortfolioGroup>,
    val fan: List<FanSlice>,
    /** Slot per entity key for the active scope and lens. */
    val slots: Map<String, Int>,
    /** Slot per wallet id: a wallet's colour everywhere (dots, avatars, the Wallets lens). */
    val walletSlots: Map<String, Int>,
    val wallets: List<WalletSummary>,
    val allTotals: PortfolioTotals,
    val allFreshness: WalletFreshness?,
    val highlighted: String?,
    val expanded: Set<String>,
    val swapDisclaimerPending: Boolean,
) {
    val selectedWallet get() = snapshot.selectedWallet
    val allWalletsAvailable: Boolean get() = snapshot.wallets.size >= 2

    /** No holdings at all in scope: the empty state (sky without the fan, "Your wallet is ready"). */
    val empty: Boolean get() = snapshot.holdings.none { it.walletId in walletIds }

    fun holding(key: String): PortfolioHolding? = snapshot.holdings.firstOrNull { it.key == key }

    fun wallet(id: String) = snapshot.wallet(id)

    fun walletSummary(id: String): WalletSummary? = wallets.firstOrNull { it.wallet.id == id }
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

    private val snapshot: StateFlow<PortfolioSnapshot?> = source
        .flatMapLatest { it.observe() }
        // Each wallet's reads already fall back on their own; this is the account list itself.
        // It is recorded and read again a little later, and the hero keeps its loading state.
        .retryWhen { error, attempt ->
            if (error is CancellationException) return@retryWhen false
            onBackgroundError(error)
            delay(minOf(RETRY_MAX_MS, RETRY_BASE_MS shl attempt.coerceAtMost(4L).toInt()))
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val scopeRequest = MutableStateFlow<PortfolioScope?>(null)
    private val lensRequest = MutableStateFlow<PortfolioLens?>(null)
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
        val scope: PortfolioScope?,
        val lens: PortfolioLens?,
        val slots: Map<String, Map<String, Int>>?,
    )

    private data class Selection(val highlight: String?, val expanded: Set<String>)

    private data class Built(val model: PortfolioModel, val slotUpdates: Map<String, Map<String, Int>>)

    val model: StateFlow<PortfolioModel?> = combine(
        combine(snapshot, source) { snapshot, source -> snapshot?.let { it to source.isSample } },
        combine(scopeRequest, lensRequest, slotMaps, ::Request),
        combine(highlight, expandedRows, ::Selection),
        clock,
        disclaimerPending,
    ) { data, request, selection, now, disclaimer ->
        if (data == null || request.slots == null) null else build(data.first, data.second, request, selection, now, disclaimer)
    }
        // The debug sample never writes the device's slot maps.
        .onEach { built ->
            val updates = built?.slotUpdates.orEmpty()
            if (built != null && !built.model.sample && updates.isNotEmpty()) storeSlots(updates)
        }
        .map { it?.model }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            scopeRequest.value = preferences.scope()
            lensRequest.value = preferences.lens()
            slotMaps.value = preferences.slotMaps()
        }
        viewModelScope.launch {
            // While the dashboard is visible: tick the freshness ages and re-read wallets that are
            // due (each wallet at most once a minute). Nothing runs while it is hidden.
            visible.collectLatest { shown ->
                if (!shown) {
                    refreshJob?.cancel()
                    return@collectLatest
                }
                while (true) {
                    clock.value = System.currentTimeMillis()
                    // A read that hangs never holds the clock back: the ages keep counting.
                    if (refreshJob?.isActive != true) refreshJob = launch { refreshDue(force = false) }
                    delay(TICK_MS)
                }
            }
        }
    }

    private fun build(
        snapshot: PortfolioSnapshot,
        sample: Boolean,
        request: Request,
        selection: Selection,
        now: Long,
        disclaimer: Boolean,
    ): Built {
        val maps = request.slots.orEmpty()
        val scope = PortfolioCalculator.effectiveScope(request.scope, snapshot.wallets.size)
        val walletIds = PortfolioCalculator.scopeWalletIds(snapshot, scope)
        val lens = PortfolioCalculator.effectiveLens(request.lens, scope)
        val groups = PortfolioCalculator.groups(snapshot, walletIds, lens)
        val updates = mutableMapOf<String, Map<String, Int>>()

        // A wallet's colour: one map over every wallet (All wallets · Wallets), largest first.
        val walletKey = PortfolioSlots.mapKey(PortfolioScope.ALL, null, PortfolioLens.WALLETS)
        val allIds = snapshot.wallets.map { it.id }
        val walletRanking = PortfolioCalculator
            .groups(snapshot, allIds, PortfolioLens.WALLETS)
            .map { it.key }
        val walletSeed = PortfolioSlots.seed(PortfolioLens.WALLETS)
        val walletSlots = PortfolioSlots.assign(maps[walletKey], walletRanking, walletSeed)
        if (snapshot.wallets.isNotEmpty() && walletSlots != maps[walletKey]) updates[walletKey] = walletSlots

        val slots = if (lens == PortfolioLens.WALLETS) {
            walletSlots
        } else {
            val key = PortfolioSlots.mapKey(scope, snapshot.selectedWallet?.id, lens)
            val ranked = groups.filter { it.countedFiat > 0.0 }.map { it.key }
            PortfolioSlots.assign(maps[key], ranked, PortfolioSlots.seed(lens)).also {
                if (ranked.isNotEmpty() && it != maps[key]) updates[key] = it
            }
        }
        val model = PortfolioModel(
            snapshot = snapshot,
            sample = sample,
            nowMillis = now,
            scope = scope,
            walletIds = walletIds,
            lens = lens,
            lenses = PortfolioCalculator.availableLenses(scope),
            totals = PortfolioCalculator.scopeTotals(snapshot, walletIds),
            freshness = PortfolioCalculator.worstFreshness(snapshot, walletIds, now),
            groups = groups,
            fan = PortfolioCalculator.fan(groups),
            slots = slots,
            walletSlots = walletSlots,
            wallets = PortfolioCalculator.walletSummaries(snapshot, now),
            allTotals = PortfolioCalculator.scopeTotals(snapshot, allIds),
            allFreshness = PortfolioCalculator.worstFreshness(snapshot, allIds, now),
            highlighted = selection.highlight?.takeIf { key -> groups.any { it.key == key } },
            expanded = selection.expanded,
            swapDisclaimerPending = disclaimer,
        )
        return Built(model, updates)
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
        val scope = model.value?.scope ?: PortfolioScope.SELECTED
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

    // ── Scope, lens, legend, rows ─────────────────────────────────────────────────────────────

    fun openSheet(sheet: PortfolioSheet) {
        _sheet.value = sheet
    }

    fun closeSheet() {
        _sheet.value = null
    }

    fun selectAllWallets() {
        scopeRequest.value = PortfolioScope.ALL
        highlight.value = null
        closeSheet()
        viewModelScope.launch { preferences.setScope(PortfolioScope.ALL) }
    }

    /** Picking one wallet shows it alone; a wallet that is not selected is switched to first. */
    fun selectWallet(walletId: String) {
        viewModelScope.launch {
            if (!ensureSelected(walletId)) return@launch
            scopeRequest.value = PortfolioScope.SELECTED
            highlight.value = null
            closeSheet()
            preferences.setScope(PortfolioScope.SELECTED)
        }
    }

    fun selectLens(lens: PortfolioLens) {
        lensRequest.value = lens
        highlight.value = null
        viewModelScope.launch { preferences.setLens(lens) }
    }

    /** First tap highlights a group; a second tap on a token group opens its "Where it lives". */
    fun onLegendTap(key: String?) {
        val current = model.value ?: return
        if (key == null) {
            highlight.value = null
            return
        }
        if (highlight.value == key) {
            if (current.lens == PortfolioLens.TOKENS) {
                _effects.trySend(PortfolioEffect.OpenWhereItLives(key))
            }
            highlight.value = null
        } else {
            highlight.value = key
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
            when (action) {
                WalletAction.SEND -> assetsRouter.showContacts(AssetHolder.XOR_ASSET_ID)
                WalletAction.RECEIVE -> walletRouter.openQrCodeFlow()
            }
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

    fun openAssetDetails(holdingKey: String) {
        viewModelScope.launch {
            val holding = model.value?.holding(holdingKey) ?: return@launch
            if (holding.network != WalletNetworkId.SORA2) return@launch
            if (!ensureSelected(holding.walletId)) return@launch
            assetsRouter.showAssetDetails(holding.token.id)
        }
    }

    /**
     * Acting on a holding of a wallet that is not selected switches to it first, with the app's
     * existing account switch. Returns false (and says why) when the switch did not happen.
     */
    private suspend fun ensureSelected(walletId: String): Boolean {
        val current = snapshot.value ?: return false
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
