/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

class PrefsWalletDatasource @Inject constructor(
    private val soraPreferences: SoraPreferences,
    private val encryptedPreferences: EncryptedPreferences,
) : WalletDatasource {

    companion object {
        private const val PREFS_PARENT_INVITATION = "parent_invitation"

        private const val KEY_DISCLAIMER_VISIBILITY = "key_disclaimer_visibility"
        private const val KEY_CONTACTS = "key_contacts"
        private const val KEY_CLAIM_BLOCK_HASH = "key_claim_block_hash"
        private const val KEY_CLAIM_TX_HASH = "key_claim_tx_hash"
        private const val KEY_MIGRATION_STATUS = "key_migration_status"
    }

    private val migrationStatusFlow = MutableStateFlow<MigrationStatus?>(null)

    override fun getDisclaimerVisibility(): Flow<Boolean> {
        return soraPreferences.getBooleanFlow(KEY_DISCLAIMER_VISIBILITY, true)
    }

    override suspend fun saveDisclaimerVisibility(v: Boolean) {
        soraPreferences.putBoolean(KEY_DISCLAIMER_VISIBILITY, v)
    }

    override suspend fun saveClaimBlockAndTxHash(inBlock: String, txHash: String) {
        encryptedPreferences.putEncryptedString(KEY_CLAIM_BLOCK_HASH, inBlock)
        encryptedPreferences.putEncryptedString(KEY_CLAIM_TX_HASH, txHash)
    }

    override suspend fun saveMigrationStatus(migrationStatus: MigrationStatus) {
        soraPreferences.putString(KEY_MIGRATION_STATUS, migrationStatus.toString())
        migrationStatusFlow.value = migrationStatus
    }

    override fun observeMigrationStatus(): Flow<MigrationStatus> {
        return migrationStatusFlow.asStateFlow().filterNotNull()
    }

    override suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String> {
        return encryptedPreferences.getDecryptedString(KEY_CLAIM_BLOCK_HASH) to encryptedPreferences.getDecryptedString(
            KEY_CLAIM_TX_HASH
        )
    }
}
