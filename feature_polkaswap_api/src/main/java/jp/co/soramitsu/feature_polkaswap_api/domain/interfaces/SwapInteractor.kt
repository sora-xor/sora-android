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
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import kotlinx.coroutines.flow.Flow

interface SwapInteractor {

    suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): Set<Market>?

    fun getPolkaswapDisclaimerVisibility(): Flow<Boolean>

    suspend fun setPolkaswapDisclaimerVisibility(v: Boolean)

    suspend fun swap(
        tokenInput: Token,
        tokenOutput: Token,
        desired: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        networkFee: BigDecimal,
        dexId: Int,
        amount2: BigDecimal,
        /** True only after the user confirmed sending the same swap again while one is waiting. */
        confirmedDuplicate: Boolean = false,
    ): TransactionOutcome

    fun setSwapMarket(market: Market)

    fun observeSelectedMarket(): Flow<Market>

    fun observeSwap(): Flow<Boolean>

    fun observePoolReserves(): Flow<String>

    fun checkSwapBalances(
        fromToken: Token,
        fromTokenBalance: BigDecimal,
        fromAmount: BigDecimal,
        swapFee: BigDecimal,
        feeBalance: BigDecimal,
        feeToken: Token,
        toToken: Token,
        toTokenBalance: BigDecimal,
        toAmount: BigDecimal,
        desired: WithDesired,
        swapDetails: SwapDetails,
    ): Token?

    suspend fun calcDetails(
        tokenFrom: Token,
        tokenTo: Token,
        feeToken: Token,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double
    ): SwapDetails?

    suspend fun fetchSwapNetworkFee(feeToken: Token): BigDecimal
}
