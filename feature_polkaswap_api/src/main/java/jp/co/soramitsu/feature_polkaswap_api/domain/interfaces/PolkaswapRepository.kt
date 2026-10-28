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

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import kotlinx.coroutines.flow.Flow

interface PolkaswapRepository {

    fun subscribeBasicPools(): Flow<List<BasicPoolData>>

    suspend fun getPoolBaseTokens(): List<PoolDex>

    fun subscribePools(address: String): Flow<List<CommonUserPoolData>>

    suspend fun getPoolsCacheOfAccount(address: String): List<CommonUserPoolData>

    suspend fun getBasicPool(b: String, t: String): BasicPoolData?

    fun subscribePoolOfAccount(
        address: String,
        baseTokenId: String,
        targetTokenId: String,
    ): Flow<CommonPoolData?>

    fun subscribeLocalPoolReserves(
        address: String,
        baseTokenId: String,
        assetId: String
    ): Flow<LiquidityData?>

    fun getPolkaswapDisclaimerVisibility(): Flow<Boolean>

    suspend fun setPolkaswapDisclaimerVisibility(v: Boolean)

    suspend fun poolFavoriteOn(ids: StringPair, account: SoraAccount)

    suspend fun poolFavoriteOff(ids: StringPair, account: SoraAccount)

    suspend fun updatePoolPosition(pools: Map<StringPair, Int>, account: SoraAccount)
    suspend fun getBasicPools(): List<BasicPoolData>
}
