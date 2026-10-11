/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.common.domain

/**
 * What is known about one signed SORA2 transaction after the app handed it to the network.
 *
 * Every screen that sends a transaction (transfer, swap, liquidity, referral, Demeter, legacy
 * claim) receives exactly one of these four values and must show each of them truthfully:
 *
 * - [Confirmed]: found in a finalized block and executed successfully.
 * - [FailedOnChain]: the network gave a final negative answer for the broadcast transaction.
 * - [RefusedByNode]: the node answered the single submission with an explicit error, so the
 *   transaction was never broadcast. It is a failure and must never be shown as "submitted".
 * - [Waiting]: handed over (or possibly handed over) and not final yet. It must never be shown
 *   as failed or rejected; the background status check resolves it and Activity shows the result.
 *
 * Errors raised before the transaction reaches the network (no connection while preparing it,
 * a fee that changed, a read-only account) are not outcomes: nothing was sent, and they are
 * thrown to the caller instead.
 */
sealed class TransactionOutcome {

    abstract val txHash: String

    data class Confirmed(
        override val txHash: String,
        val blockHash: String?,
    ) : TransactionOutcome()

    data class FailedOnChain(
        override val txHash: String,
        val blockHash: String?,
        val reason: TransactionChainFailure,
        /** Runtime error reported by the network, for example `LiquidityProxy.SlippageNotTolerated`. */
        val detail: String? = null,
    ) : TransactionOutcome()

    data class RefusedByNode(
        override val txHash: String,
        val reason: TransactionNodeRefusal,
        /** Short text the node sent with its refusal. Untrusted; show it as plain text only. */
        val nodeMessage: String? = null,
    ) : TransactionOutcome()

    data class Waiting(
        override val txHash: String,
        /** False when the node never answered, so even its receipt of the transaction is unknown. */
        val acceptedByNode: Boolean,
    ) : TransactionOutcome()
}

enum class TransactionChainFailure {
    /** Included in a finalized block; execution failed and the network fee was charged. */
    EXECUTION_FAILED,

    /** Never included before its validity window ended; nothing happened and no fee was charged. */
    NOT_INCLUDED,
}

enum class TransactionNodeRefusal {
    /** The account cannot pay the network fee, for example because its XOR is locked. */
    CANNOT_PAY_FEE,

    /** The node's transaction queue did not take it (queue full or a competing transaction). */
    NETWORK_BUSY,

    /** The node checked the transaction and found it not valid right now. */
    NOT_VALID,
}

/**
 * Raised before signing when the same action for the same account is still waiting for
 * confirmation. The caller tells the user and repeats the request only after a deliberate second
 * confirmation.
 */
class DuplicateWaitingTransactionException(
    val waitingTxHash: String,
    val waitingSinceMillis: Long,
) : IllegalStateException("SORA2_SAME_ACTION_STILL_WAITING")
