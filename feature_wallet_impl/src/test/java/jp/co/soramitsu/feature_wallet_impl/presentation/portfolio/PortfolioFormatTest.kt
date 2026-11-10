package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiatAmount
import jp.co.soramitsu.common.domain.formatFiatChange
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.common.util.GROUPING_SEPARATOR
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioFixtures.MAIN
import jp.co.soramitsu.feature_wallet_impl.domain.portfolio.PortfolioTotals
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
    fun `a row with a value shows it over the amount`() {
        val priced = PortfolioFixtures.holding(MAIN, PortfolioFixtures.token(AssetHolder.XOR_ASSET_ID, "XOR", 2.0), "10")
        assertEquals(RowNumbers("$20", "10 XOR"), format.rowNumbers(listOf(priced), priced.total, "XOR"))
    }

    @Test
    fun `without a price the amount is the row's number, with nothing under it`() {
        val xor = PortfolioFixtures.token(AssetHolder.XOR_ASSET_ID, "XOR", null)
        val held = PortfolioFixtures.holding(MAIN, xor, "25000")
        assertEquals(RowNumbers("25${g}000 XOR", null), format.rowNumbers(listOf(held), held.total, "XOR"))
    }

    @Test
    fun `a testnet holding has no value at all, whatever the token's price`() {
        listOf(null, 2.0).forEach { price ->
            val xor = PortfolioFixtures.token(AssetHolder.XOR_ASSET_ID, "XOR", price)
            val held = PortfolioFixtures.holding(MAIN, xor, "25000", network = WalletNetworkId.TAIRA)
            assertEquals(
                RowNumbers(PortfolioFormat.NO_VALUE, "25${g}000 XOR"),
                format.rowNumbers(listOf(held), held.total, "XOR"),
            )
        }
    }

    @Test
    fun `a token's value on a mainnet is never lost to its testnet holding`() {
        val xor = PortfolioFixtures.token(AssetHolder.XOR_ASSET_ID, "XOR", 2.0)
        val main = PortfolioFixtures.holding(MAIN, xor, "10")
        val test = PortfolioFixtures.holding(MAIN, xor, "25000", network = WalletNetworkId.TAIRA)
        assertEquals(RowNumbers("$20", "10 XOR"), format.rowNumbers(listOf(main, test), main.total, "XOR"))
    }

    @Test
    fun `a real price of nothing is a real zero`() {
        val xor = PortfolioFixtures.token(AssetHolder.XOR_ASSET_ID, "XOR", 0.0)
        val held = PortfolioFixtures.holding(MAIN, xor, "10")
        assertEquals(RowNumbers("$0", "10 XOR"), format.rowNumbers(listOf(held), held.total, "XOR"))
    }

    @Test
    fun `a total without a counted holding is unknown, never a zero`() {
        val nothingPriced = PortfolioTotals(0.0, 0.0, false, hidden = 0, unpriced = 3, testnet = 1)
        assertEquals(PortfolioFormat.NO_VALUE, format.total(nothingPriced))
        val unread = PortfolioTotals.ZERO.copy(unread = 2)
        assertEquals(PortfolioFormat.NO_VALUE, format.total(unread))
        val counted = PortfolioTotals(12.5, 0.0, false, hidden = 0, unpriced = 1, testnet = 0, priced = 1)
        assertEquals("$12.5", format.total(counted))
    }

    @Test
    fun `dust is never printed as nothing`() {
        assertEquals("<$0.01", format.fiat(0.004))
        assertEquals("$0", format.fiat(0.0))
        assertEquals("$0.01", format.fiat(0.01))
        assertEquals(Triple("<$", "0", ".01"), format.fiatParts(0.004))
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
