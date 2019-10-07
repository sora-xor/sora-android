/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.TransactionLocal

class TransactionTypeConverter {

    @TypeConverter
    fun fromType(type: TransactionLocal.Type): Int {
        return when (type) {
            TransactionLocal.Type.INCOMING -> 0
            TransactionLocal.Type.OUTGOING -> 1
            TransactionLocal.Type.WITHDRAW -> 2
            TransactionLocal.Type.REWARD -> 3
        }
    }

    @TypeConverter
    fun toType(state: Int): TransactionLocal.Type {
        return when (state) {
            0 -> TransactionLocal.Type.INCOMING
            1 -> TransactionLocal.Type.OUTGOING
            2 -> TransactionLocal.Type.WITHDRAW
            else -> TransactionLocal.Type.REWARD
        }
    }
}