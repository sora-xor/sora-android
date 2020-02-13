package jp.co.soramitsu.common.date

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.Calendar

class DateTimeFormatter(
    private val locale: Locale
) {

    companion object {
        const val DD_MMM_YYYY = "dd MMM yyyy"
        const val HH_mm = "HH:mm"
        const val DD_MMMM = "dd MMMM"
    }

    fun formatDate(date: Date, dateFormat: String): String {
        return SimpleDateFormat(dateFormat, locale).format(date)
    }

    fun formatTime(date: Date): String {
        return SimpleDateFormat(HH_mm, locale).format(date)
    }

    fun date2Day(date: Date, todayStr: String, yesterdayStr: String): String {
        val newTime = Date()

        val cal = Calendar.getInstance().apply { time = newTime }
        val oldCal = Calendar.getInstance().apply { time = date }

        val oldYear = oldCal.get(Calendar.YEAR)
        val year = cal.get(Calendar.YEAR)
        val oldDay = oldCal.get(Calendar.DAY_OF_YEAR)
        val day = cal.get(Calendar.DAY_OF_YEAR)

        if (oldYear == year) {
            return when (oldDay - day) {
                -1 -> yesterdayStr
                0 -> todayStr
                else -> this.formatDate(date, DD_MMM_YYYY)
            }
        }

        return this.formatDate(date, DD_MMM_YYYY)
    }
}