/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
