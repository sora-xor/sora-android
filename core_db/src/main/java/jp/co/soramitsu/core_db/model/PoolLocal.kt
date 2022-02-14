/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "pools",
)
data class PoolLocal(
    @PrimaryKey val assetId: String,
    val reservesFirst: BigDecimal,
    val reservesSecond: BigDecimal,
    val totalIssuance: BigDecimal,
    val poolProvidersBalance: BigDecimal
)
