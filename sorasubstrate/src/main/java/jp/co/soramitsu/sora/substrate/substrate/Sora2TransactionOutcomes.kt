package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus

/**
 * The one place that turns a SORA2 submission into the outcome every screen shows.
 *
 * [submit] is any [ExtrinsicManager] submit-and-wait call. Its result and the errors it can raise
 * after the transaction reached the transport map to exactly one [TransactionOutcome]:
 *
 * - finalized and successful: [TransactionOutcome.Confirmed];
 * - finalized and failed, or expired without inclusion: [TransactionOutcome.FailedOnChain];
 * - explicit refusal by the node: [TransactionOutcome.RefusedByNode];
 * - anything else after the handoff (lost answer, interrupted or unfinished wait, the screen
 *   going away): [TransactionOutcome.Waiting].
 *
 * Errors from before the handoff mean nothing was sent and are rethrown unchanged.
 */
suspend fun sora2TransactionOutcome(
    submit: suspend () -> ExtrinsicSubmitStatus,
): TransactionOutcome = try {
    submit().toTransactionOutcome()
} catch (error: Throwable) {
    error.toTransactionOutcomeOrNull() ?: throw error
}

fun ExtrinsicSubmitStatus.toTransactionOutcome(): TransactionOutcome = when {
    success -> TransactionOutcome.Confirmed(txHash = txHash, blockHash = blockHash)
    blockHash != null -> TransactionOutcome.FailedOnChain(
        txHash = txHash,
        blockHash = blockHash,
        reason = TransactionChainFailure.EXECUTION_FAILED,
        detail = failureDetail,
    )
    else -> TransactionOutcome.FailedOnChain(
        txHash = txHash,
        blockHash = null,
        reason = TransactionChainFailure.NOT_INCLUDED,
    )
}

/** Null when [this] says nothing about a transaction that reached the transport. */
fun Throwable.toTransactionOutcomeOrNull(): TransactionOutcome? = when (this) {
    is ExtrinsicRefusedByNodeException -> TransactionOutcome.RefusedByNode(
        txHash = transactionHash,
        reason = refusal.reason,
        nodeMessage = refusal.message,
    )
    is ExtrinsicSubmissionUnknown -> TransactionOutcome.Waiting(
        txHash = transactionHash,
        acceptedByNode = acceptedByNode,
    )
    else -> null
}
