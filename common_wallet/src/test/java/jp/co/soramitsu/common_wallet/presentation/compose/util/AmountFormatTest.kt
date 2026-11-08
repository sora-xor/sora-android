package jp.co.soramitsu.common_wallet.presentation.compose.util

import java.math.BigDecimal
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.test_data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The balance line of the swap, liquidity, send and request token cards. A token without a fiat
 * price (none in the price feed, or the feed is down) showed "0 ()".
 */
class AmountFormatTest {

    private val formatter = NumbersFormatter()

    @Test
    fun `a token without a fiat price shows its balance without empty parentheses`() {
        val noPrice = TestAssets.xorAsset(BigDecimal("12.5"))

        assertEquals("12.5", AmountFormat.getAssetBalanceText(noPrice, formatter, 3))
        assertEquals("0", AmountFormat.getAssetBalanceText(TestAssets.valAsset(), formatter, 3))
    }

    @Test
    fun `a token with a fiat price shows the fiat value in parentheses`() {
        val priced = TestAssets.xorAsset(BigDecimal("12.5")).let {
            it.copy(token = it.token.copy(fiatPrice = 2.0, fiatSymbol = "$"))
        }

        assertEquals("12.5 ($25)", AmountFormat.getAssetBalanceText(priced, formatter, 3))
    }

    @Test
    fun `an empty or blank fiat value is left out`() {
        assertEquals("7", AmountFormat.withFiat("7", ""))
        assertEquals("7", AmountFormat.withFiat("7", " "))
        assertEquals("7 ($1)", AmountFormat.withFiat("7", "$1"))
    }
}
