package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.core_db.model.DepositTransactionLocal
import jp.co.soramitsu.core_db.model.TransferTransactionLocal
import jp.co.soramitsu.core_db.model.WithdrawTransactionLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawTransaction
import jp.co.soramitsu.feature_wallet_impl.data.network.model.TransactionRemote
import java.math.BigDecimal
import java.math.BigInteger

fun mapTransactionRemoteToWithdrawTransactionLocal(transactionRemote: TransactionRemote): WithdrawTransactionLocal {
    return with(transactionRemote) {
        WithdrawTransactionLocal(
            transactionId,
            "",
            "",
            mapTransactionStatusRemoteToWithdrawStatusLocal(status),
            peerName,
            details,
            amount,
            BigDecimal.ZERO,
            timestamp,
            details,
            "",
            reason,
            fee,
            BigDecimal.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO
        )
    }
}

private fun mapTransactionStatusRemoteToWithdrawStatusLocal(statusRemote: TransactionRemote.Status): WithdrawTransactionLocal.Status {
    return when (statusRemote) {
        TransactionRemote.Status.COMMITTED -> WithdrawTransactionLocal.Status.CONFIRM_PENDING
        TransactionRemote.Status.PENDING -> WithdrawTransactionLocal.Status.CONFIRM_PENDING
        TransactionRemote.Status.REJECTED -> WithdrawTransactionLocal.Status.INTENT_FAILED
    }
}

fun mapTransactionRemoteToTransactionLocal(transactionRemote: TransactionRemote, myAccountId: String): TransferTransactionLocal {
    return with(transactionRemote) {
        TransferTransactionLocal(
            transactionId,
            mapTransactionStatusRemoteToTransactionStatusLocal(status),
            assetId,
            details,
            myAccountId,
            peerName,
            amount,
            timestamp,
            peerId,
            reason,
            mapTransactionTypeRemoteToTransactionTypeLocal(type),
            fee
        )
    }
}

fun mapTransactionStatusRemoteToTransactionStatusLocal(statusRemote: TransactionRemote.Status): TransferTransactionLocal.Status {
    return when (statusRemote) {
        TransactionRemote.Status.COMMITTED -> TransferTransactionLocal.Status.COMMITTED
        TransactionRemote.Status.PENDING -> TransferTransactionLocal.Status.PENDING
        TransactionRemote.Status.REJECTED -> TransferTransactionLocal.Status.REJECTED
    }
}

fun mapTransactionTypeRemoteToTransactionTypeLocal(typeRemote: TransactionRemote.Type): TransferTransactionLocal.Type {
    return when (typeRemote) {
        TransactionRemote.Type.INCOMING -> TransferTransactionLocal.Type.INCOMING
        TransactionRemote.Type.OUTGOING -> TransferTransactionLocal.Type.OUTGOING
        TransactionRemote.Type.WITHDRAW -> TransferTransactionLocal.Type.WITHDRAW
        TransactionRemote.Type.REWARD -> TransferTransactionLocal.Type.REWARD
        TransactionRemote.Type.DEPOSIT -> TransferTransactionLocal.Type.DEPOSIT
    }
}

