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
