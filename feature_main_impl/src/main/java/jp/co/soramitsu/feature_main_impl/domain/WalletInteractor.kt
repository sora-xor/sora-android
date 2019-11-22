/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import java.math.BigDecimal
import javax.inject.Inject

class WalletInteractor @Inject constructor(
    private val walletRepository: WalletRepository,
    private val didRepository: DidRepository
) {

    companion object {
        private const val TRANSACTION_COUNT = 50
    }

    private var transactionHistoryOffset = 0

    fun getAccountId(): Single<String> {
        return didRepository.getAccountId()
    }

    fun getBalance(updateCached: Boolean): Single<BigDecimal> {
        return didRepository.retrieveKeypair()
            .flatMap { keyPair ->
                didRepository.getAccountId()
                    .flatMap { accountId -> walletRepository.getWalletBalance(updateCached, accountId, keyPair) }
            }
    }

    fun getTransactionHistory(updateCached: Boolean, adding: Boolean): Single<List<Transaction>> {
        if (!adding) {
            transactionHistoryOffset = 0
        }
        return walletRepository.getTransactions(updateCached, transactionHistoryOffset, TRANSACTION_COUNT)
            .doOnSuccess { transactionHistoryOffset += it.size }
            .subscribeOn(Schedulers.io())
    }

    fun findOtherUsersAccounts(search: String): Single<List<Account>> {
        return didRepository.getAccountId()
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

    fun getQrCodeAmountString(amount: String): Single<String> {
        return didRepository.getAccountId()
            .flatMap { accountId -> walletRepository.getQrAmountString(accountId, amount) }
            .subscribeOn(Schedulers.io())
    }

    fun transferAmount(amount: String, accountId: String, description: String, fee: String): Single<String> {
        return didRepository.getAccountId()
            .flatMap { myAccountId ->
                didRepository.retrieveKeypair()
                    .flatMap { keyPair ->
                        walletRepository.transfer(amount, myAccountId, accountId, description, fee, keyPair)
                    }
            }
    }

    fun getContacts(updateCached: Boolean): Single<List<Account>> {
        return walletRepository.getContacts(updateCached)
    }

    fun withdrawFlow(amount: String, ethAddress: String, notaryAddress: String, feeAddress: String, feeAmount: String): Completable {
        return didRepository.getAccountId()
            .flatMapCompletable { accountId ->
                didRepository.retrieveKeypair()
                    .flatMapCompletable { keyPair ->
                        walletRepository.withdrawEth(amount, accountId, notaryAddress, ethAddress, feeAddress, feeAmount, keyPair)
                    }
            }
    }

    fun getBalanceAndWithdrawalMeta(): Single<Pair<BigDecimal, WithdrawalMeta>> {
        return getBalance(true)
            .zipWith(walletRepository.getWithdrawalMeta(),
                BiFunction { balance, withdrawalMeta -> Pair(balance, withdrawalMeta) })
    }

    fun getBalanceAndTransferMeta(updateCached: Boolean): Single<Pair<BigDecimal, TransferMeta>> {
        return getBalance(updateCached)
            .zipWith(walletRepository.getTransferMeta(updateCached),
                BiFunction { balance, transferMeta -> Pair(balance, transferMeta) })
    }

    fun processQr(contents: String): Single<Pair<QrData, Account>> {
        return walletRepository.getQrDataFromString(contents)
            .flatMap { qr ->
                walletRepository.findAccount(qr.accountId)
                    .map {
                        if (it.isEmpty()) {
                            throw SoraException.businessError(ResponseCode.QR_USER_NOT_FOUND)
                        } else {
                            it.first()
                        }
                    }
                    .flatMap { account ->
                        checkAccountIsMine(account)
                            .map { isMine ->
                                if (isMine) {
                                    throw SoraException.businessError(ResponseCode.SENDING_TO_MYSELF)
                                } else {
                                    account
                                }
                            }
                    }
                    .map { Pair(qr, it) }
            }
    }

    private fun checkAccountIsMine(account: Account): Single<Boolean> {
        return didRepository.getAccountId()
            .map { areAccountsSame(account.accountId, it) }
    }
}