package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.data.network.dto.PolkamarktBuyQuoteDto
import jp.co.soramitsu.common.data.network.dto.PolkamarktClaimableDto
import jp.co.soramitsu.common.data.network.dto.PolkamarktMarketId
import jp.co.soramitsu.common.data.network.dto.PolkamarktPendingAssetId
import jp.co.soramitsu.common.data.network.dto.PolkamarktSellQuoteDto
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.nexus.WalletNetworkChainIdentity
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.PiIndexerOfflineFallbackPolicy
import jp.co.soramitsu.feature_blockexplorer_api.data.PiQualifiedRead
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktCatalogCache
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkamarktMarket
import jp.co.soramitsu.feature_blockexplorer_api.data.PolkaswapIndexerClient
import jp.co.soramitsu.feature_blockexplorer_api.data.ProductionFeatureManager
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.QualifiedSora2MutationRuntime
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.DefinitelyNotSubmitted
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmissionUnknown
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmissionUnknownException
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import jp.co.soramitsu.sora.substrate.substrate.polkamarktBuy
import jp.co.soramitsu.sora.substrate.substrate.polkamarktClaimCreatorFees
import jp.co.soramitsu.sora.substrate.substrate.polkamarktClaimTraderPayout
import jp.co.soramitsu.sora.substrate.substrate.polkamarktClaimTraderPayouts
import jp.co.soramitsu.sora.substrate.substrate.polkamarktSell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class PolkamarktTradeSide {
    BUY,
    SELL,
}

enum class PolkamarktOutcome(val runtimeValue: String) {
    YES("Yes"),
    NO("No"),
}

class PolkamarktClaimAuthorization(
    accountId: String,
    marketIds: List<Long>,
    val creatorFees: Boolean,
    val reviewedFinalizedBlockHash: String,
    reviewedClaims: Map<Long, PolkamarktClaimableDto>,
) {
    val accountId: String = accountId
    val marketIds: List<Long> = marketIds.toList()
    val reviewedClaims: Map<Long, PolkamarktClaimableDto> = reviewedClaims.toMap()
}

internal object PolkamarktClaimValidator {
    fun requireTraderBatch(marketIds: List<Long>) {
        require(marketIds.isNotEmpty()) { "POLKAMARKT_EMPTY_CLAIM_BATCH" }
        require(marketIds.size <= PolkamarktWebContract.MAX_BATCH_CLAIMS) {
            "POLKAMARKT_CLAIM_BATCH_TOO_LARGE"
        }
        require(marketIds.distinct().size == marketIds.size) {
            "POLKAMARKT_DUPLICATE_CLAIM_MARKET"
        }
        require(marketIds.all { it in 0L..PolkamarktMarketId.MAX_VALUE }) {
            "POLKAMARKT_INVALID_MARKET_ID"
        }
    }

    fun requireClaimable(
        claimable: PolkamarktClaimableDto,
        expectedAccountId: String,
        expectedMarketId: Long,
        creatorFees: Boolean,
    ) {
        requireIdentity(claimable, expectedAccountId, expectedMarketId)
        check(PolkamarktWebContract.CLAIMABLE_STATUSES.any {
            it.equals(claimable.status, ignoreCase = true)
        }) { "POLKAMARKT_STATUS_NOT_CLAIMABLE" }
        if (creatorFees) {
            check(claimable.isCreator && claimable.creatorFees > BigInteger.ZERO) {
                "POLKAMARKT_CREATOR_FEES_NOT_CLAIMABLE"
            }
        } else {
            check(claimable.claimablePayout > BigInteger.ZERO) {
                "POLKAMARKT_TRADER_PAYOUT_NOT_CLAIMABLE"
            }
        }
    }

    fun requireIdentity(
        claimable: PolkamarktClaimableDto,
        expectedAccountId: String,
        expectedMarketId: Long,
    ) {
        PolkamarktMarketId.requireValid(expectedMarketId)
        check(claimable.account == expectedAccountId) {
            "POLKAMARKT_CLAIM_ACCOUNT_MISMATCH"
        }
        check(claimable.marketId == expectedMarketId) {
            "POLKAMARKT_CLAIM_MARKET_MISMATCH"
        }
    }

    fun requireFreshFeeAndBalance(
        confirmedNetworkFee: BigInteger,
        freshNetworkFee: BigInteger,
        freshXorBalance: BigInteger,
    ) {
        check(freshNetworkFee <= confirmedNetworkFee) { "POLKAMARKT_FEE_INCREASED" }
        check(freshXorBalance >= freshNetworkFee) { "POLKAMARKT_INSUFFICIENT_XOR" }
    }

    fun requireAuthorization(confirmed: PolkamarktClaimAuthorization) {
        requireTraderBatch(confirmed.marketIds)
        check(confirmed.accountId.isNotBlank()) { "POLKAMARKT_ACCOUNT_MISSING" }
        check(confirmed.marketIds == confirmed.marketIds.sorted()) {
            "POLKAMARKT_CLAIM_MARKETS_NOT_CANONICAL"
        }
        check(!confirmed.creatorFees || confirmed.marketIds.size == 1) {
            "POLKAMARKT_CLAIM_MARKETS_INVALID"
        }
        val canonicalReviewHash = try {
            confirmed.reviewedFinalizedBlockHash.canonicalExtrinsicHash()
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("POLKAMARKT_CLAIM_CHECKPOINT_INVALID", error)
        }
        check(canonicalReviewHash == confirmed.reviewedFinalizedBlockHash) {
            "POLKAMARKT_CLAIM_CHECKPOINT_INVALID"
        }
        check(confirmed.reviewedClaims.keys == confirmed.marketIds.toSet()) {
            "POLKAMARKT_CLAIM_AUTHORIZATION_INCOMPLETE"
        }
        confirmed.marketIds.forEach { marketId ->
            requireClaimable(
                claimable = checkNotNull(confirmed.reviewedClaims[marketId]) {
                    "POLKAMARKT_CLAIM_AUTHORIZATION_INCOMPLETE"
                },
                expectedAccountId = confirmed.accountId,
                expectedMarketId = marketId,
                creatorFees = confirmed.creatorFees,
            )
        }
    }

    fun requireFreshClaims(
        confirmed: PolkamarktClaimAuthorization,
        freshClaims: Map<Long, PolkamarktClaimableDto>,
    ) {
        requireAuthorization(confirmed)
        check(freshClaims == confirmed.reviewedClaims) {
            "POLKAMARKT_CLAIM_CHANGED"
        }
    }
}

internal object PolkamarktMutationAccountValidator {
    fun requireExpected(
        expectedAccountId: String,
        actualAccountId: String,
    ) {
        require(expectedAccountId.isNotBlank()) { "POLKAMARKT_ACCOUNT_MISSING" }
        check(actualAccountId == expectedAccountId) { "POLKAMARKT_ACCOUNT_CHANGED" }
    }
}

/**
 * A malformed unresolved journal is recovery state, not an empty pending overlay. Keep this
 * repository-level because mutation callers other than the current screen must fail closed too.
 */
internal object PolkamarktPendingMutationGate {
    fun requireHealthy(
        expectedWalletId: String,
        records: List<PendingNetworkTransactionLocal>,
        allowedCurrentLocalId: String? = null,
    ) {
        try {
            check(records.size <= MAX_ACTIVE_PENDING_TRANSACTIONS) {
                "PENDING_TRANSACTION_LIMIT_EXCEEDED"
            }
            val polkamarktRecords = mutableListOf<PendingNetworkTransactionLocal>()
            records.forEach { record ->
                check(record.walletId == expectedWalletId) {
                    "PENDING_TRANSACTION_WALLET_INVALID"
                }
                WalletIdentityDao.validatePendingTransaction(record)
                check(WalletIdentityDao.hasCurrentChainIdentity(record)) {
                    "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
                }
                check(
                    !record.submissionIsAmbiguous && record.state != "UNKNOWN"
                ) { "PENDING_TRANSACTION_SUBMISSION_AMBIGUOUS" }
                if (
                    record.networkId == "sora2" &&
                    PolkamarktPendingAssetId.parse(record.assetId) != null
                ) {
                    polkamarktRecords += record
                }
            }
            if (allowedCurrentLocalId == null) {
                check(polkamarktRecords.isEmpty()) {
                    "PENDING_POLKAMARKT_TRANSACTION_UNRESOLVED"
                }
            } else {
                check(
                    polkamarktRecords.size == 1 &&
                        polkamarktRecords.single().localId == allowedCurrentLocalId &&
                        polkamarktRecords.single().state == "SIGNED" &&
                        !polkamarktRecords.single().submissionIsAmbiguous
                ) {
                    "PENDING_POLKAMARKT_CURRENT_TRANSACTION_INVALID"
                }
            }
        } catch (error: IllegalStateException) {
            throw IllegalStateException("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error)
        }
    }

    private const val MAX_ACTIVE_PENDING_TRANSACTIONS = 500
}

internal object PolkamarktMarketMutationValidator {
    fun requireOpen(status: String) {
        check(status == "Open") { "POLKAMARKT_MARKET_CLOSED" }
    }
}

internal object PolkamarktSlippageValidator {
    fun requireMobileQuoteValue(slippageBps: Int) {
        require(
            slippageBps in PolkamarktWebContract.MIN_SLIPPAGE_BPS..
                PolkamarktWebContract.MAX_SLIPPAGE_BPS
        ) { "POLKAMARKT_INVALID_SLIPPAGE" }
    }

