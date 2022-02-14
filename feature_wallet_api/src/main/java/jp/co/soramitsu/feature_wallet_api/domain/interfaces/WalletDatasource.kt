/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import kotlinx.coroutines.flow.Flow

interface WalletDatasource {

    suspend fun saveContacts(results: List<Account>)

    suspend fun retrieveContacts(): List<Account>?

    suspend fun saveInvitedUsers(invitedUsers: Array<InvitedUser>)

    suspend fun retrieveInvitedUsers(): Array<InvitedUser>?

    suspend fun saveInvitationParent(parentInfo: InvitedUser)

    suspend fun retrieveInvitationParent(): InvitedUser?

    suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String>

    suspend fun saveClaimBlockAndTxHash(inBlock: String, txHash: String)

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    fun getDisclaimerVisibility(): Flow<Boolean>

    suspend fun saveDisclaimerVisibility(v: Boolean)
}
