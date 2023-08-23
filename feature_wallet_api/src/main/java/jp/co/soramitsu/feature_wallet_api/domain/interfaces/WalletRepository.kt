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

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import kotlinx.coroutines.flow.Flow

interface WalletRepository {

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String>

    suspend fun needsMigration(irohaAddress: String): Boolean

    suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Sr25519Keypair,
        from: String,
    ): ExtrinsicSubmitStatus

    fun observeStorageAccount(address: String): Flow<String>

    fun subscribeVisibleCardsHubList(address: String): Flow<List<CardHub>>

    fun subscribeVisibleGlobalCardsHubList(): Flow<List<CardHub>>

    suspend fun updateCardVisibilityOnGlobalCardsHub(cardId: String, visible: Boolean)

    suspend fun updateCardVisibilityOnCardsHub(cardId: String, visible: Boolean)

    suspend fun updateCardCollapsedState(cardId: String, collapsed: Boolean)
}
