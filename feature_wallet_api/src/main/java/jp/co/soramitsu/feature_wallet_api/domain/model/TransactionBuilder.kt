/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.common.domain.Token
import java.math.BigDecimal

object TransactionBuilder {
    fun buildLiquidity(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        token1: Token,
        token2: Token,
        amount1: BigDecimal,
        amount2: BigDecimal,
        type: TransactionLiquidityType,
    ): Transaction.Liquidity =
        Transaction.Liquidity(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            token1 = token1,
            token2 = token2,
            amount1 = amount1,
            amount2 = amount2,
            type = type,
        )

    fun buildSwap(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        tokenFrom: Token,
        tokenTo: Token,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        market: Market,
        liquidityFee: BigDecimal,
    ): Transaction.Swap =
        Transaction.Swap(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            tokenFrom = tokenFrom,
            tokenTo = tokenTo,
            amountFrom = amountFrom,
            amountTo = amountTo,
            market = market,
            lpFee = liquidityFee,
        )

    fun buildTransfer(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
        amount: BigDecimal,
        peer: String,
        type: TransactionTransferType,
        token: Token,
    ): Transaction.Transfer =
        Transaction.Transfer(
            base = buildBase(
                txHash = txHash,
                blockHash = blockHash,
                fee = fee,
                status = status,
                date = date,
            ),
            amount = amount,
            peer = peer,
            transferType = type,
            token = token,
        )

    fun buildBase(
        txHash: String,
        blockHash: String?,
        fee: BigDecimal,
        status: TransactionStatus,
        date: Long,
    ): TransactionBase =
        TransactionBase(
            txHash = txHash,
            blockHash = blockHash,
            fee = fee,
            status = status,
            timestamp = date,
        )
}
