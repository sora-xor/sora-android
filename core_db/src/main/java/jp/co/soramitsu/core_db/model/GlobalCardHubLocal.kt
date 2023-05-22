/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "globalCardsHub"
)
data class GlobalCardHubLocal(
    @PrimaryKey val cardId: String,
    val visibility: Boolean,
    val sortOrder: Int,
    val collapsed: Boolean,
)
