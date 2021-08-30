package jp.co.soramitsu.common.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class NumbersFormatterTest {

    private val formatWithGroupingDelimeter = "1 000 000.27"
    private val formatAsInteger = "1 000 000"
    private val formatWithRounding = "1 000 000.27"

    lateinit var numbersFormatter: NumbersFormatter

    @Before
    fun setUp() {
        numbersFormatter = NumbersFormatter()
    }

    @Test
    fun `should hide decimals for zero`() {
        val precision = 9

        val toFormat = BigDecimal(0)

        val expected = "0"
        val actual = numbersFormatter.formatBigDecimal(toFormat, precision)

        assertEquals(expected, actual)
    }

    @Test
    fun `should format with arbitrary precision`() {
        val precision = 9

        val toFormat = BigDecimal(1_000_000.12345678912)

        val expected = "1 000 000.123456789"
        val actual = numbersFormatter.formatBigDecimal(toFormat, precision)

        assertEquals(expected, actual)
    }

    @Test
    fun `format double string`() {
        val actual = numbersFormatter.format(1000000.27)
        assertEquals(formatWithGroupingDelimeter, actual)
    }

    @Test
    fun `format longer double round floor string`() {
        val actual = numbersFormatter.format(1000000.274)
        assertEquals(formatWithGroupingDelimeter, actual)
    }

    @Test
    fun `format longer double round up string`() {
        val actual = numbersFormatter.format(1000000.276)
        assertEquals(formatWithRounding, actual)
    }

    @Test
    fun `format long round floor bigdecimal`() {
        val actual = numbersFormatter.formatBigDecimal(BigDecimal(1000000.274))
        assertEquals(formatWithGroupingDelimeter, actual)
    }

    @Test
    fun `format long round up bigdecimal`() {
        val actual = numbersFormatter.formatBigDecimal(BigDecimal(1000000.276))
        assertEquals(formatWithRounding, actual)
    }

    @Test
    fun `format bigdecimal as integer`() {
        val actual = numbersFormatter.formatInteger(BigDecimal(1000000.27))
        assertEquals(formatAsInteger, actual)
    }
}