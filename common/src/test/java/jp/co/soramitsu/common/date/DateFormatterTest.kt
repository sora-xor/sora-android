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

package jp.co.soramitsu.common.date

import android.content.Context
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.eqNonNull
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.LanguagesHolder
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class DateTimeFormatterTest {

    private lateinit var dateTimeFormatter: DateTimeFormatter
    private val date = Date(0)
    private val currentDate = Date.from(Instant.parse("2026-06-01T12:00:00Z"))
    private val originalTimeZone = TimeZone.getDefault()

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var languagesHolder: LanguagesHolder

    @Before
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        given(languagesHolder.getCurrentLocale()).willReturn(Locale.ENGLISH)

        dateTimeFormatter = DateTimeFormatter(languagesHolder, resourceManager, context) { currentDate }
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `dd MMMM date formatting`() {
        val expectedResult = "01 January"

        val actualResult = dateTimeFormatter.formatDate(date, DateTimeFormatter.DD_MMMM)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `dd MMM YYYY date formatting`() {
        val expectedResult = "01 Jan 1970"

        val actualResult = dateTimeFormatter.formatDate(date, DateTimeFormatter.DD_MMM_YYYY)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `yesterday date to day formatting with year`() {
        val expectedResult = "Today"
        val yesterdayString = "Yesterday"
        val todayDate = currentDate

        val actualResult =
            dateTimeFormatter.dateToDayWithYear(todayDate, expectedResult, yesterdayString)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `today date to day formatting with year`() {
        val todayString = "Today"
        val expectedResult = "Yesterday"
        val yesterdayDate = Date(currentDate.time - 24 * 60 * 60 * 1000)

        val actualResult =
            dateTimeFormatter.dateToDayWithYear(yesterdayDate, todayString, expectedResult)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `other date to day formatting with year`() {
        val todayString = "Today"
        val yesterdayString = "Yesterday"
        val expectedResult = "01 Jan 1970"

        val actualResult = dateTimeFormatter.dateToDayWithYear(date, todayString, yesterdayString)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `yesterday date to day formatting without year`() {
        val expectedResult = "Today"
        val yesterdayString = "Yesterday"
        val todayDate = currentDate

        val actualResult = dateTimeFormatter.dateToDayWithoutCurrentYear(
            todayDate,
            expectedResult,
            yesterdayString
        )

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `today date to day formatting without year`() {
        val todayString = "Today"
        val expectedResult = "Yesterday"
        val yesterdayDate = Date(currentDate.time - 24 * 60 * 60 * 1000)

        val actualResult = dateTimeFormatter.dateToDayWithoutCurrentYear(
            yesterdayDate,
            todayString,
            expectedResult
        )

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `other date to day formatting without year`() {
        val todayString = "Today"
        val yesterdayString = "Yesterday"
        val expectedResult = "01 Jan 1970"

        val actualResult =
            dateTimeFormatter.dateToDayWithoutCurrentYear(date, todayString, yesterdayString)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `are in same day called`() {
        val date1 = Date()
        val date2 = Date(0)
        val actualResult = dateTimeFormatter.areInSameDay(date1, date1)

        assertEquals(true, actualResult)

        val actualResult2 = dateTimeFormatter.areInSameDay(date1, date2)

        assertEquals(false, actualResult2)
    }

    @Test
    fun `format Time From Seconds called`() {
        given(
            resourceManager.getQuantityString(
                eqNonNull(R.plurals.common_hour),
                anyInt()
            )
        ).willReturn("hour")
        given(resourceManager.getString(R.string.common_min)).willReturn("min")
        given(resourceManager.getString(R.string.common_sec)).willReturn("sec")
        val seconds = 7400.toLong()
        val exptectedString = "2h:3m:20s"
        val actualResult = dateTimeFormatter.formatTimeFromSeconds(seconds)

        assertEquals(exptectedString, actualResult)
    }
}
