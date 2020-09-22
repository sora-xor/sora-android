/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.GalleryItemTypeLocal

class GalleryItemTypeConverter {

    @TypeConverter
    fun fromType(type: GalleryItemTypeLocal): Int {
        return when (type) {
            GalleryItemTypeLocal.IMAGE -> 0
            GalleryItemTypeLocal.VIDEO -> 1
        }
    }

    @TypeConverter
    fun toType(state: Int): GalleryItemTypeLocal {
        return when (state) {
            0 -> GalleryItemTypeLocal.IMAGE
            else -> GalleryItemTypeLocal.VIDEO
        }
    }
}