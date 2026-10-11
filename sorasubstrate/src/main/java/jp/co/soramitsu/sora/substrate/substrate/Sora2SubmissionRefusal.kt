package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeRpcRemoteException

/**
 * Decides whether an error from the single `author_submitExtrinsic` call proves that the node did
 * not take the transaction.
 *
 * Only an explicit JSON-RPC error object whose code is one of the transaction-pool refusals of
 * the Substrate author RPC counts. For those the node validated the transaction, rejected it and
 * therefore neither queued nor gossiped it, and this app sends each signed transaction to exactly
 * one node exactly once. Everything else stays "submission unknown" and is resolved from
 * finalized blocks only:
 *
 * - transport errors, timeouts, malformed answers, unknown error codes;
 * - `1012` (temporarily banned) and `1013` (already imported), which mean the node has seen the
 *   same transaction before;
 * - an "outdated"/"stale" answer, which means the account nonce is already used and could be the
 *   result of this very transaction having been included.
 *
 * The journal row is never changed by a refusal: the status check still scans the transaction's
 * whole validity window, so in the unexpected case that a refused transaction does appear in a
 * finalized block, Activity shows the chain result.
 */
internal object Sora2SubmissionRefusalClassifier {

    fun refusalOrNull(error: Throwable): Sora2NodeRefusal? {
        val remote = error as? Sora2RuntimeRpcRemoteException ?: return null
        val text = listOfNotNull(remote.remoteData, remote.remoteMessage).joinToString(" ")
        val reason = when (remote.code) {
            POOL_INVALID_TRANSACTION -> when {
                STALE_MARKERS.any { text.contains(it, ignoreCase = true) } -> return null
                PAYMENT_MARKERS.any { text.contains(it, ignoreCase = true) } ->
                    TransactionNodeRefusal.CANNOT_PAY_FEE
                else -> TransactionNodeRefusal.NOT_VALID
            }
            POOL_TOO_LOW_PRIORITY,
            POOL_IMMEDIATELY_DROPPED -> TransactionNodeRefusal.NETWORK_BUSY
            in DEFINITE_NOT_VALID_CODES -> TransactionNodeRefusal.NOT_VALID
            else -> return null
        }
        return Sora2NodeRefusal(
            reason = reason,
            code = remote.code,
            message = (remote.remoteData ?: remote.remoteMessage).sanitizedStatusText(),
        )
    }

    // sc-rpc-api author error codes (client/rpc-api/src/author/error.rs).
    private const val BAD_FORMAT = 1001L
    private const val VERIFICATION_ERROR = 1002L
    private const val POOL_INVALID_TRANSACTION = 1010L
    private const val POOL_UNKNOWN_VALIDITY = 1011L
    private const val POOL_TOO_LOW_PRIORITY = 1014L
    private const val POOL_CYCLE_DETECTED = 1015L
    private const val POOL_IMMEDIATELY_DROPPED = 1016L
    private const val POOL_UNACTIONABLE = 1018L
    private const val POOL_NO_TAGS = 1019L
    private const val POOL_INVALID_BLOCK_ID = 1020L
    private const val POOL_FUTURE_TRANSACTION = 1021L

    private val DEFINITE_NOT_VALID_CODES = setOf(
        BAD_FORMAT,
        VERIFICATION_ERROR,
        POOL_UNKNOWN_VALIDITY,
        POOL_CYCLE_DETECTED,
        POOL_UNACTIONABLE,
        POOL_NO_TAGS,
        POOL_INVALID_BLOCK_ID,
        POOL_FUTURE_TRANSACTION,
    )

    // InvalidTransaction::Stale ("Transaction is outdated").
    private val STALE_MARKERS = listOf("outdated", "stale")

    // InvalidTransaction::Payment ("Inability to pay some fees (e.g. account balance too low)").
    private val PAYMENT_MARKERS = listOf("pay some fees", "payment", "balance too low")
}

/**
 * The single submission attempt was answered with an explicit refusal ([refusal]). The
 * transaction was not broadcast. It is a definitive failure for the user and never "submitted".
 */
class ExtrinsicRefusedByNodeException(
    val transactionHash: String,
    val refusal: Sora2NodeRefusal,
    cause: Throwable,
) : IllegalStateException("EXTRINSIC_REFUSED_BY_NODE", cause)
