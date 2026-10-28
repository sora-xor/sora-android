package jp.co.soramitsu.common.date

import android.content.Context
import io.mockk.*
import java.time.Instant
import java.util.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.LanguagesHolder
import org.junit.*
import org.junit.Assert.*

class VotingTimeFormattingTest {
    private val zone = TimeZone.getDefault()
    private val resources = mockk<ResourceManager>()
    private val language = mockk<LanguagesHolder> { every { getCurrentLocale() } returns Locale.US }
    private val subject = DateTimeFormatter(language, resources, mockk<Context>())
    @Before fun setup() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun cleanup() { TimeZone.setDefault(zone) }

    @Test fun `clock formatting preserves seconds only when requested`() {
        val date = Date.from(Instant.parse("2026-01-02T03:04:05Z"))
        assertEquals("03:04", subject.formatTimeWithoutSeconds(date))
        assertEquals("03:04:05", subject.formatTimeWithSeconds(date))
    }

    @Test fun `open voting countdown selects the largest whole unit and passes localized quantity`() {
        val cases = listOf(
            Triple(2 * 86_400_000L + 10_000, R.plurals.project_date_day_plurals, 2),
            Triple(3 * 3_600_000L + 10_000, R.plurals.project_date_hour_plurals, 3),
            Triple(4 * 60_000L + 10_000, R.plurals.project_date_minute_plurals, 4),
            Triple(25_500L, R.plurals.project_date_second_plurals, 25),
        )
        for ((duration, resource, number) in cases) {
            every { resources.getQuantityString(resource, number, number.toString()) } returns "unit-$number"
            assertEquals("unit-$number", subject.formatToOpenVotableDateString(System.currentTimeMillis() + duration))
            verify { resources.getQuantityString(resource, number, number.toString()) }
        }
    }

    @Test fun `expired and subsecond voting deadlines display no countdown`() {
        assertEquals("", subject.formatToOpenVotableDateString(System.currentTimeMillis() - 1000))
        assertEquals("", subject.formatToOpenVotableDateString(System.currentTimeMillis() + 500))
        verify(exactly = 0) { resources.getQuantityString(any(), any(), any<String>()) }
    }

    @Test fun `closed voting uses localized today yesterday and optional ended prefix`() {
        every { resources.getString(R.string.common_today) } returns "Today"
        every { resources.getString(R.string.common_yesterday) } returns "Yesterday"
        every { resources.getString(R.string.project_ended_template) } returns "Ended %s"
        val time = System.currentTimeMillis()
        assertEquals("Ended Today", subject.formatToClosedVotableDateString(time - 60_000))
        assertEquals("Yesterday", subject.formatToClosedVotableDateString(time - 86_400_000 - 60_000, false))
        assertEquals("", subject.formatToClosedVotableDateString(time + 60_000))
    }

    @Test fun `older closed voting includes the year only outside the current year`() {
        val now = Calendar.getInstance()
        val sameYear = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        // January 1 itself cannot be an older-than-yesterday date in early January.
        if (now.get(Calendar.DAY_OF_YEAR) > 3) {
            assertEquals("01 January", subject.formatToClosedVotableDateString(sameYear.timeInMillis, false))
        }
        val lastYear = (sameYear.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
        assertEquals("01 Jan ${lastYear.get(Calendar.YEAR)}", subject.formatToClosedVotableDateString(lastYear.timeInMillis, false))
    }

    @Test fun `duration omits zero units and handles exact hour and minute boundaries`() {
        every { resources.getQuantityString(R.plurals.common_hour, any()) } returns "hours"
        every { resources.getString(R.string.common_min) } returns "minutes"
        every { resources.getString(R.string.common_sec) } returns "seconds"
        assertEquals("", subject.formatTimeFromSeconds(0))
        assertEquals("7s", subject.formatTimeFromSeconds(7))
        assertEquals("2m:", subject.formatTimeFromSeconds(120))
        assertEquals("1h:", subject.formatTimeFromSeconds(3600))
        assertEquals("1h:2m:3s", subject.formatTimeFromSeconds(3723))
    }
}
