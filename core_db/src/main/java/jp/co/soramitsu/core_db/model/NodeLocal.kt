/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "nodes"
)
data class NodeLocal(
    @PrimaryKey val address: String,
    val chain: String,
    val name: String,
    val isDefault: Boolean,
    val isSelected: Boolean
)
