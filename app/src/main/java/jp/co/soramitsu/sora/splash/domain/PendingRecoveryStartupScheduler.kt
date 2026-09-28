/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.sora.splash.domain

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPendingRecoveryScheduler
import jp.co.soramitsu.feature_wallet_impl.data.recovery.Sora2PendingRecoveryScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Singleton
class PendingRecoveryStartupScheduler @Inject constructor(
    private val coroutineManager: CoroutineManager,
    private val nexusScheduler: NexusPendingRecoveryScheduler,
    private val sora2Scheduler: Sora2PendingRecoveryScheduler,
) {

    /**
     * Recovery is status-only, but WorkManager initialization and enqueueing do not belong on the
     * process-start critical path. Start them only after wallet migration has completed safely.
     */
    fun scheduleAfterWalletMigration() =
        coroutineManager.applicationScope.launch(coroutineManager.io) {
            scheduleSafely("NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED") {
                nexusScheduler.ensureOnStartup()
            }
            scheduleSafely("SORA2_PENDING_RECOVERY_SCHEDULING_FAILED") {
                sora2Scheduler.ensureOnStartup()
            }
        }

    private inline fun scheduleSafely(diagnostic: String, schedule: () -> Unit) {
        try {
            schedule()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // A WorkManager initialization/enqueue failure must neither crash an upgraded
            // wallet nor suppress the other network's recovery. Keep the durable journal
            // for the next startup/kick and log only a fixed, non-sensitive outcome code.
            Log.w("WalletRecoveryStartup", diagnostic)
        }
    }
}
