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
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapQuote
import kotlinx.coroutines.flow.Flow

interface PolkaswapSubscriptionRepository {

    fun subscribeEachBlock(): Flow<String>

    suspend fun getRemotePoolReserves(
        tokenFromId: String,
        tokenFromPrecision: Int,
        tokenToId: String,
        tokenToPrecision: Int,
        enabled: Boolean,
        presented: Boolean,
    ): LiquidityData

    suspend fun updateAccountPools(address: String)

    fun subscribeToPoolsAssets(address: String): Flow<String>

    fun subscribeToPoolsData(address: String): Flow<String>

    fun subscribeToBasicPools(): Flow<String>

    suspend fun updateBasicPools()

    fun observePoolXYKReserves(
        baseTokenId: String,
        tokenId: String
    ): Flow<String>

    fun observePoolTBCReserves(tokenId: String): Flow<String>

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String, dexId: Int): Boolean

    suspend fun getAvailableSources(tokenId1: String, tokenId2: String, dexId: Int): List<Market>

    suspend fun getSwapQuote(
        tokenId1: String,
        tokenId2: String,
        amount: BigDecimal,
        swapVariant: WithDesired,
        markets: List<Market>,
        feeToken: Token,
        dexId: Int,
    ): SwapQuote?

    fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String,
        accountAddress: String,
    ): Flow<Boolean>

    fun isPairPresentedInNetwork(
        baseTokenId: String,
        tokenId: String, accountAddress: String
    ): Flow<Boolean>
}
