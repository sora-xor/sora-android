/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_api.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import jp.co.soramitsu.sora.substrate.models.WithDesired
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
        liquidityFee: BigDecimal,
        dexId: Int,
        amount2: BigDecimal,
    ): String

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
