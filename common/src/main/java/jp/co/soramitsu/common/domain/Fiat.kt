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

package jp.co.soramitsu.common.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.OptionsProvider.nbspace
import jp.co.soramitsu.common.util.NumbersFormatter

fun formatFiatAmount(
    value: Double,
    currencySymbol: String,
    nf: NumbersFormatter,
    checkFraction: Boolean = false,
) = "$currencySymbol%s".format(nf.format(value, 2, checkFraction))

fun formatFiatChange(value: Double, nf: NumbersFormatter) =
    String.format("%s%s$nbspace%%", if (value >= 0.0001) "+" else "", nf.format(value * 100, 2)).trim()

fun List<Asset>.fiatSum(): Double =
    if (isNotEmpty()) map { it.fiat ?: 0.0 }.reduce { acc, d -> acc + d } else 0.0

fun List<Asset>.fiatSymbol(): String = getOrNull(0)?.token?.fiatSymbol ?: OptionsProvider.fiatSymbol

fun Asset.printFiat(
    nf: NumbersFormatter,
): String = fiat?.let { token.formatFiat(it, nf) } ?: ""

fun Token.printFiatChange(
    nf: NumbersFormatter,
): String = fiatPriceChange?.let { formatFiatChange(it, nf) } ?: ""

fun fiatChange(curValue: Double?, newValue: Double): Double =
    if (curValue == null || curValue == 0.0) {
        0.0
    } else {
        (newValue / curValue) - 1
    }

fun Token.calcFiat(amount: BigDecimal): Double? = fiatPrice?.let {
    calcAmount(it, amount)
}

fun Token.printFiat(amount: BigDecimal, nf: NumbersFormatter): String =
    formatFiat(calcFiat(amount) ?: 0.0, nf)

fun Token.printFiat(amount: String?): String? =
    amount?.let { "$fiatSymbol%s".format(it) }

fun Token.formatFiat(
    value: Double,
    nf: NumbersFormatter,
    checkFraction: Boolean = false,
) = formatFiatAmount(value, fiatSymbol.orEmpty(), nf, checkFraction)

fun Token.formatFiatOrEmpty(
    value: Double?,
    nf: NumbersFormatter,
    checkFraction: Boolean = false,
): String = formatFiatOr(value, nf, checkFraction) { "" }

fun Token.formatFiatOr(
    value: Double?,
    nf: NumbersFormatter,
    checkFraction: Boolean = false,
    default: () -> String,
): String =
    if (value == null) default() else formatFiat(value, nf, checkFraction)

fun printFiatSum(
    token1: Token,
    token2: Token,
    amount1: BigDecimal,
    amount2: BigDecimal,
    nf: NumbersFormatter
): String {
    val fiat1 = token1.calcFiat(amount1)
    val fiat2 = token2.calcFiat(amount2)
    if (fiat1 == null || fiat2 == null) return ""
    val sum = fiat1 + fiat2
    return token1.formatFiat(sum, nf)
}

fun calcAmount(rate: Double, amount: BigDecimal): Double = amount.toDouble() * rate

fun subtractFee(amount: BigDecimal, balance: BigDecimal, fee: BigDecimal?): BigDecimal {
    return if (fee == null) amount else if (amount < fee) {
        amount
    } else {
        if (amount + fee > balance) {
            amount - fee
        } else {
            amount
        }
    }
}
