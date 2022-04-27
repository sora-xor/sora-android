/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "pools",
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
data class PoolLocal(
    @PrimaryKey val assetId: String,
    val accountAddress: String,
    val reservesFirst: BigDecimal,
    val reservesSecond: BigDecimal,
    val totalIssuance: BigDecimal,
    val strategicBonusApy: BigDecimal?,
    val poolProvidersBalance: BigDecimal
)
