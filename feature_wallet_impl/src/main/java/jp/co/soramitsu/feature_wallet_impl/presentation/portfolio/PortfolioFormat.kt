package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import jp.co.soramitsu.common.util.DECIMAL_SEPARATOR
import jp.co.soramitsu.common.util.GROUPING_SEPARATOR
import jp.co.soramitsu.common.util.NumbersFormatter
import kotlin.math.abs

/**
 * Number formatting for the dashboard, on the app's own conventions (NumbersFormatter grouping
 * and decimal separators, rounding down so a value is never overstated). Fiat always shows cents.
 */
internal class PortfolioFormat(
    private val numbers: NumbersFormatter,
    val fiatSymbol: String,
) {
    private val money = DecimalFormat("#,##0.00", symbols()).apply { roundingMode = RoundingMode.FLOOR }

    private fun symbols() = DecimalFormatSymbols(Locale.getDefault()).apply {
        groupingSeparator = GROUPING_SEPARATOR
        decimalSeparator = DECIMAL_SEPARATOR
    }

    fun fiat(value: Double): String = fiatSymbol + money.format(value)

    /** Currency sign, whole part and cents (".55") for the hero total, where the cents are smaller. */
    fun fiatParts(value: Double): Triple<String, String, String> {
        val text = money.format(value)
        val dot = text.lastIndexOf(DECIMAL_SEPARATOR)
        return if (dot < 0) Triple(fiatSymbol, text, "") else Triple(fiatSymbol, text.substring(0, dot), text.substring(dot))
    }

    /** "+$148.20" / "−$12.00": the sign is always shown. */
    fun signedFiat(value: Double): String = (if (value < 0) MINUS else "+") + fiatSymbol + money.format(abs(value))

    /** "+1.2%" with one decimal; the sign is always shown. */
    fun signedPercent(fraction: Double): String =
        (if (fraction < 0) MINUS else "+") + percentNumber(abs(fraction) * 100, 1) + "%"

    /** "1.2%" without a sign. */
    fun percent(fraction: Double, decimals: Int = 1): String = percentNumber(abs(fraction) * 100, decimals) + "%"

    /**
     * Share of the total as a whole percent, rounded as the fan rounds. A part is never shown as
     * nothing or as everything: "<1%" and ">99%".
     */
    fun share(fraction: Double): String {
        val percent = fraction * 100
        return when {
            fraction <= 0.0 -> "0%"
            percent < 1.0 -> "<1%"
            fraction >= 1.0 -> "100%"
            percent > 99.0 -> ">99%"
            else -> percentNumber(percent, 0) + "%"
        }
    }

    /** Ray count as a percentage (the fan's own rounding), "<1%" for a share without a ray. */
    fun rays(rays: Int, value: Double): String = if (rays == 0 && value > 0.0) "<1%" else "$rays%"

    /** Token amount with a precision that fits its size: "420 000", "0.8512", "0.000012". */
    fun amount(value: BigDecimal): String {
        val magnitude = value.abs()
        val precision = when {
            magnitude >= BigDecimal(1000) -> 2
            magnitude >= BigDecimal.ONE -> 4
            else -> 6
        }
        return numbers.formatBigDecimal(value, precision, checkFraction = true)
    }

    fun amount(value: BigDecimal, symbol: String): String = amount(value) + " " + symbol

    /** Short amount for the orbit: "420K", "1.2M", "0.85". */
    fun compact(value: BigDecimal): String {
        val v = value.abs().toDouble()
        val (scaled, suffix) = when {
            v >= 1e9 -> v / 1e9 to "B"
            v >= 1e6 -> v / 1e6 to "M"
            v >= 1e3 -> v / 1e3 to "K"
            else -> v to ""
        }
        val decimals = when {
            suffix.isEmpty() && v < 1 -> 4
            scaled >= 100 -> 0
            else -> 1
        }
        val pattern = if (decimals == 0) "#,##0" else "#,##0." + "#".repeat(decimals)
        val format = DecimalFormat(pattern, symbols()).apply { roundingMode = RoundingMode.FLOOR }
        return format.format(scaled) + suffix
    }

    private fun percentNumber(value: Double, decimals: Int): String {
        val pattern = if (decimals <= 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        return DecimalFormat(pattern, symbols()).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    companion object {
        const val MINUS = "−"
    }
}
