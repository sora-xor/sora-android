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

package jp.co.soramitsu.common.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import jp.co.soramitsu.common.domain.OptionsProvider
import kotlin.math.absoluteValue
import kotlin.math.pow

private const val DECIMAL_PATTERN_BASE = "###,###."
private const val TWO_DIGITS_PATTERN = "00"
const val nbspace = ' '
const val euro = '€'

private const val DEFAULT_PRECISION = 2

val GROUPING_SEPARATOR: Char
    get() {
        return if (Locale.getDefault().toString() == "ar") {
            ','
        } else {
            nbspace
        }
    }

const val DECIMAL_SEPARATOR = '.'

private val precisionsMap: Map<Int, BigDecimal> = mapOf(
    0 to BigDecimal(1),
    1 to BigDecimal(0.1),
    2 to BigDecimal(0.01),
    3 to BigDecimal(0.001),
    4 to BigDecimal(0.0001),
    5 to BigDecimal(0.00001),
    6 to BigDecimal(0.000001),
    7 to BigDecimal(0.0000001),
    8 to BigDecimal(0.00000001),
    9 to BigDecimal(0.000000001),
    10 to BigDecimal(0.000000001),
    11 to BigDecimal(0.0000000001),
    12 to BigDecimal(0.00000000001),
    13 to BigDecimal(0.000000000001),
    14 to BigDecimal(0.0000000000001),
    15 to BigDecimal(0.00000000000001),
    16 to BigDecimal(0.000000000000001),
    17 to BigDecimal(0.0000000000000001),
    18 to BigDecimal(0.00000000000000001),
)

class NumbersFormatter {
    fun format(num: BigDecimal, code: String): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        formatter.currency = Currency.getInstance(code)

        return formatter.format(num)
    }

    fun format(
        num: Double,
        precision: Int = DEFAULT_PRECISION,
        checkFraction: Boolean = false,
    ): String {
        val newPrecision = if (checkFraction) {
            var nubs = num.absoluteValue
            if (num > 0 && nubs < 10.0.pow(-precision)) {
                var p = 1
                val scale = OptionsProvider.defaultScale
                while (p < scale) {
                    nubs *= 10
                    if (nubs > 1) {
                        break
                    }
                    p++
                }
                p.coerceAtLeast(precision)
            } else { precision }
        } else { precision }
        return decimalFormatterFor(patternWith(newPrecision)).format(num)
    }

    fun formatBigDecimal(
        num: BigDecimal,
        precision: Int = DEFAULT_PRECISION,
        checkFraction: Boolean = true
    ): String {
        val newPrecision = if (checkFraction) {
            var nubs = num.abs()
            if (nubs > BigDecimal.ZERO && nubs < precisionsMap[precision.coerceAtMost(18)]) {
                var p = 1
                val scale = nubs.scale()
                while (p < scale) {
                    nubs = nubs.movePointRight(1)
                    if (nubs > BigDecimal.ONE) {
                        break
                    }
                    p++
                }
                p.coerceAtLeast(precision)
            } else precision
        } else precision
        return decimalFormatterFor(patternWith(newPrecision)).format(num)
    }

    fun formatInteger(num: BigDecimal): String {
        return formatBigDecimal(num.setScale(0, RoundingMode.FLOOR))
    }

    fun formatIntegerToTwoDigits(num: Int): String {
        val formatter = DecimalFormat(TWO_DIGITS_PATTERN)
        return formatter.format(num)
    }

    private fun decimalFormatterFor(pattern: String): DecimalFormat {
        val symbols = DecimalFormatSymbols(Locale.getDefault())
        return DecimalFormat(pattern, symbols).apply {
            val symbols = decimalFormatSymbols
            symbols.groupingSeparator = GROUPING_SEPARATOR
            symbols.decimalSeparator = DECIMAL_SEPARATOR

            decimalFormatSymbols = symbols

            roundingMode = RoundingMode.FLOOR
            decimalFormatSymbols = decimalFormatSymbols
        }
    }

    private fun patternWith(precision: Int) = "$DECIMAL_PATTERN_BASE${"#".repeat(precision)}"

    fun getNumberFromString(string: String): Int {
        return string.trim().replace(GROUPING_SEPARATOR.toString(), "", ignoreCase = false)
            .toIntOrNull() ?: 0
    }
}
