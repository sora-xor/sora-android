/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate

import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.sora.substrate.substrate.HealthChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class HealthCheckerTest {

    private lateinit var healthChecker: HealthChecker

    @Mock
    private lateinit var connectionManager: ConnectionManager

    @Before
    fun setup() {
        healthChecker = HealthChecker(connectionManager)
    }

    @Ignore
    @Test
    fun `health checker test`() = runTest {
        val expected = listOf(true, false, true)
        val actual = mutableListOf<Boolean>()
        launch {
            healthChecker.observeHealthState().take(3).collect { actual.add(it) }
        }

        healthChecker.connectionStable()
        advanceUntilIdle()
        healthChecker.connectionErrorHandled()
        advanceUntilIdle()
        healthChecker.connectionStable()
        advanceUntilIdle()

        assertEquals(expected, actual)
    }
}
