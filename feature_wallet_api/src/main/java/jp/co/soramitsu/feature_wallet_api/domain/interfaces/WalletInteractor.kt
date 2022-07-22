/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.XorAssetBalance
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface WalletInteractor {

    fun observeCurAccountStorage(): Flow<String>

    fun flowCurSoraAccount(): Flow<SoraAccount>

    suspend fun getFeeToken(): Token

    suspend fun saveMigrationStatus(migrationStatus: MigrationStatus)

    fun observeMigrationStatus(): Flow<MigrationStatus>

    suspend fun needsMigration(): Boolean

    suspend fun migrate(): Boolean

    suspend fun getVisibleAssets(): List<Asset>

    suspend fun updateWhitelistBalances()

    suspend fun getWhitelistAssets(): List<Asset>

    fun subscribeVisibleAssetsOfCurAccount(): Flow<List<Asset>>

    fun subscribeVisibleAssetsOfAccount(soraAccount: SoraAccount): Flow<List<Asset>>

    suspend fun getActiveAssets(): List<Asset>

    fun subscribeAssetOfCurAccount(tokenId: String): Flow<Asset>

    fun subscribeActiveAssetsOfCurAccount(): Flow<List<Asset>>

    fun subscribeActiveAssetsOfAccount(soraAccount: SoraAccount): Flow<List<Asset>>

    suspend fun updateBalancesActiveAssets()

    suspend fun transfer(to: String, token: Token, amount: BigDecimal): Result<String>

    suspend fun observeTransfer(
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): Boolean

    suspend fun calcTransactionFee(to: String, token: Token, amount: BigDecimal): BigDecimal

    suspend fun getAddress(): String

    suspend fun getPublicKeyHex(withPrefix: Boolean = false): String

    suspend fun getAccountName(): String

    suspend fun getContacts(query: String): List<Account>

    suspend fun getXorBalance(precision: Int): XorAssetBalance

    suspend fun processQr(contents: String): Triple<String, String, BigDecimal>

    suspend fun hideAssets(assetIds: List<String>)

    suspend fun displayAssets(assetIds: List<String>)

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>)

    suspend fun getAssetOrThrow(assetId: String): Asset

    suspend fun isWhitelistedToken(tokenId: String): Boolean

    suspend fun isVisibleToken(tokenId: String): Boolean
}
