/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.util.NumbersFormatter

fun formatFiatAmount(
    value: Double,
    currencySymbol: String,
    nf: NumbersFormatter,
    checkFraction: Boolean = false,
) = "$currencySymbol%s".format(nf.format(value, 2, checkFraction))

fun formatFiatChange(value: Double, nf: NumbersFormatter) =
    String.format("%s%s %%", if (value >= 0.0001) "+" else "", nf.format(value * 100, 2)).trim()

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
        newValue / curValue - 1
    }

fun Token.calcFiat(amount: BigDecimal): Double? = fiatPrice?.let {
    calcAmount(it, amount)
}

fun Token.printFiat(amount: BigDecimal, nf: NumbersFormatter): String =
    formatFiat(calcFiat(amount) ?: 0.0, nf)

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
