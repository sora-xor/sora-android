/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "assets")
data class AssetLocal(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
    val displayAsset: Boolean,
    val position: Int,
    val precision: Int,
    val isMintable: Boolean,
    val free: BigDecimal,
    val reserved: BigDecimal,
    val miscFrozen: BigDecimal,
    val feeFrozen: BigDecimal,
)