    /** Canonical web parity: floor(output * (10_000 - slippageBps) / 10_000). */
    fun minimumOutput(amount: BigInteger, slippageBps: Int): BigInteger {
        require(amount.signum() >= 0) { "POLKAMARKT_INVALID_QUOTE_AMOUNT" }
        require(slippageBps in 0..PolkamarktWebContract.MAX_SLIPPAGE_BPS) {
            "POLKAMARKT_INVALID_SLIPPAGE"
        }
        return amount
            .multiply((BPS_DENOMINATOR - slippageBps).toBigInteger())
            .divide(BPS_DENOMINATOR.toBigInteger())
    }

    private const val BPS_DENOMINATOR = 10_000
}

internal data class PolkamarktPendingFailureDisposition(
    val state: String,
    val submissionIsAmbiguous: Boolean,
)

/**
 * Completion of the repository's pre-transport callback is the first point after which the RPC
 * transport may be reached. Failures before that point are terminal and safe to retry only as a
 * new, explicitly confirmed mutation. After it, only the transport layer's marker can prove
 * non-submission.
 */
internal object PolkamarktPendingFailurePolicy {
    fun classify(
        preTransportValidationCompleted: Boolean,
        error: Throwable,
    ): PolkamarktPendingFailureDisposition = when {
        error is ExtrinsicSubmissionUnknown -> {
            PolkamarktPendingFailureDisposition(
                state = "UNKNOWN",
                submissionIsAmbiguous = true,
            )
        }

        !preTransportValidationCompleted || error is DefinitelyNotSubmitted -> {
            PolkamarktPendingFailureDisposition(
                state = "REJECTED",
                submissionIsAmbiguous = false,
            )
        }

        else -> {
            PolkamarktPendingFailureDisposition(
                state = "UNKNOWN",
                submissionIsAmbiguous = true,
            )
        }
    }
}

private data class PolkamarktPendingHandoff(
    val localId: String,
    val transactionHash: String,
)

/**
 * Keeps the live account nonce reserved from the final pre-sign checks through the only transport
 * handoff. [UserRepository.withWalletMutationLocked], [ExtrinsicManager.calcPolkamarktFee],
 * [ExtrinsicManager.preparePolkamarktExtrinsic], and [ExtrinsicManager.submitPreparedAndWait]
 * deliberately share the same coroutine-reentrant lock while this outer boundary is held.
 */
internal object PolkamarktMutationSubmissionCoordinator {
    suspend fun <T> withNonceReservedUntilTransport(
        block: suspend () -> T,
    ): T = WalletMutationCoordinator.withLock(block)

    /**
     * Public quotes share the process-wide wallet mutation lock. This closes the gap where a
     * second repository instance could begin a quote after a mutation's pending check but before
     * its durable journal insert. Mutation-internal quote refreshes use the private quote methods
     * because the mutation already owns this reentrant lock.
     */
    suspend fun <T> withQuoteAdmission(
        block: suspend () -> T,
    ): T = WalletMutationCoordinator.withLock(block)
}

/**
 * App-wide, fail-fast admission shared by quotes, recovery, trades, and claims. Hilt supplies one
 * repository in production, but keeping the lease here also protects accidental second repository
 * instances and multiple simultaneously retained Polkamarkt screens.
 */
internal object PolkamarktMutationAdmissionGate {
    private val activeToken = java.util.concurrent.atomic.AtomicReference<UUID?>(null)

    suspend fun <T> withAdmission(block: suspend () -> T): T {
        val token = UUID.randomUUID()
        check(activeToken.compareAndSet(null, token)) { "POLKAMARKT_MUTATION_IN_FLIGHT" }
        return try {
            block()
        } finally {
            check(activeToken.compareAndSet(token, null)) {
                "POLKAMARKT_MUTATION_ADMISSION_CORRUPT"
            }
        }
    }
}

internal object PolkamarktPendingRecoveryPlanner {
    const val MAXIMUM_BATCH_SIZE = 50
    private const val SAFE_SIGNED_ID_PREFIX = "polkamarkt-v2:"

    fun newLocalId(): String = "$SAFE_SIGNED_ID_PREFIX${UUID.randomUUID()}"

    fun wasDefinitelyNeverSubmitted(record: PendingNetworkTransactionLocal): Boolean =
        record.localId.startsWith(SAFE_SIGNED_ID_PREFIX) &&
            record.state == "SIGNED" &&
            !record.submissionIsAmbiguous

