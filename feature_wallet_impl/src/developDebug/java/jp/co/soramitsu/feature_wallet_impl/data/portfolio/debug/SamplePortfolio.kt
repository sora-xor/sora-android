package jp.co.soramitsu.feature_wallet_impl.data.portfolio.debug

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.feature_wallet_impl.data.portfolio.PortfolioSource
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSlots
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioSnapshot
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioToken
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioWallet
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.WalletSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** What the sample shows: three wallets with holdings, or one empty wallet. */
enum class SampleMode { FULL, EMPTY }

/** The sync state the sample pretends to be in, to show every freshness chip. */
enum class SampleFreshness { LIVE, SAVED, PARTIAL, OFFLINE }

/**
 * Debug-only sample portfolio (developDebug source set; never compiled into production): three
 * wallets on SORA2, Minamoto and Taira with priced, unpriced, hidden and testnet holdings, so a
 * fresh emulator can show the dashboard without funds. All amounts, prices and addresses are
 * invented. Hand-offs that would carry a sample address into a real flow are refused upstream.
 */
internal object SamplePortfolio {
    const val MAIN = "cnSampleWalletMain000000000000000000000000000001"
    const val SAVINGS = "cnSampleWalletSavings000000000000000000000000002"
    const val TRADING = "cnSampleWalletTrading000000000000000000000000003"

    const val DAI_ID = "0x0200060000000000000000000000000000000000000000000000000000000000"
    const val XST_ID = "0x0200090000000000000000000000000000000000000000000000000000000000"
    const val KXOR_ID = "0x02000e0000000000000000000000000000000000000000000000000000000000"

    private data class SampleToken(
        val id: String,
        val symbol: String,
        val name: String,
        val price: Double?,
        val change: Double?,
    )

    private val xor = SampleToken(AssetHolder.XOR_ASSET_ID, "XOR", "SORA", 0.0042, 0.034)
    private val valToken =
        SampleToken(PortfolioSlots.VAL_ID, "VAL", "SORA Validator Token", 0.08, -0.008)
    private val pswap = SampleToken(PortfolioSlots.PSWAP_ID, "PSWAP", "Polkaswap", 0.0004, 0.019)
    private val xstusd =
        SampleToken(PortfolioSlots.XSTUSD_ID, "XSTUSD", "SORA Synthetic USD", 1.0, 0.0)
    private val eth = SampleToken(PortfolioSlots.ETH_ID, "ETH", "Ether", 2410.0, 0.027)
    private val xst = SampleToken(XST_ID, "XST", "SORA Synthetics", 0.0035, -0.012)
    private val dai = SampleToken(DAI_ID, "DAI", "Dai", 1.0, 0.0)
    private val kxor = SampleToken(KXOR_ID, "KXOR", "Kensetsu XOR", null, null) // no price: unpriced

    private val wallets = listOf(MAIN to "Main", SAVINGS to "Savings", TRADING to "Trading")

    private fun nexusAddress(walletId: String, network: WalletNetworkId) =
        "${network.wireId}-sample-${walletId.takeLast(3)}"

