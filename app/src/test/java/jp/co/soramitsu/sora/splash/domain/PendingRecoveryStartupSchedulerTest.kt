package jp.co.soramitsu.sora.splash.domain

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPendingRecoveryScheduler
import jp.co.soramitsu.feature_wallet_impl.data.recovery.Sora2PendingRecoveryScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingRecoveryStartupSchedulerTest {
    private class Fixture(testScope: TestScope) : AutoCloseable {
        val nexus = mockk<NexusPendingRecoveryScheduler>(relaxed = true)
        val sora2 = mockk<Sora2PendingRecoveryScheduler>(relaxed = true)
        val uncaught = mutableListOf<Throwable>()
        val messages = mutableListOf<String>()
        val loggedErrors = mutableListOf<Throwable?>()
        private val dispatcher = StandardTestDispatcher(testScope.testScheduler)
        private val scope = CoroutineScope(
            SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error -> uncaught.add(error) }
        )
        private val manager = mockk<CoroutineManager> {
            every { applicationScope } returns scope
            every { io } returns dispatcher
        }
        val scheduler = PendingRecoveryStartupScheduler(manager, nexus, sora2)

        init {
            mockkStatic(Log::class)
            every { Log.w(any(), any<String>()) } answers {
                assertEquals("WalletRecoveryStartup", firstArg<String>())
                messages.add(secondArg())
                loggedErrors.add(null)
                0
            }
            every { Log.w(any(), any<String>(), any()) } answers {
                messages.add(secondArg())
                loggedErrors.add(thirdArg())
                0
            }
        }
        override fun close() {
            scope.cancel()
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun `successful startup schedules both durable recovery workers`() = runTest {
        Fixture(this).use { fixture ->
            val job = fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            assertTrue(job.isCompleted)
            assertFalse(job.isCancelled)
            verify(exactly = 1) { fixture.nexus.ensureOnStartup() }
            verify(exactly = 1) { fixture.sora2.ensureOnStartup() }
            assertTrue(fixture.uncaught.isEmpty())
            assertTrue(fixture.messages.isEmpty())
        }
    }

    @Test
    fun `Nexus scheduling failure cannot crash startup or suppress Sora2`() = runTest {
        Fixture(this).use { fixture ->
            every { fixture.nexus.ensureOnStartup() } throws IllegalStateException("sensitive fixture value")
            val job = fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            assertFalse(job.isCancelled)
            verify(exactly = 1) { fixture.sora2.ensureOnStartup() }
            assertTrue(fixture.uncaught.isEmpty())
            assertEquals(listOf("NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED"), fixture.messages)
            assertEquals(listOf<Throwable?>(null), fixture.loggedErrors)
        }
    }

    @Test
    fun `Sora2 scheduling failure cannot crash startup or undo Nexus scheduling`() = runTest {
        Fixture(this).use { fixture ->
            every { fixture.sora2.ensureOnStartup() } throws IllegalStateException("sensitive fixture value")
            val job = fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            assertFalse(job.isCancelled)
            verify(exactly = 1) { fixture.nexus.ensureOnStartup() }
            assertTrue(fixture.uncaught.isEmpty())
            assertEquals(listOf("SORA2_PENDING_RECOVERY_SCHEDULING_FAILED"), fixture.messages)
            assertEquals(listOf<Throwable?>(null), fixture.loggedErrors)
        }
    }

    @Test
    fun `both scheduling failures are independent and observable without exception data`() = runTest {
        Fixture(this).use { fixture ->
            every { fixture.nexus.ensureOnStartup() } throws IllegalStateException("sensitive nexus fixture")
            every { fixture.sora2.ensureOnStartup() } throws IllegalStateException("sensitive sora fixture")
            val job = fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            assertFalse(job.isCancelled)
            assertTrue(fixture.uncaught.isEmpty())
            assertEquals(listOf("NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED", "SORA2_PENDING_RECOVERY_SCHEDULING_FAILED"), fixture.messages)
            assertEquals(listOf<Throwable?>(null, null), fixture.loggedErrors)
        }
    }

    @Test
    fun `Nexus cancellation propagates without attempting Sora2 or reporting failure`() = runTest {
        Fixture(this).use { fixture ->
            every { fixture.nexus.ensureOnStartup() } throws CancellationException("cancel fixture")
            val job = fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            assertTrue(job.isCancelled)
            verify(exactly = 0) { fixture.sora2.ensureOnStartup() }
            assertTrue(fixture.uncaught.isEmpty())
            assertTrue(fixture.messages.isEmpty())
        }
    }

    @Test
    fun `Sora2 cancellation propagates after Nexus without reporting failure`() = runTest {
        Fixture(this).use { fixture ->
            every { fixture.sora2.ensureOnStartup() } throws CancellationException("cancel fixture")
            val job = fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            assertTrue(job.isCancelled)
            verify(exactly = 1) { fixture.nexus.ensureOnStartup() }
            assertTrue(fixture.uncaught.isEmpty())
            assertTrue(fixture.messages.isEmpty())
        }
    }

    @Test
    fun `later startup retries scheduling after a temporary failure`() = runTest {
        Fixture(this).use { fixture ->
            every { fixture.nexus.ensureOnStartup() } throws IllegalStateException("temporarily unavailable") andThen Unit
            fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            val retry = fixture.scheduler.scheduleAfterWalletMigration()
            advanceUntilIdle()
            assertFalse(retry.isCancelled)
            verify(exactly = 2) { fixture.nexus.ensureOnStartup() }
            verify(exactly = 2) { fixture.sora2.ensureOnStartup() }
            assertTrue(fixture.uncaught.isEmpty())
            assertEquals(listOf("NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED"), fixture.messages)
        }
    }
}
