/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.AssetLocal

class AssetStateConverter {

    @TypeConverter
    fun fromType(state: AssetLocal.State): Int {
        return when (state) {
            AssetLocal.State.NORMAL -> 0
            AssetLocal.State.ASSOCIATING -> 1
            AssetLocal.State.ERROR -> 2
            AssetLocal.State.UNKNOWN -> 3
        }
    }

    @TypeConverter
    fun toType(state: Int): AssetLocal.State {
        return when (state) {
            0 -> AssetLocal.State.NORMAL
            1 -> AssetLocal.State.ASSOCIATING
            2 -> AssetLocal.State.ERROR
            else -> AssetLocal.State.UNKNOWN
        }
    }
}