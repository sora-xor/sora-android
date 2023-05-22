/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_api.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.sora.substrate.models.WithDesired
import kotlinx.coroutines.flow.Flow

interface PoolsInteractor : PolkaswapInteractor {

    fun subscribePoolsCache(): Flow<List<PoolData>>

    suspend fun getPoolsCache(): List<PoolData>

    fun subscribePoolCache(tokenFromId: String, tokenToId: String): Flow<PoolData?>

    fun subscribePoolsCacheOfAccount(account: SoraAccount): Flow<List<PoolData>>

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
        pairPresented: Boolean
    ): LiquidityDetails

    suspend fun observeAddLiquidity(
        tokenFrom: Token,
        tokenTo: Token,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double
    ): String

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
    ): String

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
