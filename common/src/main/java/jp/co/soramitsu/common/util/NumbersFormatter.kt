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

package jp.co.soramitsu.common.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.OptionsProvider.nbspace
import kotlin.math.absoluteValue
import kotlin.math.pow

private const val DECIMAL_PATTERN_BASE = "###,###."
private const val TWO_DIGITS_PATTERN = "00"

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
            if (nubs > 0 && nubs < 10.0.pow(-precision)) {
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
            } else {
                precision
            }
        } else {
            precision
        }
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
