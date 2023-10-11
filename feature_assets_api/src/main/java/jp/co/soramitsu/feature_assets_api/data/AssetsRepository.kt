/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_assets_api.data

import java.math.BigDecimal
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
        fee: BigDecimal
    ): ExtrinsicSubmitStatus

    fun subscribeAsset(
        address: String,
        tokenId: String,
    ): Flow<Asset?>

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
}
