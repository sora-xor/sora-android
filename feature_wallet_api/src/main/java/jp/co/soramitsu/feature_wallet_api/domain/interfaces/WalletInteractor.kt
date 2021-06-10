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
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.math.BigDecimal

interface WalletInteractor {

    fun saveMigrationStatus(migrationStatus: MigrationStatus): Completable

    fun observeMigrationStatus(): Observable<MigrationStatus>

    fun needsMigration(): Single<Boolean>

    fun migrate(): Single<Boolean>

    fun getAssets(forceUpdateBalance: Boolean = false, forceUpdateAssets: Boolean = false): Single<List<Asset>>

    fun transfer(to: String, assetId: String, amount: BigDecimal): Single<String>

    fun observeTransfer(to: String, assetId: String, amount: BigDecimal, fee: BigDecimal): Completable

    fun calcTransactionFee(to: String, assetId: String, amount: BigDecimal): Single<BigDecimal>

    fun getAccountId(): Single<String>

    fun getPublicKey(): Single<ByteArray>

    fun getPublicKeyHex(withPrefix: Boolean = false): Single<String>

    fun getAccountName(): Single<String>

    fun getTransactions(): Observable<List<Transaction>>

    fun findOtherUsersAccounts(search: String): Single<List<Account>>

    fun getContacts(query: String): Single<List<Account>>

    fun processQr(contents: String): Single<Triple<String, String, BigDecimal>>

    fun hideAssets(assetIds: List<String>): Completable

    fun displayAssets(assetIds: List<String>): Completable

    fun updateAssetPositions(assetPositions: Map<String, Int>): Completable
}
