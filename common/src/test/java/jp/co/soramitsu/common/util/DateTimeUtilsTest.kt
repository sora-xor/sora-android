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

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DateTimeUtilsTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `same day number in different months is not the same day`() {
        assertFalse(DateTimeUtils.isSameDay(at(2026, 10, 24), at(2026, 9, 24)))
    }

    @Test
    fun `same month and day in different years is not the same day`() {
        assertFalse(DateTimeUtils.isSameDay(at(2026, 10, 24), at(2025, 10, 24)))
    }

    @Test
    fun `same month number in different years is not the same month`() {
        assertFalse(DateTimeUtils.isSameMonth(at(2026, 10, 24), at(2025, 10, 24)))
    }

    @Test
    fun `different hours within a day share both groups`() {
        val morning = at(2026, 10, 24, 1)
        val evening = at(2026, 10, 24, 23)
        assertTrue(DateTimeUtils.isSameDay(morning, evening))
        assertTrue(DateTimeUtils.isSameMonth(morning, evening))
    }

    @Test
    fun `different days within a month share only the month group`() {
        val first = at(2026, 10, 1)
        val last = at(2026, 10, 31)
        assertFalse(DateTimeUtils.isSameDay(first, last))
        assertTrue(DateTimeUtils.isSameMonth(first, last))
    }

    @Test
    fun `year boundary starts new day and month groups`() {
        val before = at(2025, 12, 31, 23)
        val after = at(2026, 1, 1)
        assertFalse(DateTimeUtils.isSameDay(before, after))
        assertFalse(DateTimeUtils.isSameMonth(before, after))
    }

    @Test
    fun `grouping follows the device time zone`() {
        val beforeMidnight = at(2026, 10, 31, 14)
        val afterMidnight = at(2026, 10, 31, 16)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertFalse(DateTimeUtils.isSameDay(beforeMidnight, afterMidnight))
        assertFalse(DateTimeUtils.isSameMonth(beforeMidnight, afterMidnight))

        val nextUtcDay = at(2026, 11, 1, 1)
        assertTrue(DateTimeUtils.isSameDay(afterMidnight, nextUtcDay))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis
}
