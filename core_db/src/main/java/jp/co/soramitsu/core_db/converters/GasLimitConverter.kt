/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import java.math.BigInteger

class GasLimitConverter {

    @TypeConverter
    fun fromType(type: BigInteger): String {
        return type.toString()
    }

    @TypeConverter
    fun toType(state: String): BigInteger {
        return BigInteger(state)
    }
}