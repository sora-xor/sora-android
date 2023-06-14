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

package jp.co.soramitsu.feature_wallet_impl.domain

import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.common.domain.KycRepository
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WalletInteractorImpl(
    private val assetsRepository: AssetsRepository,
    private val walletRepository: WalletRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val runtimeManager: RuntimeManager,
    private val kycRepository: KycRepository,
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

    override suspend fun migrate(): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val irohaData = credentialsRepository.getIrohaData(soraAccount)
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return walletRepository.migrate(
            irohaData.address,
            irohaData.publicKey,
            irohaData.claimSignature,
            keypair,
            soraAccount.substrateAddress,
        ).success
    }

    override suspend fun getContacts(query: String): List<String>? {
        val curAccount = userRepository.getCurSoraAccount().substrateAddress
        if (curAccount == query) return null
        val contacts = transactionHistoryRepository.getContacts(query).filter {
            it != curAccount && it != query
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

    override suspend fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        walletRepository.updateSoraCardInfo(
            accessToken,
            refreshToken,
            accessTokenExpirationTime,
            kycStatus
        )
    }

    override fun subscribeSoraCardInfo(): Flow<SoraCardInformation?> =
        walletRepository.subscribeSoraCardInfo()

    override suspend fun getSoraCardInfo(): SoraCardInformation? =
        walletRepository.getSoraCardInfo()

    override suspend fun updateSoraCardKycStatus(kycStatus: String) {
        walletRepository.updateSoraCardKycStatus(kycStatus)
    }

    override fun pollSoraCardStatusIfPending(): Flow<String?> = flow {
        val pendingStatusString = SoraCardCommonVerification.Pending.toString()

        var isLoopInProgress = true
        var currentTimeInSeconds: Long

        while (isLoopInProgress) {
            currentTimeInSeconds =
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

            with(walletRepository.getSoraCardInfo()) {
                if (this == null ||
                    kycStatus != pendingStatusString ||
                    accessTokenExpirationTime < currentTimeInSeconds
                ) {
                    emit(this?.kycStatus)
                    isLoopInProgress = false
                    return@with
                }

                delay(POLLING_PERIOD_IN_MILLIS)

                kycRepository.getKycLastFinalStatus(accessToken).getOrNull()
                    ?.also { walletRepository.updateSoraCardKycStatus(it.toString()) }
            }
        }
    }

    private companion object {
        const val POLLING_PERIOD_IN_MILLIS = 30_000L
    }
}
