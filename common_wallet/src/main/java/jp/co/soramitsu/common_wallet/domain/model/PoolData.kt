/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.calcAmount
import jp.co.soramitsu.common.domain.calcFiat
import jp.co.soramitsu.common.domain.fiatChange

data class PoolData(
    val token: Token,
    val baseToken: Token,
    val basePooled: BigDecimal,
    val baseReserves: BigDecimal,
    val secondPooled: BigDecimal,
    val secondReserves: BigDecimal,
    val strategicBonusApy: Double?,
    val poolShare: Double,
    val totalIssuance: BigDecimal,
    val poolProvidersBalance: BigDecimal,
    val favorite: Boolean,
) {

    val fiatSymbol = token.fiatSymbol

    fun printFiat(): Pair<Double, Double>? {
        val f1 = baseToken.calcFiat(basePooled)
        val f2 = token.calcFiat(secondPooled)
        if (f1 == null || f2 == null) return null
        val change1 = baseToken.fiatPriceChange ?: return null
        val change2 = token.fiatPriceChange ?: return null
        val price1 = baseToken.fiatPrice ?: return null
        val price2 = token.fiatPrice ?: return null
        val newPoolFiat = f1 + f2
        val oldPoolFiat = calcAmount(price1 / (1 + change1), basePooled) +
            calcAmount(price2 / (1 + change2), secondPooled)
        val changePool = fiatChange(oldPoolFiat, newPoolFiat)
        return newPoolFiat to changePool
    }
}

val List<PoolData>.fiatSymbol: String
    get() {
        return getOrNull(0)?.fiatSymbol ?: OptionsProvider.fiatSymbol
    }
