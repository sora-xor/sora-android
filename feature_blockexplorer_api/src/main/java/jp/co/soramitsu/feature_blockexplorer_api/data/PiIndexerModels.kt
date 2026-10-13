package jp.co.soramitsu.feature_blockexplorer_api.data

import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import kotlinx.serialization.Serializable

/**
 * Models exposed by the consolidated PI indexer.
 *
 * Quantities that can affect balances, fees, quotes, or transaction construction deliberately stay
 * as decimal strings. Callers must parse them with BigInteger/BigDecimal and must never round-trip
 * them through Double.
 */
@Serializable
data class PiIndexerHealth(
    val ok: Boolean,
    val repositoryReady: Boolean,
    val service: String,
    val serviceId: String,
    val schemaVersion: Int,
    val ecosystem: String,
    val chainId: String,
    val network: String,
    val publicBaseUrl: String,
    val readOnly: Boolean,
    val genesisHash: String?,
    @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
    val latestIndexedBlock: String?,
    val latestIndexedBlockHash: String?,
    @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
    val latestIndexedAt: String?,
    val workerAvailable: Boolean,
    val workerReady: Boolean?,
    val workerReadinessReason: String?,
    val workerLifecycle: String?,
    val workerStartupComplete: Boolean?,
    @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
    val workerLatestFinalizedBlock: String?,
    @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
    val workerLatestIndexedBlock: String?,
    @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
    val workerLag: String?,
    @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
    val workerLastSuccessfulIndexTimestamp: String?,
    val workerLastError: String?,
    @Serializable(with = NullableCanonicalUnsignedIntegerLexemeSerializer::class)
    val workerLastErrorTimestamp: String?,
)

data class PiMobileChainNode(
    val name: String,
    val address: String,
)

data class PiMobileConfig(
    val blockExplorerUrl: String,
    val substrateTypesUrl: String?,
    val soracard: Boolean,
    val nodes: List<PiMobileChainNode>,
    val nexusAvailable: Boolean,
    val nexusSendsAvailable: Boolean,
    val polkamarktVisible: Boolean,
    val polkamarktMutationsAvailable: Boolean,
    val tairaDefaultVisible: Boolean,
    /**
     * The qualified PI schema v1 exposes only an untyped `account: JSON` field. Keep account
     * balances unavailable until PI publishes a typed identity- and checkpoint-bearing contract;
     * callers must continue to use runtime/RPC for authoritative balances.
     */
    val typedAccountBalancesAvailable: Boolean = false,
)

data class PiCursorPage<T>(
    val items: List<T>,
    val endCursor: String?,
    val hasNextPage: Boolean,
    val totalCount: Int,
)

/**
 * A PI read together with the exact live health/checkpoint preflight that
 * qualified it. Offline history consumers must validate against this health
 * rather than issuing a separate health request that could describe another
 * checkpoint.
 */
data class PiQualifiedRead<T>(
    val value: T,
    val health: PiIndexerHealth,
    val fromCache: Boolean,
)

@Serializable
data class PolkamarktMarket(
    val id: String,
    val marketId: Long?,
    val title: String?,
    val category: String?,
    val tags: String?,
    val description: String?,
    val rulesUri: String?,
    val resolutionSource: String?,
    val closeBlock: Long?,
    val status: String?,
    val mechanism: String?,
    val creator: String?,
    val collateralAsset: String?,
    val creatorFees: String?,
    val liquidityUsd: String?,
    val volumeUsd: String?,
    val chartProbability: Double?,
    val chartPriceYes: Double?,
    val chartPriceNo: Double?,
    val virtualDepth: String?,
    val dpmCollateral: String?,
    val realYesShares: String?,
    val realNoShares: String?,
    val marginalYesPriceBps: Int?,
    val marginalNoPriceBps: Int?,
    val impliedYesProbabilityBps: Int?,
    val impliedNoProbabilityBps: Int?,
    val collateral: String?,
    val yesShares: String?,
    val noShares: String?,
    val resolutionOutcome: String?,
    val resolutionEvidenceUri: String?,
    val governanceUrl: String?,
    val updatedAtBlock: Long?,
    val timestamp: Long?,
    val metadataUri: String? = null,
    val cancellationEvidenceUri: String? = null,
    /** Exact display-only PI enrichment, with non-DPM price/probability fallback. */
    val displayYesProbabilityBps: Int? = null,
    val displayNoProbabilityBps: Int? = null,
) {
    init {
        marketId?.let {
            PolkamarktMarketId.requireValid(it, "PI_INDEXER_INVALID_MARKET_ID")
        }
        closeBlock?.let {
            PolkamarktMarketId.requireValid(it, "PI_INDEXER_INVALID_CLOSE_BLOCK")
        }
    }
}