    fun batches(
        records: List<PendingNetworkTransactionLocal>,
        maximumBatchSize: Int,
    ): List<List<PendingNetworkTransactionLocal>> {
        require(maximumBatchSize in 1..MAXIMUM_BATCH_SIZE) {
            "POLKAMARKT_RECOVERY_BATCH_INVALID"
        }
        return records.chunked(maximumBatchSize)
    }
}

data class PolkamarktTradeQuote(
    val identity: String,
    val confirmationNonce: String,
    val accountId: String,
    val market: PolkamarktMarket,
    val side: PolkamarktTradeSide,
    val outcome: PolkamarktOutcome,
    val amountIn: BigInteger,
    val outputAmount: BigInteger,
    val minimumOutput: BigInteger,
    val marketFee: BigInteger,
    val xorNetworkFee: BigInteger,
    val observedFinalizedBlock: String,
    val authoritativeMechanism: String,
    val authoritativeCloseBlock: BigInteger,
    val kusdBalance: BigInteger,
    val xorBalance: BigInteger,
    /** Finalized runtime share balance for the selected outcome; present only for sell quotes. */
    val shareBalance: BigInteger? = null,
)

/**
 * A confirmation is a one-shot user authorization. Never evict an accepted identity: the gate
 * fails closed at its bounded capacity and a process restart cannot resurrect an in-memory quote.
 */
internal class PolkamarktConfirmedQuoteUseGate(
    private val maximumIdentities: Int = MAXIMUM_IDENTITIES,
) {
    private val consumed = LinkedHashSet<String>()

    init {
        require(maximumIdentities in 1..MAXIMUM_IDENTITIES) {
            "POLKAMARKT_QUOTE_USE_GATE_CAPACITY_INVALID"
        }
    }

    @Synchronized
    fun consume(identity: String) {
        require(identity.matches(IDENTITY_PATTERN)) {
            "POLKAMARKT_QUOTE_IDENTITY_INVALID"
        }
        check(identity !in consumed) { "POLKAMARKT_QUOTE_ALREADY_USED" }
        check(consumed.size < maximumIdentities) {
            "POLKAMARKT_QUOTE_USE_GATE_EXHAUSTED"
        }
        consumed += identity
    }

    private companion object {
        const val MAXIMUM_IDENTITIES = 4_096
        val IDENTITY_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

data class PolkamarktMarketDetail(
    /** PI catalog metadata with all state/pricing fields replaced by one finalized runtime read. */
    val market: PolkamarktMarket,
    val claimable: PolkamarktClaimableDto?,
    val finalizedBlockHash: String,
)

data class PolkamarktClaimReview(
    val requestedMarketIds: Set<Long>,
    val claims: Map<Long, PolkamarktClaimableDto>,
    val finalizedBlockHash: String,
)

/**
 * The final quote comparison performed while the wallet/nonce mutation lock is held. Keep this
 * policy side-effect free so every value that can invalidate a confirmation can be qualified
 * independently from RPC timing.
 */
internal object PolkamarktTradeMutationValidator {
    fun requireFresh(
        confirmed: PolkamarktTradeQuote,
        fresh: PolkamarktTradeQuote,
        exactNetworkFee: BigInteger,
    ) {
        requireFreshQuote(confirmed, fresh)
        requireFreshFeeAndBalances(confirmed, fresh, exactNetworkFee)
    }

    fun requireFreshQuote(
        confirmed: PolkamarktTradeQuote,
        fresh: PolkamarktTradeQuote,
    ) {
        check(fresh.outputAmount >= confirmed.minimumOutput) {
            "POLKAMARKT_STALE_QUOTE"
        }
        check(fresh.marketFee == confirmed.marketFee) {
            "POLKAMARKT_MARKET_FEE_CHANGED"
        }
        check(fresh.authoritativeMechanism == confirmed.authoritativeMechanism) {
            "POLKAMARKT_MECHANISM_CHANGED"
        }
        check(fresh.authoritativeCloseBlock == confirmed.authoritativeCloseBlock) {
            "POLKAMARKT_CLOSE_BLOCK_CHANGED"
        }
    }

    fun requireFreshFeeAndBalances(
        confirmed: PolkamarktTradeQuote,
        fresh: PolkamarktTradeQuote,
        exactNetworkFee: BigInteger,
    ) {
        check(exactNetworkFee <= confirmed.xorNetworkFee) {
            "POLKAMARKT_FEE_INCREASED"
        }
        check(fresh.market.status == confirmed.market.status) {
            "POLKAMARKT_STATUS_CHANGED"
        }
        check(fresh.xorBalance >= exactNetworkFee) {
            "POLKAMARKT_INSUFFICIENT_XOR"
        }
        if (confirmed.side == PolkamarktTradeSide.BUY) {
            check(fresh.kusdBalance >= confirmed.amountIn) {
                "POLKAMARKT_INSUFFICIENT_KUSD"
            }
        } else {
            check(checkNotNull(fresh.shareBalance) >= confirmed.amountIn) {
                "POLKAMARKT_INSUFFICIENT_SHARES"
            }
        }
    }
}

data class PolkamarktMutationResult(
    val localId: String,
    val transactionHash: String?,
    val state: String,
)

data class PolkamarktPendingTransaction(
    val localId: String,
    val transactionHash: String?,
    val operation: String,
    val amount: String,
    val state: String,
    val submissionIsAmbiguous: Boolean,
)

/**
 * Runtime-authoritative Polkamarkt quote and mutation boundary. PI supplies catalog/enrichment,
 * while every value that affects signing is re-read from finalized RPC/runtime metadata.
 */
@Singleton
class PolkamarktTraderRepository @Inject constructor(
    private val indexer: PolkaswapIndexerClient,
    private val calls: SubstrateCalls,
    private val extrinsicManager: ExtrinsicManager,
    private val runtimeContract: Sora2RuntimeContract,
    private val credentialsRepository: CredentialsRepository,
    private val userRepository: UserRepository,
    private val featureManager: ProductionFeatureManager,
    private val database: AppDatabase,
    private val catalogCache: PolkamarktCatalogCache,
) {
    private val confirmedQuoteUseGate = PolkamarktConfirmedQuoteUseGate()

    fun observePending(walletId: String): Flow<List<PolkamarktPendingTransaction>> =
        database.walletIdentityDao().observePendingTransactions(walletId).map { records ->
            records.forEach { WalletIdentityDao.validatePendingTransaction(it) }
            records
                .mapNotNull { record ->
                    if (record.networkId != "sora2") return@mapNotNull null
                    val identity = checkNotNull(
                        PolkamarktPendingAssetId.parse(record.assetId)
                    ) { "POLKAMARKT_PENDING_IDENTITY_INVALID" }
                    if (!WalletIdentityDao.hasCurrentChainIdentity(record)) {
                        return@mapNotNull PolkamarktPendingTransaction(
                            localId = record.localId,
                            transactionHash = record.transactionHash,
                            operation = identity.operation,
                            amount = record.amount,
                            state = "CHAIN_IDENTITY_RECOVERY_REQUIRED",
                            submissionIsAmbiguous = true,
                        )
                    }
                    val hash = checkNotNull(record.transactionHash) {
                        "POLKAMARKT_PENDING_HASH_MISSING"
                    }
                    PolkamarktPendingTransaction(
                        localId = record.localId,
                        transactionHash = hash.canonicalExtrinsicHash(),
                        operation = identity.operation,
                        amount = record.amount,
                        state = record.state,
                        submissionIsAmbiguous = record.submissionIsAmbiguous,
                    )
                }
        }

    /**
     * Projects an exact terminal result from the generic status-only SORA2 witness into the richer
     * Polkamarkt overlay. It never consults PI as finality authority and never signs or resubmits a
     * transaction, including records whose submission result was ambiguous.
     */
    suspend fun recoverPendingTransactions(
        walletId: String,
        maxTransactionsPerBatch: Int = PolkamarktPendingRecoveryPlanner.MAXIMUM_BATCH_SIZE,
    ): List<PolkamarktMutationResult> = PolkamarktMutationAdmissionGate.withAdmission {
        PolkamarktMutationSubmissionCoordinator.withNonceReservedUntilTransport {
            val maximumBatchSize = PolkamarktPendingRecoveryPlanner.MAXIMUM_BATCH_SIZE
            require(maxTransactionsPerBatch in 1..maximumBatchSize)
            check(userRepository.getCurSoraAccount().substrateAddress == walletId) {
                "POLKAMARKT_ACCOUNT_CHANGED"
            }
            val scopedPendingRecords = database.walletIdentityDao().getUnresolvedTransactions()
                .filter {
                    it.walletId == walletId &&
                        it.networkId == "sora2" &&
                        PolkamarktPendingAssetId.parse(it.assetId) != null
                }
            check(scopedPendingRecords.all(WalletIdentityDao::hasCurrentChainIdentity)) {
                "POLKAMARKT_PENDING_CHAIN_IDENTITY_RECOVERY_REQUIRED"
            }
            val allPendingRecords = scopedPendingRecords.filter {
                !it.transactionHash.isNullOrBlank()
            }
            check(
                allPendingRecords.map { it.localId }.distinct().size == allPendingRecords.size
            ) { "POLKAMARKT_DUPLICATE_PENDING_ID" }
            val allPendingHashes = allPendingRecords.map {
                checkNotNull(it.transactionHash).canonicalExtrinsicHash()
            }
            check(allPendingHashes.distinct().size == allPendingRecords.size) {
                "POLKAMARKT_DUPLICATE_PENDING_HASH"
            }
            if (allPendingRecords.isEmpty()) {
                return@withNonceReservedUntilTransport emptyList()
            }

            // New-version SIGNED rows can only exist before the durable transport-arm update.
            // Legacy IDs are deliberately excluded because older builds did not make that proof.
            val neverSubmitted = allPendingRecords.filter(
                PolkamarktPendingRecoveryPlanner::wasDefinitelyNeverSubmitted
            )
            val results = neverSubmitted.map { pending ->
                val hash = checkNotNull(pending.transactionHash).canonicalExtrinsicHash()
                database.walletIdentityDao().updatePendingTransaction(
                    localId = pending.localId,
                    transactionHash = hash,
                    state = "REJECTED",
                    ambiguous = false,
                    updatedAt = System.currentTimeMillis(),
                )
                PolkamarktMutationResult(pending.localId, hash, "REJECTED")
            }.toMutableList()
            val neverSubmittedIds = neverSubmitted.mapTo(mutableSetOf()) { it.localId }
            val reconcilable = allPendingRecords.filterNot {
                it.localId in neverSubmittedIds
            }
            PolkamarktPendingRecoveryPlanner.batches(reconcilable, maxTransactionsPerBatch)
                .forEach { batch ->
                    results += recoverPendingBatch(batch)
                }
            results
        }
    }

    private suspend fun recoverPendingBatch(
        pendingRecords: List<PendingNetworkTransactionLocal>,
    ): List<PolkamarktMutationResult> {
        if (pendingRecords.isEmpty()) return emptyList()
        return pendingRecords.mapNotNull { pending ->
            val hash = checkNotNull(pending.transactionHash).canonicalExtrinsicHash()
            val witness = database.walletIdentityDao().getSora2PendingSubmission(
                "sora2:${hash.removePrefix("0x")}"
            ) ?: return@mapNotNull null
            WalletIdentityDao.validateSora2PendingSubmission(witness)
            check(
                witness.walletId == pending.walletId &&
                    witness.networkId == pending.networkId &&
                    witness.transactionHash == hash &&
                    witness.operationKind == Sora2PendingSubmissionLocal.OPERATION_POLKAMARKT
            ) { "POLKAMARKT_RECOVERY_WITNESS_MISMATCH" }
            val state = when (witness.state) {
                Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS ->
                    "FINALIZED"
                Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE,
                Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED ->
                    "REJECTED"
                else -> return@mapNotNull null
            }
            database.walletIdentityDao().updatePendingTransaction(
                localId = pending.localId,
                transactionHash = hash,
                state = state,
                ambiguous = false,
                updatedAt = System.currentTimeMillis(),
            )
            PolkamarktMutationResult(pending.localId, hash, state)
        }
    }

    suspend fun quoteBuy(
        expectedAccountId: String,
        marketId: Long,
        outcome: PolkamarktOutcome,
        collateralIn: BigInteger,
        slippageBps: Int,
    ): PolkamarktTradeQuote = PolkamarktMutationAdmissionGate.withAdmission {
        PolkamarktMutationSubmissionCoordinator.withQuoteAdmission {
            PolkamarktSlippageValidator.requireMobileQuoteValue(slippageBps)
            val canonicalMarketId = PolkamarktMarketId.requireValid(marketId)
            requirePendingRecoveryClear(expectedAccountId)
            quoteBuyForAccount(
                account = userRepository.getCurSoraAccount(),
                expectedAccountId = expectedAccountId,
                marketId = canonicalMarketId,
                outcome = outcome,
                collateralIn = collateralIn,
                slippageBps = slippageBps,
            )
        }
    }

    /**
     * Loads the state shown on market detail from one finalized runtime block. PI remains the
     * catalog/metadata source only; status, close block, mechanism, prices, shares and claimability
     * are all replaced with runtime-authoritative values before reaching presentation.
     */
    suspend fun getAuthoritativeMarketDetail(
        expectedAccountId: String,
        catalogMarket: PolkamarktMarket,
    ): PolkamarktMarketDetail {
        val marketId = PolkamarktMarketId.requireValid(
            checkNotNull(catalogMarket.marketId) { "POLKAMARKT_MARKET_ID_MISSING" }
        )
        check(catalogMarket.id == marketId.toString()) {
            "POLKAMARKT_MARKET_ID_MISMATCH"
        }
        val account = userRepository.getCurSoraAccount()
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            account.substrateAddress,
        )
        requireVisibility(mutations = false)
        runtimeContract.requirePolkamarktMutationRuntime()
        val finalizedAt = calls.getFinalizedHead().canonicalExtrinsicHash()
        val authoritativeAt = calls.polkamarktAuthoritativeMarket(marketId, finalizedAt)
        val runtimeStateAt = calls.polkamarktMarketState(marketId, finalizedAt)
        val claimableAt = calls.polkamarktClaimable(
            account.substrateAddress,
            marketId,
            finalizedAt,
        )
        check(
            listOf(
                authoritativeAt.blockHash,
                runtimeStateAt.blockHash,
                claimableAt.blockHash,
            ).all { it.canonicalExtrinsicHash() == finalizedAt }
        ) { "POLKAMARKT_MARKET_BLOCK_MISMATCH" }

        val authoritative = checkNotNull(authoritativeAt.value) {
            "POLKAMARKT_MARKET_STATE_MISSING"
        }
        val runtimeState = checkNotNull(runtimeStateAt.value) {
            "POLKAMARKT_MARKET_STATE_MISSING"
        }
        check(
            authoritative.marketId == marketId &&
                runtimeState.marketId == marketId &&
                authoritative.status.isCanonicalRuntimeLabel() &&
                runtimeState.mechanism.isCanonicalRuntimeLabel() &&
                authoritative.closeBlock.signum() >= 0 &&
                authoritative.closeBlock <=
                BigInteger.valueOf(PolkamarktMarketId.MAX_VALUE) &&
                authoritative.observedBlockNumber.signum() >= 0 &&
                listOf(
                    runtimeState.virtualDepth,
                    runtimeState.realYesShares,
                    runtimeState.realNoShares,
                    runtimeState.dpmCollateral,
                ).all { it.signum() >= 0 } &&
                listOf(
                    runtimeState.marginalYesPriceBps,
                    runtimeState.marginalNoPriceBps,
                    runtimeState.impliedYesProbabilityBps,
                    runtimeState.impliedNoProbabilityBps,
                ).all { it in 0..BPS_DENOMINATOR }
        ) { "POLKAMARKT_RUNTIME_DETAIL_INVALID" }

        val claimable = claimableAt.value?.also {
            val resolutionOutcome = it.resolutionOutcome
            PolkamarktClaimValidator.requireIdentity(
                claimable = it,
                expectedAccountId = account.substrateAddress,
                expectedMarketId = marketId,
            )
            check(
                it.status.isCanonicalRuntimeLabel() &&
                    (resolutionOutcome == null ||
                        resolutionOutcome.isCanonicalRuntimeLabel()) &&
                    listOf(
                        it.yesShares,
                        it.noShares,
                        it.netCollateralPaid,
                        it.traderPayout,
                        it.claimablePayout,
                        it.creatorFees,
                    ).all { quantity -> quantity.signum() >= 0 }
            ) { "POLKAMARKT_CLAIMABLE_INVALID" }
        }
        val authoritativeMarket = catalogMarket.copy(
            closeBlock = authoritative.closeBlock.longValueExact(),
            status = authoritative.status,
            mechanism = runtimeState.mechanism,
            virtualDepth = runtimeState.virtualDepth.toString(),
            dpmCollateral = runtimeState.dpmCollateral.toString(),
            realYesShares = runtimeState.realYesShares.toString(),
            realNoShares = runtimeState.realNoShares.toString(),
            marginalYesPriceBps = runtimeState.marginalYesPriceBps,
            marginalNoPriceBps = runtimeState.marginalNoPriceBps,
            impliedYesProbabilityBps = runtimeState.impliedYesProbabilityBps,
            impliedNoProbabilityBps = runtimeState.impliedNoProbabilityBps,
            displayYesProbabilityBps = runtimeState.impliedYesProbabilityBps,
            displayNoProbabilityBps = runtimeState.impliedNoProbabilityBps,
        )
        return PolkamarktMarketDetail(
            market = authoritativeMarket,
            claimable = claimable,
            finalizedBlockHash = finalizedAt,
        )
    }

    /**
     * Reviews a bounded claim set at one finalized block before presentation may offer any claim
     * mutation. PI positions are candidate discovery only; every displayed payout, creator fee,
     * and share balance comes from these account-bound runtime responses.
     */
    suspend fun getAuthoritativeClaimables(
        expectedAccountId: String,
        marketIds: List<Long>,
    ): PolkamarktClaimReview {
        PolkamarktClaimValidator.requireTraderBatch(marketIds)
        val account = userRepository.getCurSoraAccount()
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            account.substrateAddress,
        )
        requireVisibility(mutations = false)
        runtimeContract.requirePolkamarktMutationRuntime()
        val finalizedAt = calls.getFinalizedHead().canonicalExtrinsicHash()
        val claims = linkedMapOf<Long, PolkamarktClaimableDto>()
        marketIds.forEach { marketId ->
            val claimableAt = calls.polkamarktClaimable(
                account.substrateAddress,
                marketId,
                finalizedAt,
            )
            check(
                claimableAt.blockHash.canonicalExtrinsicHash() == finalizedAt
            ) { "POLKAMARKT_CLAIM_BLOCK_MISMATCH" }
            claimableAt.value?.let { claimable ->
                val resolutionOutcome = claimable.resolutionOutcome
                PolkamarktClaimValidator.requireIdentity(
                    claimable = claimable,
                    expectedAccountId = account.substrateAddress,
                    expectedMarketId = marketId,
                )
                check(
                    claimable.status.isCanonicalRuntimeLabel() &&
                        (resolutionOutcome == null ||
                            resolutionOutcome.isCanonicalRuntimeLabel()) &&
                        listOf(
                            claimable.yesShares,
                            claimable.noShares,
                            claimable.netCollateralPaid,
                            claimable.traderPayout,
                            claimable.claimablePayout,
                            claimable.creatorFees,
                        ).all { it.signum() >= 0 }
                ) { "POLKAMARKT_CLAIMABLE_INVALID" }
                claims[marketId] = claimable
            }
        }
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            userRepository.getCurSoraAccount().substrateAddress,
        )
        return PolkamarktClaimReview(
            requestedMarketIds = marketIds.toSet(),
            claims = claims,
            finalizedBlockHash = finalizedAt,
        )
    }

