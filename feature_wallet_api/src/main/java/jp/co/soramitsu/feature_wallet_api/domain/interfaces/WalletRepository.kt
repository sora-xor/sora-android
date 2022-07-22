/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface WalletRepository {

    suspend fun tokensList(): List<Token>

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun retrieveClaimBlockAndTxHash(): Pair<String, String>

    suspend fun needsMigration(irohaAddress: String): Boolean

    suspend fun migrate(
        irohaAddress: String,
        irohaPublicKey: String,
        signature: String,
        keypair: Sr25519Keypair,
        from: String,
    ): ExtrinsicSubmitStatus

    suspend fun getAssetsWhitelist(address: String): List<Asset>

    suspend fun updateWhitelistBalances(address: String)

    suspend fun getAssetsVisible(
        address: String,
    ): List<Asset>

    fun subscribeVisibleAssets(
        address: String
    ): Flow<List<Asset>>

    suspend fun getActiveAssets(
        address: String,
    ): List<Asset>

    fun subscribeAsset(
        address: String,
        tokenId: String,
    ): Flow<Asset>

    fun subscribeActiveAssets(
        address: String
    ): Flow<List<Asset>>

    suspend fun updateBalancesActiveAssets(address: String)

    suspend fun transfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): Result<String>

    suspend fun observeTransfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): ExtrinsicSubmitStatus

    suspend fun calcTransactionFee(
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): BigDecimal

    suspend fun getXORBalance(address: String, precision: Int): XorAssetBalance

    suspend fun hideAssets(assetIds: List<String>, soraAccount: SoraAccount)

    suspend fun displayAssets(assetIds: List<String>, soraAccount: SoraAccount)

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>, soraAccount: SoraAccount)

    suspend fun getAsset(assetId: String, address: String): Asset?

    suspend fun getToken(tokenId: String): Token?

    suspend fun isWhitelistedToken(tokenId: String): Boolean

    fun observeStorageAccount(account: Any): Flow<String>

    suspend fun addFakeBalance(
        keypair: Sr25519Keypair,
        soraAccount: SoraAccount,
        assetIds: List<String>
    )
}
