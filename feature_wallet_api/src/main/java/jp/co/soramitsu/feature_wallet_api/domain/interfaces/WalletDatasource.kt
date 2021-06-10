/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Observable
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta

interface WalletDatasource {

    fun saveContacts(results: List<Account>)

    fun retrieveContacts(): List<Account>?

    fun saveTransferMeta(transferMeta: TransferMeta)

    fun observeTransferMeta(): Observable<TransferMeta>

    fun saveWithdrawMeta(transferMeta: TransferMeta)

    fun observeWithdrawMeta(): Observable<TransferMeta>

    fun saveInvitedUsers(invitedUsers: Array<InvitedUser>)

    fun retrieveInvitedUsers(): Array<InvitedUser>?

    fun saveInvitationParent(parentInfo: InvitedUser)

    fun retrieveInvitationParent(): InvitedUser?

    fun retrieveClaimBlockAndTxHash(): Pair<String, String>

    fun saveClaimBlockAndTxHash(inBlock: String, txHash: String)

    fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Observable<MigrationStatus>
}
