package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.PiIndexerOfflineFallbackPolicy
import jp.co.soramitsu.feature_blockexplorer_api.data.PiQualifiedRead
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktAccountPosition
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktAccountTrade
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktCatalogCache
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktMarket
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktMarketSnapshot
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktSignals
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkaswapIndexerClient
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktClaimAuthorization
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktMutationResult
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktOutcome
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktPendingTransaction
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktTradeQuote
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktTradeSide
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktTraderRepository
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktWebContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

data class PolkamarktScreenState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val markets: List<PolkamarktMarket> = emptyList(),
    val snapshots: List<PolkamarktMarketSnapshot> = emptyList(),
    val positions: List<PolkamarktAccountPosition> = emptyList(),
    val trades: List<PolkamarktAccountTrade> = emptyList(),
    val signals: PolkamarktSignals? = null,
    val selectedMarket: PolkamarktMarket? = null,
    val authoritativeMarket: PolkamarktMarket? = null,
    val authoritativeClaimable: PolkamarktClaimableDto? = null,
    val authoritativeFinalizedBlockHash: String? = null,
    val authoritativeClaims: Map<Long, PolkamarktClaimableDto> = emptyMap(),
    val reviewedClaimMarketIds: Set<Long> = emptySet(),
    val claimReviewFinalizedBlockHash: String? = null,
    val claimReviewLoading: Boolean = false,
    val marketDetailLoading: Boolean = false,
    val search: String = "",
    val statusFilter: String = PolkamarktWebContract.STATUS_FILTERS.first(),
    val categoryFilter: String = "all",
    val mineOnly: Boolean = false,
    val accountAddress: String = "",
    val side: PolkamarktTradeSide = PolkamarktTradeSide.BUY,
    val outcome: PolkamarktOutcome = PolkamarktOutcome.YES,
    val amountInput: String = "",
    val slippageBps: Int = PolkamarktWebContract.DEFAULT_SLIPPAGE_BPS,
    val quote: PolkamarktTradeQuote? = null,
    val quoteLoading: Boolean = false,
    val mutationLoading: Boolean = false,
    val pendingClaimConfirmation: PolkamarktClaimConfirmation? = null,
    val mutationsEnabled: Boolean = false,
    val usingCachedData: Boolean = false,
    val pendingTransactions: List<PolkamarktPendingTransaction> = emptyList(),
    val pendingRecoveryRequired: Boolean = false,
    val lastMutation: PolkamarktMutationResult? = null,
    val errorCode: String? = null,
)

internal object PolkamarktReadProvenance {
    fun requireCoherentAndUsesCache(
        reads: List<PiQualifiedRead<*>>,
    ): Boolean {
        val expected = reads.firstOrNull()?.health ?: return false
        check(reads.all { it.health == expected }) {
            "PI_INDEXER_POLKAMARKT_CHECKPOINT_CHANGED"
        }
        return reads.any { it.fromCache }
    }
}

internal object PolkamarktPendingObservationPolicy {
    const val RECOVERY_ERROR = "POLKAMARKT_PENDING_RECOVERY_REQUIRED"
    private val retryDelaysMillis = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

    fun retryDelayMillis(attempt: Long): Long =
        retryDelaysMillis[attempt.coerceAtMost(retryDelaysMillis.lastIndex.toLong()).toInt()]

    fun resolvedError(
        recoveryRequired: Boolean,
        currentError: String?,
    ): String? = when {
        recoveryRequired -> RECOVERY_ERROR
        currentError == RECOVERY_ERROR -> null
        else -> currentError
    }
}

/** Room/mapping failures fail closed but never permanently terminate pending observation. */
internal fun <T> Flow<T>.retryPolkamarktPendingObservation(
    isCurrentAccount: () -> Boolean,
    onFailure: (Throwable) -> Unit,
): Flow<T> = retryWhen { error, attempt ->
    if (error is CancellationException) throw error
    if (!isCurrentAccount()) return@retryWhen false
    onFailure(error)
    delay(PolkamarktPendingObservationPolicy.retryDelayMillis(attempt))
    isCurrentAccount()
}

/**
 * Reconciles exact persisted hashes until Room proves that no unresolved Polkamarkt row remains.
 * A pass returning no result is normal PI lag, never permission to resubmit or stop recovery.
 */
