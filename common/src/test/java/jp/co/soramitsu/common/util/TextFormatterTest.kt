/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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