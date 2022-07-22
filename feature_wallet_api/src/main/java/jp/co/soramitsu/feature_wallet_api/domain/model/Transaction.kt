/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.common.domain.Token
import java.math.BigDecimal

class TransactionsInfo(
    val transactions: List<Transaction>,
    val endReached: Boolean,
)

class TransactionBase(
    val txHash: String,
    val blockHash: String? = null,
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
