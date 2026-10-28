package jp.co.soramitsu.common.data.network.dto

import java.math.BigInteger
import jp.co.soramitsu.common.util.ParseModel

/**
 * Polkamarkt's runtime `MarketId` is SCALE `u32`. Kotlin has no unsigned type that is as broadly
 * supported by the JSON and persistence stacks used by the app, so the lossless mobile boundary is
 * a range-checked [Long].
 */
object PolkamarktMarketId {
    const val MAX_VALUE: Long = 4_294_967_295L

    fun requireValid(
        value: Long,
        error: String = "POLKAMARKT_INVALID_MARKET_ID",
    ): Long {
        require(value in 0L..MAX_VALUE) { error }
        return value
    }

    fun toScale(value: Long): BigInteger =
        BigInteger.valueOf(requireValid(value))
}

/** Exact durable-journal identity shared by Polkamarkt producers and recovery consumers. */
object PolkamarktPendingAssetId {
    data class Parsed(
        val marketId: Long,
        val operation: String,
    )

    private val operations = setOf(
        "BUY",
        "SELL",
        "TRADER_CLAIM",
        "CREATOR_CLAIM",
    )

    fun encode(marketId: Long, operation: String): String {
        PolkamarktMarketId.requireValid(marketId)
        require(operation in operations) { "POLKAMARKT_PENDING_OPERATION_INVALID" }
        return "polkamarkt:$marketId:$operation"
    }

    fun parse(value: String): Parsed? {
        if (value != value.trim() || value.length > MAXIMUM_LENGTH) return null
        val parts = value.split(':')
        if (parts.size != 3 || parts[0] != "polkamarkt") return null
        val marketId = parts[1].toLongOrNull()
            ?.takeIf { it in 0L..PolkamarktMarketId.MAX_VALUE }
            ?: return null
        val operation = parts[2].takeIf(operations::contains) ?: return null
        return Parsed(marketId, operation).takeIf {
            encode(it.marketId, it.operation) == value
        }
    }

    private const val MAXIMUM_LENGTH = 64
}

data class PolkamarktBuyQuoteDto(
    val marketId: Long,
    val outcome: String,
    val collateralIn: BigInteger,
    val feeAmount: BigInteger,
    val pricingCollateral: BigInteger,
    val sharesOut: BigInteger,
) : ParseModel() {
    init {
        PolkamarktMarketId.requireValid(marketId)
    }
}

data class PolkamarktSellQuoteDto(
    val marketId: Long,
    val outcome: String,
    val sharesIn: BigInteger,
    val grossCollateralOut: BigInteger,
    val feeAmount: BigInteger,
    val collateralOut: BigInteger,
) : ParseModel() {
    init {
        PolkamarktMarketId.requireValid(marketId)
    }
}

data class PolkamarktMarketStateDto(
    val marketId: Long,
    val mechanism: String,
    val virtualDepth: BigInteger,
    val realYesShares: BigInteger,
    val realNoShares: BigInteger,
    val dpmCollateral: BigInteger,
    val marginalYesPriceBps: Int,
    val marginalNoPriceBps: Int,
    val impliedYesProbabilityBps: Int,
    val impliedNoProbabilityBps: Int,
) : ParseModel() {
    init {
        PolkamarktMarketId.requireValid(marketId)
    }
}

data class PolkamarktAuthoritativeMarketDto(
    val marketId: Long,
    val status: String,
    val closeBlock: BigInteger,
    val observedBlockNumber: BigInteger,
) : ParseModel() {
    init {
        PolkamarktMarketId.requireValid(marketId)
    }
}

data class PolkamarktClaimableDto(
    val marketId: Long,
    val account: String,
    val status: String,
    val resolutionOutcome: String?,
    val yesShares: BigInteger,
    val noShares: BigInteger,
    val netCollateralPaid: BigInteger,
    val traderPayout: BigInteger,
    val claimablePayout: BigInteger,
    val creatorFees: BigInteger,
    val isCreator: Boolean,
) : ParseModel() {
    init {
        PolkamarktMarketId.requireValid(marketId)
    }
}

data class FinalizedRuntimeValue<T>(
    val blockHash: String,
    val value: T?,
)
