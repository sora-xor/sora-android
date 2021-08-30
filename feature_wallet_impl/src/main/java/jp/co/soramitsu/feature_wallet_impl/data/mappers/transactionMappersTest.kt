/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.math.BigDecimal

fun mapTransactionLocalToTransaction(transactionLocal: TransferTransactionLocal): Transaction {
    return with(transactionLocal) {
        Transaction(
            "",
            "",
            txHash,
            mapTransactionStatusLocalToTransactionStatus(status),
            mapTransactionStatusLocalToTransactionDetailedStatus(status),
            assetId,
            myAddress,
            "",
            "",
            amount,
            timestamp,
            peerId,
            "",
            mapTransactionTypeLocalToTransactionType(type),
            BigDecimal.ZERO,
            fee,
            null,
            blockHash,
            eventSuccess,
        )
    }
}

fun mapTransactionStatusLocalToTransactionStatus(statusRemote: TransferTransactionLocal.Status): Transaction.Status {
    return when (statusRemote) {
        TransferTransactionLocal.Status.COMMITTED -> Transaction.Status.COMMITTED
        TransferTransactionLocal.Status.PENDING -> Transaction.Status.PENDING
        TransferTransactionLocal.Status.REJECTED -> Transaction.Status.REJECTED
    }
}

fun mapTransactionStatusLocalToTransactionDetailedStatus(statusRemote: TransferTransactionLocal.Status): Transaction.DetailedStatus {
    return when (statusRemote) {
        TransferTransactionLocal.Status.COMMITTED -> Transaction.DetailedStatus.TRANSFER_COMPLETED
        TransferTransactionLocal.Status.PENDING -> Transaction.DetailedStatus.TRANSFER_PENDING
        TransferTransactionLocal.Status.REJECTED -> Transaction.DetailedStatus.TRANSFER_FAILED
    }
}

fun mapTransactionTypeLocalToTransactionType(typeRemote: TransferTransactionLocal.Type): Transaction.Type {
    return when (typeRemote) {
        TransferTransactionLocal.Type.INCOMING -> Transaction.Type.INCOMING
        TransferTransactionLocal.Type.OUTGOING -> Transaction.Type.OUTGOING
        TransferTransactionLocal.Type.WITHDRAW -> Transaction.Type.WITHDRAW
        TransferTransactionLocal.Type.REWARD -> Transaction.Type.REWARD
        TransferTransactionLocal.Type.DEPOSIT -> Transaction.Type.DEPOSIT
    }
}
