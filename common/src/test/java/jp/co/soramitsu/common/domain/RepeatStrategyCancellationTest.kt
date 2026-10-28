package jp.co.soramitsu.common.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RepeatStrategyCancellationTest {
    @Test
    fun `retry never consumes a cancellation exception even when the predicate accepts all errors`() = runTest {
        val cancellation = CancellationException("screen closed")
        var attempts = 0
        var predicates = 0
        val result = runCatching {
            RetryStrategyBuilder.build().retryIf(3, { predicates++; true }) {
                attempts++
                if (attempts == 1) throw cancellation
                "must not retry"
            }
        }
        assertSame(cancellation, result.exceptionOrNull())
        assertEquals(1, attempts)
        assertEquals(0, predicates)
    }

    @Test
    fun `retry limit counts additional attempts and preserves the original failure`() = runTest {
        val failure = IllegalStateException("offline")
        var attempts = 0
        var predicates = 0
        val result = runCatching {
            RetryStrategyBuilder.build().retryIf(2, { predicates++; true }) {
                attempts++
                throw failure
            }
        }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(3, attempts)
        assertEquals(2, predicates)
    }

    @Test
    fun `zero retries does not call a predicate that cannot affect the result`() = runTest {
        val failure = IllegalStateException("offline")
        val result = runCatching {
            RetryStrategyBuilder.build().retryIf(0, { error("no retry is available") }) { throw failure }
        }
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `non retryable failures stop immediately`() = runTest {
        val failure = IllegalArgumentException("bad request")
        var attempts = 0
        val result = runCatching {
            RetryStrategyBuilder.build().retryIf(3, { false }) { attempts++; throw failure }
        }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, attempts)
    }

    @Test
    fun `successful retry returns its value without another predicate call`() = runTest {
        var attempts = 0
        var predicates = 0
        val value = RetryStrategyBuilder.build().retryIf(3, { predicates++; true }) {
            if (++attempts < 3) throw IllegalStateException("retry")
            "ready"
        }
        assertEquals("ready", value)
        assertEquals(3, attempts)
        assertEquals(2, predicates)
    }

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

    @Test
    fun `already cancelled scopes never start another retry operation`() = runTest {
        var calls = 0
        val job = launch {
            cancel()
            RetryStrategyBuilder.build().retryIf(1, { true }) { calls++; "ready" }
        }
        job.join()
        assertEquals(0, calls)
    }
}
