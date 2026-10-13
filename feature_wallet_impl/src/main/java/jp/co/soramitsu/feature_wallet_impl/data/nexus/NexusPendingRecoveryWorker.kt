package jp.co.soramitsu.feature_wallet_impl.data.nexus

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import kotlinx.coroutines.CancellationException

/**
 * Durable status-only recovery for exact Nexus transaction hashes.
 *
 * The worker never holds signed bytes and has no submission dependency. Each execution processes a
 * bounded batch, then lets WorkManager apply bounded exponential backoff while any exact hash still
 * needs terminal/finality/history reconciliation.
 */
@HiltWorker
class NexusPendingRecoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val recovery: NexusPendingRecovery,
    private val scheduler: NexusPendingRecoveryScheduler,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        if (WalletRecoveryCapabilityGate.mode() != WalletRecoveryCapabilityGate.Mode.NORMAL) {
            return Result.retry()
        }
        return try {
            val pass = recovery.recoverAfterRestart(
                maxTransactions = MAX_TRANSACTIONS_PER_RUN,
                startOffset = runAttemptCount.toLong() * MAX_TRANSACTIONS_PER_RUN,
            )
            when {
                pass.failures > 0 || pass.unresolved > 0 -> Result.retry()
                pass.remainingUnattempted > 0 -> {
                    // Draining a healthy backlog must not inherit exponential network-error
                    // backoff. Append one fresh bounded owner, then let this owner finish.
                    scheduler.continueAfterBoundedBatch()
                    Result.success()
                }
                else -> Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private companion object {
        const val MAX_TRANSACTIONS_PER_RUN = 10
    }
}

@Singleton
class NexusPendingRecoveryScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val applicationContext = context.applicationContext

    fun ensureOnStartup() {
        enqueue(ExistingWorkPolicy.KEEP)
    }

    /**
     * Appending after the durable journal write closes the KEEP lost-kick race: if an existing
     * worker has already observed an empty snapshot while RUNNING, this successor still executes
     * after that owner reaches terminal. A retrying owner will see the same durable row itself.
     */
    fun kickAfterJournal() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun continueAfterBoundedBatch() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<NexusPendingRecoveryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "nexus-pending-transaction-reconciliation-v1"
        const val WORK_TAG = "nexus-pending-transaction-reconciliation"
        const val INITIAL_BACKOFF_SECONDS = 30L
    }
}
