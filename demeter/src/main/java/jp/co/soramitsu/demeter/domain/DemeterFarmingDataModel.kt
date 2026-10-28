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

package jp.co.soramitsu.demeter.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.StringTriple

data class DemeterFarmingPool(
    val tokenBase: Token,
    val tokenTarget: Token,
    val tokenReward: Token,
    val apr: Double,
    val amount: BigDecimal,
    val amountReward: BigDecimal,
)

data class DemeterFarmingBasicPool(
    val tokenBase: Token,
    val tokenTarget: Token,
    val tokenReward: Token,
    val apr: Double,
    val tvl: BigDecimal,
    val fee: Double,
)

fun DemeterFarmingBasicPool.isFilterMatch(filter: String): Boolean {
    val t1 = tokenBase.name.lowercase().contains(filter.lowercase()) ||
        tokenBase.symbol.lowercase().contains(filter.lowercase()) ||
        tokenBase.id.lowercase().contains(filter.lowercase())
    val t2 = tokenTarget.name.lowercase().contains(filter.lowercase()) ||
        tokenTarget.symbol.lowercase().contains(filter.lowercase()) ||
        tokenTarget.id.lowercase().contains(filter.lowercase())
    val t3 = tokenReward.name.lowercase().contains(filter.lowercase()) ||
        tokenReward.symbol.lowercase().contains(filter.lowercase()) ||
        tokenReward.id.lowercase().contains(filter.lowercase())
    return t1 || t2 || t3
}

fun DemeterFarmingBasicPool.ids() = StringTriple(this.tokenBase.id, this.tokenTarget.id, this.tokenReward.id)
