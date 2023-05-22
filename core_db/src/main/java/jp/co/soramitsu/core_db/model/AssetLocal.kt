/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.math.BigDecimal

@Entity(
    tableName = "assets",
    primaryKeys = ["tokenId", "accountAddress"],
    foreignKeys = [
        ForeignKey(
            entity = TokenLocal::class,
            parentColumns = ["id"],
            childColumns = ["tokenId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = SoraAccountLocal::class,
            parentColumns = ["substrateAddress"],
            childColumns = ["accountAddress"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ]
)
data class AssetLocal(
    val tokenId: String,
    @ColumnInfo(index = true)
    val accountAddress: String,
    val displayAsset: Boolean,
    val position: Int,
    val free: BigDecimal,
    val reserved: BigDecimal,
    val miscFrozen: BigDecimal,
    val feeFrozen: BigDecimal,
    val bonded: BigDecimal,
    val redeemable: BigDecimal,
    val unbonding: BigDecimal,
    @ColumnInfo(defaultValue = "0") val visibility: Boolean = false,
)
