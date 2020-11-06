/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import java.math.BigDecimal

interface WalletInteractor {

    fun getAssets(): Observable<List<Asset>>

    fun updateAssets(): Completable

    fun getAccountId(): Single<String>

    fun getBalance(assetId: String): Observable<AssetBalance>

    fun getValAndValErcBalanceAmount(): Observable<BigDecimal>

    fun getTransactions(): Observable<List<Transaction>>

    fun updateTransactions(pageSize: Int): Single<Int>

    fun loadMoreTransactions(pageSize: Int, offset: Int): Single<Int>

    fun findOtherUsersAccounts(search: String): Single<List<Account>>

    fun getQrCodeAmountString(amount: String): Single<String>

    fun transferAmount(amount: String, accountId: String, description: String, fee: String): Single<Pair<String, String>>

    fun getContacts(updateCached: Boolean): Single<List<Account>>

    fun getTransferMeta(): Observable<TransferMeta>

    fun getWithdrawMeta(): Observable<TransferMeta>

    fun updateTransferMeta(): Completable

    fun updateWithdrawMeta(): Completable

    fun processQr(contents: String): Single<Pair<BigDecimal, Account>>

    fun getBlockChainExplorerUrl(transactionHash: String): Single<String>

    fun getEtherscanExplorerUrl(transactionHash: String): Single<String>

    fun calculateDefaultMinerFeeInEthWithdraw(): Single<BigDecimal>

    fun calculateDefaultMinerFeeInEthTransfer(): Single<BigDecimal>

    fun hideAssets(assetIds: List<String>): Completable

    fun displayAssets(assetIds: List<String>): Completable

    fun updateAssetPositions(assetPositions: Map<String, Int>): Completable
}