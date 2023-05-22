/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory

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
)

sealed class Transaction(
    val base: TransactionBase,
) {

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
        val token: Token
    ) : Transaction(base)

    class Swap(
        base: TransactionBase,
        val tokenFrom: Token,
        val tokenTo: Token,
        val amountFrom: BigDecimal,
        val amountTo: BigDecimal,
        val market: Market,
        val lpFee: BigDecimal,
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

enum class TransactionStatus {
    PENDING,
    COMMITTED,
    REJECTED
}

enum class TransactionTransferType {
    OUTGOING, INCOMING
}

enum class TransactionLiquidityType {
    ADD, WITHDRAW
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
