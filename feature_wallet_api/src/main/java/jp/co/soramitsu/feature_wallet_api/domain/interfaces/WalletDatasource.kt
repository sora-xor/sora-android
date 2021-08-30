package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import kotlinx.coroutines.flow.Flow

interface WalletDatasource {

    fun saveContacts(results: List<Account>)

    fun retrieveContacts(): List<Account>?

    fun saveInvitedUsers(invitedUsers: Array<InvitedUser>)

    fun retrieveInvitedUsers(): Array<InvitedUser>?

    fun saveInvitationParent(parentInfo: InvitedUser)

    fun retrieveInvitationParent(): InvitedUser?

    fun retrieveClaimBlockAndTxHash(): Pair<String, String>

    fun saveClaimBlockAndTxHash(inBlock: String, txHash: String)

    fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>
}
