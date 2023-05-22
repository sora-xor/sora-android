/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_api.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_assets_api.data.models.XorAssetBalance
import kotlinx.coroutines.flow.Flow

interface AssetsInteractor {

    suspend fun calcTransactionFee(to: String, token: Token, amount: BigDecimal): BigDecimal?

    suspend fun getAccountName(): String

    suspend fun getAssetOrThrow(assetId: String): Asset

    suspend fun getCurSoraAccount(): SoraAccount

    suspend fun getPublicKeyHex(withPrefix: Boolean = false): String

    suspend fun getVisibleAssets(): List<Asset>

    suspend fun getWhitelistAssets(): List<Asset>

    suspend fun getXorBalance(precision: Int): XorAssetBalance

    fun flowCurSoraAccount(): Flow<SoraAccount>

    suspend fun isWhitelistedToken(tokenId: String): Boolean

    suspend fun observeTransfer(
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): String

    fun subscribeAssetOfCurAccount(tokenId: String): Flow<Asset>

    fun subscribeAssetsActiveOfCurAccount(): Flow<List<Asset>>

    fun subscribeAssetsFavoriteOfAccount(soraAccount: SoraAccount): Flow<List<Asset>>

    fun subscribeAssetsVisibleOfCurAccount(): Flow<List<Asset>>

    suspend fun toggleVisibilityOfToken(tokenId: String, visibility: Boolean)

    suspend fun tokenFavoriteOff(assetIds: List<String>)

    suspend fun tokenFavoriteOn(assetIds: List<String>)

    suspend fun transfer(to: String, token: Token, amount: BigDecimal): Result<String>

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>)

    suspend fun updateBalancesVisibleAssets()

    suspend fun updateWhitelistBalances(update: Boolean)
}
