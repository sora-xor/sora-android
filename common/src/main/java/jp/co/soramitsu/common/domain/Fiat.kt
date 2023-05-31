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
