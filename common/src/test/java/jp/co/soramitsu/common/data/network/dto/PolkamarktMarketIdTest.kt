package jp.co.soramitsu.common.data.network.dto

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PolkamarktMarketIdTest {

    @Test
    fun `runtime u32 maximum is preserved as exact scale integer`() {
        assertEquals(
            BigInteger("4294967295"),
            PolkamarktMarketId.toScale(PolkamarktMarketId.MAX_VALUE),
        )
    }

    @Test
    fun `runtime dto retains the unsigned maximum as long`() {
        val quote = PolkamarktBuyQuoteDto(
            marketId = PolkamarktMarketId.MAX_VALUE,
            outcome = "Yes",
            collateralIn = BigInteger.ONE,
            feeAmount = BigInteger.ZERO,
            pricingCollateral = BigInteger.ONE,
            sharesOut = BigInteger.ONE,
        )

        assertEquals(4_294_967_295L, quote.marketId)
    }

    @Test
    fun `values outside runtime u32 fail before encoding`() {
        val negative = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktMarketId.toScale(-1L)
        }
        assertEquals("POLKAMARKT_INVALID_MARKET_ID", negative.message)

        val overflow = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktMarketId.toScale(PolkamarktMarketId.MAX_VALUE + 1L)
        }
        assertEquals("POLKAMARKT_INVALID_MARKET_ID", overflow.message)

        val dtoOverflow = assertThrows(IllegalArgumentException::class.java) {
            PolkamarktBuyQuoteDto(
                marketId = PolkamarktMarketId.MAX_VALUE + 1L,
                outcome = "Yes",
                collateralIn = BigInteger.ONE,
                feeAmount = BigInteger.ZERO,
                pricingCollateral = BigInteger.ONE,
                sharesOut = BigInteger.ONE,
            )
        }
        assertEquals("POLKAMARKT_INVALID_MARKET_ID", dtoOverflow.message)
    }

    @Test
    fun `pending journal asset identity is exact and operation scoped`() {
        val canonical = PolkamarktPendingAssetId.encode(
            PolkamarktMarketId.MAX_VALUE,
            "CREATOR_CLAIM",
        )
        assertEquals(
            "polkamarkt:4294967295:CREATOR_CLAIM",
            canonical,
        )
        assertEquals(
            PolkamarktPendingAssetId.Parsed(
                PolkamarktMarketId.MAX_VALUE,
                "CREATOR_CLAIM",
            ),
            PolkamarktPendingAssetId.parse(canonical),
        )
        listOf(
            "polkamarkt::BUY",
            "polkamarkt:1:",
            "polkamarkt:1:buy",
            "polkamarkt:01:BUY",
            "polkamarkt:4294967296:BUY",
            "polkamarkt:1:RESOLVE",
            "polkamarkt:1:BUY:extra",
        ).forEach { assertNull(PolkamarktPendingAssetId.parse(it)) }
    }
}
