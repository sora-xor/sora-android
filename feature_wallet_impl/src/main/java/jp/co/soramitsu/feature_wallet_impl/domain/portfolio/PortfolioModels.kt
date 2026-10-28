package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import java.math.BigDecimal
import jp.co.soramitsu.common.nexus.WalletNetworkDirectory
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.nexus.WalletNetworkTag

/** Lenses over the portfolio list and the hero fan (portfolio-view SPEC §4). */
enum class PortfolioLens(val key: String) {
    TOKENS("tokens"),
    NETWORKS("networks"),
    WALLETS("wallets");

    companion object {
        fun fromKey(key: String?): PortfolioLens? = entries.firstOrNull { it.key == key }
    }
}

/** Scope of the dashboard: every wallet together, or the selected wallet alone. */
enum class PortfolioScope(val key: String) {
    ALL("all"),
    SELECTED("selected");

    companion object {
        fun fromKey(key: String?): PortfolioScope? = entries.firstOrNull { it.key == key }
    }
}

/** A SORA account (wallet) of this app. [id] is its SORA2 address. */
data class PortfolioWallet(
    val id: String,
    val name: String,
    val selected: Boolean,
)

/**
 * A token as the app prices it. [price] is in the app's selected fiat currency and is null when
 * the app has no price for it; [change24h] is a fraction (0.034 is +3.4 %).
 */
data class PortfolioToken(
    val id: String,
    val symbol: String,
    val name: String,
    val iconUri: String?,
    val precision: Int,
    val price: Double?,
    val change24h: Double?,
)

/**
 * One token on one network in one wallet. [total] is the asset's total balance as the app defines
 * it; [transferable] is the part the wallet can send now. [hidden] is the user's own visibility
 * choice for this wallet. [address] is the wallet's account on [network].
 */
data class PortfolioHolding(
    val walletId: String,
    val network: WalletNetworkId,
    val token: PortfolioToken,
    val total: BigDecimal,
    val transferable: BigDecimal,
    val hidden: Boolean,
    val address: String,
    val sendAvailable: Boolean = true,
) {
    val key: String
        get() = "$walletId|${network.wireId}|${token.id}"

    /** Reserved, frozen or bonded: whatever the app counts in the total but cannot send now. */
    val locked: BigDecimal
        get() = (total - transferable).max(BigDecimal.ZERO)
}

/** Why a holding is not part of the total. */
enum class Exclusion { HIDDEN, UNPRICED, TESTNET }

/** Sync state of a wallet, from best to worst. */
enum class FreshnessKind(val severity: Int) {
    LIVE(0),
    SAVED(1),
    PARTIAL(2),
    OFFLINE(3),
}

/** [updatedAtMillis] is the last successful sync of the wallet; [unreachable] names failed networks. */
data class WalletFreshness(
    val kind: FreshnessKind,
    val updatedAtMillis: Long?,
    val unreachable: Set<WalletNetworkId> = emptySet(),
)

/**
 * What the source knows about a wallet's sync. [live] is true for the selected wallet, which the
 * app keeps current with a live subscription; other wallets are read on demand and age.
 */
data class WalletSync(
    val refreshedAtMillis: Long?,
    val unreachable: Set<WalletNetworkId> = emptySet(),
    val live: Boolean = false,
)

/** Every wallet's holdings as one consistent picture; the input to [PortfolioCalculator]. */
data class PortfolioSnapshot(
    val wallets: List<PortfolioWallet>,
    val holdings: List<PortfolioHolding>,
    val sync: Map<String, WalletSync>,
    val connected: Boolean,
    /** Each wallet's own account per network, used to pick Move destinations. */
    val addresses: Map<String, Map<WalletNetworkId, String>>,
    /** The app's selected fiat currency sign, for formatting. */
    val fiatSymbol: String,
) {
    val selectedWallet: PortfolioWallet?
        get() = wallets.firstOrNull { it.selected }

    fun wallet(id: String): PortfolioWallet? = wallets.firstOrNull { it.id == id }

    companion object {
        val EMPTY = PortfolioSnapshot(emptyList(), emptyList(), emptyMap(), true, emptyMap(), "$")
    }
}

/** The wallet's main network, as the directory picks it: listed first and tagged "Main". */
val PORTFOLIO_MAIN_NETWORK: WalletNetworkId
    get() = WalletNetworkDirectory.current.mainNetwork(WalletNetworkId.entries.toSet())

/**
 * Networks in display order, from the wallet's one network directory: the main network first,
 * then the other mainnets, then testnets. Taira is the Nexus testnet; it is shown but never counted.
 */
val PORTFOLIO_NETWORKS: List<WalletNetworkId>
    get() = WalletNetworkDirectory.current.ordered(WalletNetworkId.entries, PORTFOLIO_MAIN_NETWORK)

val WalletNetworkId.isTestnet: Boolean
    get() = WalletNetworkDirectory.current.info(this).isTestnet

/**
 * The directory's tag on a portfolio network row: Main or Testnet. The portfolio only has
 * balances on networks this build reads, so "Coming soon" (an address and nothing else) never
 * labels a row here.
 */
val WalletNetworkId.portfolioTag: WalletNetworkTag?
    get() = WalletNetworkDirectory.current.tag(this, PORTFOLIO_MAIN_NETWORK)
        ?.takeUnless { it == WalletNetworkTag.COMING_SOON }
