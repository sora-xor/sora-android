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
