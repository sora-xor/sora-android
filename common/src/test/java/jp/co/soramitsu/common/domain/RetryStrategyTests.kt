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

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RetryStrategyTests {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Test
    fun `retry test 01`() = runTest {
        val retry = RetryStrategyBuilder.build()
        val res = retry.retryIf(
            3,
            { t -> t is IllegalArgumentException },
            { okTime1() },
        )
        assertEquals(12, res)
    }

    @Test
    fun `retry test 02`() = runTest {
        val retry = RetryStrategyBuilder.build()
        val res = retry.retryIf(
            3,
            { t -> t is IllegalArgumentException },
            { okTime2() },
        )
        assertEquals(2, res)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `retry test 03`() = runTest {
        val retry = RetryStrategyBuilder.build()
        retry.retryIf(
            3,
            { t -> t is IllegalArgumentException },
            { okTime3() },
        )
    }

    private fun okTime1(): Int {
        return 12
    }

    private var count2 = 0
    private fun okTime2(): Int {
        count2++
        if (count2 == 1) throw IllegalArgumentException("count2=$count2")
        return count2
    }

    private var count3 = 0
    private fun okTime3(): Int {
        count3++
        if (count3 < 5) throw IllegalArgumentException("count3=$count3")
        return count3
    }
}
