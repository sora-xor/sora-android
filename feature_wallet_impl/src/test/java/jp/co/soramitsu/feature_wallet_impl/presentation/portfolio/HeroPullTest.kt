package jp.co.soramitsu.feature_wallet_impl.presentation.portfolio

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeroPullTest {
    /** A display that shows a frame every 16 ms of test time. */
    private class TestClock : MonotonicFrameClock {
        private var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(16)
            nanos += 16_000_000L
            return onFrame(nanos)
        }
    }

    private val scope = TestScope(StandardTestDispatcher() + TestClock())
    private val pull = HeroPull(scope)
    private val finger = NestedScrollSource.UserInput

    private fun pulledPastTheTop(by: Float) {
        pull.onPostScroll(Offset.Zero, Offset(0f, by), finger)
    }

    @Test
    fun aDragTheScrollCouldNotUseAddsUp() {
        pulledPastTheTop(30f)
        pulledPastTheTop(12f)
        assertEquals(42f, pull.pulled, 0.001f)
    }

    @Test
    fun onlyAFingerPulls() {
        pull.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.SideEffect)
        assertEquals(0f, pull.pulled, 0.001f)
    }

    @Test
    fun aDragUpTakesThePullBackAndNeverBelowNothing() {
        pulledPastTheTop(20f)
        pull.onPreScroll(Offset(0f, -8f), finger)
        assertEquals(12f, pull.pulled, 0.001f)
        pull.onPreScroll(Offset(0f, -50f), finger)
        assertEquals(0f, pull.pulled, 0.001f)
    }

    @Test
    fun itNeverConsumesTheScroll() {
        assertEquals(Offset.Zero, pull.onPreScroll(Offset(0f, -8f), finger))
        assertEquals(Offset.Zero, pull.onPostScroll(Offset.Zero, Offset(0f, 8f), finger))
    }

    @Test
    fun liftingTheFingerLetsItFallBackAndKeepsTheFling() = runTest {
        pulledPastTheTop(60f)
        val velocity = pull.onPreFling(Velocity(0f, 300f))
        assertEquals(Velocity.Zero, velocity)
        assertTrue("the fall back has not started yet", pull.pulled > 0f)
        scope.advanceUntilIdle()
        assertEquals(0f, pull.pulled, 0.001f)
    }

    @Test
    fun aNewDragStopsTheFallBack() = runTest {
        pulledPastTheTop(60f)
        pull.onPreFling(Velocity.Zero)
        pull.onPreScroll(Offset(0f, 1f), finger)
        pulledPastTheTop(5f)
        val held = pull.pulled
        scope.advanceUntilIdle()
        assertEquals(held, pull.pulled, 0.001f)
    }
}
