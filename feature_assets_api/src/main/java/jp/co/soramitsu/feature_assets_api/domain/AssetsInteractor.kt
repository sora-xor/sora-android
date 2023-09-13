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

package jp.co.soramitsu.feature_assets_api.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import kotlinx.coroutines.flow.Flow

interface AssetsInteractor {

    suspend fun calcTransactionFee(to: String, token: Token, amount: BigDecimal): BigDecimal?

    suspend fun isNotEnoughXorLeftAfterTransaction(
        networkFeeInXor: BigDecimal,
        xorChange: BigDecimal? = null,
    ): Boolean

    suspend fun getAccountName(): String

    suspend fun getAssetOrThrow(assetId: String): Asset

    suspend fun getCurSoraAccount(): SoraAccount

    suspend fun getPublicKeyHex(withPrefix: Boolean = false): String

    suspend fun getVisibleAssets(): List<Asset>

    suspend fun getWhitelistAssets(): List<Asset>

    suspend fun getXorBalance(precision: Int): XorAssetBalance

    fun flowCurSoraAccount(): Flow<SoraAccount>

    suspend fun isWhitelistedToken(tokenId: String): Boolean

    suspend fun getTokensList(): List<Token>

    suspend fun observeTransfer(
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): String

    fun subscribeAssetOfCurAccount(tokenId: String): Flow<Asset?>

    fun subscribeAssetsActiveOfCurAccount(): Flow<List<Asset>>

    fun subscribeAssetsFavoriteOfAccount(soraAccount: SoraAccount): Flow<List<Asset>>

    fun subscribeAssetsVisibleOfCurAccount(): Flow<List<Asset>>

    suspend fun toggleVisibilityOfToken(tokenId: String, visibility: Boolean)

    suspend fun tokenFavoriteOff(assetIds: List<String>)

    suspend fun tokenFavoriteOn(assetIds: List<String>)

    suspend fun transfer(to: String, token: Token, amount: BigDecimal): Result<String>

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>)

    suspend fun updateWhitelistBalances()

    suspend fun updateBalanceVisibleAssets()
}
