/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.util

import android.content.res.Resources
import jp.co.soramitsu.feature_main_impl.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val MONTH_DAY_YEAR_FORMAT = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
private val MONTH_DAY_FORMAT = SimpleDateFormat("MMMM dd", Locale.ENGLISH)
private val HH_mm = SimpleDateFormat("HH:mm", Locale.ENGLISH)

fun Date.formatToOpenProjectDate(resources: Resources): String {
    val diffInMillis = time - Date().time

    if (diffInMillis < 0) return ""

    val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)

    return if (diffInDays == 0L) {
        val diffInHours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
        if (diffInHours == 0L) {
            val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
            if (diffInMinutes == 0L) {
                val diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(diffInMillis)
                if (diffInSeconds == 0L) {
                    ""
                } else {
                    resources.getQuantityString(R.plurals.project_date_second_plurals, diffInSeconds.toInt(), diffInSeconds.toString())
                }
            } else {
                resources.getQuantityString(R.plurals.project_date_minute_plurals, diffInMinutes.toInt(), diffInMinutes.toString())
            }
        } else {
            resources.getQuantityString(R.plurals.project_date_hour_plurals, diffInHours.toInt(), diffInHours.toString())
        }
    } else {
        resources.getQuantityString(R.plurals.project_date_day_plurals, diffInDays.toInt(), diffInDays.toString())
    }
}

fun Date.formatToClosedProjectDate(resources: Resources): String {
    val diffInMillis = Date().time - time

    if (diffInMillis < 0) return ""

    return when (TimeUnit.MILLISECONDS.toDays(diffInMillis)) {
        0L -> {
            val today = resources.getString(R.string.common_today)
            resources.getString(R.string.project_ended_template, today)
        }
        1L -> {
            val yesterday = resources.getString(R.string.common_yesterday)
            resources.getString(R.string.project_ended_template, yesterday)
        }
        else -> {
            val currentCalendar = Calendar.getInstance()
            val projectCalendar = Calendar.getInstance()
            projectCalendar.timeInMillis = time
            if (currentCalendar.get(Calendar.YEAR) == projectCalendar.get(Calendar.YEAR)) {
                val dateStr = MONTH_DAY_FORMAT.format(time)
                resources.getString(R.string.project_ended_template, dateStr)
            } else {
                val dateStr = MONTH_DAY_YEAR_FORMAT.format(time)
                resources.getString(R.string.project_ended_template, dateStr)
            }
        }
    }
}