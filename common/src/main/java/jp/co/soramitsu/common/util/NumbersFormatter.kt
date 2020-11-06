package jp.co.soramitsu.common.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val DECIMAL_PATTERN_BASE = "###,###."
private const val TWO_DIGITS_PATTERN = "00"

private const val DEFAULT_PRECISION = 2

private const val GROUPING_SEPARATOR = ' '
private const val DECIMAL_SEPARATOR = '.'

class NumbersFormatter {
    fun format(num: BigDecimal, code: String): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        formatter.currency = Currency.getInstance(code)

        return formatter.format(num)
    }

    fun format(num: Double, precision: Int = DEFAULT_PRECISION): String {
        return decimalFormatterFor(patternWith(precision))
            .format(num)
    }

    fun formatBigDecimal(num: BigDecimal, precision: Int = DEFAULT_PRECISION): String {
        return decimalFormatterFor(patternWith(precision)).format(num)
    }

    fun formatInteger(num: BigDecimal): String {
        return formatBigDecimal(num.setScale(0, RoundingMode.FLOOR))
    }

    fun formatIntegerToTwoDigits(num: Int): String {
        val formatter = DecimalFormat(TWO_DIGITS_PATTERN)
        return formatter.format(num)
    }

    private fun decimalFormatterFor(pattern: String): DecimalFormat {
        return DecimalFormat(pattern).apply {
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
        return string.trim().replace(GROUPING_SEPARATOR.toString(), "", ignoreCase = false).toIntOrNull() ?: 0
    }
}