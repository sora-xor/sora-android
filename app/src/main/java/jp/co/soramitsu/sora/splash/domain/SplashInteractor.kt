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

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

@Singleton
class SplashInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val migrationManager: MigrationManager
) {

    @Volatile
    private var migrationsDone = CompletableDeferred<Boolean>()

    suspend fun checkMigration() {
        checkMigration(migrationsDone)
    }

    suspend fun retryMigration(): Boolean {
        val retry = CompletableDeferred<Boolean>()
        synchronized(this) {
            migrationsDone = retry
        }
        checkMigration(retry)
        return retry.await()
    }

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

    suspend fun getMigrationFailureCode(): String? = migrationManager.failureCode()

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
