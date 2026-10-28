package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktPendingTransaction
import jp.co.soramitsu.feature_polkaswap_impl.data.repository.PolkamarktWebContract

enum class PolkamarktClaimConfirmationSource {
    SELECTED_DETAIL,
    REVIEWED_POSITIONS,
}

enum class PolkamarktClaimKind {
    TRADER_PAYOUT,
    CREATOR_FEES,
}

/**
 * Exact, account-bound runtime values that the user reviewed before a claim mutation.
 *
 * PI positions only discover candidate market IDs. Every quantity in this authorization comes
 * from one finalized runtime checkpoint, and [PolkamarktClaimConfirmationPolicy.requireCurrent]
 * requires the corresponding state namespace to remain byte-for-byte equivalent before launch.
 */
data class PolkamarktClaimConfirmation(
    val source: PolkamarktClaimConfirmationSource,
    val kind: PolkamarktClaimKind,
    val accountId: String,
    val marketIds: List<Long>,
    val finalizedBlockHash: String,
    val claims: Map<Long, PolkamarktClaimableDto>,
) {
    val totalAmount: BigInteger
        get() = marketIds.fold(BigInteger.ZERO) { total, marketId ->
            val claim = checkNotNull(claims[marketId]) {
                "POLKAMARKT_CLAIM_CONFIRMATION_INCOMPLETE"
            }
            total + when (kind) {
                PolkamarktClaimKind.TRADER_PAYOUT -> claim.claimablePayout
                PolkamarktClaimKind.CREATOR_FEES -> claim.creatorFees
            }
        }
}

internal object PolkamarktClaimConfirmationPolicy {
    private val finalizedHashPattern = Regex("^0x[0-9a-f]{64}$")

    fun selected(
        accountId: String,
        marketId: Long,
        claimable: PolkamarktClaimableDto?,
        finalizedBlockHash: String?,
        creatorFees: Boolean,
    ): PolkamarktClaimConfirmation = single(
        source = PolkamarktClaimConfirmationSource.SELECTED_DETAIL,
        accountId = accountId,
        marketId = marketId,
        claimable = claimable,
        finalizedBlockHash = finalizedBlockHash,
        creatorFees = creatorFees,
    )

    fun reviewedPosition(
        accountId: String,
        marketId: Long,
        claims: Map<Long, PolkamarktClaimableDto>,
        reviewedMarketIds: Set<Long>,
        finalizedBlockHash: String?,
        creatorFees: Boolean,
    ): PolkamarktClaimConfirmation {
        check(marketId in reviewedMarketIds) { "POLKAMARKT_CLAIM_NOT_REVIEWED" }
        return single(
            source = PolkamarktClaimConfirmationSource.REVIEWED_POSITIONS,
            accountId = accountId,
            marketId = marketId,
            claimable = claims[marketId],
            finalizedBlockHash = finalizedBlockHash,
            creatorFees = creatorFees,
        )
    }

    fun reviewedBatch(
        accountId: String,
        marketIds: List<Long>,
        claims: Map<Long, PolkamarktClaimableDto>,
        reviewedMarketIds: Set<Long>,
        finalizedBlockHash: String?,
    ): PolkamarktClaimConfirmation {
        check(marketIds.size in 2..PolkamarktWebContract.MAX_BATCH_CLAIMS) {
            "POLKAMARKT_CLAIM_BATCH_INVALID"
        }
        check(marketIds == marketIds.distinct().sorted()) {
            "POLKAMARKT_CLAIM_BATCH_NOT_CANONICAL"
        }
        check(reviewedMarketIds.containsAll(marketIds)) {
            "POLKAMARKT_CLAIM_NOT_REVIEWED"
        }
        val canonicalHash = requireFinalizedHash(finalizedBlockHash)
        val reviewedClaims = linkedMapOf<Long, PolkamarktClaimableDto>()
        marketIds.forEach { marketId ->
            reviewedClaims[marketId] = requireClaim(
                accountId = accountId,
                marketId = marketId,
                claimable = claims[marketId],
                kind = PolkamarktClaimKind.TRADER_PAYOUT,
            )
        }
        return PolkamarktClaimConfirmation(
            source = PolkamarktClaimConfirmationSource.REVIEWED_POSITIONS,
            kind = PolkamarktClaimKind.TRADER_PAYOUT,
            accountId = accountId,
            marketIds = marketIds,
            finalizedBlockHash = canonicalHash,
            claims = reviewedClaims,
        )
    }