fun mapTransactionLocalToTransaction(transactionLocal: TransferTransactionLocal): Transaction {
    return with(transactionLocal) {
        Transaction(
            if (assetId == AssetHolder.SORA_XOR_ERC_20.id) {
                txHash
            } else {
                ""
            },
            if (assetId == AssetHolder.SORA_XOR.id) {
                txHash
            } else {
                ""
            },
            mapTransactionStatusLocalToTransactionStatus(status),
            assetId,
            myAddress,
            details,
            peerName,
            amount,
            timestamp,
            peerId,
            reason,
            mapTransactionTypeLocalToTransactionType(type),
            if (assetId == AssetHolder.SORA_XOR_ERC_20.id) {
                fee
            } else {
                BigDecimal.ZERO
            },
            if (assetId == AssetHolder.SORA_XOR.id) {
                fee
            } else {
                BigDecimal.ZERO
            }
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

fun mapTransactionTypeLocalToTransactionType(typeRemote: TransferTransactionLocal.Type): Transaction.Type {
    return when (typeRemote) {
        TransferTransactionLocal.Type.INCOMING -> Transaction.Type.INCOMING
        TransferTransactionLocal.Type.OUTGOING -> Transaction.Type.OUTGOING
        TransferTransactionLocal.Type.WITHDRAW -> Transaction.Type.WITHDRAW
        TransferTransactionLocal.Type.REWARD -> Transaction.Type.REWARD
        TransferTransactionLocal.Type.DEPOSIT -> Transaction.Type.DEPOSIT
    }
}

fun mapWithdrawTransactionLocalToTransaction(transactionLocal: WithdrawTransactionLocal, myAccountId: String, myEthAddress: String): Transaction {
    return with(transactionLocal) {
        Transaction(
            transactionLocal.transferTxHash,
            intentTxHash,
            mapWithdrawTransactionStatusLocalToTransactionStatus(status, transferAmount != BigDecimal.ZERO),
            AssetHolder.SORA_XOR_ERC_20.id,
            if (transferPeerId.isNullOrEmpty()) {
                myAccountId
            } else {
                myEthAddress
            },
            details,
            peerName,
            if (transferPeerId.isNullOrEmpty()) {
                withdrawAmount
            } else {
                transferAmount
            },
            timestamp,
            if (transferPeerId.isNullOrEmpty()) {
                peerId
            } else {
                transferPeerId
            },
            reason,
            if (transferPeerId.isNullOrEmpty()) {
                Transaction.Type.WITHDRAW
            } else {
                Transaction.Type.OUTGOING
            },
            minerFeeInEth,
            intentFee
        )
    }
}

fun mapWithdrawTransactionStatusLocalToTransactionStatus(statusLocal: WithdrawTransactionLocal.Status, hasTransfer: Boolean): Transaction.Status {
    return when (statusLocal) {
        WithdrawTransactionLocal.Status.INTENT_FAILED, WithdrawTransactionLocal.Status.CONFIRM_FAILED, WithdrawTransactionLocal.Status.TRANSFER_FAILED -> Transaction.Status.REJECTED
        WithdrawTransactionLocal.Status.INTENT_PENDING, WithdrawTransactionLocal.Status.CONFIRM_PENDING, WithdrawTransactionLocal.Status.TRANSFER_PENDING, WithdrawTransactionLocal.Status.INTENT_COMPLETED, WithdrawTransactionLocal.Status.INTENT_STARTED -> Transaction.Status.PENDING
        WithdrawTransactionLocal.Status.CONFIRM_COMPLETED ->
            if (hasTransfer) {
                Transaction.Status.PENDING
            } else {
                Transaction.Status.COMMITTED
            }
        WithdrawTransactionLocal.Status.TRANSFER_COMPLETED -> Transaction.Status.COMMITTED
    }
}

fun mapWithdrawTransactionLocalToWithdrawTransaction(transactionLocal: WithdrawTransactionLocal): WithdrawTransaction {
    return with(transactionLocal) {
        WithdrawTransaction(
            intentTxHash,
            confirmTxHash,
            transferTxHash,
            mapWithdrawTransactionStatusLocalToWithdrawTransactionStatus(status),
            details,
            peerName,
            withdrawAmount,
            transferAmount,
            timestamp,
            peerId,
            transferPeerId,
            reason,
            intentFee,
            gasLimit,
            gasPrice
        )
    }
}

fun mapWithdrawTransactionStatusLocalToWithdrawTransactionStatus(statusLocal: WithdrawTransactionLocal.Status): WithdrawTransaction.Status {
    return when (statusLocal) {
        WithdrawTransactionLocal.Status.INTENT_STARTED -> WithdrawTransaction.Status.INTENT_STARTED
        WithdrawTransactionLocal.Status.INTENT_PENDING -> WithdrawTransaction.Status.INTENT_PENDING
        WithdrawTransactionLocal.Status.INTENT_COMPLETED -> WithdrawTransaction.Status.INTENT_COMPLETED
        WithdrawTransactionLocal.Status.INTENT_FAILED -> WithdrawTransaction.Status.INTENT_FAILED
        WithdrawTransactionLocal.Status.CONFIRM_PENDING -> WithdrawTransaction.Status.CONFIRM_PENDING
        WithdrawTransactionLocal.Status.CONFIRM_FAILED -> WithdrawTransaction.Status.CONFIRM_FAILED
        WithdrawTransactionLocal.Status.CONFIRM_COMPLETED -> WithdrawTransaction.Status.CONFIRM_COMPLETED
        WithdrawTransactionLocal.Status.TRANSFER_PENDING -> WithdrawTransaction.Status.TRANSFER_PENDING
        WithdrawTransactionLocal.Status.TRANSFER_COMPLETED -> WithdrawTransaction.Status.TRANSFER_COMPLETED
        WithdrawTransactionLocal.Status.TRANSFER_FAILED -> WithdrawTransaction.Status.TRANSFER_FAILED
    }
}

fun mapWithdrawTransactionStatusToWithdrawTransactionStatusLocal(status: WithdrawTransaction.Status): WithdrawTransactionLocal.Status {
    return when (status) {
        WithdrawTransaction.Status.INTENT_STARTED -> WithdrawTransactionLocal.Status.INTENT_STARTED
        WithdrawTransaction.Status.INTENT_PENDING -> WithdrawTransactionLocal.Status.INTENT_PENDING
        WithdrawTransaction.Status.INTENT_COMPLETED -> WithdrawTransactionLocal.Status.INTENT_COMPLETED
        WithdrawTransaction.Status.INTENT_FAILED -> WithdrawTransactionLocal.Status.INTENT_FAILED
        WithdrawTransaction.Status.CONFIRM_PENDING -> WithdrawTransactionLocal.Status.CONFIRM_PENDING
        WithdrawTransaction.Status.CONFIRM_FAILED -> WithdrawTransactionLocal.Status.CONFIRM_FAILED
        WithdrawTransaction.Status.CONFIRM_COMPLETED -> WithdrawTransactionLocal.Status.CONFIRM_COMPLETED
        WithdrawTransaction.Status.TRANSFER_PENDING -> WithdrawTransactionLocal.Status.TRANSFER_PENDING
        WithdrawTransaction.Status.TRANSFER_COMPLETED -> WithdrawTransactionLocal.Status.TRANSFER_COMPLETED
        WithdrawTransaction.Status.TRANSFER_FAILED -> WithdrawTransactionLocal.Status.TRANSFER_FAILED
    }
}

fun mapDepositTransactionLocalToTransaction(transactionLocal: DepositTransactionLocal, myAccountId: String, myEthAddress: String): Transaction {
    return with(transactionLocal) {
        Transaction(
            transactionLocal.depositTxHash,
            transactionLocal.transferTxHash,
            mapDepositTransactionLocalStatusToTransactionStatus(status),
            AssetHolder.SORA_XOR_ERC_20.id,
            myEthAddress,
            details,
            peerName,
            partialAmount,
            timestamp,
            myAccountId,
            reason,
            Transaction.Type.DEPOSIT,
            depositFee,
            BigDecimal.ZERO
        )
    }
}

fun mapDepositTransactionLocalStatusToTransactionStatus(status: DepositTransactionLocal.Status): Transaction.Status {
    return when (status) {
        DepositTransactionLocal.Status.DEPOSIT_PENDING, DepositTransactionLocal.Status.DEPOSIT_RECEIVED, DepositTransactionLocal.Status.TRANSFER_PENDING -> Transaction.Status.PENDING
        DepositTransactionLocal.Status.DEPOSIT_FAILED, DepositTransactionLocal.Status.TRANSFER_FAILED -> Transaction.Status.REJECTED
        DepositTransactionLocal.Status.TRANSFER_COMPLETED, DepositTransactionLocal.Status.DEPOSIT_COMPLETED -> Transaction.Status.COMMITTED
    }
}

fun mapTransactionRemoteToDepositTransactionLocal(transactionRemote: TransactionRemote): DepositTransactionLocal {
    return with(transactionRemote) {
        DepositTransactionLocal(
            "0x$details",
            transactionId,
            mapTransactionStatusRemoteToDepositStatusLocal(status),
            assetId,
            details,
            peerName,
            amount,
            BigDecimal.ZERO,
            timestamp,
            peerId,
            reason,
            BigDecimal.ZERO,
            fee
        )
    }
}

private fun mapTransactionStatusRemoteToDepositStatusLocal(statusRemote: TransactionRemote.Status): DepositTransactionLocal.Status {
    return when (statusRemote) {
        TransactionRemote.Status.COMMITTED -> DepositTransactionLocal.Status.TRANSFER_COMPLETED
        TransactionRemote.Status.PENDING -> DepositTransactionLocal.Status.TRANSFER_PENDING
        TransactionRemote.Status.REJECTED -> DepositTransactionLocal.Status.TRANSFER_FAILED
    }
}