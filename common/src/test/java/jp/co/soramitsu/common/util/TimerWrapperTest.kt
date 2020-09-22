package jp.co.soramitsu.common.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class TimerWrapperTest {

    private lateinit var timer: TimerWrapper

    @Before fun setUp() {
        timer = TimerWrapper()
    }

    @Test fun `calc time left show correct time`() {
        assertEquals("00:00:00", timer.calcTimeLeft(0))
        assertEquals("00:01:00", timer.calcTimeLeft(60000))
        assertEquals("06:06:40", timer.calcTimeLeft(22000000))
        assertEquals("12:13:20", timer.calcTimeLeft(44000000))
        assertEquals("23:59:59", timer.calcTimeLeft(86399000))
    }
}