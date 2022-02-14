/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import androidx.paging.PagingData
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.extensions.toHexString
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    override fun observeCurAccountStorage(): Flow<String> {
        return flow {
            val id = credentialsRepository.getAccountId()
            emitAll(walletRepository.observeStorageAccount(id))
        }
    }

    override fun getEventsFlow(assetId: String): Flow<PagingData<Transaction>> {
        return flow {
            val address = credentialsRepository.getAddress()
            emitAll(walletRepository.getTransactionsFlow(address, assetId))
        }
    }

    override suspend fun getTransaction(txHash: String) = walletRepository.getTransaction(txHash)

    override suspend fun saveMigrationStatus(migrationStatus: MigrationStatus) {
        return walletRepository.saveMigrationStatus(migrationStatus)
    }

    override fun observeMigrationStatus(): Flow<MigrationStatus> {
        return walletRepository.observeMigrationStatus()
    }

    override suspend fun needsMigration(): Boolean {
        val irohaAddress = credentialsRepository.getIrohaAddress()
        val needs = walletRepository.needsMigration(irohaAddress)
        userRepository.saveNeedsMigration(needs)
        return needs
    }

    override suspend fun migrate(): Boolean {
        val signature = credentialsRepository.getClaimSignature()
        val irohaAddress = credentialsRepository.getIrohaAddress()
        val irohaKeypair = credentialsRepository.retrieveIrohaKeyPair()
        val keypair = credentialsRepository.retrieveKeyPair()
        val result = walletRepository.migrate(
            irohaAddress,
            irohaKeypair.public.encoded.toHexString(),
            signature,
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
                    userRepository.saveNeedsMigration(!isSuccess)
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
        return credentialsRepository.getAddress().let {
            walletRepository.calcTransactionFee(it, to, assetId, amount)
        }
    }

    override suspend fun transfer(to: String, assetId: String, amount: BigDecimal): String =
        credentialsRepository.getAddress().let { address ->
            credentialsRepository.retrieveKeyPair().let { keypair ->
                walletRepository.transfer(keypair, address, to, assetId, amount)
            }
        }

    override suspend fun observeTransfer(
        to: String,
        assetId: String,
        amount: BigDecimal,
        fee: BigDecimal
    ): Boolean {
        val address = credentialsRepository.getAddress()
        val keypair = credentialsRepository.retrieveKeyPair()
        return walletRepository.observeTransfer(keypair, address, to, assetId, amount, fee)
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
                        null
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
        val address = credentialsRepository.getAddress()
        walletRepository.updateWhitelistBalances(address)
    }

    override suspend fun getWhitelistAssets(): List<Asset> {
        val address = credentialsRepository.getAddress()
        return walletRepository.getAssetsWhitelist(address)
    }

    override suspend fun getVisibleAssets(): List<Asset> {
        val address = credentialsRepository.getAddress()
        return walletRepository.getAssetsVisible(address)
    }

    override fun subscribeVisibleAssets(): Flow<List<Asset>> {
        return flow {
            val address = credentialsRepository.getAddress()
            emitAll(walletRepository.subscribeVisibleAssets(address))
        }
    }

    override suspend fun updateBalancesVisibleAssets() {
        walletRepository.updateBalancesVisibleAssets(credentialsRepository.getAddress())
    }

    override suspend fun getAddress(): String =
        credentialsRepository.getAddress()

    override suspend fun getPublicKey(): ByteArray {
        return credentialsRepository.getAccountId()
    }

    override suspend fun getPublicKeyHex(withPrefix: Boolean): String {
        return credentialsRepository.getAccountId().toHexString(withPrefix)
    }

    override suspend fun getAccountName(): String = userRepository.getAccountName()

    override suspend fun findOtherUsersAccounts(search: String): List<Account> {
        val address = credentialsRepository.getAddress()
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
        val myAddress = credentialsRepository.getAddress()
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
        return walletRepository.hideAssets(assetIds)
    }

    override suspend fun displayAssets(assetIds: List<String>) {
        return walletRepository.displayAssets(assetIds)
    }

    override suspend fun getXorBalance(precision: Int): XorAssetBalance {
        return credentialsRepository.getAddress()
            .let { myAddress -> walletRepository.getXORBalance(myAddress, precision) }
    }

    override suspend fun updateAssetPositions(assetPositions: Map<String, Int>) =
        walletRepository.updateAssetPositions(assetPositions)

    override suspend fun getAsset(assetId: String): Asset? {
        val address = credentialsRepository.getAddress()
        return walletRepository.getAsset(assetId, address)
    }

    override suspend fun isWhitelistedToken(tokenId: String): Boolean {
        return walletRepository.isWhitelistedToken(tokenId)
    }

    override suspend fun getFeeToken(): Token {
        return requireNotNull(walletRepository.getToken(OptionsProvider.feeAssetId))
    }
}
