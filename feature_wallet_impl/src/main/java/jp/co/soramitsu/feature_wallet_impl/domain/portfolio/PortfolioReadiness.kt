package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import java.math.BigDecimal
import jp.co.soramitsu.common.nexus.WalletNetworkId

/**
 * What stands between a holding and a swap (portfolio-view SPEC §5.1). Polkaswap swaps run on
 * SORA2 only and this app has no route from the Nexus networks to SORA2, so nothing is ever
 * "one move away": a route that does not exist is never invented.
 */
enum class Readiness {
    /** On SORA2, the asset trades on Polkaswap and swaps are on in this build. */
    READY,

    /** On Minamoto (or SORA2 with swaps off): no route to Polkaswap in this version. */
    NONE,

    /** On Taira, the Nexus testnet: no monetary value. */
    TEST,
}

/** Why Move cannot start from a holding. */
enum class MoveBlock {
    /** The user has no other wallet with an account on this network. */
    NO_OTHER_WALLET,

    /** The app cannot send on this network in this build (Nexus send unavailable or not live). */
    SEND_UNAVAILABLE,

    /** Nothing is transferable. */
    NOTHING_TRANSFERABLE,
}

/** Why Swap cannot start from a holding. */
enum class SwapBlock {
    NOT_ON_SORA2,
    TESTNET,
    NOTHING_TRANSFERABLE,
}

object PortfolioReadiness {

    /** Polkaswap swaps are part of every build of this app (the Swap tab). */
    const val SWAPS_AVAILABLE = true

    /** Popular Polkaswap targets offered by the Swap sheet, in this order. */
    val POPULAR_SWAP_TARGETS: List<String> = listOf(
        jp.co.soramitsu.common.domain.AssetHolder.XOR_ASSET_ID,
        PortfolioSlots.VAL_ID,
        PortfolioSlots.PSWAP_ID,
        PortfolioSlots.XSTUSD_ID,
    )

    fun readiness(holding: PortfolioHolding, swapsAvailable: Boolean = SWAPS_AVAILABLE): Readiness =
        when (holding.network) {
            WalletNetworkId.SORA2 -> if (swapsAvailable) Readiness.READY else Readiness.NONE
            WalletNetworkId.MINAMOTO -> Readiness.NONE
            WalletNetworkId.TAIRA -> Readiness.TEST
        }

    /** Other wallets of this user that have an account on the holding's network: Move destinations. */
    fun moveTargets(snapshot: PortfolioSnapshot, holding: PortfolioHolding): List<PortfolioWallet> =
        snapshot.wallets.filter { wallet ->
            wallet.id != holding.walletId &&
                !snapshot.addresses[wallet.id]?.get(holding.network).isNullOrBlank()
        }

    fun moveBlock(snapshot: PortfolioSnapshot, holding: PortfolioHolding): MoveBlock? = when {
        moveTargets(snapshot, holding).isEmpty() -> MoveBlock.NO_OTHER_WALLET
        !holding.sendAvailable -> MoveBlock.SEND_UNAVAILABLE
        holding.transferable <= BigDecimal.ZERO -> MoveBlock.NOTHING_TRANSFERABLE
        else -> null
    }

    fun swapBlock(holding: PortfolioHolding): SwapBlock? = when (readiness(holding)) {
        Readiness.READY -> if (holding.transferable <= BigDecimal.ZERO) SwapBlock.NOTHING_TRANSFERABLE else null
        Readiness.NONE -> SwapBlock.NOT_ON_SORA2
        Readiness.TEST -> SwapBlock.TESTNET
    }

    /** Swap targets for a pay token: the popular list without the token itself. */
    fun swapTargets(payTokenId: String): List<String> = POPULAR_SWAP_TARGETS.filter { it != payTokenId }

    /** Amount for Max: the transferable balance; the destination flow reserves the fee. */
    fun maxAmount(holding: PortfolioHolding): BigDecimal = holding.transferable.max(BigDecimal.ZERO)
}
