package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TransferTransactionLocal

class TransactionStatusConverter {

    @TypeConverter
    fun fromType(type: TransferTransactionLocal.Status): Int {
        return when (type) {
            TransferTransactionLocal.Status.PENDING -> 0
            TransferTransactionLocal.Status.COMMITTED -> 1
            TransferTransactionLocal.Status.REJECTED -> 2
        }
    }

    @TypeConverter
    fun toType(state: Int): TransferTransactionLocal.Status {
        return when (state) {
            0 -> TransferTransactionLocal.Status.PENDING
            1 -> TransferTransactionLocal.Status.COMMITTED
            else -> TransferTransactionLocal.Status.REJECTED
        }
    }
}