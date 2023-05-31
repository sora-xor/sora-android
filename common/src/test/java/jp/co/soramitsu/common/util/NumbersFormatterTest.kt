/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

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
}
