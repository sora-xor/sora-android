/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_assets_api.data

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
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

    suspend fun getAssetsActive(address: String): List<Asset>

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
        fee: BigDecimal,
        validateSelectedWallet: suspend () -> Unit,
        /** True only after the user confirmed sending the same transfer again while one waits. */
        confirmedDuplicate: Boolean = false,
    ): ExtrinsicSubmitStatus

    fun subscribeAsset(
        address: String,
        tokenId: String,
    ): Flow<Asset?>

    fun subscribeAssetsActive(
        address: String
    ): Flow<List<Asset>>

    suspend fun toggleVisibilityOfToken(
        tokenId: String,
        visibility: Boolean,
        soraAccount: SoraAccount
    )

    suspend fun tokensList(): List<Token>

    fun subscribeTokensList(): Flow<List<Token>>

    suspend fun transfer(
        keypair: Sr25519Keypair,
        from: String,
        to: String,
        token: Token,
        amount: BigDecimal
    ): Result<String>

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>, soraAccount: SoraAccount)

    suspend fun updateBalancesVisibleAssets(address: String)

    suspend fun updateWhitelistBalances(address: String)

    suspend fun fetchBalance(address: String, ids: List<String>): List<BigInteger>
}
