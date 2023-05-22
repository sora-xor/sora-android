/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "fiatTokenPrices",
    primaryKeys = ["tokenIdFiat", "currencyId"],
    foreignKeys = [
        ForeignKey(
            entity = TokenLocal::class,
            parentColumns = ["id"],
            childColumns = ["tokenIdFiat"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ]
)
data class FiatTokenPriceLocal(
    val tokenIdFiat: String,
    val currencyId: String,
    val fiatPrice: Double,
    val fiatPriceTime: Long,
    val fiatPricePrevH: Double,
    val fiatPricePrevHTime: Long,
    val fiatPricePrevD: Double,
    val fiatPricePrevDTime: Long,
    @ColumnInfo(defaultValue = "null") val fiatChange: Double? = null,
)
