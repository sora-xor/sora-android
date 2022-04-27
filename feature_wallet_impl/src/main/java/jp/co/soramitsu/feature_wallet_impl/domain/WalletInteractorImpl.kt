/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import androidx.paging.PagingData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.blake2b256String
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val ethRepository: EthereumRepository,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cryptoAssistant: CryptoAssistant,
    private val coroutineManager: CoroutineManager,
) : WalletInteractor {

    init {
        userRepository.flowCurSoraAccount()
            .onEach {
                walletRepository.setCurSoraAccount(it)
            }
            .launchIn(coroutineManager.applicationScope)
    }

    override fun flowCurSoraAccount(): Flow<SoraAccount> =
        userRepository.flowCurSoraAccount()

    override fun observeCurAccountStorage(): Flow<String> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            walletRepository.observeStorageAccount(it.substrateAddress.toAccountId())
        }
    }

    override fun getEventsFlow(assetId: String): Flow<PagingData<Transaction>> {
        return walletRepository.getTransactionsFlow(assetId)
    }

    override suspend fun getTransaction(txHash: String) = walletRepository.getTransaction(txHash)

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
        val result = walletRepository.migrate(
            irohaData.address,
            irohaData.publicKey,
            irohaData.claimSignature,
            keypair
        )
            .catch {
                FirebaseWrapper.recordException(it)
                emit("" to ExtrinsicStatusResponse.ExtrinsicStatusPending(""))
            }
            .map {
                Triple(
                    it.first,
                    (it.second as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    it.second.subscription
                )
            }.transformWhile<Triple<String, String?, String>, Boolean> { value ->
                val finish = value.second?.let { blockHash ->
                    val txHash = value.first
                    val subscription = value.third
                    val blockResponse = walletRepository.getBlock(blockHash)
                    val extrinsicId =
                        blockResponse.block.extrinsics.indexOfFirst { s -> s.blake2b256String() == txHash }
                            .toLong()
                    val isSuccess = walletRepository.isTxSuccessful(extrinsicId, blockHash, txHash)
                    userRepository.saveNeedsMigration(!isSuccess, soraAccount)
                    true
                } ?: false
                emit(finish)
                value.second.isNullOrEmpty() && value.first.isNotEmpty()
            }.first {
                it
            }
        return result
    }

    override suspend fun calcTransactionFee(
        to: String,
        assetId: String,
        amount: BigDecimal
    ): BigDecimal {
        return userRepository.getCurSoraAccount().let {
            walletRepository.calcTransactionFee(it.substrateAddress, to, assetId, amount)
        }
    }

    override suspend fun transfer(to: String, assetId: String, amount: BigDecimal): String {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return walletRepository.transfer(keypair, soraAccount.substrateAddress, to, assetId, amount)
    }

    override suspend fun observeTransfer(
        to: String,
        assetId: String,
        amount: BigDecimal,
        fee: BigDecimal
    ): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return walletRepository.observeTransfer(
            keypair,
            soraAccount.substrateAddress,
            to,
            assetId,
            amount,
            fee
        )
            .catch {
                FirebaseWrapper.recordException(it)
                emit("" to ExtrinsicStatusResponse.ExtrinsicStatusPending(""))
            }
            .map {
                if (it.first.isNotEmpty()) {
                    walletRepository.saveTransfer(
                        to,
                        assetId,
                        amount,
                        it.second,
                        it.first,
                        fee,
                        null,
                        soraAccount
                    )
                }
                Triple(
                    it.first,
                    (it.second as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    it.second.subscription
                )
            }
            .transformWhile { value ->
                this.emit(value.first.isNotEmpty())
                value.second?.let { blockHash ->
                    val txHash = value.first
                    // val subscription = value.third
                    val blockResponse = walletRepository.getBlock(blockHash)
                    val extrinsicId = blockResponse.block.extrinsics.indexOfFirst { s ->
                        s.blake2b256String() == txHash
                    }.toLong()
                    val isSuccess = walletRepository.isTxSuccessful(extrinsicId, blockHash, txHash)
                    walletRepository.updateTransactionSuccess(txHash, isSuccess)
                    // walletRepository.unwatch(subscription)
                }
                value.second.isNullOrEmpty() && value.first.isNotEmpty()
            }.stateIn(coroutineManager.applicationScope).first()
    }

    override suspend fun updateWhitelistBalances() {
        val address = userRepository.getCurSoraAccount().substrateAddress
        walletRepository.updateWhitelistBalances(address)
    }

    override suspend fun getWhitelistAssets(): List<Asset> {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return walletRepository.getAssetsWhitelist(address)
    }

    override suspend fun getVisibleAssets(): List<Asset> {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return walletRepository.getAssetsVisible(address)
    }

    override fun subscribeVisibleAssetsOfCurAccount(): Flow<List<Asset>> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            walletRepository.subscribeVisibleAssets(it.substrateAddress)
        }
    }

    override fun subscribeVisibleAssetsOfAccount(soraAccount: SoraAccount): Flow<List<Asset>> =
        walletRepository.subscribeVisibleAssets(soraAccount.substrateAddress)

    override suspend fun updateBalancesVisibleAssets() {
        walletRepository.updateBalancesVisibleAssets(userRepository.getCurSoraAccount().substrateAddress)
    }

    override suspend fun getAddress(): String = userRepository.getCurSoraAccount().substrateAddress

    override suspend fun getPublicKeyHex(withPrefix: Boolean): String {
        return userRepository.getCurSoraAccount().substrateAddress.toAccountId()
            .toHexString(withPrefix)
    }

    override suspend fun getAccountName(): String = userRepository.getCurSoraAccount().accountName

    override suspend fun findOtherUsersAccounts(search: String): List<Account> {
        val address = userRepository.getCurSoraAccount().substrateAddress
        val ok = credentialsRepository.isAddressOk(search)
        val list = if (ok && address != search) {
            listOf(Account("", "", search))
        } else {
            emptyList()
        }
        val contacts = walletRepository.getContacts(search)
            .map { s -> Account("", "", s) }
        return listOf(list, contacts).flatten()
    }

    override suspend fun getContacts(query: String): List<Account> {
        return walletRepository.getContacts(query).map {
            Account("", "", it)
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
                    val ok = credentialsRepository.isAddressOk(address)
                    val whitelisted = walletRepository.isWhitelistedToken(tokenId)
                    if (!ok || !whitelisted) throw QrException.userNotFoundError() else Triple(
                        address,
                        tokenId,
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

    override suspend fun hideAssets(assetIds: List<String>) {
        val curAccount = userRepository.getCurSoraAccount()
        return walletRepository.hideAssets(assetIds, curAccount)
    }

    override suspend fun displayAssets(assetIds: List<String>) {
        val curAccount = userRepository.getCurSoraAccount()
        return walletRepository.displayAssets(assetIds, curAccount)
    }

    override suspend fun getXorBalance(precision: Int): XorAssetBalance {
        return userRepository.getCurSoraAccount()
            .let { account -> walletRepository.getXORBalance(account.substrateAddress, precision) }
    }

    override suspend fun updateAssetPositions(assetPositions: Map<String, Int>) {
        val curAccount = userRepository.getCurSoraAccount()
        walletRepository.updateAssetPositions(assetPositions, curAccount)
    }

    override suspend fun getAssetOrThrow(assetId: String): Asset {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return walletRepository.getAsset(assetId, address)
            ?: throw NoSuchElementException("$assetId not found")
    }

    override suspend fun isWhitelistedToken(tokenId: String): Boolean {
        return walletRepository.isWhitelistedToken(tokenId)
    }

    override suspend fun getFeeToken(): Token {
        return requireNotNull(walletRepository.getToken(OptionsProvider.feeAssetId))
    }
}
