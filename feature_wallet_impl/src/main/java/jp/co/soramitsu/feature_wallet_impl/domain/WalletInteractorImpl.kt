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

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.sora2TransactionOutcome
import kotlinx.coroutines.flow.Flow

class WalletInteractorImpl(
    private val assetsRepository: AssetsRepository,
    private val walletRepository: WalletRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val runtimeManager: RuntimeManager,
) : WalletInteractor {

    override suspend fun saveMigrationStatus(migrationStatus: MigrationStatus) {
        return walletRepository.saveMigrationStatus(migrationStatus)
    }

    override fun observeMigrationStatus(): Flow<MigrationStatus> {
        return walletRepository.observeMigrationStatus()
    }

    override suspend fun needsMigration(): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val irohaData = credentialsRepository.getIrohaData(soraAccount)
        val needs = walletRepository.needsMigration(irohaData.address)
        userRepository.saveNeedsMigration(needs, soraAccount)
        return needs
    }

    override suspend fun migrate(): TransactionOutcome? {
        val soraAccount = userRepository.getCurSoraAccount()
        val irohaData = credentialsRepository.getIrohaData(soraAccount)
        // The claim screen can remain open while another client completes the legacy migration.
        // Re-read the authoritative account-bound state before touching the SORA2 signing key;
        // a stale local/fetched flag is never authority to submit a duplicate claim.
        if (!walletRepository.needsMigration(irohaData.address)) {
            userRepository.saveNeedsMigration(false, soraAccount)
            return null
        }
        check(
            userRepository.getCurSoraAccount().substrateAddress ==
                soraAccount.substrateAddress
        ) { "MIGRATION_SELECTED_WALLET_CHANGED" }
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val outcome = try {
            sora2TransactionOutcome {
                walletRepository.migrate(
                    irohaData.address,
                    irohaData.publicKey,
                    irohaData.claimSignature,
                    keypair,
                    soraAccount.substrateAddress,
                )
            }
        } finally {
            keypair.privateKey.fill(0)
            keypair.nonce.fill(0)
        }
        claimDoneWhenConfirmed(outcome, soraAccount)
        return outcome
    }

    override suspend fun checkSubmittedClaim(txHash: String?): TransactionOutcome? {
        val soraAccount = userRepository.getCurSoraAccount()
        val outcome = walletRepository.checkSubmittedClaim(
            walletId = soraAccount.substrateAddress,
            txHash = txHash,
        )
        claimDoneWhenConfirmed(outcome, soraAccount)
        return outcome
    }

    /**
     * The network confirmed the claim of [account]: it is no longer pending for that account
     * (the one that sent it, whichever account is selected now), so the wallet home's "Claim"
     * entry goes. Only a confirmation counts; a claim that waits or failed stays pending.
     */
    private suspend fun claimDoneWhenConfirmed(outcome: TransactionOutcome?, account: SoraAccount) {
        if (outcome is TransactionOutcome.Confirmed) userRepository.saveNeedsMigration(false, account)
    }

    override suspend fun getContacts(query: String): List<String>? {
        val curAccount = userRepository.getCurSoraAccount().substrateAddress
        if (curAccount == query) return null
        val contacts = transactionHistoryRepository.getContacts(curAccount, query).filter {
            it != curAccount && it != query && runtimeManager.isAddressOk(it)
        }
        return buildList {
            if (runtimeManager.isAddressOk(query) && query != curAccount) {
                add(query)
            }
            addAll(contacts)
        }
    }

    override suspend fun getSoraAccounts(): List<SoraAccount> {
        return userRepository.soraAccountsList()
    }

    override suspend fun getFeeToken(): Token {
        return requireNotNull(assetsRepository.getToken(SubstrateOptionsProvider.feeAssetId))
    }
}