    fun requireCurrent(
        confirmed: PolkamarktClaimConfirmation,
        accountId: String,
        selectedMarketId: Long?,
        selectedClaimable: PolkamarktClaimableDto?,
        selectedFinalizedBlockHash: String?,
        reviewedClaims: Map<Long, PolkamarktClaimableDto>,
        reviewedMarketIds: Set<Long>,
        claimReviewFinalizedBlockHash: String?,
    ) {
        val current = when (confirmed.source) {
            PolkamarktClaimConfirmationSource.SELECTED_DETAIL -> selected(
                accountId = accountId,
                marketId = checkNotNull(selectedMarketId) {
                    "POLKAMARKT_CLAIM_CONFIRMATION_STALE"
                },
                claimable = selectedClaimable,
                finalizedBlockHash = selectedFinalizedBlockHash,
                creatorFees = confirmed.kind == PolkamarktClaimKind.CREATOR_FEES,
            )

            PolkamarktClaimConfirmationSource.REVIEWED_POSITIONS ->
                if (confirmed.marketIds.size == 1) {
                    reviewedPosition(
                        accountId = accountId,
                        marketId = confirmed.marketIds.single(),
                        claims = reviewedClaims,
                        reviewedMarketIds = reviewedMarketIds,
                        finalizedBlockHash = claimReviewFinalizedBlockHash,
                        creatorFees = confirmed.kind == PolkamarktClaimKind.CREATOR_FEES,
                    )
                } else {
                    check(confirmed.kind == PolkamarktClaimKind.TRADER_PAYOUT) {
                        "POLKAMARKT_CLAIM_CONFIRMATION_STALE"
                    }
                    reviewedBatch(
                        accountId = accountId,
                        marketIds = confirmed.marketIds,
                        claims = reviewedClaims,
                        reviewedMarketIds = reviewedMarketIds,
                        finalizedBlockHash = claimReviewFinalizedBlockHash,
                    )
                }
        }
        check(current == confirmed) { "POLKAMARKT_CLAIM_CONFIRMATION_STALE" }
    }

    private fun single(
        source: PolkamarktClaimConfirmationSource,
        accountId: String,
        marketId: Long,
        claimable: PolkamarktClaimableDto?,
        finalizedBlockHash: String?,
        creatorFees: Boolean,
    ): PolkamarktClaimConfirmation {
        val kind = if (creatorFees) {
            PolkamarktClaimKind.CREATOR_FEES
        } else {
            PolkamarktClaimKind.TRADER_PAYOUT
        }
        val reviewedClaim = requireClaim(accountId, marketId, claimable, kind)
        return PolkamarktClaimConfirmation(
            source = source,
            kind = kind,
            accountId = accountId,
            marketIds = listOf(marketId),
            finalizedBlockHash = requireFinalizedHash(finalizedBlockHash),
            claims = mapOf(marketId to reviewedClaim),
        )
    }

    private fun requireClaim(
        accountId: String,
        marketId: Long,
        claimable: PolkamarktClaimableDto?,
        kind: PolkamarktClaimKind,
    ): PolkamarktClaimableDto {
        check(accountId.isNotBlank()) { "POLKAMARKT_ACCOUNT_CHANGED" }
        val reviewedClaim = checkNotNull(claimable) {
            "POLKAMARKT_CLAIM_IDENTITY_CHANGED"
        }
        check(
            reviewedClaim.marketId == marketId &&
                reviewedClaim.account == accountId
        ) { "POLKAMARKT_CLAIM_IDENTITY_CHANGED" }
        check(
            PolkamarktWebContract.CLAIMABLE_STATUSES.any {
                it.equals(reviewedClaim.status, ignoreCase = true)
            }
        ) { "POLKAMARKT_CLAIM_STATUS_CHANGED" }
        when (kind) {
            PolkamarktClaimKind.TRADER_PAYOUT ->
                check(reviewedClaim.claimablePayout.signum() == 1) {
                    "POLKAMARKT_CLAIM_NOT_AVAILABLE"
                }

            PolkamarktClaimKind.CREATOR_FEES -> check(
                reviewedClaim.isCreator && reviewedClaim.creatorFees.signum() == 1
            ) { "POLKAMARKT_CREATOR_FEES_NOT_AVAILABLE" }
        }
        return reviewedClaim
    }

    private fun requireFinalizedHash(value: String?): String =
        (value ?: throw IllegalStateException("POLKAMARKT_CLAIM_CHECKPOINT_INVALID"))
            .also {
                check(
                    finalizedHashPattern.matches(it) &&
                        it.substring(2).any { digit -> digit != '0' }
                ) {
                    "POLKAMARKT_CLAIM_CHECKPOINT_INVALID"
                }
            }
}

/** Synchronously serializes every signing mutation before a coroutine can be queued. */
internal class PolkamarktMutationUseGate {
    private val inFlight = AtomicBoolean(false)

    fun tryAcquire(): Boolean = inFlight.compareAndSet(false, true)

    fun release() {
        check(inFlight.compareAndSet(true, false)) {
            "POLKAMARKT_MUTATION_GATE_NOT_ACQUIRED"
        }
    }

    fun isAcquired(): Boolean = inFlight.get()
}

internal object PolkamarktMutationAdmissionPolicy {
    fun requiresPendingRecovery(
        pending: List<PolkamarktPendingTransaction>,
    ): Boolean = pending.isNotEmpty()

    fun canPrepareQuote(
        mutationInFlight: Boolean,
        pendingRecoveryRequired: Boolean,
    ): Boolean = !mutationInFlight && !pendingRecoveryRequired
}
