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

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "allpools",
    primaryKeys = ["tokenIdBase", "tokenIdTarget"],
)
data class BasicPoolLocal(
    val tokenIdBase: String,
    val tokenIdTarget: String,
    val reserveBase: BigDecimal,
    val reserveTarget: BigDecimal,
    val totalIssuance: BigDecimal,
    val reservesAccount: String,
)

@Entity(
    tableName = "userpools",
    primaryKeys = ["userTokenIdBase", "userTokenIdTarget", "accountAddress"],
    indices = [Index(value = ["accountAddress"])],
    foreignKeys = [
        ForeignKey(
            entity = SoraAccountLocal::class,
            parentColumns = ["substrateAddress"],
            childColumns = ["accountAddress"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = BasicPoolLocal::class,
            parentColumns = ["tokenIdBase", "tokenIdTarget"],
            childColumns = ["userTokenIdBase", "userTokenIdTarget"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ]
)
data class UserPoolLocal(
    val userTokenIdBase: String,
    val userTokenIdTarget: String,
    val accountAddress: String,
    val poolProvidersBalance: BigDecimal,
    val favorite: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "poolBaseTokens"
)
data class PoolBaseTokenLocal(
    @PrimaryKey val tokenId: String,
    val dexId: Int,
)
