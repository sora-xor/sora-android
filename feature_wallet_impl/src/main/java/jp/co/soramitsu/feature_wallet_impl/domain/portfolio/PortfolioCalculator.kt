package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import java.math.BigDecimal
import jp.co.soramitsu.common.nexus.WalletNetworkId

/**
 * Counted value of a set of holdings (portfolio-view SPEC §6). Only visible, priced, mainnet
 * holdings are counted; the others are named by kind so the caption can say what is left out.
 */
data class PortfolioTotals(
    val fiat: Double,
    val change: Double,
    val changeKnown: Boolean,
    val hidden: Int,
    val unpriced: Int,
    val testnet: Int,
) {
    /** 24 h change as a fraction of the value 24 h ago; null when no counted holding has a change. */
    val changeFraction: Double?
        get() {
            val previous = fiat - change
            return if (changeKnown && previous > 0.0) change / previous else null
        }

    val excluded: Int
        get() = hidden + unpriced + testnet

    operator fun plus(other: PortfolioTotals): PortfolioTotals = PortfolioTotals(
        fiat = fiat + other.fiat,
        change = change + other.change,
        changeKnown = changeKnown || other.changeKnown,
        hidden = hidden + other.hidden,
        unpriced = unpriced + other.unpriced,
        testnet = testnet + other.testnet,
    )

    companion object {
        val ZERO = PortfolioTotals(0.0, 0.0, false, 0, 0, 0)
    }
}

/** One row of the active lens: a token, a network or a wallet, with the holdings it groups. */
data class PortfolioGroup(
    val key: String,
    val lens: PortfolioLens,
    /** Visible holdings (hidden ones are never listed), largest counted value first. */
    val holdings: List<PortfolioHolding>,
    val countedFiat: Double,
    /** [countedFiat] over the scope's counted total; 0 when nothing is counted. */
    val share: Double,
    /** Tokens lens: the visible mainnet amount, or the testnet amount for a testnet-only token. */
    val amount: BigDecimal,
    val testnetOnly: Boolean,
    val walletIds: List<String>,
    val networks: List<WalletNetworkId>,
)

/** A share of the 100-ray allocation fan. [key] is null for Other. */
data class FanSlice(
    val key: String?,
    val countedFiat: Double,
    val rays: Int,
)

/** A wallet with its own total, as the Wallets sheet and the Wallets lens show it. */
data class WalletSummary(
    val wallet: PortfolioWallet,
    val totals: PortfolioTotals,
    val freshness: WalletFreshness?,
    val networks: List<WalletNetworkId>,
)

/** Everything "Where it lives" shows for one token in the current scope. */
data class TokenLocations(
    val token: PortfolioToken,
    /** Visible holdings, largest counted value first, testnet last. */
    val holdings: List<PortfolioHolding>,
    val countedAmount: BigDecimal,
    val countedFiat: Double,
    val share: Double,
    val networks: List<WalletNetworkId>,
)

/** Pure portfolio arithmetic. Nothing here reads storage, keys or the network. */
object PortfolioCalculator {

    /** Why [holding] is not counted, or null when it is. */
    fun exclusion(holding: PortfolioHolding): Exclusion? = when {
        holding.network.isTestnet -> Exclusion.TESTNET
        holding.hidden -> Exclusion.HIDDEN
        holding.token.price == null -> Exclusion.UNPRICED
        else -> null
    }

    /** Amount × the asset's own price, for a counted holding only. A group never lends a price. */
    fun countedFiat(holding: PortfolioHolding): Double? {
        if (exclusion(holding) != null) return null
        val price = holding.token.price ?: return null
        return holding.total.toDouble() * price
    }

    /** Fiat change over 24 h of a counted holding, from its token's own price change. */
    fun countedChange(holding: PortfolioHolding): Double? {
        val fiat = countedFiat(holding) ?: return null
        val change = holding.token.change24h ?: return null
        if (1 + change <= 0.0) return null
        return fiat - fiat / (1 + change)
    }

