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

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.test_shared.MainCoroutineRule
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
