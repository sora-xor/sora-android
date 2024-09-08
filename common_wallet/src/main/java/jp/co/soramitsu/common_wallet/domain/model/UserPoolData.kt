/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    val kensetsuIncluded: BigDecimal?,
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
