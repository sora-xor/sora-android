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
    fun `December 31 is yesterday on January 1 with and without a displayed year`() {
        val formatter = formatter("2026-01-01T00:30:00Z")
        val yesterday = date("2025-12-31T23:45:00Z")
        assertEquals("Yesterday", formatter.dateToDayWithYear(yesterday, "Today", "Yesterday"))
        assertEquals("Yesterday", formatter.dateToDayWithoutCurrentYear(yesterday, "Today", "Yesterday"))
    }

    @Test
    fun `calendar patterns do not display the next week based year in December`() {
        val formatter = formatter("2025-12-31T12:00:00Z")
        val december = date("2025-12-31T12:00:00Z")
        assertEquals("December 2025", formatter.formatDate(december, DateTimeFormatter.MMMM_YYYY))
        assertEquals("December 31, 2025", formatter.formatDate(december, DateTimeFormatter.MMMM_DD_YYYY))
    }

    @Test
    fun `yesterday follows local calendar days across daylight saving changes`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val spring = formatter("2026-03-09T04:30:00Z")
        assertEquals("Yesterday", spring.dateToDayWithYear(date("2026-03-08T05:00:00Z"), "Today", "Yesterday"))
        val fall = formatter("2026-11-02T05:30:00Z")
        assertEquals("Yesterday", fall.dateToDayWithYear(date("2026-11-01T04:00:00Z"), "Today", "Yesterday"))
    }

    @Test
    fun `leap day is yesterday on March 1`() {
        val formatter = formatter("2024-03-01T00:01:00Z")
        assertEquals("Yesterday", formatter.dateToDayWithYear(date("2024-02-29T23:59:00Z"), "Today", "Yesterday"))
    }

    @Test
    fun `relative labels respect the local date across a UTC year boundary`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val formatter = formatter("2025-12-31T15:30:00Z")
        assertEquals("Today", formatter.dateToDayWithYear(date("2025-12-31T15:00:00Z"), "Today", "Yesterday"))
        assertEquals("Yesterday", formatter.dateToDayWithYear(date("2025-12-31T14:59:59Z"), "Today", "Yesterday"))
    }

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
