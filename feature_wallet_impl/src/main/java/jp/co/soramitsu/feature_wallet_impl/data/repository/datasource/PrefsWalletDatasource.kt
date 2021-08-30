/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

class PrefsWalletDatasource @Inject constructor(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val serializer: Serializer
) : WalletDatasource {

    companion object {
        private const val PREFS_PARENT_INVITATION = "parent_invitation"

        private const val KEY_CONTACTS = "key_contacts"
        private const val KEY_CLAIM_BLOCK_HASH = "key_claim_block_hash"
        private const val KEY_CLAIM_TX_HASH = "key_claim_tx_hash"
        private const val KEY_MIGRATION_STATUS = "key_migration_status"
    }

    private val migrationStatusFlow = MutableStateFlow<MigrationStatus?>(null)

    override fun saveContacts(results: List<Account>) {
        preferences.putString(KEY_CONTACTS, serializer.serialize(results))
    }

    override fun retrieveContacts(): List<Account>? {
        val contactsJson = preferences.getString(KEY_CONTACTS)

        return if (contactsJson.isEmpty()) {
            null
        } else {
            serializer.deserialize<List<Account>>(
                contactsJson,
                object : TypeToken<List<Account>>() {}.type
            )
        }
    }

    override fun saveInvitationParent(parentInfo: InvitedUser) {
        encryptedPreferences.putEncryptedString(
            PREFS_PARENT_INVITATION,
            serializer.serialize(parentInfo)
        )
    }

    override fun retrieveInvitationParent(): InvitedUser? {
        val parentInfoString = encryptedPreferences.getDecryptedString(PREFS_PARENT_INVITATION)
        return if (parentInfoString.isEmpty()) {
            null
        } else {
            serializer.deserialize(parentInfoString, InvitedUser::class.java)
        }
    }

    override fun saveClaimBlockAndTxHash(inBlock: String, txHash: String) {
        encryptedPreferences.putEncryptedString(KEY_CLAIM_BLOCK_HASH, inBlock)
        encryptedPreferences.putEncryptedString(KEY_CLAIM_TX_HASH, txHash)
    }

    override fun saveMigrationStatus(migrationStatus: MigrationStatus) {
        preferences.putString(KEY_MIGRATION_STATUS, migrationStatus.toString())
        migrationStatusFlow.value = migrationStatus
    }

    override fun observeMigrationStatus(): Flow<MigrationStatus> {
        return migrationStatusFlow.asStateFlow().filterNotNull()
    }

    override fun retrieveClaimBlockAndTxHash(): Pair<String, String> {
        return encryptedPreferences.getDecryptedString(KEY_CLAIM_BLOCK_HASH) to encryptedPreferences.getDecryptedString(
            KEY_CLAIM_TX_HASH
        )
    }

    override fun saveInvitedUsers(invitedUsers: Array<InvitedUser>) {
        preferences.putString(Const.INVITED_USERS, serializer.serialize(invitedUsers))
    }

    override fun retrieveInvitedUsers(): Array<InvitedUser>? {
        val invitedUsersJson = preferences.getString(Const.INVITED_USERS)

        return if (invitedUsersJson.isEmpty()) {
            null
        } else {
            serializer.deserialize<Array<InvitedUser>>(
                invitedUsersJson,
                object : TypeToken<Array<InvitedUser>>() {}.type
            )
        }
    }
}
