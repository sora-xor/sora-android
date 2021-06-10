/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import javax.inject.Inject

class PrefsWalletDatasource @Inject constructor(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val serializer: Serializer
) : WalletDatasource {

    companion object {
        private const val PREFS_PARENT_INVITATION = "parent_invitation"

        private const val KEY_BALANCE = "key_balance"
        private const val KEY_CONTACTS = "key_contacts"
        private const val KEY_TRANSFER_META_FEE_RATE = "key_transfer_meta_rate"
        private const val KEY_TRANSFER_META_FEE_TYPE = "key_transfer_meta_type"
        private const val KEY_WITHDRAW_META_FEE_RATE = "key_withdraw_meta_rate"
        private const val KEY_WITHDRAW_META_FEE_TYPE = "key_withdraw_meta_type"
        private const val KEY_CLAIM_BLOCK_HASH = "key_claim_block_hash"
        private const val KEY_CLAIM_TX_HASH = "key_claim_tx_hash"
        private const val KEY_MIGRATION_STATUS = "key_migration_status"
    }

    private val transferMetaSubject = BehaviorSubject.create<TransferMeta>()
    private val withdrawMetaSubject = BehaviorSubject.create<TransferMeta>()
    private val migrationStatusSubject = BehaviorSubject.create<MigrationStatus>()

    init {
        val transferMeta = retrieveTransferMeta()
        if (transferMeta != null) {
            transferMetaSubject.onNext(transferMeta)
        }

        val withdrawMeta = retrieveWithdrawMeta()
        if (withdrawMeta != null) {
            withdrawMetaSubject.onNext(withdrawMeta)
        }
    }

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

    override fun saveTransferMeta(transferMeta: TransferMeta) {
        transferMetaSubject.onNext(transferMeta)
        preferences.putDouble(KEY_TRANSFER_META_FEE_RATE, transferMeta.feeRate)
        preferences.putString(KEY_TRANSFER_META_FEE_TYPE, transferMeta.feeType.toString())
    }

    private fun retrieveTransferMeta(): TransferMeta? {
        val feeRate = preferences.getDouble(KEY_TRANSFER_META_FEE_RATE, -1.0)

        if (feeRate != -1.0) {
            return TransferMeta(
                feeRate,
                FeeType.valueOf(preferences.getString(KEY_TRANSFER_META_FEE_TYPE))
            )
        }

        return null
    }

    override fun observeTransferMeta(): Observable<TransferMeta> {
        return transferMetaSubject
    }

    override fun saveWithdrawMeta(transferMeta: TransferMeta) {
        withdrawMetaSubject.onNext(transferMeta)
        preferences.putDouble(KEY_WITHDRAW_META_FEE_RATE, transferMeta.feeRate)
        preferences.putString(KEY_WITHDRAW_META_FEE_TYPE, transferMeta.feeType.toString())
    }

    private fun retrieveWithdrawMeta(): TransferMeta? {
        val feeRate = preferences.getDouble(KEY_WITHDRAW_META_FEE_RATE, -1.0)

        if (feeRate != -1.0) {
            return TransferMeta(
                feeRate,
                FeeType.valueOf(preferences.getString(KEY_WITHDRAW_META_FEE_TYPE))
            )
        }

        return null
    }

    override fun observeWithdrawMeta(): Observable<TransferMeta> {
        return withdrawMetaSubject
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

        migrationStatusSubject.onNext(migrationStatus)
    }

    override fun observeMigrationStatus(): Observable<MigrationStatus> {
        return migrationStatusSubject
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
