/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionBuilder
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapMath.isZero
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.math.BigDecimal
import java.util.Date

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val ethRepository: EthereumRepository,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cryptoAssistant: CryptoAssistant,
    private val coroutineManager: CoroutineManager,
) : WalletInteractor {

    override fun flowCurSoraAccount(): Flow<SoraAccount> =
        userRepository.flowCurSoraAccount()

    override fun observeCurAccountStorage(): Flow<String> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            walletRepository.observeStorageAccount(it.substrateAddress.toAccountId())
        }
    }

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

    override suspend fun calcTransactionFee(
        to: String,
        token: Token,
        amount: BigDecimal
    ): BigDecimal {
        return userRepository.getCurSoraAccount().let {
            walletRepository.calcTransactionFee(it.substrateAddress, to, token, amount)
        }
    }

    override suspend fun transfer(to: String, token: Token, amount: BigDecimal): Result<String> {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return walletRepository.transfer(keypair, soraAccount.substrateAddress, to, token, amount)
    }

    override suspend fun observeTransfer(
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val status = walletRepository.observeTransfer(
            keypair,
            soraAccount.substrateAddress,
            to,
            token,
            amount,
            fee
        )
        if (status.success) {
            transactionHistoryRepository.saveTransaction(
                TransactionBuilder.buildTransfer(
                    txHash = status.txHash,
                    blockHash = status.blockHash,
                    fee = fee,
                    status = TransactionStatus.PENDING,
                    date = Date().time,
                    amount = amount,
                    peer = to,
                    type = TransactionTransferType.OUTGOING,
                    token = token,
                )
            )
        }
        return status.success
    }

    override suspend fun updateWhitelistBalances() {
        val soraAccount = userRepository.getCurSoraAccount()
        walletRepository.updateWhitelistBalances(soraAccount.substrateAddress)

        if (needFakeBalance()) {
            addFakeBalance(soraAccount)
        }
    }

    private suspend fun needFakeBalance(): Boolean {
        val xorBalance = getWhitelistAssets().firstOrNull {
            it.token.id == SubstrateOptionsProvider.feeAssetId
        }
            ?.balance
            ?.transferable
            ?: BigDecimal.ZERO

        return BuildUtils.isFlavors(Flavor.SORALUTION) && xorBalance.isZero()
    }

    private suspend fun addFakeBalance(soraAccount: SoraAccount) {
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val assetsIds = AssetHolder.getIds().subList(0, AssetHolder.getIds().lastIndex)
        return walletRepository.addFakeBalance(keypair, soraAccount, assetsIds)
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

    override suspend fun getActiveAssets(): List<Asset> {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return walletRepository.getActiveAssets(address)
    }

    override fun subscribeActiveAssetsOfAccount(soraAccount: SoraAccount): Flow<List<Asset>> =
        walletRepository.subscribeActiveAssets(soraAccount.substrateAddress)

    override fun subscribeActiveAssetsOfCurAccount(): Flow<List<Asset>> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            walletRepository.subscribeActiveAssets(it.substrateAddress)
        }
    }

    override fun subscribeAssetOfCurAccount(tokenId: String): Flow<Asset> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            walletRepository.subscribeAsset(it.substrateAddress, tokenId)
        }
    }

    override suspend fun updateBalancesActiveAssets() {
        walletRepository.updateBalancesActiveAssets(userRepository.getCurSoraAccount().substrateAddress)
    }

    override suspend fun getAddress(): String = userRepository.getCurSoraAccount().substrateAddress

    override suspend fun getPublicKeyHex(withPrefix: Boolean): String {
        return userRepository.getCurSoraAccount().substrateAddress.toAccountId()
            .toHexString(withPrefix)
    }

    override suspend fun getAccountName(): String = userRepository.getCurSoraAccount().accountName

    override suspend fun getContacts(query: String): List<Account> {
        val address = userRepository.getCurSoraAccount().substrateAddress
        val contacts = transactionHistoryRepository.getContacts(query).filter {
            it != address && it != query
        }.map {
            Account("", "", it)
        }
        return buildList {
            if (credentialsRepository.isAddressOk(query)) {
                add(Account("", "", query))
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
                    val ok = credentialsRepository.isAddressOk(address)
                    if (!ok) throw QrException.userNotFoundError()
                    val whitelisted = walletRepository.isWhitelistedToken(tokenId)
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

    override suspend fun isVisibleToken(tokenId: String): Boolean {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return walletRepository.getAssetsVisible(address)
            .firstOrNull { it.token.id == tokenId } != null
    }

    override suspend fun getSoraAccounts(): List<SoraAccount> {
        return userRepository.soraAccountsList()
    }

    override suspend fun getFeeToken(): Token {
        return requireNotNull(walletRepository.getToken(SubstrateOptionsProvider.feeAssetId))
    }
}
