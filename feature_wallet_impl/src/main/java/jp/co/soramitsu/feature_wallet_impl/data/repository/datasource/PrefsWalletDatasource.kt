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

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import javax.inject.Inject
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

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
