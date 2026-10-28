/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.util

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.OptionsProvider.nbspace
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NumbersFormatterTest {

    private val formatWithGroupingDelimeter = "1${nbspace}000${nbspace}000.27"
    private val formatAsInteger = "1${nbspace}000${nbspace}000"
    private val formatWithRounding = "1${nbspace}000${nbspace}000.27"

    lateinit var numbersFormatter: NumbersFormatter

    @Before
    fun setUp() {
        numbersFormatter = NumbersFormatter()
    }

    @Test
    fun `should calc the same`() {
        val toFormat = BigDecimal("0.00014")

        val expected = "0.00014"
        val actual = numbersFormatter.formatBigDecimal(toFormat, 18)

        assertEquals(expected, actual)
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
    fun `should hide decimals for zero decimal`() {
        val precision = 9

        val toFormat = BigDecimal(0.000007364243)

        val expected = "0.000007364"
        val actual = numbersFormatter.formatBigDecimal(toFormat, precision)

        assertEquals(expected, actual)
    }

    @Test
    fun `should hide decimals for zero decimal 2`() {
        val precision = 2

        val toFormat = BigDecimal(0.000007364243)

        val expected = "0"
        val actual = numbersFormatter.formatBigDecimal(toFormat, precision, false)

        assertEquals(expected, actual)
    }

    @Test
    fun `should hide decimals for zero decimal 3`() {
        val precision = 2

        val toFormat = BigDecimal(0.000007364243)

        val expected = "0.000007"
        val actual = numbersFormatter.formatBigDecimal(toFormat, precision)

        assertEquals(expected, actual)
    }

    @Test
    fun `should hide decimals for zero decimal 4`() {
        val precision = 2

        val toFormat = BigDecimal(-0.000007364243)

        val expected = "-0.000008"
        val actual = numbersFormatter.formatBigDecimal(toFormat, precision)

        assertEquals(expected, actual)
    }

    @Test
    fun `should hide decimals for zero decimal 5`() {
        val precision = 9

        val toFormat = BigDecimal(-0.000007364243)

        val expected = "-0.000007365"
        val actual = numbersFormatter.formatBigDecimal(toFormat, precision)

        assertEquals(expected, actual)
    }

    @Test
    fun `should format with arbitrary precision`() {
        val precision = 9

        val toFormat = BigDecimal(1_000_000.12345678912)

        val expected = "1${nbspace}000${nbspace}000.123456789"
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
    fun `format longer double currency round up string`() {
        val actual = numbersFormatter.format(0.276, 2, false)
        assertEquals("0.27", actual)
    }

    @Test
    fun `format longer double currency fraction false round up string`() {
        val actual = numbersFormatter.format(0.00003, 2, false)
        assertEquals("0", actual)
    }

    @Test
    fun `format longer double currency fraction true round up string`() {
        val actual = numbersFormatter.format(0.00003456, 2, true)
        assertEquals("0.00003", actual)
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
    @Test
    fun `small negative doubles retain their first significant fraction digit`() {
        assertEquals("-0.000008", numbersFormatter.format(-0.000007364243, 2, true))
        assertEquals("-0.00004", numbersFormatter.format(-0.00003456, 2, true))
        assertEquals("0.00003", numbersFormatter.format(0.00003456, 2, true))
        assertEquals("0", numbersFormatter.format(0.0, 2, true))
    }

    @Test
    fun `explicit precision still disables fraction expansion for small negative doubles`() {
        assertEquals("-0", numbersFormatter.format(-0.000007364243, 2, false))
        assertEquals("-0.000007365", numbersFormatter.format(-0.000007364243, 9, true))
    }

    @Test
    fun `fraction expansion preserves exact token base units across all supported scales`() {
        for (scale in 1..18) {
            val amount = BigDecimal.ONE.movePointLeft(scale)
            assertEquals(amount.toPlainString(), numbersFormatter.formatBigDecimal(amount, 2))
            assertEquals(amount.negate().toPlainString(), numbersFormatter.formatBigDecimal(amount.negate(), 2))
        }
    }
}
