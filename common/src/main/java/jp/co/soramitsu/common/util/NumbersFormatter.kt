/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

class NumbersFormatter @Inject constructor() {

    companion object {
        private const val DECIMAL_PATTERN = "###,###.##"
        private const val TWO_DIGITS_PATTERN = "00"
    }

    fun format(num: Double): String {
        val formatter = DecimalFormat(DECIMAL_PATTERN)
        val decimalFormatSymbols = formatter.decimalFormatSymbols
        decimalFormatSymbols.groupingSeparator = ' '
        decimalFormatSymbols.decimalSeparator = '.'
        formatter.decimalFormatSymbols = decimalFormatSymbols
        return formatter.format(num)
    }

    fun format(num: BigDecimal, code: String): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        formatter.currency = Currency.getInstance(code)
        return formatter.format(num)
    }

    fun formatBigDecimal(num: BigDecimal): String {
        val formatter = DecimalFormat(DECIMAL_PATTERN)
        val decimalFormatSymbols = formatter.decimalFormatSymbols
        decimalFormatSymbols.groupingSeparator = ' '
        decimalFormatSymbols.decimalSeparator = '.'
        formatter.decimalFormatSymbols = decimalFormatSymbols
        return formatter.format(num)
    }

    fun formatInteger(num: BigDecimal): String {
        return formatBigDecimal(num.setScale(0, RoundingMode.FLOOR))
    }

    fun formatIntegerToTwoDigits(num: Int): String {
        val formatter = DecimalFormat(TWO_DIGITS_PATTERN)
        return formatter.format(num)
    }
}