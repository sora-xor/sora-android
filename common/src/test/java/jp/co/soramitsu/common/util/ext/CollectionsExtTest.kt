/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import org.junit.Test
import java.math.BigInteger

class CollectionsExtTest {

    @Test
    fun `calc trailing zeros of Int`() {
        val map = mutableMapOf<Int, String>()
        map[0] = "A"
        map[1] = "B"
        map[2] = "C"
        map[3] = "D"

        val expectedMap = mutableMapOf<String, Int>()
        expectedMap["A"] = 0
        expectedMap["B"] = 1
        expectedMap["C"] = 2
        expectedMap["D"] = 3

        assert(expectedMap == map.inverseMap())
    }

    @Test
    fun `sum by integer called`() {
        val list = listOf(BigInteger.ONE, BigInteger.TEN, BigInteger("2"))

        assert(BigInteger("13") == list.sumByBigInteger { it })
    }
}