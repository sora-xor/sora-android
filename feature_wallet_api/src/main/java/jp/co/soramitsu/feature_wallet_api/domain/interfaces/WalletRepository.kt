/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import androidx.paging.PagingData
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockEvent
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface WalletRepository {

    fun getTransactionsFlow(address: String, assetId: String = ""): Flow<PagingData<Transaction>>

    suspend fun getTransaction(txHash: String): Transaction

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String>

    suspend fun needsMigration(irohaAddress: String): Boolean

    suspend fun checkEvents(blockHash: String): List<BlockEvent>

    suspend fun getBlock(blockHash: String): BlockResponse

    suspend fun isTxSuccessful(extrinsicId: Long, blockHash: String, txHash: String): Boolean

    suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Sr25519Keypair
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun getAssetsWhitelist(address: String): List<Asset>

    suspend fun updateWhitelistBalances(address: String)

    suspend fun getAssetsVisible(
        address: String,
    ): List<Asset>

    fun subscribeVisibleAssets(
        address: String
    ): Flow<List<Asset>>

    suspend fun updateBalancesVisibleAssets(address: String)

    suspend fun transfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal
    ): String

    suspend fun observeTransfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal,
        fee: BigDecimal
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun saveTransfer(
        to: String,
        assetId: String,
        amount: BigDecimal,
        status: ExtrinsicStatusResponse,
        hash: String,
        fee: BigDecimal,
        eventSuccess: Boolean?
    )

    suspend fun updateTransactionSuccess(hash: String, success: Boolean)

    suspend fun calcTransactionFee(
        from: String,
        to: String,
        assetId: String,
        amount: BigDecimal
    ): BigDecimal

    suspend fun getXORBalance(address: String, precision: Int): XorAssetBalance

    suspend fun getContacts(query: String): Set<String>

    suspend fun hideAssets(assetIds: List<String>)

    suspend fun displayAssets(assetIds: List<String>)

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>)

    suspend fun getAsset(assetId: String, address: String): Asset?

    suspend fun getToken(tokenId: String): Token?

    suspend fun isWhitelistedToken(tokenId: String): Boolean

    fun observeStorageAccount(account: Any): Flow<String>
}
