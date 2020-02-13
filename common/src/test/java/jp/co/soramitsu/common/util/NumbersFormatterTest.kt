package jp.co.soramitsu.common.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class NumbersFormatterTest {

    private val formatWithGroupingDelimeter = "1 000 000.27"
    private val formatAsInteger = "1 000 000"
    private val formatWithUpRounding = "1 000 000.28"

    lateinit var numbersFormatter: NumbersFormatter

    @Before
    fun setUp() {
        numbersFormatter = NumbersFormatter()
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
        assertEquals(formatWithUpRounding, actual)
    }

    @Test
    fun `format long round floor bigdecimal`() {
        val actual = numbersFormatter.formatBigDecimal(BigDecimal(1000000.274))
        assertEquals(formatWithGroupingDelimeter, actual)
    }

    @Test
    fun `format long round up bigdecimal`() {
        val actual = numbersFormatter.formatBigDecimal(BigDecimal(1000000.276))
        assertEquals(formatWithUpRounding, actual)
    }

    @Test
    fun `format bigdecimal as integer`() {
        val actual = numbersFormatter.formatInteger(BigDecimal(1000000.27))
        assertEquals(formatAsInteger, actual)
    }
}