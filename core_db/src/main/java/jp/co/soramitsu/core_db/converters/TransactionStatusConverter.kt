/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicType

class ExtrinsicStatusConverter {

    @TypeConverter
    fun fromType(type: ExtrinsicStatus): Int {
        return when (type) {
            ExtrinsicStatus.PENDING -> 0
            ExtrinsicStatus.COMMITTED -> 1
            ExtrinsicStatus.REJECTED -> 2
        }
    }

    @TypeConverter
    fun toType(state: Int): ExtrinsicStatus {
        return when (state) {
            0 -> ExtrinsicStatus.PENDING
            1 -> ExtrinsicStatus.COMMITTED
            else -> ExtrinsicStatus.REJECTED
        }
    }
}

class ExtrinsicTypeConverter {

    @TypeConverter
    fun fromType(type: ExtrinsicType): Int {
        return when (type) {
            ExtrinsicType.SWAP -> 0
            ExtrinsicType.TRANSFER -> 1
            ExtrinsicType.ADD_REMOVE_LIQUIDITY -> 2
        }
    }

    @TypeConverter
    fun toType(state: Int): ExtrinsicType {
        return when (state) {
            0 -> ExtrinsicType.SWAP
            1 -> ExtrinsicType.TRANSFER
            else -> ExtrinsicType.ADD_REMOVE_LIQUIDITY
        }
    }
}
