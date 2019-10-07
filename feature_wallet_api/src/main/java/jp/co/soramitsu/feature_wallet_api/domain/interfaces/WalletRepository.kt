/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.QrData
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import java.math.BigDecimal
import java.security.KeyPair

interface WalletRepository {

    fun getTransactions(updateCached: Boolean, offset: Int, count: Int): Single<List<Transaction>>

    fun transfer(amount: String, myAccountId: String, dstUserId: String, description: String, fee: String, keyPair: KeyPair): Single<String>

    fun findAccount(search: String): Single<List<Account>>

    fun getContacts(updateCached: Boolean): Single<List<Account>>

    fun getLastTransactionDetails(): Single<Transaction>

    fun getWalletBalance(updateCached: Boolean, accountId: String, keyPair: KeyPair): Single<BigDecimal>

    fun transferD3(): Completable

    fun transferD3(amount: String, accountId: String, d3AccountId: String, keyPair: KeyPair): Completable

    fun getWithdrawalMeta(): Single<WithdrawalMeta>

    fun getTransferMeta(updateCached: Boolean): Single<TransferMeta>

    fun withdrawEth(amount: String, srcAccountId: String, notaryAddress: String, ethAddress: String, feeAddress: String, feeAmount: String, keyPair: KeyPair): Completable

    fun getQrAmountString(accountId: String, amount: String): Single<String>

    fun getQrDataFromString(content: String): Single<QrData>
}