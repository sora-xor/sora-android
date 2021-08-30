package jp.co.soramitsu.common.util

import java.util.Calendar

object DateTimeUtils {
    fun isSameMonth(d1: Long, d2: Long): Boolean {
        val date1 = Calendar.getInstance().apply { timeInMillis = d1 }
        val date2 = Calendar.getInstance().apply { timeInMillis = d2 }
        return date1.get(Calendar.MONTH) == date2.get(Calendar.MONTH)
    }
    fun isSameDay(d1: Long, d2: Long): Boolean {
        val date1 = Calendar.getInstance().apply { timeInMillis = d1 }
        val date2 = Calendar.getInstance().apply { timeInMillis = d2 }
        return date1.get(Calendar.DAY_OF_MONTH) == date2.get(Calendar.DAY_OF_MONTH)
    }
}
