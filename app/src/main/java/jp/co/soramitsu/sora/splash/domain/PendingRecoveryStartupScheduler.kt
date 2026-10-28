/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora.splash.domain

import timber.log.Timber
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
     * process-start critical path. Start them only after wallet migration has completed safely,
     * and only when the journal has something to recover, so an idle wallet never wakes up in
     * the background. If the journal cannot be read, schedule anyway.
     */
    fun scheduleAfterWalletMigration() =
        coroutineManager.applicationScope.launch(coroutineManager.io) {
            scheduleSafely("NEXUS_PENDING_RECOVERY_SCHEDULING_FAILED") {
                if (hasWorkOrUnknown { nexusScheduler.hasRecoverableWork() }) {
                    nexusScheduler.ensureOnStartup()
                }
            }
            scheduleSafely("SORA2_PENDING_RECOVERY_SCHEDULING_FAILED") {
                if (hasWorkOrUnknown { sora2Scheduler.hasRecoverableWork() }) {
                    sora2Scheduler.ensureOnStartup()
                }
            }
        }

    private suspend inline fun hasWorkOrUnknown(probe: () -> Boolean): Boolean =
        try {
            probe()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            true
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
            Timber.tag("WalletRecoveryStartup").w(diagnostic)
        }
    }
}
