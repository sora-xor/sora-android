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
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.resourses.LanguagesHolder

class DateTimeFormatter(
    private val languagesHolder: LanguagesHolder,
    private val resourceManager: ResourceManager,
    private val context: Context,
    private val now: () -> Date = { Date() },
) {

    companion object {
        const val DD_MMM_YYYY = "dd MMM yyyy"
        const val DD_MMM_YYYY_HH_MM = "dd MMM. yyyy, HH:mm"
        const val HH_mm = "HH:mm"
        const val DD_MMMM = "dd MMMM"
    }

    fun formatDateTime(date: Date): String =
        DateUtils.getRelativeDateTimeString(context, date.time, DateUtils.SECOND_IN_MILLIS, 0, 0)
            .toString()

    fun formatDate(date: Date, dateFormat: String): String {
        return SimpleDateFormat(dateFormat, languagesHolder.getCurrentLocale()).format(date)
    }

    fun formatTimeWithoutSeconds(date: Date): String {
        return formatTime(date, HH_mm)
    }

    private fun formatTime(date: Date, pattern: String): String {
        return SimpleDateFormat(pattern, languagesHolder.getCurrentLocale()).format(date)
    }

    fun dateToDayWithoutCurrentYear(date: Date, todayStr: String, yesterdayStr: String): String {
        val currentDate = now()
        val pattern = if (areInCurrentYear(date, currentDate)) DD_MMMM else DD_MMM_YYYY
        return dateToDay(date, todayStr, yesterdayStr, pattern, currentDate)
    }

    private fun dateToDay(
        date: Date,
        todayStr: String,
        yesterdayStr: String,
        pattern: String,
        currentDate: Date,
    ): String {
        if (areInSameDay(date, currentDate)) return todayStr
        val yesterday = Calendar.getInstance().apply {
            time = currentDate
            add(Calendar.DAY_OF_YEAR, -1)
        }.time
        return if (areInSameDay(date, yesterday)) yesterdayStr else formatDate(date, pattern)
    }

    fun areInSameDay(date1: Date, date2: Date): Boolean {
        val cal = Calendar.getInstance().apply { time = date1 }
        val oldCal = Calendar.getInstance().apply { time = date2 }

        val oldYear = oldCal.get(Calendar.YEAR)
        val year = cal.get(Calendar.YEAR)
        val oldDay = oldCal.get(Calendar.DAY_OF_YEAR)
        val day = cal.get(Calendar.DAY_OF_YEAR)

        return oldYear == year && oldDay == day
    }

    private fun areInCurrentYear(date: Date, currentDate: Date): Boolean {
        val cal = Calendar.getInstance().apply { time = currentDate }
        val cal2 = Calendar.getInstance().apply { time = date }

        return cal.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }
}
