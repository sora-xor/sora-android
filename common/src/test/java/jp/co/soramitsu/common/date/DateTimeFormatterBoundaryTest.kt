package jp.co.soramitsu.common.date

import android.content.Context
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class DateTimeFormatterBoundaryTest {
    private val originalTimeZone = TimeZone.getDefault()
    private val languagesHolder = mock(LanguagesHolder::class.java)
    private val resourceManager = mock(ResourceManager::class.java)
    private val context = mock(Context::class.java)

    @Before
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        given(languagesHolder.getCurrentLocale()).willReturn(Locale.US)
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun date(value: String): Date = Date.from(Instant.parse(value))
    private fun formatter(now: String) = DateTimeFormatter(languagesHolder, resourceManager, context) { date(now) }

    @Test
    fun `only older or future dates outside the current year include the year`() {
        val formatter = formatter("2026-06-01T12:00:00Z")
        assertEquals("30 May", formatter.dateToDayWithoutCurrentYear(date("2026-05-30T12:00:00Z"), "Today", "Yesterday"))
        assertEquals("30 May 2025", formatter.dateToDayWithoutCurrentYear(date("2025-05-30T12:00:00Z"), "Today", "Yesterday"))
        assertEquals("02 January", formatter.dateToDayWithoutCurrentYear(date("2026-01-02T12:00:00Z"), "Today", "Yesterday"))
        assertEquals("01 Jan 2027", formatter.dateToDayWithoutCurrentYear(date("2027-01-01T12:00:00Z"), "Today", "Yesterday"))
    }

    @Test
    fun `a single clock snapshot decides both relative label and year display`() {
        var reads = 0
        val formatter = DateTimeFormatter(languagesHolder, resourceManager, context) {
            reads++
            date(if (reads == 1) "2025-12-31T23:59:59Z" else "2026-01-01T00:00:00Z")
        }
        assertEquals("Today", formatter.dateToDayWithoutCurrentYear(date("2025-12-31T12:00:00Z"), "Today", "Yesterday"))
        assertEquals(1, reads)
    }
}