    /** Totals of one wallet's holdings, with that wallet's own hidden choices. */
    fun totals(holdings: List<PortfolioHolding>): PortfolioTotals {
        var fiat = 0.0
        var change = 0.0
        var changeKnown = false
        var hidden = 0
        var unpriced = 0
        var testnet = 0
        holdings.forEach { holding ->
            when (exclusion(holding)) {
                Exclusion.HIDDEN -> hidden++
                Exclusion.UNPRICED -> unpriced++
                Exclusion.TESTNET -> testnet++
                null -> {
                    fiat += countedFiat(holding) ?: 0.0
                    countedChange(holding)?.let {
                        change += it
                        changeKnown = true
                    }
                }
            }
        }
        return PortfolioTotals(fiat, change, changeKnown, hidden, unpriced, testnet)
    }

    /** All wallets total = Σ each wallet's own total; the exclusions are summed too. */
    fun scopeTotals(snapshot: PortfolioSnapshot, walletIds: Collection<String>): PortfolioTotals =
        walletIds.distinct()
            .map { id -> totals(snapshot.holdings.filter { it.walletId == id }) }
            .fold(PortfolioTotals.ZERO, PortfolioTotals::plus)

    /** All wallets appears only with two or more wallets and is their default. */
    fun effectiveScope(requested: PortfolioScope?, walletCount: Int): PortfolioScope =
        if (walletCount >= 2) requested ?: PortfolioScope.ALL else PortfolioScope.SELECTED

    fun scopeWalletIds(snapshot: PortfolioSnapshot, scope: PortfolioScope): List<String> = when (scope) {
        PortfolioScope.ALL -> snapshot.wallets.map { it.id }
        PortfolioScope.SELECTED -> listOfNotNull(snapshot.selectedWallet?.id)
    }

    /** The Wallets lens exists only in All wallets. Tokens is the default. */
    fun availableLenses(scope: PortfolioScope): List<PortfolioLens> = when (scope) {
        PortfolioScope.ALL -> PortfolioLens.entries.toList()
        PortfolioScope.SELECTED -> listOf(PortfolioLens.TOKENS, PortfolioLens.NETWORKS)
    }

    fun effectiveLens(requested: PortfolioLens?, scope: PortfolioScope): PortfolioLens =
        requested?.takeIf { it in availableLenses(scope) } ?: PortfolioLens.TOKENS

    /** Visible holdings in scope: hidden holdings are counted out and never listed. */
    fun visibleHoldings(snapshot: PortfolioSnapshot, walletIds: Collection<String>): List<PortfolioHolding> {
        val ids = walletIds.toSet()
        return snapshot.holdings.filter { it.walletId in ids && !it.hidden }
    }

    /** Groups for [lens], largest counted value first. Their counted values add up to the scope total. */
    fun groups(
        snapshot: PortfolioSnapshot,
        walletIds: List<String>,
        lens: PortfolioLens,
    ): List<PortfolioGroup> {
        val visible = visibleHoldings(snapshot, walletIds)
        val total = visible.sumOf { countedFiat(it) ?: 0.0 }
        val walletOrder = walletIds.withIndex().associate { it.value to it.index }
        val grouped: List<Pair<String, List<PortfolioHolding>>> = when (lens) {
            PortfolioLens.TOKENS -> visible.groupBy { it.token.id }.toList()
            PortfolioLens.NETWORKS -> visible.groupBy { it.network.wireId }.toList()
            PortfolioLens.WALLETS -> walletIds.distinct().map { id ->
                id to visible.filter { it.walletId == id }
            }
        }
        return grouped.map { (key, holdings) ->
            val counted = holdings.sumOf { countedFiat(it) ?: 0.0 }
            val mainnet = holdings.filterNot { it.network.isTestnet }
            val testnetOnly = holdings.isNotEmpty() && mainnet.isEmpty()
            PortfolioGroup(
                key = key,
                lens = lens,
                holdings = sortHoldings(holdings),
                countedFiat = counted,
                share = if (total > 0.0) counted / total else 0.0,
                amount = if (lens == PortfolioLens.TOKENS) {
                    (if (testnetOnly) holdings else mainnet).fold(BigDecimal.ZERO) { acc, h -> acc + h.total }
                } else {
                    BigDecimal.ZERO
                },
                testnetOnly = testnetOnly,
                walletIds = holdings.map { it.walletId }.distinct().sortedBy { walletOrder[it] ?: Int.MAX_VALUE },
                networks = PORTFOLIO_NETWORKS.filter { network -> holdings.any { it.network == network } },
            )
        }.sortedWith(
            compareByDescending<PortfolioGroup> { it.countedFiat }
                .thenBy { it.testnetOnly }
                .thenBy { if (lens == PortfolioLens.WALLETS) walletOrder[it.key] ?: Int.MAX_VALUE else 0 }
                .thenBy { group -> group.holdings.firstOrNull()?.token?.symbol ?: group.key }
                .thenBy { it.key }
        )
    }

