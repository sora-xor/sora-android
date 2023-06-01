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

class TextFormatterTest {

    private val textWithDoubleSpace = " Word  Two "
    private val expectedTextWithDoubleSpace = "WT"
    private val textWithSpaceAtEndAndStartWords = " Word Two Three Four "
    private val expectedTextWithSpaceAtEndAndStartWords = "WF"
    private val textWithFourWords = "Word Two Three Four"
    private val expectedTextWithFourWords = "WF"
    private val textWithTwoWords = "Word Two"
    private val expectedTextWithTwoWords = "WT"
    private val textWithOneWord = "Word"
    private val expectedTextWithOneWord = "WW"
    private val emptyText = ""

    lateinit var textFormatter: TextFormatter

    @Before
    fun setUp() {
        textFormatter = TextFormatter()
    }

    @Test
    fun `get first letter from first and last word capitalized called`() {
        val result = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(textWithFourWords)
        assertEquals(expectedTextWithFourWords, result)

        val result1 = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(textWithTwoWords)
        assertEquals(expectedTextWithTwoWords, result1)

        val result2 = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(textWithOneWord)
        assertEquals(expectedTextWithOneWord, result2)

        val result3 = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(emptyText)
        assertEquals("", result3)

        val result4 = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(textWithDoubleSpace)
        assertEquals(expectedTextWithDoubleSpace, result4)

        val result5 = textFormatter.getFirstLetterFromFirstAndLastWordCapitalized(textWithSpaceAtEndAndStartWords)
        assertEquals(expectedTextWithSpaceAtEndAndStartWords, result5)
    }
}
