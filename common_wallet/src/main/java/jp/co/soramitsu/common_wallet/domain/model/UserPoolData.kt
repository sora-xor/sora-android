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

package jp.co.soramitsu.common_wallet.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.calcAmount
import jp.co.soramitsu.common.domain.calcFiat
import jp.co.soramitsu.common.domain.fiatChange

data class CommonUserPoolData(
    val basic: BasicPoolData,
    val user: UserPoolData,
) {
    fun printFiat(): Pair<Double, Double>? = printFiatInternal(basic, user)
}

data class CommonPoolData(
    val basic: BasicPoolData,
    val user: UserPoolData?,
) {
    fun printFiat(): Pair<Double, Double>? = printFiatInternal(basic, user)
}

data class UserPoolData(
    val basePooled: BigDecimal,
    val targetPooled: BigDecimal,
    val poolShare: Double,
    val poolProvidersBalance: BigDecimal,
    val favorite: Boolean,
    val sort: Int,
)

val List<CommonUserPoolData>.fiatSymbol: String
    get() {
        return getOrNull(0)?.basic?.fiatSymbol ?: OptionsProvider.fiatSymbol
    }

private fun printFiatInternal(basic: BasicPoolData, user: UserPoolData?): Pair<Double, Double>? {
    if (user == null) return null
    val f1 = basic.baseToken.calcFiat(user.basePooled)
    val f2 = basic.targetToken.calcFiat(user.targetPooled)
    if (f1 == null || f2 == null) return null
    val change1 = basic.baseToken.fiatPriceChange ?: 0.0
    val change2 = basic.targetToken.fiatPriceChange ?: 0.0
    val price1 = basic.baseToken.fiatPrice ?: return null
    val price2 = basic.targetToken.fiatPrice ?: return null
    val newPoolFiat = f1 + f2
    val oldPoolFiat = calcAmount(price1 / (1 + change1), user.basePooled) +
        calcAmount(price2 / (1 + change2), user.targetPooled)
    val changePool = fiatChange(oldPoolFiat, newPoolFiat)
    return newPoolFiat to changePool
}

fun BasicPoolData.isFilterMatch(filter: String): Boolean {
    val t1 = targetToken.name.lowercase().contains(filter.lowercase()) ||
        targetToken.symbol.lowercase().contains(filter.lowercase()) ||
        targetToken.id.lowercase().contains(filter.lowercase())
    val t2 = baseToken.name.lowercase().contains(filter.lowercase()) ||
        baseToken.symbol.lowercase().contains(filter.lowercase()) ||
        baseToken.id.lowercase().contains(filter.lowercase())
    return t1 || t2
}
