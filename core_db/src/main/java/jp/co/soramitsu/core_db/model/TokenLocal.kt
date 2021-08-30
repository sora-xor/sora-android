/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tokens")
data class TokenLocal(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isMintable: Boolean,
    val whitelistName: String,
    val isHidable: Boolean,
)
