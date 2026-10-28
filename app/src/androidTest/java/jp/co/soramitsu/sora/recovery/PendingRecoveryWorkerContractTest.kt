@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import io.mockk.*
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.feature_wallet_impl.data.nexus.*
import jp.co.soramitsu.feature_wallet_impl.data.recovery.*
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingSubmissionCoordinator
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingRecoveryPass
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Workers and work requests stay real; all journal/transport/enqueue boundaries are inert. */
@RunWith(AndroidJUnit4::class)
class PendingRecoveryWorkerContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val nexus = mockk<NexusPendingRecovery>()
    private val sora = mockk<Sora2PendingSubmissionCoordinator>()
    private val nexusScheduler = mockk<NexusPendingRecoveryScheduler>(relaxed = true)
    private val soraScheduler = mockk<Sora2PendingRecoveryScheduler>(relaxed = true)
    @Before fun setup() {
        WalletRecoveryCapabilityGate.enterNormal()
        coEvery { nexus.recoverAfterRestart(any(), any()) } returns NexusRecoveryPass(0, 0, 0, 0, 0)
        coEvery { sora.recoverStatusOnly(any(), any()) } returns Sora2PendingRecoveryPass(0, 0, 0, 0, 0)
    }
    @After fun cleanup() { WalletRecoveryCapabilityGate.enterNormal() }
    private fun workers(attempt: Int = 0): List<CoroutineWorker> {
        val params = mockk<WorkerParameters>(relaxed = true) {
            every { runAttemptCount } returns attempt
            every { workerContext } returns Dispatchers.Default
        }
        return listOf(NexusPendingRecoveryWorker(context, params, nexus, nexusScheduler), Sora2PendingRecoveryWorker(context, params, sora, soraScheduler))
    }
    @Test fun upgradeAndRecoveryModesNeverReadOrRescheduleTheJournal() = runBlocking {
        val modes = listOf({ WalletRecoveryCapabilityGate.enterBlocked() }, { WalletRecoveryCapabilityGate.enterInspectedRecovery() },
            { WalletRecoveryCapabilityGate.enterLegacyReadOnly() }, { WalletRecoveryCapabilityGate.authorizeMigrationRetry() })
        modes.forEach { enter -> enter(); workers().forEach { assertEquals(ListenableWorker.Result.success(), it.doWork()) } }
        coVerify(exactly = 0) { nexus.recoverAfterRestart(any(), any()); sora.recoverStatusOnly(any(), any()) }
        verify(exactly = 0) { nexusScheduler.continueAfterBoundedBatch(); soraScheduler.continueAfterBoundedBatch() }
    }
    @Test fun boundedAttemptLimitStopsWithoutDeletingOrReadingAnything() = runBlocking {
        for (attempt in listOf(10, 100)) workers(attempt).forEach { assertEquals(ListenableWorker.Result.success(), it.doWork()) }
        coVerify(exactly = 0) { nexus.recoverAfterRestart(any(), any()); sora.recoverStatusOnly(any(), any()) }
    }
    @Test fun retryOffsetAdvancesEachNetworkByItsOwnBoundedBatchSize() = runBlocking {
        workers(3).forEach { assertEquals(ListenableWorker.Result.success(), it.doWork()) }
        coVerify(exactly = 1) { nexus.recoverAfterRestart(10, 30) }
        coVerify(exactly = 1) { sora.recoverStatusOnly(8, 24) }
        verify(exactly = 0) { nexusScheduler.continueAfterBoundedBatch(); soraScheduler.continueAfterBoundedBatch() }
    }
    @Test fun unresolvedTransactionsAndReadFailuresRetryWithoutAppendingNewOwners() = runBlocking {
        for ((unresolved, failures) in listOf(1 to 0, 0 to 1)) {
            coEvery { nexus.recoverAfterRestart(any(), any()) } returns NexusRecoveryPass(1, 0, unresolved, failures, 5)
            coEvery { sora.recoverStatusOnly(any(), any()) } returns Sora2PendingRecoveryPass(1, 0, unresolved, failures, 5)
            workers().forEach { assertEquals(ListenableWorker.Result.retry(), it.doWork()) }
        }
        verify(exactly = 0) { nexusScheduler.continueAfterBoundedBatch(); soraScheduler.continueAfterBoundedBatch() }
    }
    @Test fun healthyBacklogAppendsAFreshBatchWithoutNetworkBackoff() = runBlocking {
        coEvery { nexus.recoverAfterRestart(any(), any()) } returns NexusRecoveryPass(10, 10, 0, 0, 2)
        coEvery { sora.recoverStatusOnly(any(), any()) } returns Sora2PendingRecoveryPass(8, 8, 0, 0, 2)
        workers().forEach { assertEquals(ListenableWorker.Result.success(), it.doWork()) }
        verify(exactly = 1) { nexusScheduler.continueAfterBoundedBatch(); soraScheduler.continueAfterBoundedBatch() }
    }
    @Test fun unexpectedReadFailureRetriesAndCancellationPropagates() = runBlocking {
        coEvery { nexus.recoverAfterRestart(any(), any()) } throws IllegalStateException("fixture unavailable")
        coEvery { sora.recoverStatusOnly(any(), any()) } throws IllegalStateException("fixture unavailable")
        workers().forEach { assertEquals(ListenableWorker.Result.retry(), it.doWork()) }
        coEvery { nexus.recoverAfterRestart(any(), any()) } throws CancellationException("cancelled")
        coEvery { sora.recoverStatusOnly(any(), any()) } throws CancellationException("cancelled")
        workers().forEach { worker ->
            val failure = runCatching { worker.doWork() }.exceptionOrNull()
            assertTrue(failure is CancellationException)
        }
    }
    @Test fun failedContinuationEnqueueIsRetriedWithoutClaimingBacklogDrained() = runBlocking {
        coEvery { nexus.recoverAfterRestart(any(), any()) } returns NexusRecoveryPass(0, 0, 0, 0, 1)
        coEvery { sora.recoverStatusOnly(any(), any()) } returns Sora2PendingRecoveryPass(0, 0, 0, 0, 1)
        every { nexusScheduler.continueAfterBoundedBatch() } throws IllegalStateException("scheduler unavailable")
        every { soraScheduler.continueAfterBoundedBatch() } throws IllegalStateException("scheduler unavailable")
        workers().forEach { assertEquals(ListenableWorker.Result.retry(), it.doWork()) }
    }
    @Test fun schedulersKeepStartupButAppendAfterDurableJournalWrites() = runBlocking {
        val db = mockk<AppDatabase>(); val dao = mockk<WalletIdentityDao>()
        every { db.walletIdentityDao() } returns dao
        coEvery { dao.countUnresolvedTransactions() } returns 0
        coEvery { dao.countUnresolvedSora2PendingSubmissionsInternal() } returns 1
        val manager = mockk<WorkManager>()
        val names = mutableListOf<String>(); val policies = mutableListOf<ExistingWorkPolicy>(); val requests = mutableListOf<OneTimeWorkRequest>()
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any()) } returns manager
            every { manager.enqueueUniqueWork(capture(names), capture(policies), capture(requests)) } returns mockk(relaxed = true)
            val first = NexusPendingRecoveryScheduler(context, db); val second = Sora2PendingRecoveryScheduler(context, db)
            assertFalse(first.hasRecoverableWork()); assertTrue(second.hasRecoverableWork())
            first.ensureOnStartup(); first.kickAfterJournal(); first.continueAfterBoundedBatch()
            second.ensureOnStartup(); second.kickAfterJournal(); second.continueAfterBoundedBatch()
            assertEquals(List(2) { listOf(ExistingWorkPolicy.KEEP, ExistingWorkPolicy.APPEND_OR_REPLACE, ExistingWorkPolicy.APPEND_OR_REPLACE) }.flatten(), policies)
            assertEquals(2, names.toSet().size); assertEquals(names[0], names[2]); assertEquals(names[3], names[5])
            requests.forEach { request ->
                assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
                assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
                assertEquals(30_000L, request.workSpec.backoffDelayDuration)
                assertEquals(Data.EMPTY, request.workSpec.input)
            }
            assertEquals(NexusPendingRecoveryWorker::class.java.name, requests.first().workSpec.workerClassName)
            assertEquals(Sora2PendingRecoveryWorker::class.java.name, requests.last().workSpec.workerClassName)
        } finally { unmockkObject(WorkManager.Companion) }
    }
}
