package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.formatFiatChange
import jp.co.soramitsu.common.util.GROUPING_SEPARATOR
import jp.co.soramitsu.common.util.NumbersFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dashboard's numbers are the app's own: the same text the asset list shows for a value. */
class PortfolioFormatTest {

    private val numbers = NumbersFormatter()
    private val format = PortfolioFormat(numbers, "$")
    private val g = GROUPING_SEPARATOR

    @Test
    fun `fiat amounts are the app's fiat format`() {
        listOf(12_019.94, 3_150.0, 0.5, 0.0, 1_234_567.891).forEach {
            assertEquals(formatFiatAmount(it, "$", numbers), format.fiat(it))
        }
        assertEquals("$12${g}019.94", format.fiat(12_019.94))
        assertEquals("$3${g}150", format.fiat(3_150.0))
        assertEquals(Triple("$", "12${g}019", ".94"), format.fiatParts(12_019.94))
        assertEquals(Triple("$", "3${g}150", ""), format.fiatParts(3_150.0))
        assertEquals("+$171.55", format.signedFiat(171.55))
        assertEquals("-$12.4", format.signedFiat(-12.4))
    }

    @Test
    fun `token amounts and changes are the asset list's`() {
        val amount = BigDecimal("420000.123456789")
        assertEquals(numbers.formatBigDecimal(amount, AssetHolder.ROUNDING), format.amount(amount))
        assertEquals("420${g}000 XOR", format.amount(BigDecimal("420000"), "XOR"))
        assertEquals(formatFiatChange(0.034, numbers), format.signedPercent(0.034))
        assertEquals(formatFiatChange(-0.008, numbers), format.signedPercent(-0.008))
    }

    @Test
    fun `orbit amounts are short and never rounded up`() {
        assertEquals("570K", format.compact(BigDecimal("570000")))
        assertEquals("25K", format.compact(BigDecimal("25000")))
        assertEquals("1.2M", format.compact(BigDecimal("1299999")))
        assertEquals("999", format.compact(BigDecimal("999.99")))
    }

    @Test
    fun `shares never show a part as none or as all`() {
        assertEquals("<1%", format.share(0.004))
        assertEquals("94%", format.share(0.9371))
        assertEquals(">99%", format.share(0.996))
        assertEquals("100%", format.share(1.0))
        assertEquals("<1%", format.rays(0, 3.0))
        assertEquals("26%", format.rays(26, 3_150.0))
    }
}
