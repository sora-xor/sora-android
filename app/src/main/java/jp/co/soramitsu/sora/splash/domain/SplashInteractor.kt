/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SplashInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val migrationManager: MigrationManager
) {

    private val migrationsDone = CompletableDeferred<Boolean>()

    suspend fun checkMigration() {
        migrationsDone.complete(migrationManager.start())
    }

    fun getMigrationDoneAsync(): Deferred<Boolean> = migrationsDone

    suspend fun getRegistrationState() = userRepository.getRegistrationState()

    suspend fun saveInviteCode(inviteCode: String) {
        userRepository.saveParentInviteCode(inviteCode)
    }
}