    private suspend fun quoteBuyForAccount(
        account: SoraAccount,
        expectedAccountId: String,
        marketId: Long,
        outcome: PolkamarktOutcome,
        collateralIn: BigInteger,
        slippageBps: Int,
    ): PolkamarktTradeQuote {
        PolkamarktMarketId.requireValid(marketId)
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            account.substrateAddress,
        )
        require(collateralIn.signum() > 0) { "POLKAMARKT_AMOUNT_NOT_POSITIVE" }
        validateSlippage(slippageBps)
        requireVisibility(mutations = false)
        runtimeContract.requirePolkamarktMutationRuntime()
        val market = requireTradableMarket(marketId)
        val finalizedAt = calls.getFinalizedHead().canonicalExtrinsicHash()
        val authoritativeAt = calls.polkamarktAuthoritativeMarket(marketId, finalizedAt)
        check(authoritativeAt.blockHash.canonicalExtrinsicHash() == finalizedAt) {
            "POLKAMARKT_MARKET_BLOCK_MISMATCH"
        }
        val authoritative = checkNotNull(
            authoritativeAt.value
        ) { "POLKAMARKT_MARKET_STATE_MISSING" }
        PolkamarktMarketMutationValidator.requireOpen(authoritative.status)
        val runtimeState = calls.polkamarktMarketState(marketId, finalizedAt)
        check(runtimeState.blockHash.canonicalExtrinsicHash() == finalizedAt) {
            "POLKAMARKT_MARKET_BLOCK_MISMATCH"
        }
        val runtimeMarketState = checkNotNull(runtimeState.value) {
            "POLKAMARKT_MARKET_STATE_MISSING"
        }
        check(runtimeMarketState.marketId == marketId) { "POLKAMARKT_MARKET_STATE_MISSING" }
        val finalizedQuote = calls.polkamarktQuoteBuy(
            marketId,
            outcome.runtimeValue,
            collateralIn,
            finalizedAt,
        )
        check(finalizedQuote.blockHash.canonicalExtrinsicHash() == finalizedAt) {
            "POLKAMARKT_QUOTE_BLOCK_MISMATCH"
        }
        val quote = checkNotNull(finalizedQuote.value) { "POLKAMARKT_QUOTE_UNAVAILABLE" }
        validateBuyQuote(quote, marketId, outcome, collateralIn)
        val minimum = PolkamarktSlippageValidator.minimumOutput(
            quote.sharesOut,
            slippageBps,
        )
        val networkFee = checkNotNull(
            extrinsicManager.calcFee(account.substrateAddress) {
                polkamarktBuy(
                    marketId,
                    outcome.runtimeValue,
                    collateralIn,
                    minimum,
                )
            }
        ) { "POLKAMARKT_FEE_UNAVAILABLE" }
        val balances = calls.fetchBalances(
            account.substrateAddress,
            listOf(SubstrateOptionsProvider.kusdAssetId, SubstrateOptionsProvider.feeAssetId),
            finalizedAt,
        )
        check(balances.size == 2) { "POLKAMARKT_BALANCE_UNAVAILABLE" }

