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

class DeciminalFormatter {
    companion object {
        val PATTERN = "###.##"
        @JvmStatic
        fun format(num: Double): String {
            val formatter = DecimalFormat(PATTERN)
            val decimalFormatSymbols = formatter.decimalFormatSymbols
            decimalFormatSymbols.groupingSeparator = ' '
            decimalFormatSymbols.decimalSeparator = '.'
            formatter.decimalFormatSymbols = decimalFormatSymbols
            return formatter.format(num)
        }

        @JvmStatic
        fun format(num: BigDecimal, code: String): String {
            val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
            formatter.currency = Currency.getInstance(code)
            return formatter.format(num)
        }

        @JvmStatic
        fun formatBigDecimal(num: BigDecimal): String {
            val formatter = DecimalFormat(PATTERN)
            val decimalFormatSymbols = formatter.decimalFormatSymbols
            decimalFormatSymbols.groupingSeparator = ' '
            decimalFormatSymbols.decimalSeparator = '.'
            formatter.decimalFormatSymbols = decimalFormatSymbols
            return formatter.format(num)
        }

        @JvmStatic
        fun formatInteger(num: BigDecimal): String {
            return formatBigDecimal(num.setScale(0, RoundingMode.FLOOR))
        }
    }
}