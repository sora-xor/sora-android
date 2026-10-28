/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory

import androidx.annotation.StringRes
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.ui_core.theme.customColors

class TransactionsInfo(
    val transactions: List<Transaction>,
    val endReached: Boolean,
    val errorMessage: String? = null
)

class TransactionBase(
    val txHash: String,
    var blockHash: String? = null,
    val fee: BigDecimal,
    var status: TransactionStatus,
    val timestamp: Long,
    /**
     * Why a transaction sent from this device has its [status]. Null for ordinary history and
     * for a confirmed transaction, where the status says everything.
     */
    var statusDetail: TransactionStatusDetail? = null,
)

/**
 * The reason behind the status of a transaction sent from this device, taken from the durable
 * status journal. [text] is a short technical text reported by the network (a runtime error or
 * the node's refusal message); it is untrusted and shown as plain text only.
 */
data class TransactionStatusDetail(
    val kind: Kind,
    val text: String? = null,
) {
    enum class Kind {
        /** The node took the transaction; it is not in a finalized block yet. */
        WAITING_FOR_CONFIRMATION,

        /** The node's answer was lost; the transaction may or may not have been received. */
        SUBMISSION_UNCERTAIN,

        /** Included in a finalized block and failed there; the fee was charged. */
        FAILED_ON_CHAIN,

        /** Never included before it expired; nothing happened and no fee was charged. */
        NOT_INCLUDED,

        /** The node refused it before broadcast: the account cannot pay the fee. */
        REFUSED_CANNOT_PAY_FEE,

        /** The node refused it before broadcast: its transaction queue did not take it. */
        REFUSED_NETWORK_BUSY,

        /** The node refused it before broadcast: not valid right now. */
        REFUSED_NOT_VALID,

        /** It never left this device: no SORA endpoint could be connected to. */
        NOT_REACHED,
    }

    /** True when the transaction was provably never broadcast. */
    val isRefusal: Boolean
        get() = kind == Kind.REFUSED_CANNOT_PAY_FEE ||
            kind == Kind.REFUSED_NETWORK_BUSY ||
            kind == Kind.REFUSED_NOT_VALID ||
            kind == Kind.NOT_REACHED

    /** True when the transaction never reached a block, so an explorer has nothing to show. */
    val neverOnChain: Boolean
        get() = isRefusal || kind == Kind.NOT_INCLUDED
}

sealed class Transaction(
    val base: TransactionBase,
) {

    /**
     * A restart-safe SORA2 mutation whose durable recovery witness intentionally contains no
     * semantic amount, asset, recipient, or signed bytes. The activity UI must not invent those
     * values while PI history is still catching up.
     */
    class Sora2Submission(
        base: TransactionBase,
        val networkId: String,
        val submissionIsAmbiguous: Boolean,
        val terminalBlockNumber: Long?,
    ) : Transaction(base)

    class AdarIncome(
        base: TransactionBase,
        val amount: BigDecimal,
        val peer: String,
        val token: Token,
    ) : Transaction(base)

    class DemeterFarming(
        base: TransactionBase,
        val type: DemeterType,
        val amount: BigDecimal,
        val baseToken: Token,
        val targetToken: Token,
        val rewardToken: Token,
    ) : Transaction(base)

    class ReferralBond(
        base: TransactionBase,
        val amount: BigDecimal,
        val token: Token,
    ) : Transaction(base)

    class ReferralUnbond(
        base: TransactionBase,
        val amount: BigDecimal,
        val token: Token,
    ) : Transaction(base)

    class ReferralSetReferrer(
        base: TransactionBase,
        val who: String,
        val myReferrer: Boolean,
        val token: Token,
    ) : Transaction(base)

    class Transfer(
        base: TransactionBase,
        val amount: BigDecimal,
        val peer: String,
        val transferType: TransactionTransferType,
        val token: Token,
    ) : Transaction(base)

    class EthTransfer(
        base: TransactionBase,
        val amount: BigDecimal,
        val token: Token,
        val ethToken: Token,
        val requestHash: String,
        val sidechainAddress: String,
    ) : Transaction(base)

    class Swap(
        base: TransactionBase,
        val tokenFrom: Token,
        val tokenTo: Token,
        val amountFrom: BigDecimal,
        val amountTo: BigDecimal,
        val market: Market,
    ) : Transaction(base)

    class Liquidity(
        base: TransactionBase,
        val token1: Token,
        val token2: Token,
        val amount1: BigDecimal,
        val amount2: BigDecimal,
        val type: TransactionLiquidityType,
    ) : Transaction(base)
}

enum class DemeterType {
    STAKE,
    UNSTAKE,
    REWARD,
}

enum class TransactionStatus {
    PENDING,
    COMMITTED,
    REJECTED
}

enum class TransactionTransferType {
    OUTGOING,
    INCOMING
}

enum class TransactionLiquidityType {
    ADD,
    WITHDRAW
}

/**
 * The title of an entry that is known only by its transaction hash. A transaction the node
 * refused was never broadcast, so it is not called "submitted" (nor "status unknown").
 */
@StringRes
fun Transaction.Sora2Submission.genericTitle(): Int = when {
    base.statusDetail?.isRefusal == true -> R.string.tx_outcome_not_sent_title
    submissionIsAmbiguous -> R.string.wallet_transaction_status_unknown
    else -> R.string.wallet_transaction_submitted
}

fun TransactionStatus.toName() = when (this) {
    TransactionStatus.PENDING -> R.string.status_pending
    TransactionStatus.COMMITTED -> R.string.status_successful
    TransactionStatus.REJECTED -> R.string.common_failed
}

@Composable
fun TransactionStatus.toColor() = when (this) {
    TransactionStatus.PENDING -> MaterialTheme.customColors.fgSecondary
    TransactionStatus.COMMITTED -> MaterialTheme.customColors.statusSuccess
    TransactionStatus.REJECTED -> MaterialTheme.customColors.statusError
}
