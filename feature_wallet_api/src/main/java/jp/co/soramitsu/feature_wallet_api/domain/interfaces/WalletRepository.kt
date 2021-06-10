/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockEvent
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.math.BigDecimal

interface WalletRepository {

    fun saveMigrationStatus(migrationStatus: MigrationStatus): Completable

    fun observeMigrationStatus(): Observable<MigrationStatus>

    fun retrieveClaimBlockAndTxHash(): Single<Pair<String, String>>

    fun needsMigration(irohaAddress: String): Single<Boolean>

    fun checkEvents(blockHash: String): Single<List<BlockEvent>>

    fun unwatch(subscription: String): Completable

    fun getBlock(blockHash: String): Single<BlockResponse>

    fun isTxSuccessful(extrinsicId: Long, blockHash: String, txHash: String): Single<Boolean>

    fun migrate(irohaAddress: String, irohaPublicKey: String, signature: String, keypair: Keypair, address: String): Observable<Pair<String, ExtrinsicStatusResponse>>

    fun getAssets(address: String, forceUpdateBalances: Boolean = false, forceUpdateAssets: Boolean = false): Single<List<Asset>>

    fun transfer(keypair: Keypair, from: String, to: String, assetId: String, amount: BigDecimal): Single<String>

    fun observeTransfer(keypair: Keypair, from: String, to: String, assetId: String, amount: BigDecimal, fee: BigDecimal): Observable<Pair<String, ExtrinsicStatusResponse>>

    fun saveTransaction(from: String, to: String, assetId: String, amount: BigDecimal, status: ExtrinsicStatusResponse, hash: String, fee: BigDecimal, eventSuccess: Boolean?): Long

    fun updateTransactionSuccess(hash: String, success: Boolean)

    fun calcTransactionFee(from: String, to: String, assetId: String, amount: BigDecimal): Single<BigDecimal>

    fun getTransactions(myAddress: String, myEthAddress: String): Observable<List<Transaction>>

    fun getContacts(query: String): Single<Set<String>>

    fun hideAssets(assetIds: List<String>): Completable

    fun displayAssets(assetIds: List<String>): Completable

    fun updateAssetPositions(assetPositions: Map<String, Int>): Completable
}
