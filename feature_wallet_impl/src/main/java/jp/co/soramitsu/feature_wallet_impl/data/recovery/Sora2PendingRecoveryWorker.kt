package jp.co.soramitsu.feature_wallet_impl.data.recovery

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
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingRecoveryKick
import jp.co.soramitsu.sora.substrate.substrate.Sora2PendingSubmissionCoordinator
import kotlinx.coroutines.CancellationException

/** Status-only restart recovery. This worker has no signer or submission dependency. */
@HiltWorker
class Sora2PendingRecoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val coordinator: Sora2PendingSubmissionCoordinator,
    private val scheduler: Sora2PendingRecoveryScheduler,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        if (WalletRecoveryCapabilityGate.mode() != WalletRecoveryCapabilityGate.Mode.NORMAL) {
            return Result.retry()
        }
        return try {
            val pass = coordinator.recoverStatusOnly(MAXIMUM_TRANSACTIONS_PER_RUN)
            when {
                pass.failures > 0 || pass.unresolved > 0 -> Result.retry()
                pass.remainingUnattempted > 0 -> {
                    scheduler.continueAfterBoundedBatch()
                    Result.success()
                }
                else -> Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Corrupt, incomplete, runtime-drifted, and unavailable witnesses all remain durable.
            Result.retry()
        }
    }

    private companion object {
        const val MAXIMUM_TRANSACTIONS_PER_RUN = 8
    }
}

@Singleton
class Sora2PendingRecoveryScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : Sora2PendingRecoveryKick {
    private val applicationContext = context.applicationContext

    fun ensureOnStartup() {
        enqueue(ExistingWorkPolicy.KEEP)
    }

    override fun kickAfterJournal() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun continueAfterBoundedBatch() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<Sora2PendingRecoveryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
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
        const val UNIQUE_WORK_NAME = "sora2-pending-submission-reconciliation-v1"
        const val WORK_TAG = "sora2-pending-submission-reconciliation"
        const val INITIAL_BACKOFF_SECONDS = 30L
    }
}
