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

package jp.co.soramitsu.feature_assets_api.domain

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import kotlinx.coroutines.flow.Flow

interface AssetsInteractor {

    suspend fun calcTransactionFee(
        to: String,
        token: Token,
        amount: BigDecimal,
        expectedWalletId: String,
    ): BigDecimal?

    suspend fun isNotEnoughXorLeftAfterTransaction(
        networkFeeInXor: BigDecimal,
        xorChange: BigDecimal? = null,
    ): Boolean

    suspend fun getAccountName(): String

    suspend fun getAssetOrThrow(assetId: String): Asset

    suspend fun getCurSoraAccount(): SoraAccount

    suspend fun getPublicKeyHex(withPrefix: Boolean = false): String

    suspend fun getWhitelistAssets(): List<Asset>

    suspend fun getXorBalance(precision: Int): XorAssetBalance

    fun flowCurSoraAccount(): Flow<SoraAccount>

    suspend fun isWhitelistedToken(tokenId: String): Boolean

    suspend fun getTokensList(): List<Token>

    suspend fun observeTransfer(
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal,
        expectedWalletId: String,
        /** True only after the user confirmed sending the same transfer again while one waits. */
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome

    fun subscribeAssetOfCurAccount(tokenId: String): Flow<Asset?>

    /**
     * Observes one asset for an explicitly bound account. Presentation that combines an address
     * with a balance must use this overload so an account switch cannot transiently pair the old
     * address with the newly selected account's asset stream.
     */
    fun subscribeAssetOfAccount(soraAccount: SoraAccount, tokenId: String): Flow<Asset?>

    fun subscribeAssetsActiveOfCurAccount(): Flow<List<Asset>>

    suspend fun toggleVisibilityOfToken(tokenId: String, visibility: Boolean)

    suspend fun tokenFavoriteOff(assetIds: List<String>)

    suspend fun tokenFavoriteOn(assetIds: List<String>)

    suspend fun transfer(to: String, token: Token, amount: BigDecimal): Result<String>

    suspend fun updateAssetPositions(assetPositions: Map<String, Int>)

    suspend fun updateWhitelistBalances()

    suspend fun updateBalanceVisibleAssets()

    suspend fun fetchBalance(
        address: String,
        ids: List<String>,
        precision: Int = OptionsProvider.defaultScale,
    ): List<BigDecimal>
}
