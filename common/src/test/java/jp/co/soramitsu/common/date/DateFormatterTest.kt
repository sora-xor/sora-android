package jp.co.soramitsu.common.date

import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date
import java.util.Locale

@RunWith(MockitoJUnitRunner::class)
class DateTimeFormatterTest {

    private lateinit var dateTimeFormatter: DateTimeFormatter
    private val date = Date(0)

    @Before
    fun setup() {
        dateTimeFormatter = DateTimeFormatter(Locale.ENGLISH)
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
    fun `yesterday date to day formatting`() {
        val expectedResult = "Today"
        val yesterdayString = "Yesterday"
        val todayDate = Date()

        val actualResult = dateTimeFormatter.date2Day(todayDate, expectedResult, yesterdayString)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `today date to day formatting`() {
        val todayString = "Today"
        val expectedResult = "Yesterday"
        val yesterdayDate = Date(Date().time - 24 * 60 * 60 * 1000)

        val actualResult = dateTimeFormatter.date2Day(yesterdayDate, todayString, expectedResult)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `other date to day formatting`() {
        val todayString = "Today"
        val yesterdayString = "Yesterday"
        val expectedResult = "01 Jan 1970"

        val actualResult = dateTimeFormatter.date2Day(date, todayString, yesterdayString)

        assertEquals(expectedResult, actualResult)
    }
}