internal suspend fun reconcilePolkamarktPendingUntilTerminal(
    isCurrentAccount: () -> Boolean,
    reconcileOnce: suspend () -> Boolean,
    hasUnresolvedPending: suspend () -> Boolean,
    onFailure: (Throwable) -> Unit,
    delayBeforeRetry: suspend (Long) -> Unit = { delay(it) },
) {
    var retryAttempt = 0L
    while (isCurrentAccount()) {
        val madeProgress = try {
            val progress = reconcileOnce()
            if (!isCurrentAccount()) return
            if (!hasUnresolvedPending()) return
            progress
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!isCurrentAccount()) return
            onFailure(error)
            false
        }
        val delayAttempt = if (madeProgress) 0L else retryAttempt
        delayBeforeRetry(PolkamarktPendingObservationPolicy.retryDelayMillis(delayAttempt))
        retryAttempt = if (madeProgress) {
            0L
        } else {
            (retryAttempt + 1L).coerceAtMost(5L)
        }
    }
}

@HiltViewModel
class PolkamarktViewModel @Inject constructor(
    private val indexer: PolkaswapIndexerClient,
    private val trader: PolkamarktTraderRepository,
    private val userRepository: UserRepository,
    private val featureManager: ProductionFeatureManager,
    private val catalogCache: PolkamarktCatalogCache,
) : BaseViewModel() {
    private val _state = MutableStateFlow(PolkamarktScreenState())
    val state = _state.asStateFlow()
    private var quoteRefreshJob: Job? = null
    private var refreshJob: Job? = null
    private var pendingRecoveryJob: Job? = null
    private var quoteRequestVersion = 0L
    private var marketDetailRequestVersion = 0L
    private var claimReviewRequestVersion = 0L
    private var accountGeneration = 0L
    private var refreshGeneration = 0L
    private var currentAccountAddress: String? = null
    private var coreUsingCachedData = false
    private val mutationGate = PolkamarktMutationUseGate()

    init {
        _toolbarState.value = initSmallTitle2(R.string.polkamarkt_title)
        observeAccount()
    }

    private fun observeAccount() {
        viewModelScope.launch {
            userRepository.flowCurSoraAccount()
                .map { it.substrateAddress }
                .distinctUntilChanged()
                .collectLatest { walletId ->
                    accountGeneration += 1
                    val generation = accountGeneration
                    currentAccountAddress = walletId
                    refreshJob?.cancel()
                    pendingRecoveryJob?.cancel()
                    quoteRefreshJob?.cancel()
                    quoteRefreshJob = null
                    quoteRequestVersion += 1
                    marketDetailRequestVersion += 1
                    claimReviewRequestVersion += 1
                    coreUsingCachedData = false
                    _state.value = _state.value.copy(
                        loading = _state.value.markets.isEmpty(),
                        refreshing = _state.value.markets.isNotEmpty(),
                        positions = emptyList(),
                        trades = emptyList(),
                        authoritativeMarket = null,
                        authoritativeClaimable = null,
                        authoritativeFinalizedBlockHash = null,
                        authoritativeClaims = emptyMap(),
                        reviewedClaimMarketIds = emptySet(),
                        claimReviewFinalizedBlockHash = null,
                        claimReviewLoading = false,
                        marketDetailLoading = false,
                        accountAddress = walletId,
                        amountInput = "",
                        quote = null,
                        quoteLoading = false,
                        mutationLoading = mutationGate.isAcquired(),
                        pendingClaimConfirmation = null,
                        pendingTransactions = emptyList(),
                        pendingRecoveryRequired = false,
                        lastMutation = null,
                        errorCode = null,
                    )
                    launchRefresh(walletId, generation)
                    coroutineScope {
                        trader.observePending(walletId)
                            .retryPolkamarktPendingObservation(
                                isCurrentAccount = {
                                    isCurrentAccount(walletId, generation)
                                },
                                onFailure = {
                                    _state.value = _state.value.copy(
                                        pendingTransactions = emptyList(),
                                        pendingRecoveryRequired = true,
                                        errorCode = PolkamarktPendingObservationPolicy
                                            .RECOVERY_ERROR,
                                    )
                                },
                            )
                            .collect { pending ->
                                if (isCurrentAccount(walletId, generation)) {
                                    val recoveryRequired =
                                        PolkamarktMutationAdmissionPolicy
                                            .requiresPendingRecovery(pending)
                                    if (recoveryRequired) {
                                        quoteRefreshJob?.cancel()
                                        quoteRefreshJob = null
                                        quoteRequestVersion += 1
                                    }
                                    _state.value = _state.value.copy(
                                        pendingTransactions = pending,
                                        pendingRecoveryRequired = recoveryRequired,
                                        quote = if (recoveryRequired) {
                                            null
                                        } else {
                                            _state.value.quote
                                        },
                                        quoteLoading = if (recoveryRequired) {
                                            false
                                        } else {
                                            _state.value.quoteLoading
                                        },
                                        pendingClaimConfirmation = if (recoveryRequired) {
                                            null
                                        } else {
                                            _state.value.pendingClaimConfirmation
                                        },
                                        errorCode = PolkamarktPendingObservationPolicy
                                            .resolvedError(
                                                recoveryRequired = recoveryRequired,
                                                currentError = _state.value.errorCode,
                                            ),
                                    )
                                    if (recoveryRequired) {
                                        if (pendingRecoveryJob?.isActive != true) {
                                            launchPendingRecovery(walletId, generation)
                                        }
                                    } else {
                                        pendingRecoveryJob?.cancel()
                                    }
                                }
                            }
                    }
                }
        }
    }

    fun refresh() {
        val accountAddress = currentAccountAddress ?: return
        launchPendingRecovery(accountAddress, accountGeneration)
        launchRefresh(accountAddress, accountGeneration)
    }

    private fun launchPendingRecovery(
        accountAddress: String,
        generation: Long,
    ) {
        val previousRecovery = pendingRecoveryJob
        pendingRecoveryJob = viewModelScope.launch {
            previousRecovery?.cancelAndJoin()
            reconcilePolkamarktPendingUntilTerminal(
                isCurrentAccount = {
                    isCurrentAccount(accountAddress, generation)
                },
                reconcileOnce = {
                    trader.recoverPendingTransactions(accountAddress).isNotEmpty()
                },
                hasUnresolvedPending = {
                    trader.observePending(accountAddress).first().isNotEmpty()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        pendingRecoveryRequired = true,
                        errorCode = PolkamarktPendingObservationPolicy.RECOVERY_ERROR,
                    )
                },
            )
        }
    }

    private fun launchRefresh(
        accountAddress: String,
        generation: Long,
    ) {
        refreshGeneration += 1
        claimReviewRequestVersion += 1
        marketDetailRequestVersion += 1
        quoteRequestVersion += 1
        quoteRefreshJob?.cancel()
        quoteRefreshJob = null
        val requestGeneration = refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (
                !isCurrentRefresh(
                    accountAddress,
                    generation,
                    requestGeneration,
                )
            ) return@launch
            _state.value = _state.value.copy(
                refreshing = _state.value.loading.not(),
                authoritativeMarket = null,
                authoritativeClaimable = null,
                authoritativeFinalizedBlockHash = null,
                authoritativeClaims = emptyMap(),
                reviewedClaimMarketIds = emptySet(),
                claimReviewFinalizedBlockHash = null,
                claimReviewLoading = false,
                marketDetailLoading = _state.value.selectedMarket != null,
                quote = null,
                quoteLoading = false,
                pendingClaimConfirmation = null,
                errorCode = null,
            )
            runCatching {
                val flags = featureManager.getState()
                check(flags.polkamarktVisible) { "POLKAMARKT_DISABLED" }
                runCatching {
                    coroutineScope {
                        val markets = async { indexer.getMarketsQualified() }
                        val positions = async {
                            indexer.getAccountPositionsQualified(accountAddress)
                        }
                        val trades = async {
                            indexer.getAccountTradesQualified(accountAddress)
                        }
                        val signals = async {
                            try {
                                indexer.getPolkamarktSignalsQualified()
                            } catch (error: Throwable) {
                                if (!PiIndexerOfflineFallbackPolicy.allows(error)) throw error
                                null
                            }
                        }
                        val marketRead = markets.await()
                        val positionRead = positions.await()
                        val tradeRead = trades.await()
                        val signalRead = signals.await()
                        val usingCachedData =
                            PolkamarktReadProvenance.requireCoherentAndUsesCache(
                                buildList<PiQualifiedRead<*>> {
                                    add(marketRead)
                                    add(positionRead)
                                    add(tradeRead)
                                    signalRead?.let { add(it) }
                                }
                            )
                        storeCatalogBestEffort(marketRead)
                        Loaded(
                            markets = marketRead.value,
                            positions = positionRead.value,
                            trades = tradeRead.value,
                            signals = signalRead?.value,
                            accountAddress = accountAddress,
                            mutationsEnabled = flags.polkamarktMutationsAvailable,
                            usingCachedData = usingCachedData,
                        )
                    }
                }.getOrElse { liveError ->
                    if (!PiIndexerOfflineFallbackPolicy.allows(liveError)) throw liveError
                    val cachedMarkets = catalogCache.read() ?: throw liveError
                    Loaded(
                        markets = cachedMarkets,
                        positions = emptyList(),
                        trades = emptyList(),
                        signals = null,
                        accountAddress = accountAddress,
                        mutationsEnabled = flags.polkamarktMutationsAvailable,
                        usingCachedData = true,
                    )
                }
            }.onSuccess success@{ loaded ->
                if (
                    !isCurrentRefresh(
                        accountAddress,
                        generation,
                        requestGeneration,
                    )
                ) {
                    return@success
                }
                coreUsingCachedData = loaded.usingCachedData
                val selected = _state.value.selectedMarket?.marketId?.let { selectedId ->
                    loaded.markets.firstOrNull { it.marketId == selectedId }
                }
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    markets = loaded.markets,
                    positions = loaded.positions,
                    trades = loaded.trades,
                    signals = loaded.signals,
                    selectedMarket = selected,
                    accountAddress = loaded.accountAddress,
                    mutationsEnabled = loaded.mutationsEnabled,
                    usingCachedData = loaded.usingCachedData,
                    authoritativeClaims = emptyMap(),
                    reviewedClaimMarketIds = emptySet(),
                    claimReviewFinalizedBlockHash = null,
                    claimReviewLoading = false,
                )
                selected?.let(::selectMarket)
            }.onFailure failure@{
                if (it is CancellationException) throw it
                if (
                    !isCurrentRefresh(
                        accountAddress,
                        generation,
                        requestGeneration,
                    )
                ) {
                    return@failure
                }
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    marketDetailLoading = false,
                    errorCode = it.message ?: "POLKAMARKT_LOAD_FAILED",
                )
            }
        }
    }

    private fun isCurrentAccount(
        accountAddress: String,
        generation: Long,
    ): Boolean =
        currentAccountAddress == accountAddress &&
            accountGeneration == generation

    private fun isCurrentRefresh(
        accountAddress: String,
        accountGeneration: Long,
        requestGeneration: Long,
    ): Boolean =
        isCurrentAccount(accountAddress, accountGeneration) &&
            refreshGeneration == requestGeneration

    fun search(value: String) {
        _state.value = _state.value.copy(search = value)
    }

    private suspend fun storeCatalogBestEffort(
        catalog: PiQualifiedRead<List<PolkamarktMarket>>,
    ) {
        try {
            catalogCache.store(catalog)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The qualified PI result remains usable even when display-cache
            // persistence is unavailable.
        }
    }

    fun filterStatus(value: String) {
        if (value in PolkamarktWebContract.STATUS_FILTERS) {
            _state.value = _state.value.copy(statusFilter = value)
        }
    }

    fun filterCategory(value: String) {
        if (value == "all" || value in PolkamarktWebContract.CATEGORIES) {
            _state.value = _state.value.copy(categoryFilter = value)
        }
    }

    fun toggleMineOnly() {
        _state.value = _state.value.copy(mineOnly = !_state.value.mineOnly)
    }

    fun clearSelection() {
        quoteRefreshJob?.cancel()
        quoteRequestVersion += 1
        marketDetailRequestVersion += 1
        _state.value = _state.value.copy(
            selectedMarket = null,
            authoritativeMarket = null,
            authoritativeClaimable = null,
            authoritativeFinalizedBlockHash = null,
            marketDetailLoading = false,
            snapshots = emptyList(),
            quote = null,
            pendingClaimConfirmation = null,
            usingCachedData = coreUsingCachedData,
            errorCode = null,
        )
    }

    fun selectMarket(market: PolkamarktMarket) {
        quoteRefreshJob?.cancel()
        quoteRequestVersion += 1
        val detailRequestVersion = ++marketDetailRequestVersion
        _state.value = _state.value.copy(
            selectedMarket = market,
            authoritativeMarket = null,
            authoritativeClaimable = null,
            authoritativeFinalizedBlockHash = null,
            marketDetailLoading = true,
            snapshots = emptyList(),
            side = PolkamarktTradeSide.BUY,
            outcome = PolkamarktOutcome.YES,
            amountInput = "",
            quote = null,
            pendingClaimConfirmation = null,
            usingCachedData = coreUsingCachedData,
            errorCode = null,
        )
        val id: Long = market.marketId ?: run {
            _state.value = _state.value.copy(
                marketDetailLoading = false,
                errorCode = "POLKAMARKT_MARKET_ID_MISSING",
            )
            return
        }
        val accountAddress = currentAccountAddress
        if (accountAddress == null) {
            _state.value = _state.value.copy(
                marketDetailLoading = false,
                errorCode = "POLKAMARKT_ACCOUNT_CHANGED",
            )
            return
        }
        val generation = accountGeneration
        viewModelScope.launch {
            runCatching {
                trader.getAuthoritativeMarketDetail(
                    expectedAccountId = accountAddress,
                    catalogMarket = market,
                )
            }.onSuccess { detail ->
                if (
                    isCurrentAccount(accountAddress, generation) &&
                    marketDetailRequestVersion == detailRequestVersion &&
                    _state.value.selectedMarket?.marketId == id
                ) {
                    _state.value = _state.value.copy(
                        authoritativeMarket = detail.market,
                        authoritativeClaimable = detail.claimable,
                        authoritativeFinalizedBlockHash = detail.finalizedBlockHash,
                        marketDetailLoading = false,
                    )
                }
            }.onFailure {
                if (it is CancellationException) throw it
                if (
                    isCurrentAccount(accountAddress, generation) &&
                    marketDetailRequestVersion == detailRequestVersion &&
                    _state.value.selectedMarket?.marketId == id
                ) {
                    _state.value = _state.value.copy(
                        authoritativeMarket = null,
                        authoritativeClaimable = null,
                        authoritativeFinalizedBlockHash = null,
                        marketDetailLoading = false,
                        errorCode = "POLKAMARKT_RUNTIME_DETAIL_UNAVAILABLE",
                    )
                }
            }
        }
        viewModelScope.launch {
            runCatching { indexer.getMarketSnapshotsQualified(id) }
                .onSuccess { snapshotRead ->
                    if (
                        isCurrentAccount(accountAddress, generation) &&
                        marketDetailRequestVersion == detailRequestVersion &&
                        _state.value.selectedMarket?.marketId == id
                    ) {
                        _state.value = _state.value.copy(
                            snapshots = snapshotRead.value,
                            usingCachedData =
                                _state.value.usingCachedData || snapshotRead.fromCache,
                        )
                    }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (
                        isCurrentAccount(accountAddress, generation) &&
                        marketDetailRequestVersion == detailRequestVersion &&
                        _state.value.selectedMarket?.marketId == id
                    ) {
                        _state.value = _state.value.copy(
                            snapshots = emptyList(),
                            errorCode = "POLKAMARKT_CHART_UNAVAILABLE",
                        )
                    }
                }
        }
    }

    fun setSide(side: PolkamarktTradeSide) {
        _state.value = _state.value.copy(side = side, quote = null)
        scheduleQuote()
    }

    fun setOutcome(outcome: PolkamarktOutcome) {
        _state.value = _state.value.copy(outcome = outcome, quote = null)
        scheduleQuote()
    }

    fun setAmount(value: String) {
        if (
            value.length <= MAX_AMOUNT_INPUT_LENGTH &&
            (value.isEmpty() || DECIMAL_INPUT.matches(value))
        ) {
            _state.value = _state.value.copy(amountInput = value, quote = null)
            scheduleQuote()
        }
    }

    fun setSlippageBps(value: Int) {
        if (value in PolkamarktWebContract.MIN_SLIPPAGE_BPS..
            PolkamarktWebContract.MAX_SLIPPAGE_BPS
        ) {
            _state.value = _state.value.copy(slippageBps = value, quote = null)
            scheduleQuote()
        }
    }

    fun requestQuote() {
        if (!canPrepareQuote()) {
            quoteRefreshJob?.cancel()
            quoteRefreshJob = null
            quoteRequestVersion += 1
            _state.value = _state.value.copy(quote = null, quoteLoading = false)
            return
        }
        quoteRefreshJob?.cancel()
        val version = ++quoteRequestVersion
        quoteRefreshJob = viewModelScope.launch {
            loadQuote(version)
        }
    }

    private fun scheduleQuote() {
        quoteRefreshJob?.cancel()
        val version = ++quoteRequestVersion
        _state.value = _state.value.copy(
            quote = null,
            quoteLoading = false,
        )
        if (!canPrepareQuote()) {
            return
        }
        if (_state.value.selectedMarket?.marketId == null ||
            _state.value.amountInput.isBlank()
        ) {
            return
        }
        quoteRefreshJob = viewModelScope.launch {
            delay(PolkamarktWebContract.QUOTE_DEBOUNCE_MILLISECONDS)
            loadQuote(version)
        }
    }

    private suspend fun loadQuote(version: Long) {
        val current = _state.value
        val marketId: Long = current.selectedMarket?.marketId ?: return
        val accountId = currentAccountAddress ?: return
        if (
            version != quoteRequestVersion ||
            !PolkamarktMutationAdmissionPolicy.canPrepareQuote(
                mutationInFlight = mutationGate.isAcquired(),
                pendingRecoveryRequired = current.pendingRecoveryRequired,
            )
        ) return
        _state.value = current.copy(quoteLoading = true, errorCode = null)
        try {
            val amount = parseUnits(current.amountInput)
            val quote = when (current.side) {
                PolkamarktTradeSide.BUY -> trader.quoteBuy(
                    expectedAccountId = accountId,
                    marketId = marketId,
                    outcome = current.outcome,
                    collateralIn = amount,
                    slippageBps = current.slippageBps,
                )
                PolkamarktTradeSide.SELL -> trader.quoteSell(
                    expectedAccountId = accountId,
                    marketId = marketId,
                    outcome = current.outcome,
                    sharesIn = amount,
                    slippageBps = current.slippageBps,
                )
            }
            if (
                version == quoteRequestVersion &&
                canPrepareQuote()
            ) {
                _state.value = _state.value.copy(
                    quote = quote,
                    quoteLoading = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (version == quoteRequestVersion && canPrepareQuote()) {
                _state.value = _state.value.copy(
                    quote = null,
                    quoteLoading = false,
                    errorCode = error.message ?: "POLKAMARKT_QUOTE_FAILED",
                )
            }
        }
    }

    fun confirmTrade() {
        val quote = _state.value.quote ?: return
        if (!_state.value.mutationsEnabled || _state.value.pendingRecoveryRequired) return
        val accountAddress = currentAccountAddress ?: return
        val generation = accountGeneration
        if (!beginMutation()) return
        // A confirmation is single-use. Any failure requires a fresh runtime quote and a new
        // deliberate confirmation; an ambiguously submitted quote is never offered for retry.
        _state.value = _state.value.copy(
            quote = null,
            quoteLoading = false,
        )
        viewModelScope.launch {
            try {
                val mutation = trader.executeTrade(quote)
                if (isCurrentAccount(accountAddress, generation)) {
                    _state.value = _state.value.copy(
                        amountInput = "",
                        lastMutation = mutation,
                    )
                    refresh()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentAccount(accountAddress, generation)) {
                    _state.value = _state.value.copy(
                        errorCode = error.message ?: "POLKAMARKT_SUBMISSION_FAILED",
                    )
                }
            } finally {
                endMutation()
            }
        }
    }

    fun reviewClaim(marketId: Long) {
        reviewClaims(listOf(marketId))
    }

    fun reviewBatchClaims() {
        val marketIds: List<Long> = _state.value.positions
            .asSequence()
            .mapNotNull(PolkamarktAccountPosition::marketId)
            .distinct()
            .sorted()
            .take(PolkamarktWebContract.MAX_BATCH_CLAIMS)
            .toList()
        if (marketIds.isEmpty()) return
        reviewClaims(marketIds)
    }

    private fun reviewClaims(marketIds: List<Long>) {
        if (_state.value.claimReviewLoading) return
        val accountAddress = currentAccountAddress ?: return
        val generation = accountGeneration
        val requestVersion = ++claimReviewRequestVersion
        _state.value = _state.value.copy(
            authoritativeClaims = emptyMap(),
            reviewedClaimMarketIds = emptySet(),
            claimReviewFinalizedBlockHash = null,
            claimReviewLoading = true,
            pendingClaimConfirmation = null,
            errorCode = null,
        )
        viewModelScope.launch {
            runCatching {
                trader.getAuthoritativeClaimables(accountAddress, marketIds)
            }.onSuccess { review ->
                if (
                    isCurrentAccount(accountAddress, generation) &&
                    claimReviewRequestVersion == requestVersion
                ) {
                    _state.value = _state.value.copy(
                        authoritativeClaims = review.claims,
                        reviewedClaimMarketIds = review.requestedMarketIds,
                        claimReviewFinalizedBlockHash = review.finalizedBlockHash,
                        claimReviewLoading = false,
                    )
                }
            }.onFailure {
                if (it is CancellationException) throw it
                if (
                    isCurrentAccount(accountAddress, generation) &&
                    claimReviewRequestVersion == requestVersion
                ) {
                    _state.value = _state.value.copy(
                        claimReviewLoading = false,
                        errorCode = "POLKAMARKT_CLAIM_REVIEW_UNAVAILABLE",
                    )
                }
            }
        }
    }

    fun requestClaim(position: PolkamarktAccountPosition, creatorFees: Boolean) {
        val marketId: Long = position.marketId ?: return
        requestClaimConfirmation { state, accountAddress ->
            check(state.positions.any { it.marketId == marketId }) {
                "POLKAMARKT_CLAIM_POSITION_STALE"
            }
            PolkamarktClaimConfirmationPolicy.reviewedPosition(
                accountId = accountAddress,
                marketId = marketId,
                claims = state.authoritativeClaims,
                reviewedMarketIds = state.reviewedClaimMarketIds,
                finalizedBlockHash = state.claimReviewFinalizedBlockHash,
                creatorFees = creatorFees,
            )
        }
    }

    fun requestSelectedClaim(creatorFees: Boolean) {
        requestClaimConfirmation { state, accountAddress ->
            PolkamarktClaimConfirmationPolicy.selected(
                accountId = accountAddress,
                marketId = checkNotNull(state.selectedMarket?.marketId) {
                    "POLKAMARKT_CLAIM_POSITION_STALE"
                },
                claimable = state.authoritativeClaimable,
                finalizedBlockHash = state.authoritativeFinalizedBlockHash,
                creatorFees = creatorFees,
            )
        }
    }

    fun requestAllTraderPayouts() {
        requestClaimConfirmation { state, accountAddress ->
            val marketIds = state.positions.asSequence()
                .mapNotNull(PolkamarktAccountPosition::marketId)
                .filter { marketId ->
                    state.authoritativeClaims[marketId]?.let { claimable ->
                        PolkamarktWebContract.CLAIMABLE_STATUSES.any {
                            it.equals(claimable.status, ignoreCase = true)
                        } && claimable.claimablePayout.signum() == 1
                    } == true
                }
                .distinct()
                .sorted()
                .take(PolkamarktWebContract.MAX_BATCH_CLAIMS)
                .toList()
            PolkamarktClaimConfirmationPolicy.reviewedBatch(
                accountId = accountAddress,
                marketIds = marketIds,
                claims = state.authoritativeClaims,
                reviewedMarketIds = state.reviewedClaimMarketIds,
                finalizedBlockHash = state.claimReviewFinalizedBlockHash,
            )
        }
    }

    private fun requestClaimConfirmation(
        create: (PolkamarktScreenState, String) -> PolkamarktClaimConfirmation,
    ) {
        val accountAddress = currentAccountAddress ?: return
        val snapshot = _state.value
        if (
            !snapshot.mutationsEnabled ||
            snapshot.pendingRecoveryRequired ||
            snapshot.mutationLoading
        ) return
        try {
            val confirmation = create(snapshot, accountAddress)
            val current = _state.value
            check(currentAccountAddress == accountAddress) {
                "POLKAMARKT_ACCOUNT_CHANGED"
            }
            requireCurrentClaimConfirmation(confirmation, current, accountAddress)
            _state.value = current.copy(
                pendingClaimConfirmation = confirmation,
                errorCode = null,
            )
        } catch (error: Throwable) {
            if (currentAccountAddress == accountAddress) {
                _state.value = _state.value.copy(
                    pendingClaimConfirmation = null,
                    errorCode = error.message ?: "POLKAMARKT_CLAIM_CONFIRMATION_UNAVAILABLE",
                )
            }
        }
    }

    fun dismissClaimConfirmation() {
        _state.value = _state.value.copy(pendingClaimConfirmation = null)
    }

    fun confirmClaim() {
        val confirmed = _state.value.pendingClaimConfirmation ?: return
        val accountAddress = currentAccountAddress ?: return
        val current = _state.value
        if (
            !current.mutationsEnabled ||
            current.pendingRecoveryRequired ||
            current.mutationLoading
        ) return
        try {
            check(confirmed.accountId == accountAddress) { "POLKAMARKT_ACCOUNT_CHANGED" }
            requireCurrentClaimConfirmation(confirmed, current, accountAddress)
        } catch (_: Throwable) {
            _state.value = _state.value.copy(
                pendingClaimConfirmation = null,
                errorCode = "POLKAMARKT_CLAIM_CONFIRMATION_STALE",
            )
            return
        }
        val authorization = PolkamarktClaimAuthorization(
            accountId = confirmed.accountId,
            marketIds = confirmed.marketIds,
            creatorFees = confirmed.kind == PolkamarktClaimKind.CREATOR_FEES,
            reviewedFinalizedBlockHash = confirmed.finalizedBlockHash,
            reviewedClaims = confirmed.claims,
        )
        val generation = accountGeneration
        if (!beginMutation()) return
        _state.value = _state.value.copy(pendingClaimConfirmation = null)
        viewModelScope.launch {
            try {
                val mutation = when {
                    confirmed.kind == PolkamarktClaimKind.CREATOR_FEES ->
                        trader.claimCreatorFees(authorization)

                    confirmed.marketIds.size == 1 ->
                        trader.claimTraderPayout(authorization)

                    else -> trader.claimTraderPayouts(authorization)
                }
                if (isCurrentAccount(accountAddress, generation)) {
                    _state.value = _state.value.copy(lastMutation = mutation)
                    refresh()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentAccount(accountAddress, generation)) {
                    _state.value = _state.value.copy(
                        errorCode = error.message ?: "POLKAMARKT_CLAIM_FAILED",
                    )
                }
            } finally {
                endMutation()
            }
        }
    }

    private fun requireCurrentClaimConfirmation(
        confirmed: PolkamarktClaimConfirmation,
        current: PolkamarktScreenState,
        accountAddress: String,
    ) {
        PolkamarktClaimConfirmationPolicy.requireCurrent(
            confirmed = confirmed,
            accountId = accountAddress,
            selectedMarketId = current.selectedMarket?.marketId,
            selectedClaimable = current.authoritativeClaimable,
            selectedFinalizedBlockHash = current.authoritativeFinalizedBlockHash,
            reviewedClaims = current.authoritativeClaims,
            reviewedMarketIds = current.reviewedClaimMarketIds,
            claimReviewFinalizedBlockHash = current.claimReviewFinalizedBlockHash,
        )
    }

    private fun beginMutation(): Boolean {
        if (!mutationGate.tryAcquire()) return false
        quoteRefreshJob?.cancel()
        quoteRefreshJob = null
        quoteRequestVersion += 1
        _state.value = _state.value.copy(
            mutationLoading = true,
            quote = null,
            quoteLoading = false,
            errorCode = null,
        )
        return true
    }

    private fun endMutation() {
        mutationGate.release()
        _state.value = _state.value.copy(
            mutationLoading = false,
            quote = null,
            quoteLoading = false,
        )
    }

    private fun canPrepareQuote(): Boolean =
        PolkamarktMutationAdmissionPolicy.canPrepareQuote(
            mutationInFlight = mutationGate.isAcquired(),
            pendingRecoveryRequired = _state.value.pendingRecoveryRequired,
        )

    private fun parseUnits(input: String): BigInteger {
        require(
            input.isNotBlank() &&
                input.length <= MAX_AMOUNT_INPUT_LENGTH &&
                DECIMAL_INPUT.matches(input)
        ) {
            "POLKAMARKT_INVALID_AMOUNT"
        }
        val decimal = BigDecimal(input)
        require(decimal.signum() > 0 && decimal.scale() <= ASSET_PRECISION) {
            "POLKAMARKT_INVALID_AMOUNT"
        }
        return decimal.movePointRight(ASSET_PRECISION).toBigIntegerExact()
    }

    private data class Loaded(
        val markets: List<PolkamarktMarket>,
        val positions: List<PolkamarktAccountPosition>,
        val trades: List<PolkamarktAccountTrade>,
        val signals: PolkamarktSignals?,
        val accountAddress: String,
        val mutationsEnabled: Boolean,
        val usingCachedData: Boolean,
    )

    companion object {
        const val ASSET_PRECISION = 18
        const val MAX_AMOUNT_INPUT_LENGTH = 128
        val DECIMAL_INPUT = Regex("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{0,18})?$")

        fun formatUnits(value: BigInteger): String =
            BigDecimal(value, ASSET_PRECISION).stripTrailingZeros().toPlainString()
    }
}