data class PolkamarktMarketSnapshot(
    val id: String,
    val marketId: Long?,
    val timestamp: Long?,
    val blockHeight: Long?,
    val type: String?,
    val chartProbability: Double?,
    val chartPriceYes: Double?,
    val chartPriceNo: Double?,
    val virtualDepth: String?,
    val dpmCollateral: String?,
    val realYesShares: String?,
    val realNoShares: String?,
    val marginalYesPriceBps: Int?,
    val marginalNoPriceBps: Int?,
    val impliedYesProbabilityBps: Int?,
    val impliedNoProbabilityBps: Int?,
    val collateral: String?,
    val yesShares: String?,
    val noShares: String?,
    val liquidityUsd: String?,
    val volumeUsd: String?,
    val status: String?,
) {
    init {
        marketId?.let {
            PolkamarktMarketId.requireValid(it, "PI_INDEXER_INVALID_MARKET_ID")
        }
    }
}

data class PolkamarktAccountPosition(
    val id: String,
    val account: String?,
    val marketId: Long?,
    val outcome: String?,
    val shares: String?,
    val yesShares: String?,
    val noShares: String?,
    val netCollateralPaid: String?,
    val costBasisUsd: String?,
    val marketValueUsd: String?,
    val realizedPnlUsd: String?,
    val unrealizedPnlUsd: String?,
    val claimablePayoutUsd: String?,
    val isCreator: Boolean?,
    val status: String?,
    val updatedAt: String?,
    val market: PolkamarktMarket?,
) {
    init {
        marketId?.let {
            PolkamarktMarketId.requireValid(it, "PI_INDEXER_INVALID_MARKET_ID")
        }
    }
}

data class PolkamarktAccountTrade(
    val id: String,
    val account: String?,
    val marketId: Long?,
    val marketIds: List<Long>,
    val side: String?,
    val outcome: String?,
    val collateralAmountUsd: String?,
    val sharesAmount: String?,
    val sharesIn: String?,
    val sharesOut: String?,
    val executionPrice: String?,
    val feeAmountUsd: String?,
    val realizedPnlUsd: String?,
    val timestamp: String?,
    val blockNumber: Long?,
    val blockHash: String?,
    val extrinsicHash: String?,
    val market: PolkamarktMarket?,
) {
    init {
        marketId?.let {
            PolkamarktMarketId.requireValid(it, "PI_INDEXER_INVALID_MARKET_ID")
        }
        require(
            marketIds.size <= 24 &&
                marketIds.distinct().size == marketIds.size &&
                marketIds.all { it in 0L..PolkamarktMarketId.MAX_VALUE } &&
                (marketIds.isEmpty() || marketIds.first() == marketId)
        ) { "PI_INDEXER_INVALID_MARKET_IDS" }
    }
}

data class PolkamarktSignals(
    val totalVolumeUsd: String,
    val activeMarkets: Int,
    val activeAccounts: Int,
    val liquidityUsd: String,
    val liquiditySeries: List<PolkamarktSignalPoint>,
    val answerBreakdown: List<PolkamarktAnswerBreakdown>,
    val accuracyPercent: String?,
)

data class PolkamarktSignalPoint(
    val label: String,
    val value: String,
)

data class PolkamarktAnswerBreakdown(
    val answer: String,
    val volumeUsd: String,
    val markets: Int,
)
