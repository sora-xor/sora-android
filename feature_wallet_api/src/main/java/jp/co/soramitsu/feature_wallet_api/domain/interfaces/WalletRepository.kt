/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.domain.CardHub
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
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

    /**
     * What is known now about a legacy claim this device already sent for [walletId], after one
     * read-only status check of that transaction. [txHash] names the claim; when it is null the
     * newest claim this device recorded for the account is used. Null when there is none.
     * Nothing is signed or sent, and a status read that fails only leaves the answer as it was.
     */
    suspend fun checkSubmittedClaim(walletId: String, txHash: String?): TransactionOutcome?

    fun observeStorageAccount(address: String): Flow<String>

    fun subscribeCardsHubList(address: String): Flow<List<CardHub>>

    fun subscribeVisibleGlobalCardsHubList(): Flow<List<CardHub>>

    suspend fun updateCardVisibilityOnGlobalCardsHub(cardId: String, visible: Boolean)

    suspend fun updateCardVisibilityOnCardsHub(cardId: String, visible: Boolean)

    suspend fun updateCardCollapsedState(cardId: String, collapsed: Boolean)
}
