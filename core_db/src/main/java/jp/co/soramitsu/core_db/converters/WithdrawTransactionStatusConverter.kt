package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.WithdrawTransactionLocal

class WithdrawTransactionStatusConverter {

    @TypeConverter
    fun fromType(type: WithdrawTransactionLocal.Status): Int {
        return when (type) {
            WithdrawTransactionLocal.Status.INTENT_STARTED -> 0
            WithdrawTransactionLocal.Status.INTENT_PENDING -> 1
            WithdrawTransactionLocal.Status.INTENT_COMPLETED -> 2
            WithdrawTransactionLocal.Status.INTENT_FAILED -> 3
            WithdrawTransactionLocal.Status.CONFIRM_PENDING -> 4
            WithdrawTransactionLocal.Status.CONFIRM_FAILED -> 5
            WithdrawTransactionLocal.Status.CONFIRM_COMPLETED -> 6
            WithdrawTransactionLocal.Status.TRANSFER_PENDING -> 7
            WithdrawTransactionLocal.Status.TRANSFER_FAILED -> 8
            WithdrawTransactionLocal.Status.TRANSFER_COMPLETED -> 9
        }
    }

    @TypeConverter
    fun toType(state: Int): WithdrawTransactionLocal.Status {
        return when (state) {
            0 -> WithdrawTransactionLocal.Status.INTENT_STARTED
            1 -> WithdrawTransactionLocal.Status.INTENT_PENDING
            2 -> WithdrawTransactionLocal.Status.INTENT_COMPLETED
            3 -> WithdrawTransactionLocal.Status.INTENT_FAILED
            4 -> WithdrawTransactionLocal.Status.CONFIRM_PENDING
            5 -> WithdrawTransactionLocal.Status.CONFIRM_FAILED
            6 -> WithdrawTransactionLocal.Status.CONFIRM_COMPLETED
            7 -> WithdrawTransactionLocal.Status.TRANSFER_PENDING
            8 -> WithdrawTransactionLocal.Status.TRANSFER_FAILED
            else -> WithdrawTransactionLocal.Status.TRANSFER_COMPLETED
        }
    }
}