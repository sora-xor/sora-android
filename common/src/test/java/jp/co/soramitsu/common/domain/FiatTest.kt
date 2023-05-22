/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.util.NumbersFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FiatTest {

    private val nf: NumbersFormatter = NumbersFormatter()

    @Before
    fun setup() {

    }

    @After
    fun teardown() {

    }

    @Test
    fun test001() {
        val f = formatFiatChange(12.2394, nf)
        assertEquals("+1 223.94 %", f)
    }

    @Test
    fun test002() {
        val f = formatFiatChange(0.0004, nf)
        assertEquals("+0.04 %", f)
    }

    @Test
    fun test003() {
        val f = formatFiatChange(0.0001, nf)
        assertEquals("+0.01 %", f)
    }

    @Test
    fun test004() {
        val f = formatFiatChange(0.0000999999, nf)
        assertEquals("0 %", f)
    }
}