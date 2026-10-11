/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.common.presentation.transaction

import androidx.annotation.StringRes
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome

/** The short result mark a confirm screen shows before it moves on. */
enum class TransactionResultBadge(@StringRes val text: Int, val positive: Boolean) {
    CONFIRMED(R.string.tx_outcome_badge_confirmed, true),

    /** Handed to the network and not final yet. Never shown as failed or rejected. */
    SUBMITTED(R.string.wallet_transaction_submitted_1, true),
    FAILED(R.string.tx_outcome_badge_failed, false),

    /** Nothing was broadcast: refused by the node or not prepared at all. */
    NOT_SENT(R.string.tx_outcome_badge_not_sent, false),
}

fun TransactionOutcome.badge(): TransactionResultBadge = when (this) {
    is TransactionOutcome.Confirmed -> TransactionResultBadge.CONFIRMED
    is TransactionOutcome.Waiting -> TransactionResultBadge.SUBMITTED
    is TransactionOutcome.FailedOnChain -> TransactionResultBadge.FAILED
    is TransactionOutcome.RefusedByNode -> TransactionResultBadge.NOT_SENT
}

data class TransactionMessage(val title: String, val message: String)

/**
 * One wording for the same fact on every screen. Each sentence says what happened to the
 * transaction and to the fee, and what the user should do, so no screen needs its own copy.
 */
object TransactionOutcomeTexts {

    /** Explains any outcome; used where a screen has no transaction page to open. */
    fun describe(resources: ResourceManager, outcome: TransactionOutcome): TransactionMessage =
        when (outcome) {
            is TransactionOutcome.Confirmed -> TransactionMessage(
                title = resources.getString(R.string.tx_outcome_confirmed_title),
                message = resources.getString(R.string.tx_outcome_confirmed_message),
            )
            is TransactionOutcome.Waiting -> TransactionMessage(
                title = resources.getString(R.string.tx_outcome_waiting_title),
                message = waitingNote(resources, outcome.acceptedByNode),
            )
            is TransactionOutcome.FailedOnChain -> TransactionMessage(
                title = resources.getString(R.string.tx_outcome_failed_title),
                message = failedOnChainNote(resources, outcome.reason, outcome.detail),
            )
            is TransactionOutcome.RefusedByNode -> refused(resources, outcome)
        }

    fun refused(
        resources: ResourceManager,
        outcome: TransactionOutcome.RefusedByNode,
    ): TransactionMessage = TransactionMessage(
        title = resources.getString(R.string.tx_outcome_refused_title),
        message = refusedNote(resources, outcome.reason, outcome.nodeMessage),
    )

    fun waitingNote(resources: ResourceManager, acceptedByNode: Boolean): String =
        resources.getString(
            if (acceptedByNode) {
                R.string.tx_outcome_note_waiting
            } else {
                R.string.tx_outcome_note_uncertain
            }
        )

    fun failedOnChainNote(
        resources: ResourceManager,
        reason: TransactionChainFailure,
        detail: String?,
    ): String = when (reason) {
        TransactionChainFailure.NOT_INCLUDED ->
            resources.getString(R.string.tx_outcome_note_not_included)
        TransactionChainFailure.EXECUTION_FAILED -> withTechnicalText(
            resources = resources,
            sentence = resources.getString(R.string.tx_outcome_note_failed_on_chain),
            label = R.string.tx_outcome_note_failed_on_chain_reason,
            technicalText = detail,
        )
    }

    fun refusedNote(
        resources: ResourceManager,
        reason: TransactionNodeRefusal,
        nodeMessage: String?,
    ): String = when (reason) {
        TransactionNodeRefusal.CANNOT_PAY_FEE ->
            resources.getString(R.string.tx_outcome_note_refused_fee)
        TransactionNodeRefusal.NETWORK_BUSY ->
            resources.getString(R.string.tx_outcome_note_refused_busy)
        TransactionNodeRefusal.NOT_VALID -> withTechnicalText(
            resources = resources,
            sentence = resources.getString(R.string.tx_outcome_note_refused_invalid),
            label = R.string.tx_outcome_note_network_message,
            technicalText = nodeMessage,
        )
    }

    /**
     * For an error raised before the transaction reached the network. Nothing was sent, so the
     * message says so and never suggests that a transaction may be on its way.
     */
    fun notSent(resources: ResourceManager, error: Throwable): TransactionMessage =
        TransactionMessage(
            title = resources.getString(R.string.tx_outcome_not_sent_title),
            message = resources.getString(
                when {
                    error.hasMessageInChain(FEE_CHANGED_CODE) ->
                        R.string.tx_outcome_not_sent_fee_changed
                    NetworkFeeUnavailable.from(error) ==
                        NetworkFeeUnavailable.NETWORK_UNREACHABLE ->
                        R.string.tx_outcome_not_sent_network
                    else -> R.string.tx_outcome_not_sent_generic
                }
            ),
        )

    private fun withTechnicalText(
        resources: ResourceManager,
        sentence: String,
        @StringRes label: Int,
        technicalText: String?,
    ): String = if (technicalText.isNullOrBlank()) {
        sentence
    } else {
        sentence + "\n\n" + resources.getString(label).format(technicalText)
    }

    private fun Throwable.hasMessageInChain(code: String): Boolean {
        var current: Throwable? = this
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current.message == code) return true
            current = current.cause?.takeIf { it !== current }
            depth += 1
        }
        return false
    }

    private const val FEE_CHANGED_CODE = "SORA2_TRANSFER_FEE_CHANGED"
    private const val MAX_CAUSE_DEPTH = 16
}
