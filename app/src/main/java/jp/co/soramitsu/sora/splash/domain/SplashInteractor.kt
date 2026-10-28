/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.splash.domain

import android.content.Context
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.core_db.SetUpAgainScope
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationManager
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

@Singleton
class SplashInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val migrationManager: MigrationManager,
    @param:ApplicationContext private val context: Context,
    private val coroutineManager: CoroutineManager,
) {

    @Volatile
    private var migrationsDone = CompletableDeferred<Boolean>()
    private var verificationJob: Job? = null

    /**
     * True when this start resumes an interrupted verification (automatic retry), so the
     * progress screen says "Finishing your update" instead of "Updating your wallet".
     */
    @Volatile
    var resumingInterruptedUpgrade: Boolean = false
        private set

    /** The real phases of the running verification, for the upgrade progress screen. */
    val migrationProgress: StateFlow<MigrationProgress> get() = migrationManager.progress

    /**
     * When the latest verification started ([SystemClock.elapsedRealtime]), so the progress
     * screen counts its 10 s and 60 s prompts from the start of the work, not from when the
     * screen appeared. Null before the first run.
     */
    @Volatile
    var verificationStartedAtElapsed: Long? = null
        private set

    /** Publishes the new run before navigation can await it. The work belongs to the
     * application; losing a splash screen does not cancel a wallet verification halfway. */
    @Synchronized
    fun startMigration(): Deferred<Boolean> {
        if (verificationJob?.isActive == true) return migrationsDone
        if (migrationsDone.isCompleted) migrationsDone = CompletableDeferred()
        val target = migrationsDone
        verificationStartedAtElapsed = SystemClock.elapsedRealtime()
        resumingInterruptedUpgrade =
            runCatching { WalletUpgradeBackup.authorizeAutomaticMigrationRetry(context) }.getOrDefault(false)
        verificationJob = coroutineManager.applicationScope.launch(coroutineManager.io) {
            try {
                checkMigration(target)
            } catch (error: CancellationException) {
                target.cancel(error)
                throw error
            }
        }
        return target
    }

    suspend fun checkMigration() { startMigration().await() }

    suspend fun retryMigration(): Boolean = startMigration().await()

    private suspend fun checkMigration(target: CompletableDeferred<Boolean>) {
        val result = try {
            migrationManager.start()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // An unexpected migration exception may follow an ambiguously committed Room write.
            // Keep the current process fail-closed even if the lower layer could not journal it.
            // The backup-aware helper must not demote or clear an earlier preflight blocker.
            WalletUpgradeBackup.noteUnexpectedMigrationFailure()
            false
        }
        if (!target.isCompleted) {
            target.complete(result)
        }
    }

    fun getMigrationDoneAsync(): Deferred<Boolean> = migrationsDone

    /** The code for the recovery screen. Never throws (startup may have closed the database). */
    suspend fun getMigrationFailureCode(): String? = try {
        migrationManager.failureCode()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        currentRecoveryCode()
    }

    /** The blocking or transient code known in memory, without opening the database. */
    fun currentRecoveryCode(): String? =
        WalletUpgradeBackup.blockingFailure()?.code
            ?: WalletUpgradeBackup.transientVerificationFailure()

    /**
     * Forward paths for the current recovery state (never null while recovery is shown). They
     * come from the same state the actions use ([WalletUpgradeBackup.recoveryOptions]), so every
     * action offered works. Only when no recovery state is recorded at all (for example an
     * unexpected error while routing) are they built here, from the facts gathered at startup and
     * without "Set up this device again" or a restore: [WalletUpgradeBackup.requestSetUpAgain]
     * refuses those without a recorded state.
     */
    fun recoveryOptions(code: String): WalletRecoveryOptions =
        WalletUpgradeBackup.recoveryOptions()
            ?: WalletRecoveryOptions.forState(
                code = code,
                transientFailure = false,
                // The verification runs on the open current-schema database; read-only is
                // checked again by authorizeSafeReadOnly and against the current selection by
                // canContinueLegacyWallet().
                facts = WalletUpgradeBackup.recoveryFacts().copy(
                    databaseAtCurrentVersion = true,
                    accountsReadableReadOnly = true,
                ),
            ).withoutSetUpAgain()

    private fun WalletRecoveryOptions.withoutSetUpAgain(): WalletRecoveryOptions = copy(
        canRestoreFromPhrase = false,
        canRestoreFromDrive = false,
        canSetUpAgain = false,
        restoreNeedsSetUpAgain = false,
        setUpAgainScope = SetUpAgainScope.NONE,
    )

    suspend fun getRegistrationState() = userRepository.getRegistrationState()

    suspend fun hasExistingWallet(): Boolean = userRepository.getSoraAccountsCount() > 0

    /**
     * Recovery may expose legacy SORA2 only when the existing selection still resolves exactly.
     * A missing/corrupt selection is never treated as permission to select or create a wallet.
     */
    suspend fun canContinueLegacyWallet(): Boolean = try {
        userRepository.getCurSoraAccount()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }

    suspend fun saveInviteCode(inviteCode: String) {
        userRepository.saveParentInviteCode(inviteCode)
    }
}
