/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.network.model.TransactionRemote

fun mapTransactionRemoteToTransaction(transactionRemote: TransactionRemote): Transaction {
    return with(transactionRemote) {
        Transaction(
            transactionId,
            mapTransactionStatusRemoteToTransactionStatus(status),
            assetId,
            details,
            peerName,
            amount,
            timestamp,
            peerId,
            reason,
            mapTransactionTypeRemoteToTransactionType(type),
            fee
        )
    }
}

fun mapTransactionStatusRemoteToTransactionStatus(statusRemote: TransactionRemote.Status): Transaction.Status {
    return when (statusRemote) {
        TransactionRemote.Status.COMMITTED -> Transaction.Status.COMMITTED
        TransactionRemote.Status.PENDING -> Transaction.Status.PENDING
        TransactionRemote.Status.REJECTED -> Transaction.Status.REJECTED
    }
}

fun mapTransactionTypeRemoteToTransactionType(typeRemote: TransactionRemote.Type): Transaction.Type {
    return when (typeRemote) {
        TransactionRemote.Type.INCOMING -> Transaction.Type.INCOMING
        TransactionRemote.Type.OUTGOING -> Transaction.Type.OUTGOING
        TransactionRemote.Type.WITHDRAW -> Transaction.Type.WITHDRAW
        TransactionRemote.Type.REWARD -> Transaction.Type.REWARD
    }
}

fun mapTransactionLocalToTransaction(transaction: TransactionLocal): Transaction {
    return with(transaction) {
        Transaction(
            transactionId,
            mapTransactionStatusLocalToTransactionStatus(status),
            assetId,
            details,
            peerName,
            amount,
            timestamp,
            peerId,
            reason,
            mapTransactionTypeLocalToTransactionType(type),
            fee
        )
    }
}

fun mapTransactionStatusLocalToTransactionStatus(statusRemote: TransactionLocal.Status): Transaction.Status {
    return when (statusRemote) {
        TransactionLocal.Status.COMMITTED -> Transaction.Status.COMMITTED
        TransactionLocal.Status.PENDING -> Transaction.Status.PENDING
        TransactionLocal.Status.REJECTED -> Transaction.Status.REJECTED
    }
}

fun mapTransactionTypeLocalToTransactionType(typeRemote: TransactionLocal.Type): Transaction.Type {
    return when (typeRemote) {
        TransactionLocal.Type.INCOMING -> Transaction.Type.INCOMING
        TransactionLocal.Type.OUTGOING -> Transaction.Type.OUTGOING
        TransactionLocal.Type.WITHDRAW -> Transaction.Type.WITHDRAW
        TransactionLocal.Type.REWARD -> Transaction.Type.REWARD
    }
}

fun mapTransactionToTransactionLocal(transaction: Transaction): TransactionLocal {
    return with(transaction) {
        TransactionLocal(
            transactionId,
            mapTransactionStatusToTransactionStatusLocal(status),
            assetId,
            details,
            peerName,
            amount,
            timestamp,
            peerId,
            reason,
            mapTransactionTypeToTransactionTypeLocal(type),
            fee
        )
    }
}

fun mapTransactionStatusToTransactionStatusLocal(status: Transaction.Status): TransactionLocal.Status {
    return when (status) {
        Transaction.Status.COMMITTED -> TransactionLocal.Status.COMMITTED
        Transaction.Status.PENDING -> TransactionLocal.Status.PENDING
        Transaction.Status.REJECTED -> TransactionLocal.Status.REJECTED
    }
}

fun mapTransactionTypeToTransactionTypeLocal(type: Transaction.Type): TransactionLocal.Type {
    return when (type) {
        Transaction.Type.INCOMING -> TransactionLocal.Type.INCOMING
        Transaction.Type.OUTGOING -> TransactionLocal.Type.OUTGOING
        Transaction.Type.WITHDRAW -> TransactionLocal.Type.WITHDRAW
        Transaction.Type.REWARD -> TransactionLocal.Type.REWARD
    }
}