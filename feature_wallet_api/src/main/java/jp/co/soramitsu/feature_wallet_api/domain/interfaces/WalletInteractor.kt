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

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import kotlinx.coroutines.flow.Flow

interface WalletInteractor {

    suspend fun getFeeToken(): Token

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun needsMigration(): Boolean

    /**
     * Submits the legacy claim and reports what is known about it. Null means there was nothing
     * to send: the network already has this account's claim.
     */
    suspend fun migrate(): TransactionOutcome?

    /**
     * What is known now about a legacy claim this device already sent for the current account,
     * after one read-only status check. [txHash] names the claim; when it is null the newest
     * claim this device recorded for the account is used. Null when there is none. Nothing is
     * signed or sent.
     */
    suspend fun checkSubmittedClaim(txHash: String?): TransactionOutcome?

    suspend fun getContacts(query: String): List<String>?

    suspend fun getSoraAccounts(): List<SoraAccount>
}
