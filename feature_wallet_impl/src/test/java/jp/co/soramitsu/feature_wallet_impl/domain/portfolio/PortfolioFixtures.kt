package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId

/** Small, readable fixtures for the portfolio tests. Prices are invented. */
internal object PortfolioFixtures {
    const val MAIN = "wallet-main"
    const val SAVINGS = "wallet-savings"
    const val TRADING = "wallet-trading"
    const val DAI = "0x0200060000000000000000000000000000000000000000000000000000000000"
    const val KXOR = "0x02000e0000000000000000000000000000000000000000000000000000000000"

    fun token(id: String, symbol: String, price: Double?, change: Double? = null) =
        PortfolioToken(id, symbol, symbol, null, 18, price, change)

    val xor = token(AssetHolder.XOR_ASSET_ID, "XOR", 0.004, 0.05)
    val valToken = token(PortfolioSlots.VAL_ID, "VAL", 0.1, -0.1)
    val pswap = token(PortfolioSlots.PSWAP_ID, "PSWAP", 0.001)
    val xstusd = token(PortfolioSlots.XSTUSD_ID, "XSTUSD", 1.0, 0.0)
    val eth = token(PortfolioSlots.ETH_ID, "ETH", 2000.0, 0.25)
    val dai = token(DAI, "DAI", 1.0)
    val kxor = token(KXOR, "KXOR", null)

    fun holding(
        wallet: String,
        token: PortfolioToken,
        total: String,
        network: WalletNetworkId = WalletNetworkId.SORA2,
        transferable: String = total,
        hidden: Boolean = false,
        sendAvailable: Boolean = true,
    ) = PortfolioHolding(
        walletId = wallet,
        network = network,
        token = token,
        total = BigDecimal(total),
        transferable = BigDecimal(transferable),
        hidden = hidden,
        address = if (network == WalletNetworkId.SORA2) wallet else "$wallet-${network.wireId}",
        sendAvailable = sendAvailable,
    )

    fun snapshot(
        holdings: List<PortfolioHolding>,
        wallets: List<String> = listOf(MAIN, SAVINGS, TRADING),
        selected: String = MAIN,
        sync: Map<String, WalletSync> = wallets.associateWith {
            WalletSync(refreshedAtMillis = 1_000L, live = it == selected)
        },
        connected: Boolean = true,
        addresses: Map<String, Map<WalletNetworkId, String>> = wallets.associateWith { id ->
            mapOf(
                WalletNetworkId.SORA2 to id,
                WalletNetworkId.MINAMOTO to "$id-minamoto",
                WalletNetworkId.TAIRA to "$id-taira",
            )
        },
    ) = PortfolioSnapshot(
        wallets = wallets.map {
            PortfolioWallet(it, it.removePrefix("wallet-").replaceFirstChar(Char::uppercase), it == selected)
        },
        holdings = holdings,
        sync = sync,
        connected = connected,
        addresses = addresses,
        fiatSymbol = "$",
    )
}
