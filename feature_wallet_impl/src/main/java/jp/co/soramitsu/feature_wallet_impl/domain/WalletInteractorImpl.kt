/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_ethereum_api.domain.model.EthRegisterState
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.AccountSettings
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val ethRepository: EthereumRepository,
    private val accountSettings: AccountSettings
) : WalletInteractor {

    override fun getAssets(): Observable<List<Asset>> {
        return Observable.combineLatest(
            walletRepository.getAssets().subscribeOn(Schedulers.io()),
            ethRepository.observeEthRegisterState().subscribeOn(Schedulers.io()),
            BiFunction<List<Asset>, EthRegisterState.State, List<Asset>> { assets, ethRegisterState ->
                val valAsset = assets[0]

                val ethAssetState = when (ethRegisterState) {
                    EthRegisterState.State.NONE -> Asset.State.ASSOCIATING
                    EthRegisterState.State.IN_PROGRESS -> Asset.State.ASSOCIATING
                    EthRegisterState.State.REGISTERED -> Asset.State.NORMAL
                    EthRegisterState.State.FAILED -> Asset.State.ERROR
                }
                val ethAsset = assets[1].copy(state = ethAssetState)
                val valErc20Asset = assets[2].copy(state = ethAssetState)

                mutableListOf<Asset>().apply {
                    add(valAsset)
                    add(ethAsset)
                    add(valErc20Asset)
                }
            }
        )
    }

    override fun updateAssets(): Completable {
        return walletRepository.updateAssets(accountSettings)
            .andThen(
                accountSettings.mnemonic()
                    .flatMap { ethRepository.getEthCredentials(it) }
                    .flatMap { ethCredentials ->
                        ethRepository.getEthWalletAddress(ethCredentials)
                            .flatMap { ethWalletAddress ->
                                ethRepository.getValTokenAddress(ethCredentials)
                                    .map { Triple(ethCredentials, ethWalletAddress, it) }
                            }
                    }
                    .flatMapCompletable { ethRepository.updateValErc20AndEthBalance(it.first, it.second, it.third) }

            )
    }

    override fun getAccountId(): Single<String> {
        return accountSettings.getAccountId()
    }

    override fun getBalance(assetId: String): Observable<AssetBalance> {
        return walletRepository.getBalance(assetId)
    }

    override fun getValAndValErcBalanceAmount(): Observable<BigDecimal> {
        return walletRepository.getBalance(AssetHolder.SORA_VAL.id)
            .flatMap { valBalance ->
                walletRepository.getBalance(AssetHolder.SORA_VAL_ERC_20.id)
                    .map { mutableListOf(valBalance, it) }
            }
            .map {
                it.fold<AssetBalance, BigDecimal>(BigDecimal.ZERO) { amount, assetBalance ->
                    amount + assetBalance.balance
                }
            }
    }

    override fun getTransactions(): Observable<List<Transaction>> {
        return accountSettings.getAccountId()
            .flatMap { accountId ->
                accountSettings.mnemonic()
                    .flatMap {
                        ethRepository.getEthCredentials(it)
                            .flatMap {
                                ethRepository.getEthWalletAddress(it)
                                    .map { Pair(accountId, it) }
                            }
                    }
            }
            .flatMapObservable { walletRepository.getTransactions(it.first, it.second) }
    }

    override fun updateTransactions(pageSize: Int): Single<Int> {
        return accountSettings.getAccountId()
            .flatMap {
                walletRepository.fetchRemoteTransactions(pageSize, 0, it)
            }
    }

    override fun loadMoreTransactions(pageSize: Int, offset: Int): Single<Int> {
        return accountSettings.getAccountId()
            .flatMap { walletRepository.fetchRemoteTransactions(pageSize, offset, it) }
    }

    override fun findOtherUsersAccounts(search: String): Single<List<Account>> {
        return accountSettings.getAccountId()
            .flatMap { userAccountId ->
                walletRepository.findAccount(search)
                    .map { it.filter { !areAccountsSame(userAccountId, it.accountId) } }
            }
    }

    private fun areAccountsSame(accountId1: String, accountId2: String): Boolean {
        return removeDomainFromAccountId(accountId1) == removeDomainFromAccountId(accountId2)
    }

    private fun removeDomainFromAccountId(accountId: String): String {
        return accountId.substring(0, accountId.indexOf("@"))
    }

    override fun getQrCodeAmountString(amount: String): Single<String> {
        return accountSettings.getAccountId()
            .flatMap { accountId -> walletRepository.getQrAmountString(accountId, amount) }
            .subscribeOn(Schedulers.io())
    }

    override fun transferAmount(amount: String, accountId: String, description: String, fee: String): Single<Pair<String, String>> {
        return accountSettings.getAccountId()
            .flatMap { myAccountId ->
                accountSettings.getKeyPair()
                    .flatMap { keyPair -> walletRepository.transfer(amount, myAccountId, accountId, description, fee, keyPair) }
                    .map { Pair(it, myAccountId) }
            }
    }

    override fun getContacts(updateCached: Boolean): Single<List<Account>> {
        return walletRepository.getContacts(updateCached)
    }

    override fun getTransferMeta(): Observable<TransferMeta> {
        return walletRepository.getTransferMeta()
    }

    override fun getWithdrawMeta(): Observable<TransferMeta> {
        return walletRepository.getWithdrawMeta()
    }

    override fun updateTransferMeta(): Completable {
        return walletRepository.updateTransferMeta()
    }

    override fun updateWithdrawMeta(): Completable {
        return walletRepository.updateWithdrawMeta()
    }

    override fun processQr(contents: String): Single<Pair<BigDecimal, Account>> {
        return walletRepository.getQrDataFromString(contents)
            .flatMap { qr ->
                walletRepository.findAccount(qr.accountId)
                    .map {
                        if (it.isEmpty()) {
                            throw QrException.userNotFoundError()
                        } else {
                            it.first()
                        }
                    }
                    .flatMap { account ->
                        checkAccountIsMine(account)
                            .map { isMine ->
                                if (isMine) {
                                    throw QrException.sendingToMyselfError()
                                } else {
                                    account
                                }
                            }
                    }
                    .map { Pair(qr, it) }
            }
            .flatMap { qrData ->
                parseAmountFromQr(qrData.first.amount)
                    .map { Pair(it, qrData.second) }
            }
    }

    private fun parseAmountFromQr(amount: String?): Single<BigDecimal> {
        return Single.fromCallable { amount?.let { BigDecimal(it) } ?: BigDecimal.ZERO }
            .onErrorResumeNext { Single.just(BigDecimal.ZERO) }
    }

    private fun checkAccountIsMine(account: Account): Single<Boolean> {
        return accountSettings.getAccountId()
            .map { areAccountsSame(account.accountId, it) }
    }

    override fun getBlockChainExplorerUrl(transactionHash: String): Single<String> {
        return walletRepository.getBlockChainExplorerUrl(transactionHash)
    }

    override fun getEtherscanExplorerUrl(transactionHash: String): Single<String> {
        return ethRepository.getBlockChainExplorerUrl(transactionHash)
    }

    override fun calculateDefaultMinerFeeInEthWithdraw(): Single<BigDecimal> {
        return calculateDefaultMinerFeeInEth(true)
    }

    override fun calculateDefaultMinerFeeInEthTransfer(): Single<BigDecimal> {
        return calculateDefaultMinerFeeInEth(false)
    }

    private fun calculateDefaultMinerFeeInEth(isWithdrawal: Boolean): Single<BigDecimal> {
        return if (isWithdrawal) {
            ethRepository.calculateValErc20WithdrawFee()
        } else {
            ethRepository.calculateValErc20TransferFee()
        }
    }

    override fun hideAssets(assetIds: List<String>): Completable {
        return walletRepository.hideAssets(assetIds)
    }

    override fun displayAssets(assetIds: List<String>): Completable {
        return walletRepository.displayAssets(assetIds)
    }

    override fun updateAssetPositions(assetPositions: Map<String, Int>): Completable {
        return walletRepository.updateAssetPositions(assetPositions)
    }
}