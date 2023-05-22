/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "cardsHub",
    primaryKeys = ["cardId", "accountAddress"],
    foreignKeys = [
        ForeignKey(
            entity = SoraAccountLocal::class,
            parentColumns = ["substrateAddress"],
            childColumns = ["accountAddress"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ]
)
data class CardHubLocal(
    val cardId: String,
    @ColumnInfo(index = true) val accountAddress: String,
    val visibility: Boolean,
    val sortOrder: Int,
    val collapsed: Boolean,
)
