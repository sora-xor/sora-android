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

package jp.co.soramitsu.common.date

import android.content.Context
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.eqNonNull
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.LanguagesHolder
import org.junit.Assert.assertEquals
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

        dateTimeFormatter = DateTimeFormatter(languagesHolder, resourceManager, context)
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
        val todayDate = Date()

        val actualResult =
            dateTimeFormatter.dateToDayWithYear(todayDate, expectedResult, yesterdayString)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `today date to day formatting with year`() {
        val todayString = "Today"
        val expectedResult = "Yesterday"
        val yesterdayDate = Date(Date().time - 24 * 60 * 60 * 1000)

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
        val todayDate = Date()

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
        val yesterdayDate = Date(Date().time - 24 * 60 * 60 * 1000)

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
