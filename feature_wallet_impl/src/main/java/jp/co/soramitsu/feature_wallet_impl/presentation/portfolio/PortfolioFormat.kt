package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.formatFiatChange
import jp.co.soramitsu.common.util.DECIMAL_SEPARATOR
import jp.co.soramitsu.common.util.GROUPING_SEPARATOR
import jp.co.soramitsu.common.util.NumbersFormatter
import kotlin.math.abs

/**
 * Numbers on the dashboard come from the app's own formatters, so they read exactly as on the
 * asset list and asset details in the same build and locale: fiat through formatFiatAmount (the
 * app's grouping separator, a no-break space outside Arabic, at most two decimals, rounded down),
 * token amounts through NumbersFormatter.formatBigDecimal with the asset list's precision, and
 * 24 h changes through formatFiatChange ("+3.37 %").
 */
internal class PortfolioFormat(
    private val numbers: NumbersFormatter,
    val fiatSymbol: String,
) {
    private fun symbols() = DecimalFormatSymbols(Locale.getDefault()).apply {
        groupingSeparator = GROUPING_SEPARATOR
        decimalSeparator = DECIMAL_SEPARATOR
    }

    fun fiat(value: Double): String = formatFiatAmount(value, fiatSymbol, numbers)

    /** Currency sign, whole part and cents (".55") for the hero total, where the cents are smaller. */
    fun fiatParts(value: Double): Triple<String, String, String> {
        val text = numbers.format(value, FIAT_PRECISION)
        val dot = text.lastIndexOf(DECIMAL_SEPARATOR)
        return if (dot < 0) Triple(fiatSymbol, text, "") else Triple(fiatSymbol, text.substring(0, dot), text.substring(dot))
    }

    /** "+$148.2" / "-$12": the sign is always shown. */
    fun signedFiat(value: Double): String = (if (value < 0) "-" else "+") + fiat(abs(value))

    /** A 24 h change as the app shows it: "+3.37 %", "-0.8 %". */
    fun signedPercent(fraction: Double): String = formatFiatChange(fraction, numbers)

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

    /** A token amount as the asset list prints it (its precision, rounded down). */
    fun amount(value: BigDecimal): String = numbers.formatBigDecimal(value, AssetHolder.ROUNDING)

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

    private companion object {
        const val FIAT_PRECISION = 2
    }
}
