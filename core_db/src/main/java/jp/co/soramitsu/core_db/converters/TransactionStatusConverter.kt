/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TransactionLocal

class TransactionStatusConverter {

    @TypeConverter
    fun fromType(type: TransactionLocal.Status): Int {
        return when (type) {
            TransactionLocal.Status.PENDING -> 0
            TransactionLocal.Status.COMMITTED -> 1
            TransactionLocal.Status.REJECTED -> 2
        }
    }

    @TypeConverter
    fun toType(state: Int): TransactionLocal.Status {
        return when (state) {
            0 -> TransactionLocal.Status.PENDING
            1 -> TransactionLocal.Status.COMMITTED
            else -> TransactionLocal.Status.REJECTED
        }
    }
}