    /** Largest counted value first; then mainnet before testnet; then the larger amount. */
    fun sortHoldings(holdings: List<PortfolioHolding>): List<PortfolioHolding> = holdings.sortedWith(
        compareByDescending<PortfolioHolding> { countedFiat(it) ?: 0.0 }
            .thenBy { it.network.isTestnet }
            .thenByDescending { it.total }
            .thenBy { PORTFOLIO_NETWORKS.indexOf(it.network) }
            .thenBy { it.walletId }
    )

    /**
     * The fan: at most [maxSlots] groups plus Other, largest first, one ray per percent of the
     * counted value with largest-remainder rounding so the rays always add up to 100.
     */
    fun fan(groups: List<PortfolioGroup>, maxSlots: Int = PortfolioSlots.SLOT_COUNT): List<FanSlice> {
        val counted = groups.filter { it.countedFiat > 0.0 }.sortedByDescending { it.countedFiat }
        if (counted.isEmpty()) return emptyList()
        val top = counted.take(maxSlots)
        val other = counted.drop(maxSlots).sumOf { it.countedFiat }
        val slices = top.map { FanSlice(it.key, it.countedFiat, 0) } +
            if (other > 0.0) listOf(FanSlice(null, other, 0)) else emptyList()
        val rays = FanAllocation.largestRemainder(slices.map { it.countedFiat }, FanAllocation.RAYS)
        return slices.mapIndexed { index, slice -> slice.copy(rays = rays[index]) }
    }

    /** Each wallet's own total and freshness, in account order. */
    fun walletSummaries(snapshot: PortfolioSnapshot, nowMillis: Long): List<WalletSummary> =
        snapshot.wallets.map { wallet ->
            val holdings = snapshot.holdings.filter { it.walletId == wallet.id }
            WalletSummary(
                wallet = wallet,
                totals = totals(holdings),
                freshness = PortfolioFreshness.of(snapshot.sync[wallet.id], snapshot.connected, nowMillis),
                networks = PORTFOLIO_NETWORKS.filter { network ->
                    holdings.any { it.network == network && !it.hidden }
                },
            )
        }

    /**
     * The scope's freshness is its worst wallet's: offline, then partial, then saved, then live.
     * Among equals the oldest sync wins, and every unreachable network is named.
     */
    fun worstFreshness(
        snapshot: PortfolioSnapshot,
        walletIds: Collection<String>,
        nowMillis: Long,
    ): WalletFreshness? {
        val states = walletIds.distinct().map {
            PortfolioFreshness.of(snapshot.sync[it], snapshot.connected, nowMillis)
        }
        val worst = states.maxWithOrNull(
            compareBy<WalletFreshness> { it.kind.severity }
                .thenByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }
        ) ?: return null
        return worst.copy(unreachable = states.flatMapTo(linkedSetOf()) { it.unreachable })
    }

    /** "Where it lives" for one token, or null when the scope holds none of it. */
    fun tokenLocations(
        snapshot: PortfolioSnapshot,
        walletIds: List<String>,
        tokenId: String,
    ): TokenLocations? {
        val visible = visibleHoldings(snapshot, walletIds)
        val holdings = visible.filter { it.token.id == tokenId }
        if (holdings.isEmpty()) return null
        val total = visible.sumOf { countedFiat(it) ?: 0.0 }
        val counted = holdings.sumOf { countedFiat(it) ?: 0.0 }
        val token = holdings.firstOrNull { !it.network.isTestnet }?.token ?: holdings.first().token
        return TokenLocations(
            token = token,
            holdings = sortHoldings(holdings),
            countedAmount = holdings.filter { !it.network.isTestnet }
                .fold(BigDecimal.ZERO) { acc, h -> acc + h.total },
            countedFiat = counted,
            share = if (total > 0.0) counted / total else 0.0,
            networks = PORTFOLIO_NETWORKS.filter { network -> holdings.any { it.network == network } },
        )
    }
}

