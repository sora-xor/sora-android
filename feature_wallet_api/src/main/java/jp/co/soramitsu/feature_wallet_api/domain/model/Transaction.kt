/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.common.domain.Token
import java.math.BigDecimal

sealed class Transaction(
    val txHash: String,
    val blockHash: String? = null,
    val fee: BigDecimal,
    val status: TransactionStatus,
    val timestamp: Long,
    val successStatus: Boolean? = null,
) {

    class Transfer(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        timestamp: Long,
        successStatus: Boolean?,
        val amount: BigDecimal,
        val peer: String,
        val transferType: TransactionTransferType,
        val token: Token
    ) : Transaction(txHash, blockHash, fee, status, timestamp, successStatus)

    class Swap(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        timestamp: Long,
        successStatus: Boolean?,
        val tokenFrom: Token,
        val tokenTo: Token,
        val amountFrom: BigDecimal,
        val amountTo: BigDecimal,
        val market: Market,
        val lpFee: BigDecimal,
    ) : Transaction(txHash, blockHash, fee, status, timestamp, successStatus)

    class Liquidity(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        timestamp: Long,
        successStatus: Boolean?,
        val token1: Token,
        val token2: Token,
        val amount1: BigDecimal,
        val amount2: BigDecimal,
        val type: TransactionLiquidityType,
    ) : Transaction(txHash, blockHash, fee, status, timestamp, successStatus)
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
