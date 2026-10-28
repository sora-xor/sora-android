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

package jp.co.soramitsu.feature_polkaswap_api.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import kotlinx.coroutines.flow.Flow

interface PoolsInteractor : PolkaswapInteractor {

    fun subscribePoolsCacheOfCurAccount(): Flow<List<CommonUserPoolData>>
    fun subscribePoolsCacheOfAccount(account: SoraAccount): Flow<List<CommonUserPoolData>>

    suspend fun getRewardToken(): Token

    suspend fun getPoolsCacheOfCurAccount(): List<CommonUserPoolData>

    suspend fun getPoolOfCurAccount(ids: StringPair): CommonUserPoolData?

    suspend fun getBasicPool(poolIds: StringPair): BasicPoolData?

    suspend fun getBasicPools(): List<BasicPoolData>

    fun subscribePoolCacheOfCurAccount(tokenFromId: String, tokenToId: String): Flow<CommonPoolData?>

    fun subscribeReservesCache(
        baseTokenId: String,
        assetId: String
    ): Flow<LiquidityData?>

    fun subscribePoolsChanges(): Flow<String>

    fun subscribePoolsChangesOfAccount(address: String): Flow<String>

    suspend fun updatePools()

    suspend fun fetchRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token
    ): BigDecimal

    suspend fun calcLiquidityDetails(
        tokenFrom: Token,
        tokenTo: Token,
        reservesFrom: BigDecimal,
        reservesTo: BigDecimal,
        pooledTo: BigDecimal,
        baseAmount: BigDecimal,
        targetAmount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
        pairEnabled: Boolean,
        pairPresented: Boolean,
    ): LiquidityDetails

    suspend fun observeAddLiquidity(
        tokenFrom: Token,
        tokenTo: Token,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double,
        /** True only after the user confirmed sending the same action again while one is waiting. */
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome

    fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String
    ): Flow<Boolean>

    fun isPairPresentedInNetwork(
        baseTokenId: String,
        tokenId: String
    ): Flow<Boolean>

    suspend fun removeLiquidity(
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        networkFee: BigDecimal,
        /** True only after the user confirmed sending the same action again while one is waiting. */
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome

    suspend fun getLiquidityData(
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean
    ): LiquidityData

    suspend fun updatePoolPosition(pools: Map<StringPair, Int>)

    suspend fun poolFavoriteOn(ids: StringPair)

    suspend fun poolFavoriteOff(ids: StringPair)
}