/** Largest-remainder (Hamilton) rounding of shares into whole rays. */
object FanAllocation {
    const val RAYS = 100

    fun largestRemainder(values: List<Double>, total: Int = RAYS): List<Int> {
        val positive = values.map { if (it.isFinite() && it > 0.0) it else 0.0 }
        val scale = positive.maxOrNull() ?: 0.0
        if (scale <= 0.0 || total <= 0) return positive.map { 0 }
        // Normalize before summing: individually finite holdings can overflow their total.
        val normalized = positive.map { it / scale }
        val sum = normalized.sum()
        val quotas = normalized.map { it / sum * total }
        val result = quotas.map { kotlin.math.floor(it).toInt() }.toMutableList()
        var remaining = total - result.sum()
        val order = quotas.indices.sortedWith(
            compareByDescending<Int> { quotas[it] - result[it] }
                .thenByDescending { positive[it] }
                .thenBy { it }
        )
        for (index in order) {
            if (remaining <= 0) break
            if (positive[index] <= 0.0) continue
            result[index] += 1
            remaining -= 1
        }
        return result
    }
}

/**
 * Honest sync states (portfolio-view SPEC §3.2 and §6). The selected wallet is kept current by the
 * app's live subscription; other wallets are read while the dashboard is visible, at most once a
 * minute, and show their age when a read is older than [LIVE_WINDOW_MS] or failed.
 */
object PortfolioFreshness {
    const val REFRESH_INTERVAL_MS = 60_000L
    const val LIVE_WINDOW_MS = 2 * REFRESH_INTERVAL_MS

    /**
     * A read that finishes just after the dashboard's clock ticked is stamped a moment "later" than
     * that tick. Up to this much is the same moment; beyond it the device clock went back.
     */
    const val CLOCK_SKEW_MS = 30_000L

    fun of(sync: WalletSync?, connected: Boolean, nowMillis: Long): WalletFreshness {
        val updatedAt = sync?.refreshedAtMillis
        val unreachable = sync?.unreachable.orEmpty()
        val kind = when {
            !connected -> FreshnessKind.OFFLINE
            unreachable.isNotEmpty() -> FreshnessKind.PARTIAL
            sync?.live == true -> FreshnessKind.LIVE
            updatedAt != null && isRecent(updatedAt, nowMillis) -> FreshnessKind.LIVE
            else -> FreshnessKind.SAVED
        }
        return WalletFreshness(kind, updatedAt, unreachable)
    }

    private fun isRecent(updatedAt: Long, nowMillis: Long): Boolean =
        if (nowMillis >= updatedAt) {
            nowMillis - updatedAt in 0..LIVE_WINDOW_MS
        } else {
            updatedAt - nowMillis in 0..CLOCK_SKEW_MS
        }

    /** True when a wallet has not been read for [REFRESH_INTERVAL_MS]: the rate limit of re-reads. */
    fun isDue(lastReadMillis: Long?, nowMillis: Long): Boolean =
        lastReadMillis == null || nowMillis < lastReadMillis || nowMillis - lastReadMillis !in 0 until REFRESH_INTERVAL_MS
}
