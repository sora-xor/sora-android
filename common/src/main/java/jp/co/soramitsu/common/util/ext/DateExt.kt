/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Date.date2Day(todayStr: String, yesterdayStr: String): String {
    val newTime = Date()

    val cal = Calendar.getInstance().apply { time = newTime }
    val oldCal = Calendar.getInstance().apply { time = this@date2Day }

    val oldYear = oldCal.get(Calendar.YEAR)
    val year = cal.get(Calendar.YEAR)
    val oldDay = oldCal.get(Calendar.DAY_OF_YEAR)
    val day = cal.get(Calendar.DAY_OF_YEAR)

    if (oldYear == year) {
        return when (oldDay - day) {
            -1 -> yesterdayStr
            0 -> todayStr
            else -> this.formatDate()
        }
    }

    return this.formatDate()
}

const val dd_MMM_YYYY = "dd MMM yyyy"
const val HH_mm = "HH:mm"

fun Date.formatTime(): String {
    return SimpleDateFormat(HH_mm, Locale.ENGLISH).format(this)
}

fun Date.formatDate(): String {
    return SimpleDateFormat(dd_MMM_YYYY, Locale.ENGLISH).format(this)
}