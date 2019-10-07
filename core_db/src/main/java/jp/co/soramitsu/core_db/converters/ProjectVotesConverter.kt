/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import java.math.BigDecimal

class ProjectVotesConverter {

    @TypeConverter
    fun fromType(type: BigDecimal): String {
        return type.toString()
    }

    @TypeConverter
    fun toType(state: String): BigDecimal {
        return BigDecimal(state)
    }
}