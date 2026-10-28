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
