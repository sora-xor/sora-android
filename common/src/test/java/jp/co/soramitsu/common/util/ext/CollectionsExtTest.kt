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

package jp.co.soramitsu.common.util.ext

import java.math.BigInteger
import org.junit.Test

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
