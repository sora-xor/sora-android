package jp.co.soramitsu.common.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatStrategyCancellationTest {

    @Test
    fun `repeat observes cancellation even when the block does not suspend`() = runTest {
        var calls = 0
        val job = launch {
            RepeatStrategyBuilder.infinite().repeat {
                calls++
                cancel()
                // Bound the pre-fix busy loop so a regression fails without hanging the suite.
                if (calls == 3) throw CancellationException("test loop limit")
            }
        }
        job.join()
        assertEquals(1, calls)
    }

}
