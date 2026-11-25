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
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioCalculator
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioHolding
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioTotals
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.isTestnet
import kotlin.math.abs

/** A row's number (the amount held), and the smaller one under it (its value) if it has one. */
internal data class RowNumbers(val main: String, val detail: String?)

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

    /** A value as the app prints it. Dust is never printed as nothing: "<$0.01". */
    fun fiat(value: Double): String =
        if (value > 0.0 && value < SMALLEST_FIAT) {
            "<" + formatFiatAmount(SMALLEST_FIAT, fiatSymbol, numbers)
        } else {
            formatFiatAmount(value, fiatSymbol, numbers)
        }

    /** A total, or [NO_VALUE] while nothing counts towards it: a missing price is never a zero. */
    fun total(totals: PortfolioTotals): String = if (totals.known) fiat(totals.fiat) else NO_VALUE

    /**
     * Currency sign, whole part and cents (".55") for the hero total, where the cents are smaller.
     * Dust is never printed as nothing: "<$", "0", ".01".
     */
    fun fiatParts(value: Double): Triple<String, String, String> {
        if (value > 0.0 && value < SMALLEST_FIAT) return Triple("<$fiatSymbol", "0", "${DECIMAL_SEPARATOR}01")
        val text = numbers.format(value, FIAT_PRECISION)
        val dot = text.lastIndexOf(DECIMAL_SEPARATOR)
        return if (dot < 0) Triple(fiatSymbol, text, "") else Triple(fiatSymbol, text.substring(0, dot), text.substring(dot))
    }

    /**
     * What a row of [holdings] shows: the amount held is always the row's number, with its value
     * under it. Without a price, or on a testnet (no value at all), the amount stands alone: no
     * dash and no price note on every line while prices are out. A real price of nothing is "$0".
     */
    fun rowNumbers(holdings: List<PortfolioHolding>, total: BigDecimal, symbol: String): RowNumbers {
        val held = amount(total, symbol)
        val value = holdings.sumOf { PortfolioCalculator.countedFiat(it) ?: 0.0 }
        return when {
            value > 0.0 -> RowNumbers(held, fiat(value))
            holdings.all { it.network.isTestnet } ||
                holdings.any { it.token.price == null && !it.network.isTestnet } -> RowNumbers(held, null)
            else -> RowNumbers(held, fiat(0.0))
        }
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

    private fun percentNumber(value: Double, decimals: Int): String {
        val pattern = if (decimals <= 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        return DecimalFormat(pattern, symbols()).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    companion object {
        /** What stands for a value the app does not have. */
        const val NO_VALUE = "—"

        private const val SMALLEST_FIAT = 0.01
        private const val FIAT_PRECISION = 2
    }
}
