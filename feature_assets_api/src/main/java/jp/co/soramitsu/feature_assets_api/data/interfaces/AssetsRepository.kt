/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_api.data.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_assets_api.data.models.XorAssetBalance
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import kotlinx.coroutines.flow.Flow

interface AssetsRepository {

    suspend fun addFakeBalance(
        keypair: Sr25519Keypair,
        soraAccount: SoraAccount,
        assetIds: List<String>
    )

    suspend fun calcTransactionFee(
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): BigDecimal?

    suspend fun displayAssets(assetIds: List<String>, soraAccount: SoraAccount)

    suspend fun getAsset(assetId: String, address: String): Asset?

    suspend fun getAssetsFavorite(address: String,): List<Asset>

    suspend fun getAssetsVisible(
        address: String,
    ): List<Asset>

    suspend fun getAssetsWhitelist(address: String): List<Asset>

    suspend fun getToken(tokenId: String): Token?

    suspend fun getXORBalance(address: String, precision: Int): XorAssetBalance

    suspend fun hideAssets(assetIds: List<String>, soraAccount: SoraAccount)

    suspend fun isWhitelistedToken(tokenId: String): Boolean

    suspend fun observeTransfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): ExtrinsicSubmitStatus

    fun subscribeAsset(
        address: String,
        tokenId: String,
    ): Flow<Asset>

    fun subscribeAssetsActive(
        address: String
    ): Flow<List<Asset>>

    fun subscribeAssetsFavorite(
        address: String
    ): Flow<List<Asset>>

    fun subscribeAssetsVisible(
        address: String
    ): Flow<List<Asset>>

    suspend fun toggleVisibilityOfToken(
        tokenId: String,
        visibility: Boolean,
        soraAccount: SoraAccount
    )

    suspend fun tokensList(): List<Token>

    suspend fun transfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): Result<String>

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>, soraAccount: SoraAccount)

    suspend fun updateBalancesVisibleAssets(address: String)

    suspend fun updateWhitelistBalances(address: String, update: Boolean)
}
