package jp.co.soramitsu.common.util

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceMapperTest {
    @Test
    fun `decoded balances keep canonical scale for equality and persistence`() {
        for (precision in listOf(0, 6, 18, 23, 255)) {
            val unit = BigInteger.TEN.pow(precision)
            assertEquals(BigDecimal.ZERO, mapBalance(BigInteger.ZERO, precision))
            assertEquals(BigDecimal.ONE, mapBalance(unit, precision))
            assertEquals(BigDecimal("100"), mapBalance(unit * BigInteger("100"), precision))
            if (precision >= 2) {
                val fractional = BigInteger("123") * BigInteger.TEN.pow(precision - 2)
                assertEquals(BigDecimal("1.23"), mapBalance(fractional, precision))
            }
        }
    }

    @Test
    fun `one base unit is exact across every unsigned byte token precision`() {
        for (precision in 0..255) {
            val expected = BigDecimal(BigInteger.ONE, precision)
            assertEquals("precision $precision", 0, expected.compareTo(mapBalance(BigInteger.ONE, precision)))
        }
    }

    @Test
    fun `one whole token encodes exactly above double integer precision`() {
        for (precision in listOf(0, 6, 9, 18, 22, 23, 24, 38, 255)) {
            assertEquals("precision $precision", BigInteger.TEN.pow(precision), mapBalance(BigDecimal.ONE, precision))
        }
    }

    @Test
    fun `large chain balances round trip without a floating point intermediate`() {
        val raw = BigInteger("340282366920938463463374607431768211455")
        for (precision in listOf(0, 6, 9, 18, 23, 38, 255)) {
            val amount = mapBalance(raw, precision)
            assertEquals("precision $precision", raw, mapBalance(amount, precision))
            assertEquals(0, BigDecimal(raw, precision).compareTo(amount))
        }
    }

    @Test
    fun `fractional token balances preserve every declared base unit`() {
        val amount = BigDecimal("12345678901234567890.1234567890123456789012345")
        val expected = BigInteger("123456789012345678901234567890123456789012345")
        assertEquals(expected, mapBalance(amount, 25))
        assertEquals(0, amount.compareTo(mapBalance(expected, 25)))
    }

    @Test
    fun `sub base unit dust is truncated toward zero consistently`() {
        assertEquals(BigInteger("123"), mapBalance(BigDecimal("1.239"), 2))
        assertEquals(BigInteger("-123"), mapBalance(BigDecimal("-1.239"), 2))
        assertEquals(BigInteger.ZERO, mapBalance(BigDecimal("0.0000000000000000001"), 18))
    }

    @Test
    fun `zero and negative signed balances keep their exact value`() {
        for (precision in listOf(0, 18, 23, 255)) {
            assertEquals(BigInteger.ZERO, mapBalance(BigDecimal.ZERO, precision))
            val raw = BigInteger("-9007199254740993")
            assertEquals(0, BigDecimal(raw, precision).compareTo(mapBalance(raw, precision)))
            assertEquals(raw, mapBalance(mapBalance(raw, precision), precision))
        }
    }
}