        val confirmationNonce = UUID.randomUUID().toString()
        return PolkamarktTradeQuote(
            identity = quoteIdentity(
                confirmationNonce,
                account.substrateAddress,
                marketId,
                PolkamarktTradeSide.BUY,
                outcome,
                collateralIn,
                quote.sharesOut,
                minimum,
                quote.feeAmount,
                networkFee,
                runtimeMarketState.mechanism,
                authoritative.closeBlock,
                finalizedAt,
                authoritative.status,
            ),
            confirmationNonce = confirmationNonce,
            accountId = account.substrateAddress,
            market = market.copy(status = authoritative.status),
            side = PolkamarktTradeSide.BUY,
            outcome = outcome,
            amountIn = collateralIn,
            outputAmount = quote.sharesOut,
            minimumOutput = minimum,
            marketFee = quote.feeAmount,
            xorNetworkFee = networkFee,
            observedFinalizedBlock = finalizedAt,
            authoritativeMechanism = runtimeMarketState.mechanism,
            authoritativeCloseBlock = authoritative.closeBlock,
            kusdBalance = balances[0],
            xorBalance = balances[1],
        )
    }

    suspend fun quoteSell(
        expectedAccountId: String,
        marketId: Long,
        outcome: PolkamarktOutcome,
        sharesIn: BigInteger,
        slippageBps: Int,
    ): PolkamarktTradeQuote = PolkamarktMutationAdmissionGate.withAdmission {
        PolkamarktMutationSubmissionCoordinator.withQuoteAdmission {
            PolkamarktSlippageValidator.requireMobileQuoteValue(slippageBps)
            val canonicalMarketId = PolkamarktMarketId.requireValid(marketId)
            requirePendingRecoveryClear(expectedAccountId)
            quoteSellForAccount(
                account = userRepository.getCurSoraAccount(),
                expectedAccountId = expectedAccountId,
                marketId = canonicalMarketId,
                outcome = outcome,
                sharesIn = sharesIn,
                slippageBps = slippageBps,
            )
        }
    }

    private suspend fun quoteSellForAccount(
        account: SoraAccount,
        expectedAccountId: String,
        marketId: Long,
        outcome: PolkamarktOutcome,
        sharesIn: BigInteger,
        slippageBps: Int,
    ): PolkamarktTradeQuote {
        PolkamarktMarketId.requireValid(marketId)
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            account.substrateAddress,
        )
        require(sharesIn.signum() > 0) { "POLKAMARKT_AMOUNT_NOT_POSITIVE" }
        validateSlippage(slippageBps)
        requireVisibility(mutations = false)
        runtimeContract.requirePolkamarktMutationRuntime()
        val market = requireTradableMarket(marketId)
        val finalizedAt = calls.getFinalizedHead().canonicalExtrinsicHash()
        val authoritativeAt = calls.polkamarktAuthoritativeMarket(marketId, finalizedAt)
        check(authoritativeAt.blockHash.canonicalExtrinsicHash() == finalizedAt) {
            "POLKAMARKT_MARKET_BLOCK_MISMATCH"
        }
        val authoritative = checkNotNull(
            authoritativeAt.value
        ) { "POLKAMARKT_MARKET_STATE_MISSING" }
        PolkamarktMarketMutationValidator.requireOpen(authoritative.status)
        val runtimeState = calls.polkamarktMarketState(marketId, finalizedAt)
        check(runtimeState.blockHash.canonicalExtrinsicHash() == finalizedAt) {
            "POLKAMARKT_MARKET_BLOCK_MISMATCH"
        }
        val runtimeMarketState = checkNotNull(runtimeState.value) {
            "POLKAMARKT_MARKET_STATE_MISSING"
        }
        check(runtimeMarketState.marketId == marketId) { "POLKAMARKT_MARKET_STATE_MISSING" }
        val positionAt = calls.polkamarktClaimable(
            account.substrateAddress,
            marketId,
            finalizedAt,
        )
        check(positionAt.blockHash.canonicalExtrinsicHash() == finalizedAt) {
            "POLKAMARKT_CLAIM_BLOCK_MISMATCH"
        }
        val position = positionAt.value?.also {
            PolkamarktClaimValidator.requireIdentity(
                claimable = it,
                expectedAccountId = account.substrateAddress,
                expectedMarketId = marketId,
            )
        }
        val availableShares = when (outcome) {
            PolkamarktOutcome.YES -> position?.yesShares
            PolkamarktOutcome.NO -> position?.noShares
        } ?: BigInteger.ZERO
        check(availableShares >= sharesIn) { "POLKAMARKT_INSUFFICIENT_SHARES" }
        val finalizedQuote = calls.polkamarktQuoteSell(
            marketId,
            outcome.runtimeValue,
            sharesIn,
            finalizedAt,
        )
        check(finalizedQuote.blockHash.canonicalExtrinsicHash() == finalizedAt) {
            "POLKAMARKT_QUOTE_BLOCK_MISMATCH"
        }
        val quote = checkNotNull(finalizedQuote.value) { "POLKAMARKT_QUOTE_UNAVAILABLE" }
        validateSellQuote(quote, marketId, outcome, sharesIn)
        val minimum = PolkamarktSlippageValidator.minimumOutput(
            quote.collateralOut,
            slippageBps,
        )
        val networkFee = checkNotNull(
            extrinsicManager.calcFee(account.substrateAddress) {
                polkamarktSell(
                    marketId,
                    outcome.runtimeValue,
                    sharesIn,
                    minimum,
                )
            }
        ) { "POLKAMARKT_FEE_UNAVAILABLE" }
        val balances = calls.fetchBalances(
            account.substrateAddress,
            listOf(SubstrateOptionsProvider.kusdAssetId, SubstrateOptionsProvider.feeAssetId),
            finalizedAt,
        )
        check(balances.size == 2) { "POLKAMARKT_BALANCE_UNAVAILABLE" }

        val confirmationNonce = UUID.randomUUID().toString()
        return PolkamarktTradeQuote(
            identity = quoteIdentity(
                confirmationNonce,
                account.substrateAddress,
                marketId,
                PolkamarktTradeSide.SELL,
                outcome,
                sharesIn,
                quote.collateralOut,
                minimum,
                quote.feeAmount,
                networkFee,
                runtimeMarketState.mechanism,
                authoritative.closeBlock,
                finalizedAt,
                authoritative.status,
            ),
            confirmationNonce = confirmationNonce,
            accountId = account.substrateAddress,
            market = market.copy(status = authoritative.status),
            side = PolkamarktTradeSide.SELL,
            outcome = outcome,
            amountIn = sharesIn,
            outputAmount = quote.collateralOut,
            minimumOutput = minimum,
            marketFee = quote.feeAmount,
            xorNetworkFee = networkFee,
            observedFinalizedBlock = finalizedAt,
            authoritativeMechanism = runtimeMarketState.mechanism,
            authoritativeCloseBlock = authoritative.closeBlock,
            kusdBalance = balances[0],
            xorBalance = balances[1],
            shareBalance = availableShares,
        )
    }

    suspend fun executeTrade(
        confirmed: PolkamarktTradeQuote,
    ): PolkamarktMutationResult = PolkamarktMutationAdmissionGate.withAdmission {
        executeTradeAdmitted(confirmed)
    }

    private suspend fun executeTradeAdmitted(
        confirmed: PolkamarktTradeQuote,
    ): PolkamarktMutationResult {
        requireVisibility(mutations = true)
        runtimeContract.requirePolkamarktMutationRuntime()
        val marketId = checkNotNull(confirmed.market.marketId) {
            "POLKAMARKT_MARKET_ID_MISSING"
        }
        requireConfirmedQuoteIdentity(confirmed, marketId)
        val expectedAccountId = confirmed.accountId
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            userRepository.getCurSoraAccount().substrateAddress,
        )
        requirePendingRecoveryClear(expectedAccountId)
        confirmedQuoteUseGate.consume(confirmed.identity)
        return PolkamarktMutationSubmissionCoordinator.withNonceReservedUntilTransport {
            var pendingHandoff: PolkamarktPendingHandoff? = null
            var preTransportValidationCompleted = false
            try {
                val (prepared, localId, signingRuntime) = userRepository.withWalletMutationLocked { snapshot ->
                    val account = checkNotNull(snapshot.selectedAccount) {
                        "POLKAMARKT_ACCOUNT_CHANGED"
                    }
                    PolkamarktMutationAccountValidator.requireExpected(
                        expectedAccountId,
                        account.substrateAddress,
                    )
                    val fresh = when (confirmed.side) {
                        PolkamarktTradeSide.BUY -> quoteBuyForAccount(
                            account = account,
                            expectedAccountId = expectedAccountId,
                            marketId = marketId,
                            outcome = confirmed.outcome,
                            collateralIn = confirmed.amountIn,
                            slippageBps = 0,
                        )
                        PolkamarktTradeSide.SELL -> quoteSellForAccount(
                            account = account,
                            expectedAccountId = expectedAccountId,
                            marketId = marketId,
                            outcome = confirmed.outcome,
                            sharesIn = confirmed.amountIn,
                            slippageBps = 0,
                        )
                    }
                    PolkamarktTradeMutationValidator.requireFreshQuote(
                        confirmed = confirmed,
                        fresh = fresh,
                    )
                    val signingRuntime =
                        runtimeContract.requirePolkamarktMutationRuntimeContext()
                    val exactNetworkFee = calculateTradeFee(
                        accountId = account.substrateAddress,
                        quote = confirmed,
                        signingRuntime = signingRuntime,
                    )
                    PolkamarktTradeMutationValidator.requireFreshFeeAndBalances(
                        confirmed = confirmed,
                        fresh = fresh,
                        exactNetworkFee = exactNetworkFee,
                    )
                    requireVisibility(mutations = true)
                    check(!database.walletIdentityDao().hasActiveDeletionOperation()) {
                        "WALLET_DELETION_ACTIVE"
                    }
                    requirePendingRecoveryClear(expectedAccountId)

                    val keyPair = credentialsRepository.retrieveKeyPair(account)
                    val signed = try {
                        extrinsicManager.preparePolkamarktExtrinsic(
                            from = account.substrateAddress,
                            keypair = keyPair,
                            runtime = signingRuntime,
                        ) {
                            when (confirmed.side) {
                                PolkamarktTradeSide.BUY -> polkamarktBuy(
                                    marketId = marketId,
                                    outcome = confirmed.outcome.runtimeValue,
                                    collateralIn = confirmed.amountIn,
                                    minimumSharesOut = confirmed.minimumOutput,
                                )
                                PolkamarktTradeSide.SELL -> polkamarktSell(
                                    marketId = marketId,
                                    outcome = confirmed.outcome.runtimeValue,
                                    sharesIn = confirmed.amountIn,
                                    minimumCollateralOut = confirmed.minimumOutput,
                                )
                            }
                        }
                    } finally {
                        keyPair.privateKey.fill(0)
                        keyPair.nonce.fill(0)
                    }
                    // Arm the recovery identity before the durable insert can suspend. The outer
                    // failure boundary can therefore terminalize a committed row even when
                    // cancellation is delivered while this locked block is returning.
                    val pendingId = PolkamarktPendingRecoveryPlanner.newLocalId()
                    pendingHandoff = PolkamarktPendingHandoff(
                        localId = pendingId,
                        transactionHash = signed.transactionHash,
                    )
                    createPending(
                        localId = pendingId,
                        walletId = account.substrateAddress,
                        marketId = marketId,
                        amount = confirmed.amountIn,
                        outcome = confirmed.outcome,
                        operation = confirmed.side.name,
                        transactionHash = signed.transactionHash,
                    )
                    Triple(signed, pendingId, signingRuntime)
                }
                val result = extrinsicManager.submitPreparedAndWait(
                    prepared = prepared,
                    preTransportValidation = {
                        requireMutationPreTransport(
                            expectedAccountId = expectedAccountId,
                            signingRuntime = signingRuntime,
                            allowedCurrentLocalId = localId,
                        )
                        markPendingSubmitted(localId, prepared.transactionHash)
                        preTransportValidationCompleted = true
                    },
                )
                finishPending(localId, prepared.transactionHash, result)
            } catch (error: Throwable) {
                pendingHandoff?.let { pending ->
                    persistPendingFailure(
                        localId = pending.localId,
                        transactionHash = pending.transactionHash,
                        error = error,
                        preTransportValidationCompleted = preTransportValidationCompleted,
                    )
                }
                throw error
            }
        }
    }

    private suspend fun calculateTradeFee(
        accountId: String,
        quote: PolkamarktTradeQuote,
        signingRuntime: QualifiedSora2MutationRuntime,
    ): BigInteger = checkNotNull(
        extrinsicManager.calcPolkamarktFee(
            from = accountId,
            runtime = signingRuntime,
        ) {
            when (quote.side) {
                PolkamarktTradeSide.BUY -> polkamarktBuy(
                    marketId = checkNotNull(quote.market.marketId),
                    outcome = quote.outcome.runtimeValue,
                    collateralIn = quote.amountIn,
                    minimumSharesOut = quote.minimumOutput,
                )
                PolkamarktTradeSide.SELL -> polkamarktSell(
                    marketId = checkNotNull(quote.market.marketId),
                    outcome = quote.outcome.runtimeValue,
                    sharesIn = quote.amountIn,
                    minimumCollateralOut = quote.minimumOutput,
                )
            }
        }
    ) { "POLKAMARKT_FEE_UNAVAILABLE" }

    suspend fun claimTraderPayout(
        confirmed: PolkamarktClaimAuthorization,
    ): PolkamarktMutationResult {
        check(!confirmed.creatorFees && confirmed.marketIds.size == 1) {
            "POLKAMARKT_CLAIM_AUTHORIZATION_MISMATCH"
        }
        return executeClaim(confirmed)
    }

    suspend fun claimTraderPayouts(
        confirmed: PolkamarktClaimAuthorization,
    ): PolkamarktMutationResult {
        check(!confirmed.creatorFees && confirmed.marketIds.size > 1) {
            "POLKAMARKT_CLAIM_AUTHORIZATION_MISMATCH"
        }
        return executeClaim(confirmed)
    }

    suspend fun claimCreatorFees(
        confirmed: PolkamarktClaimAuthorization,
    ): PolkamarktMutationResult {
        check(confirmed.creatorFees && confirmed.marketIds.size == 1) {
            "POLKAMARKT_CLAIM_AUTHORIZATION_MISMATCH"
        }
        return executeClaim(confirmed)
    }

    private suspend fun executeClaim(
        confirmed: PolkamarktClaimAuthorization,
    ): PolkamarktMutationResult = PolkamarktMutationAdmissionGate.withAdmission {
        executeClaimAdmitted(confirmed)
    }

    private suspend fun executeClaimAdmitted(
        confirmed: PolkamarktClaimAuthorization,
    ): PolkamarktMutationResult {
        PolkamarktClaimValidator.requireAuthorization(confirmed)
        val expectedAccountId = confirmed.accountId
        val marketIds = confirmed.marketIds
        val creatorFees = confirmed.creatorFees
        requireVisibility(mutations = true)
        runtimeContract.requirePolkamarktMutationRuntime()
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            userRepository.getCurSoraAccount().substrateAddress,
        )
        requirePendingRecoveryClear(expectedAccountId)
        return PolkamarktMutationSubmissionCoordinator.withNonceReservedUntilTransport {
            var pendingHandoff: PolkamarktPendingHandoff? = null
            var preTransportValidationCompleted = false
            try {
                val (prepared, localId, signingRuntime) = userRepository.withWalletMutationLocked { snapshot ->
                    val account = checkNotNull(snapshot.selectedAccount) {
                        "POLKAMARKT_ACCOUNT_CHANGED"
                    }
                    PolkamarktMutationAccountValidator.requireExpected(
                        expectedAccountId,
                        account.substrateAddress,
                    )
                    val finalizedAt = calls.getFinalizedHead().canonicalExtrinsicHash()
                    val freshClaims = requireClaims(
                        account.substrateAddress,
                        marketIds,
                        creatorFees,
                        finalizedAt,
                    )
                    PolkamarktClaimValidator.requireFreshClaims(confirmed, freshClaims)
                    val networkFee = checkNotNull(
                        extrinsicManager.calcFee(
                            from = account.substrateAddress,
                            useBatchAll = false,
                        ) {
                            when {
                                creatorFees -> polkamarktClaimCreatorFees(marketIds.single())
                                marketIds.size == 1 ->
                                    polkamarktClaimTraderPayout(marketIds.single())
                                else -> polkamarktClaimTraderPayouts(marketIds)
                            }
                        }
                    ) { "POLKAMARKT_FEE_UNAVAILABLE" }
                    val xorBalance = calls.fetchBalances(
                        account.substrateAddress,
                        listOf(SubstrateOptionsProvider.feeAssetId),
                        finalizedAt,
                    ).singleOrNull()
                        ?: throw IllegalStateException("POLKAMARKT_BALANCE_UNAVAILABLE")
                    check(xorBalance >= networkFee) { "POLKAMARKT_INSUFFICIENT_XOR" }
                    requireVisibility(mutations = true)
                    runtimeContract.requirePolkamarktMutationRuntime()
                    val beforeSignAt = calls.getFinalizedHead().canonicalExtrinsicHash()
                    val beforeSignClaims = requireClaims(
                        account.substrateAddress,
                        marketIds,
                        creatorFees,
                        beforeSignAt,
                    )
                    PolkamarktClaimValidator.requireFreshClaims(confirmed, beforeSignClaims)
                    val beforeSignXor = calls.fetchBalances(
                        account.substrateAddress,
                        listOf(SubstrateOptionsProvider.feeAssetId),
                        beforeSignAt,
                    ).singleOrNull()
                        ?: throw IllegalStateException("POLKAMARKT_BALANCE_UNAVAILABLE")
                    val signingRuntime =
                        runtimeContract.requirePolkamarktMutationRuntimeContext()
                    val beforeSignFee = checkNotNull(
                        extrinsicManager.calcPolkamarktFee(
                            from = account.substrateAddress,
                            runtime = signingRuntime,
                            useBatchAll = false,
                        ) {
                            when {
                                creatorFees -> polkamarktClaimCreatorFees(marketIds.single())
                                marketIds.size == 1 ->
                                    polkamarktClaimTraderPayout(marketIds.single())
                                else -> polkamarktClaimTraderPayouts(marketIds)
                            }
                        }
                    ) { "POLKAMARKT_FEE_UNAVAILABLE" }
                    PolkamarktClaimValidator.requireFreshFeeAndBalance(
                        confirmedNetworkFee = networkFee,
                        freshNetworkFee = beforeSignFee,
                        freshXorBalance = beforeSignXor,
                    )
                    requireVisibility(mutations = true)
                    check(!database.walletIdentityDao().hasActiveDeletionOperation()) {
                        "WALLET_DELETION_ACTIVE"
                    }
                    requirePendingRecoveryClear(expectedAccountId)

                    val keyPair = credentialsRepository.retrieveKeyPair(account)
                    val signed = try {
                        extrinsicManager.preparePolkamarktExtrinsic(
                            from = account.substrateAddress,
                            keypair = keyPair,
                            runtime = signingRuntime,
                            useBatchAll = false,
                        ) {
                            when {
                                creatorFees -> polkamarktClaimCreatorFees(marketIds.single())
                                marketIds.size == 1 ->
                                    polkamarktClaimTraderPayout(marketIds.single())
                                else -> polkamarktClaimTraderPayouts(marketIds)
                            }
                        }
                    } finally {
                        keyPair.privateKey.fill(0)
                        keyPair.nonce.fill(0)
                    }
                    // See executeTrade: this identity must be observable by the outer failure
                    // boundary before the journal write reaches its first suspension point.
                    val pendingId = PolkamarktPendingRecoveryPlanner.newLocalId()
                    pendingHandoff = PolkamarktPendingHandoff(
                        localId = pendingId,
                        transactionHash = signed.transactionHash,
                    )
                    createPending(
                        localId = pendingId,
                        walletId = account.substrateAddress,
                        marketId = marketIds.first(),
                        amount = BigInteger.ZERO,
                        outcome = null,
                        operation = if (creatorFees) "CREATOR_CLAIM" else "TRADER_CLAIM",
                        transactionHash = signed.transactionHash,
                    )
                    Triple(signed, pendingId, signingRuntime)
                }
                val result = extrinsicManager.submitPreparedAndWait(
                    prepared = prepared,
                    preTransportValidation = {
                        requireMutationPreTransport(
                            expectedAccountId = expectedAccountId,
                            signingRuntime = signingRuntime,
                            allowedCurrentLocalId = localId,
                        )
                        markPendingSubmitted(localId, prepared.transactionHash)
                        preTransportValidationCompleted = true
                    },
                )
                finishPending(localId, prepared.transactionHash, result)
            } catch (error: Throwable) {
                pendingHandoff?.let { pending ->
                    persistPendingFailure(
                        localId = pending.localId,
                        transactionHash = pending.transactionHash,
                        error = error,
                        preTransportValidationCompleted = preTransportValidationCompleted,
                    )
                }
                throw error
            }
        }
    }

    private suspend fun requireTradableMarket(marketId: Long): PolkamarktMarket {
        PolkamarktMarketId.requireValid(marketId)
        val market = runCatching {
            indexer.getMarket(marketId.toString())
                ?: indexer.getMarketsQualified().let { catalogRead ->
                    storeCatalogBestEffort(catalogRead)
                    catalogRead.value.firstOrNull { it.marketId == marketId }
                }
                ?: throw IllegalStateException("POLKAMARKT_MARKET_MISSING")
        }.getOrElse { liveError ->
            if (!PiIndexerOfflineFallbackPolicy.allows(liveError)) throw liveError
            catalogCache.read()?.firstOrNull { it.marketId == marketId }
                ?: throw liveError
        }
        check(
            market.marketId == marketId &&
                market.id == marketId.toString()
        ) { "POLKAMARKT_MARKET_ID_MISMATCH" }
        return market
    }

    private suspend fun storeCatalogBestEffort(
        catalog: PiQualifiedRead<List<PolkamarktMarket>>,
    ) {
        try {
            catalogCache.store(catalog)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Catalog persistence never changes authoritative quote/mutation
            // validation and must not fail a qualified live read.
        }
    }

    private suspend fun requireClaims(
        accountId: String,
        marketIds: List<Long>,
        creatorFees: Boolean,
        blockHash: String,
    ): Map<Long, PolkamarktClaimableDto> {
        val claims = linkedMapOf<Long, PolkamarktClaimableDto>()
        marketIds.forEach { marketId ->
            val claimableAt = calls.polkamarktClaimable(accountId, marketId, blockHash)
            check(
                claimableAt.blockHash.canonicalExtrinsicHash() ==
                    blockHash.canonicalExtrinsicHash()
            ) { "POLKAMARKT_CLAIM_BLOCK_MISMATCH" }
            val claimable = checkNotNull(
                claimableAt.value
            ) { "POLKAMARKT_NOT_CLAIMABLE" }
            PolkamarktClaimValidator.requireClaimable(
                claimable = claimable,
                expectedAccountId = accountId,
                expectedMarketId = marketId,
                creatorFees = creatorFees,
            )
            claims[marketId] = claimable
        }
        return claims
    }

    /**
     * Last fail-closed gate invoked by [ExtrinsicManager.submitPreparedAndWait] before it may open
     * the RPC transport. Keeping the ordered checks here prevents trade and claim paths from
     * drifting apart and makes every emergency boundary directly qualifiable.
     */
    internal suspend fun requireMutationPreTransport(
        expectedAccountId: String,
        signingRuntime: QualifiedSora2MutationRuntime,
        allowedCurrentLocalId: String? = null,
    ) {
        PolkamarktMutationAccountValidator.requireExpected(
            expectedAccountId,
            userRepository.getCurSoraAccount().substrateAddress,
        )
        requireVisibility(mutations = true)
        runtimeContract.requirePolkamarktMutationRuntimeUnchanged(signingRuntime)
        check(!database.walletIdentityDao().hasActiveDeletionOperation()) {
            "WALLET_DELETION_ACTIVE"
        }
        requirePendingRecoveryClear(expectedAccountId, allowedCurrentLocalId)
    }

    private suspend fun requirePendingRecoveryClear(
        expectedAccountId: String,
        allowedCurrentLocalId: String? = null,
    ) {
        val records = try {
            val walletDao = database.walletIdentityDao()
            check(walletDao.countPendingTransactionsRequiringChainRecovery() == 0) {
                "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
            }
            walletDao.observePendingTransactions(expectedAccountId)
                .first()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("POLKAMARKT_PENDING_RECOVERY_REQUIRED", error)
        }
        PolkamarktPendingMutationGate.requireHealthy(
            expectedWalletId = expectedAccountId,
            records = records,
            allowedCurrentLocalId = allowedCurrentLocalId,
        )
    }

    private suspend fun requireVisibility(mutations: Boolean) {
        val flags = featureManager.getState()
        check(flags.polkamarktVisible) { "POLKAMARKT_DISABLED" }
        if (mutations) {
            check(flags.polkamarktMutationsAvailable) { "POLKAMARKT_MUTATIONS_DISABLED" }
        }
    }

    private fun String.isCanonicalRuntimeLabel(): Boolean =
        isNotBlank() &&
            this == trim() &&
            length <= MAX_RUNTIME_LABEL_LENGTH &&
            none { it == '\n' || it == '\r' || it == '\u0000' }

    private fun validateBuyQuote(
        quote: PolkamarktBuyQuoteDto,
        marketId: Long,
        outcome: PolkamarktOutcome,
        collateralIn: BigInteger,
    ) {
        check(quote.marketId == marketId) { "POLKAMARKT_QUOTE_MARKET_MISMATCH" }
        check(quote.outcome == outcome.runtimeValue) { "POLKAMARKT_QUOTE_OUTCOME_MISMATCH" }
        check(quote.collateralIn == collateralIn) { "POLKAMARKT_QUOTE_AMOUNT_MISMATCH" }
        check(quote.sharesOut > BigInteger.ZERO) { "POLKAMARKT_QUOTE_EMPTY" }
        check(quote.feeAmount >= BigInteger.ZERO) { "POLKAMARKT_QUOTE_INVALID_FEE" }
    }

    private fun validateSellQuote(
        quote: PolkamarktSellQuoteDto,
        marketId: Long,
        outcome: PolkamarktOutcome,
        sharesIn: BigInteger,
    ) {
        check(quote.marketId == marketId) { "POLKAMARKT_QUOTE_MARKET_MISMATCH" }
        check(quote.outcome == outcome.runtimeValue) { "POLKAMARKT_QUOTE_OUTCOME_MISMATCH" }
        check(quote.sharesIn == sharesIn) { "POLKAMARKT_QUOTE_AMOUNT_MISMATCH" }
        check(quote.collateralOut > BigInteger.ZERO) { "POLKAMARKT_QUOTE_EMPTY" }
        check(quote.feeAmount >= BigInteger.ZERO) { "POLKAMARKT_QUOTE_INVALID_FEE" }
    }

    private fun validateSlippage(slippageBps: Int) {
        require(slippageBps in 0..MAX_SLIPPAGE_BPS) { "POLKAMARKT_INVALID_SLIPPAGE" }
    }

    private fun requireConfirmedQuoteIdentity(
        quote: PolkamarktTradeQuote,
        marketId: Long,
    ) {
        require(quote.accountId.isNotBlank()) { "POLKAMARKT_ACCOUNT_MISSING" }
        val marketStatus = checkNotNull(quote.market.status) {
            "POLKAMARKT_MARKET_STATUS_MISSING"
        }
        require(
            runCatching {
                UUID.fromString(quote.confirmationNonce).toString() ==
                    quote.confirmationNonce
            }.getOrDefault(false)
        ) { "POLKAMARKT_QUOTE_NONCE_INVALID" }
        check(
            quote.identity == quoteIdentity(
                quote.confirmationNonce,
                quote.accountId,
                marketId,
                quote.side,
                quote.outcome,
                quote.amountIn,
                quote.outputAmount,
                quote.minimumOutput,
                quote.marketFee,
                quote.xorNetworkFee,
                quote.authoritativeMechanism,
                quote.authoritativeCloseBlock,
                quote.observedFinalizedBlock,
                marketStatus,
            )
        ) { "POLKAMARKT_QUOTE_IDENTITY_MISMATCH" }
    }

    private fun quoteIdentity(vararg values: Any): String =
        MessageDigest.getInstance("SHA-256")
            .digest(values.joinToString("\u0000").toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private suspend fun createPending(
        localId: String,
        walletId: String,
        marketId: Long,
        amount: BigInteger,
        outcome: PolkamarktOutcome?,
        operation: String,
        transactionHash: String,
    ): String {
        val canonicalHash = transactionHash.canonicalExtrinsicHash()
        check(
            Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH == WalletNetworkChainIdentity.SORA2
        ) { "POLKAMARKT_PENDING_CHAIN_CONFIGURATION_MISMATCH" }
        val now = System.currentTimeMillis()
        withContext(NonCancellable) {
            database.walletIdentityDao().insertPendingTransaction(
                PendingNetworkTransactionLocal(
                    localId = localId,
                    walletId = walletId,
                    networkId = "sora2",
                    chainId = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
                    transactionHash = canonicalHash,
                    assetId = PolkamarktPendingAssetId.encode(marketId, operation),
                    amount = amount.toString(),
                    recipient = outcome?.runtimeValue ?: "claims",
                    state = "SIGNED",
                    submissionIsAmbiguous = false,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        return localId
    }

    private suspend fun finishPending(
        localId: String,
        expectedHash: String,
        result: ExtrinsicSubmitStatus,
    ): PolkamarktMutationResult {
        val canonicalExpectedHash = expectedHash.canonicalExtrinsicHash()
        val returnedHash = result.txHash.takeIf(String::isNotBlank)
            ?.canonicalExtrinsicHash()
        val returnedBlockHash = result.blockHash?.canonicalExtrinsicHash()
        val hashMatches = returnedHash == canonicalExpectedHash
        val state = when {
            !hashMatches -> "UNKNOWN"
            result.success && returnedBlockHash != null -> "FINALIZED"
            !result.success && returnedBlockHash != null -> "REJECTED"
            else -> "UNKNOWN"
        }
        val authoritativeTransportResult = hashMatches && returnedBlockHash != null
        try {
            withContext(NonCancellable) {
                database.walletIdentityDao().updatePendingTransaction(
                    localId = localId,
                    transactionHash = canonicalExpectedHash,
                    state = state,
                    ambiguous = state == "UNKNOWN",
                    updatedAt = System.currentTimeMillis(),
                )
            }
        } catch (persistenceError: Throwable) {
            try {
                FirebaseWrapper.recordErrorClass(
                    FirebaseWrapper.PrivacySafeErrorClass.STATE_FAILURE
                )
            } catch (_: Throwable) {
                // Telemetry cannot change an authoritative transport result either.
            }
            // Finalized inclusion/execution is authoritative. A local overlay write failure must
            // not turn that chain result into a rejection or a mutation that appears retryable.
            if (!authoritativeTransportResult) {
                throw ExtrinsicSubmissionUnknownException(
                    canonicalExpectedHash,
                    persistenceError,
                )
            }
        }
        if (returnedHash != null && !hashMatches) {
            throw ExtrinsicSubmissionUnknownException(
                canonicalExpectedHash,
                IllegalStateException("POLKAMARKT_TRANSACTION_HASH_MISMATCH"),
            )
        }
        return PolkamarktMutationResult(localId, canonicalExpectedHash, state)
    }

    /**
     * Durable transport-arm boundary. A new-version row still in SIGNED after process death is
     * proven not submitted; once this update commits, recovery treats the hash as ambiguous and
     * only finalized chain evidence may make it terminal.
     */
    internal suspend fun markPendingSubmitted(
        localId: String,
        transactionHash: String,
    ) {
        val canonicalHash = transactionHash.canonicalExtrinsicHash()
        withContext(NonCancellable) {
            database.walletIdentityDao().updatePendingTransaction(
                localId = localId,
                transactionHash = canonicalHash,
                state = "SUBMITTED",
                ambiguous = true,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun markPendingUnknown(localId: String, transactionHash: String) {
        val canonicalHash = transactionHash.canonicalExtrinsicHash()
        database.walletIdentityDao().updatePendingTransaction(
            localId = localId,
            transactionHash = canonicalHash,
            state = "UNKNOWN",
            ambiguous = true,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun markPendingDefinitiveFailure(
        localId: String,
        transactionHash: String,
    ) {
        val canonicalHash = transactionHash.canonicalExtrinsicHash()
        database.walletIdentityDao().updatePendingTransaction(
            localId = localId,
            transactionHash = canonicalHash,
            state = "REJECTED",
            ambiguous = false,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Cancellation is a normal way for an Android UI scope to disappear while an RPC watch is
     * active. Persist the exact pre/post-transport classification before propagating cancellation
     * so restart recovery never inherits a misleading non-ambiguous SIGNED record.
     */
    private suspend fun persistPendingFailure(
        localId: String,
        transactionHash: String,
        error: Throwable,
        preTransportValidationCompleted: Boolean,
    ) {
        try {
            withContext(NonCancellable) {
                val disposition = PolkamarktPendingFailurePolicy.classify(
                    preTransportValidationCompleted = preTransportValidationCompleted,
                    error = error,
                )
                if (disposition.submissionIsAmbiguous) {
                    markPendingUnknown(localId, transactionHash)
                } else {
                    markPendingDefinitiveFailure(localId, transactionHash)
                }
            }
        } catch (persistenceError: Throwable) {
            if (persistenceError !== error) {
                error.addSuppressed(persistenceError)
            }
        }
    }

    private companion object {
        const val BPS_DENOMINATOR = 10_000
        const val MAX_SLIPPAGE_BPS = PolkamarktWebContract.MAX_SLIPPAGE_BPS
        const val MAX_RUNTIME_LABEL_LENGTH = 256
    }
}