    fun snapshot(
        mode: SampleMode,
        freshness: SampleFreshness,
        selectedId: String,
        reads: Map<String, Long>,
        now: Long,
        icon: (String) -> String,
    ): PortfolioSnapshot {
        val walletList = when (mode) {
            SampleMode.FULL -> wallets
            SampleMode.EMPTY -> wallets.take(1)
        }.map { (id, name) -> PortfolioWallet(id, name, id == selectedId) }
        fun token(t: SampleToken) = PortfolioToken(t.id, t.symbol, t.name, icon(t.id), 18, t.price, t.change)
        fun holding(
            wallet: String,
            network: WalletNetworkId,
            t: SampleToken,
            total: String,
            locked: String = "0",
            hidden: Boolean = false,
        ) = PortfolioHolding(
            walletId = wallet,
            network = network,
            token = token(t),
            total = BigDecimal(total),
            transferable = BigDecimal(total) - BigDecimal(locked),
            hidden = hidden,
            address = if (network == WalletNetworkId.SORA2) wallet else nexusAddress(wallet, network),
            sendAvailable = network != WalletNetworkId.MINAMOTO,
        )
        val minamotoMainReachable = freshness != SampleFreshness.PARTIAL
        val holdings = if (mode == SampleMode.EMPTY) {
            emptyList()
        } else {
            listOfNotNull(
                holding(MAIN, WalletNetworkId.SORA2, xor, "420000", locked = "8000"),
                holding(MAIN, WalletNetworkId.SORA2, xstusd, "2100.55"),
                holding(MAIN, WalletNetworkId.SORA2, eth, "0.8512"),
                holding(MAIN, WalletNetworkId.SORA2, valToken, "9500"),
                holding(MAIN, WalletNetworkId.SORA2, pswap, "1200000"),
                if (minamotoMainReachable) holding(MAIN, WalletNetworkId.MINAMOTO, xor, "120000") else null,
                holding(MAIN, WalletNetworkId.TAIRA, xor, "5000"),
                holding(SAVINGS, WalletNetworkId.SORA2, xstusd, "1500"),
                holding(SAVINGS, WalletNetworkId.SORA2, eth, "0.3"),
                holding(SAVINGS, WalletNetworkId.SORA2, xst, "150000"),
                holding(SAVINGS, WalletNetworkId.SORA2, dai, "600", hidden = true),
                holding(SAVINGS, WalletNetworkId.SORA2, kxor, "2000"),
                holding(SAVINGS, WalletNetworkId.MINAMOTO, xor, "60000"),
                holding(TRADING, WalletNetworkId.SORA2, xor, "150000"),
                holding(TRADING, WalletNetworkId.SORA2, valToken, "4000"),
                holding(TRADING, WalletNetworkId.SORA2, pswap, "500000"),
                holding(TRADING, WalletNetworkId.SORA2, xst, "60000"),
                holding(TRADING, WalletNetworkId.TAIRA, xor, "20000"),
            )
        }
        val sync = walletList.associate { wallet ->
            val unreachable = if (wallet.id == MAIN && !minamotoMainReachable) setOf(WalletNetworkId.MINAMOTO) else emptySet()
            wallet.id to WalletSync(
                refreshedAtMillis = reads[wallet.id],
                unreachable = unreachable,
                live = wallet.selected,
            )
        }
        val addresses = walletList.associate { wallet ->
            wallet.id to mapOf(
                WalletNetworkId.SORA2 to wallet.id,
                WalletNetworkId.MINAMOTO to nexusAddress(wallet.id, WalletNetworkId.MINAMOTO),
                WalletNetworkId.TAIRA to nexusAddress(wallet.id, WalletNetworkId.TAIRA),
            )
        }
        return PortfolioSnapshot(
            wallets = walletList,
            holdings = holdings,
            sync = sync,
            connected = freshness != SampleFreshness.OFFLINE,
            addresses = addresses,
            fiatSymbol = "$",
        )
    }

    /** Ages the sample starts with: Main 12 s, Savings 31 s (or 2 h), Trading 18 s. */
    fun initialReads(freshness: SampleFreshness, now: Long): Map<String, Long> = mapOf(
        MAIN to now - 12_000L,
        SAVINGS to if (freshness == SampleFreshness.SAVED) now - 2 * 3_600_000L else now - 31_000L,
        TRADING to now - 18_000L,
    )
}

/** [PortfolioSource] over [SamplePortfolio]. Switching wallets only changes the sample. */
internal class SamplePortfolioSource(
    private val mode: SampleMode,
    private val freshness: SampleFreshness,
    private val icon: (String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : PortfolioSource {

    private val selected = MutableStateFlow(SamplePortfolio.MAIN)
    private val reads = MutableStateFlow(SamplePortfolio.initialReads(freshness, clock()))

    override val isSample: Boolean = true

    override val lastReads: StateFlow<Map<String, Long>> = reads.asStateFlow()

    override fun observe(): Flow<PortfolioSnapshot> = combine(selected, reads) { selectedId, read ->
        SamplePortfolio.snapshot(mode, freshness, selectedId, read, clock(), icon)
    }

    override suspend fun refresh(walletIds: Collection<String>) {
        delay(300)
        if (freshness == SampleFreshness.OFFLINE) return
        val now = clock()
        // In the "saved" sample, Savings cannot be reached, so it keeps its age.
        val refreshed = walletIds.filterNot {
            freshness == SampleFreshness.SAVED && it == SamplePortfolio.SAVINGS
        }
        reads.update { current -> current + refreshed.associateWith { now } }
    }

    override suspend fun select(walletId: String) {
        selected.value = walletId
    }
}
