/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.sora.substrate.models.WithDesired
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface PolkaswapInteractor {

    suspend fun getPoolBaseTokens(): List<String>

    fun subscribePoolsCache(): Flow<List<PoolData>>
    fun subscribePoolCache(tokenFromId: String, tokenToId: String): Flow<PoolData>

    fun subscribeReservesCache(
        baseTokenId: String,
        assetId: String
    ): Flow<LiquidityData?>

    fun subscribePoolsChanges(): Flow<String>

    suspend fun updatePools()

    suspend fun getPoolStrategicBonusAPY(tokenId: String): Double?

    suspend fun updatePool(
        baseTokenId: String,
        tokenId: String
    )

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean

    suspend fun getAvailableSources(): List<Market>

    suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): List<Market>?

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
    ): Boolean

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

    fun checkLiquidityBalance(
        fromTokenBalance: BigDecimal,
        fromAmount: BigDecimal,
        toTokenBalance: BigDecimal,
        toAmount: BigDecimal,
        networkFee: BigDecimal
    ): Boolean

    suspend fun calcDetails(
        tokenFrom: Token,
        tokenTo: Token,
        feeToken: Token,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Float
    ): SwapDetails?

    suspend fun fetchSwapNetworkFee(feeToken: Token): BigDecimal

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
        slippageTolerance: Float,
        pairEnabled: Boolean,
        pairPresented: Boolean
    ): LiquidityDetails

    suspend fun fetchAddLiquidityNetworkFee(
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Float,
    ): BigDecimal

    suspend fun observeAddLiquidity(
        tokenFrom: Token,
        tokenTo: Token,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Float
    ): Boolean

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
        networkFee: BigDecimal
    ): Boolean

    suspend fun getLiquidityData(
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean
    ): LiquidityData
}
