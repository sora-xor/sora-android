package jp.co.soramitsu.common.domain

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HealthCheckerTest {

    private lateinit var healthChecker: HealthChecker

    @Before fun setup() {
        healthChecker = HealthChecker()
    }

    @Test fun `healh checker test`() {
        val actual = mutableListOf<Boolean>()
        val expected = mutableListOf(true, false, true)

        healthChecker.observeHealthState()
            .subscribe { actual.add(it) }

        healthChecker.connectionStable()
        healthChecker.connectionErrorHandled()
        healthChecker.connectionStable()

        assertEquals(expected, actual)
    }
}