/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
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

    override suspend fun processQr(contents: String): Triple<String, String, BigDecimal> {
        val myAddress = userRepository.getCurSoraAccount().substrateAddress
        val list = contents.split(":")
        return if (list.size == 5) {
            if (list[0] == OptionsProvider.substrate) {
                val address = list[1]
                if (address == myAddress) {
                    throw QrException.sendingToMyselfError()
                } else {
                    val tokenId = list[4]
                    val ok = runtimeManager.isAddressOk(address)
                    if (!ok) throw QrException.userNotFoundError()
                    val whitelisted = assetsRepository.isWhitelistedToken(tokenId)
                    Triple(
                        address,
                        if (whitelisted) tokenId else SubstrateOptionsProvider.feeAssetId,
                        BigDecimal.ZERO
                    )
                }
            } else {
                throw QrException.decodeError()
            }
        } else {
            throw QrException.decodeError()
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
