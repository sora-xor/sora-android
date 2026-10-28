package jp.co.soramitsu.feature_wallet_impl.domain.portfolio

import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.nexus.WalletNetworkId

/**
 * Stable chart slots (portfolio-view SPEC §3.1): colour follows the entity, not its rank. An entity
 * keeps its slot while it stays in the top five; a newcomer takes the lowest free slot; everything
 * else is Other. One map per scope and lens, persisted as `portfolio.slots.v1`.
 */
object PortfolioSlots {
    const val SLOT_COUNT = 5

    const val VAL_ID = "0x0200040000000000000000000000000000000000000000000000000000000000"
    const val PSWAP_ID = "0x0200050000000000000000000000000000000000000000000000000000000000"
    const val ETH_ID = "0x0200070000000000000000000000000000000000000000000000000000000000"
    const val XSTUSD_ID = "0x0200080000000000000000000000000000000000000000000000000000000000"

    /** First launch matches across devices: XOR, VAL, PSWAP, XSTUSD, ETH. */
    private val tokenSeed: Map<String, Int> = listOf(
        AssetHolder.XOR_ASSET_ID,
        VAL_ID,
        PSWAP_ID,
        XSTUSD_ID,
        ETH_ID,
    ).withIndex().associate { it.value to it.index }

    private val networkSeed: Map<String, Int> = listOf(
        WalletNetworkId.SORA2.wireId,
        WalletNetworkId.MINAMOTO.wireId,
    ).withIndex().associate { it.value to it.index }

    fun seed(lens: PortfolioLens): Map<String, Int> = when (lens) {
        PortfolioLens.TOKENS -> tokenSeed
        PortfolioLens.NETWORKS -> networkSeed
        PortfolioLens.WALLETS -> emptyMap()
    }

    /** The persisted map's key for one scope and lens; a single-wallet scope is keyed by its wallet. */
    fun mapKey(scope: PortfolioScope, selectedWalletId: String?, lens: PortfolioLens): String =
        when (scope) {
            PortfolioScope.ALL -> "all"
            PortfolioScope.SELECTED -> "wallet:${selectedWalletId.orEmpty()}"
        } + "|" + lens.key

    /**
     * Assigns slots to the top [SLOT_COUNT] of [ranked] (largest first). [previous] is the stored
     * map for this scope and lens, or null on first use, when [seed] applies.
     */
    fun assign(
        previous: Map<String, Int>?,
        ranked: List<String>,
        seed: Map<String, Int>,
    ): Map<String, Int> {
        val start = previous ?: seed
        val top = ranked.distinct().take(SLOT_COUNT)
        val result = LinkedHashMap<String, Int>()
        top.forEach { key ->
            val slot = start[key]
            if (slot != null && slot in 0 until SLOT_COUNT && slot !in result.values) result[key] = slot
        }
        top.filter { it !in result }.forEach { key ->
            val free = (0 until SLOT_COUNT).first { it !in result.values }
            result[key] = free
        }
        return result
    }
}
