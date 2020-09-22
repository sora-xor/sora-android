/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TransferTransactionLocal

class TransactionTypeConverter {

    @TypeConverter
    fun fromType(type: TransferTransactionLocal.Type): Int {
        return when (type) {
            TransferTransactionLocal.Type.INCOMING -> 0
            TransferTransactionLocal.Type.OUTGOING -> 1
            TransferTransactionLocal.Type.WITHDRAW -> 2
            TransferTransactionLocal.Type.REWARD -> 3
            TransferTransactionLocal.Type.DEPOSIT -> 4
        }
    }

    @TypeConverter
    fun toType(state: Int): TransferTransactionLocal.Type {
        return when (state) {
            0 -> TransferTransactionLocal.Type.INCOMING
            1 -> TransferTransactionLocal.Type.OUTGOING
            2 -> TransferTransactionLocal.Type.WITHDRAW
            3 -> TransferTransactionLocal.Type.REWARD
            else -> TransferTransactionLocal.Type.DEPOSIT
        }
    }
}