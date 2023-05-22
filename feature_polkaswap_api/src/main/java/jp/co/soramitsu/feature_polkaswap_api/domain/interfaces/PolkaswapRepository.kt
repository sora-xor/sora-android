/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_api.domain.interfaces

import java.math.BigDecimal
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapQuote
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.models.WithDesired
import kotlinx.coroutines.flow.Flow

interface PolkaswapRepository {

    suspend fun getPoolBaseTokens(): List<PoolDex>

    suspend fun updateAccountPools(address: String)

    fun subscribeToPoolsData(address: String): Flow<String>

    fun getPoolData(
        address: String,
        baseTokenId: String,
        tokenId: String
    ): Flow<PoolData?>

    fun subscribeToPoolsAssets(address: String): Flow<String>

    fun subscribePoolFlow(address: String): Flow<List<PoolData>>

    suspend fun getPoolsCache(address: String): List<PoolData>

    fun subscribeLocalPoolReserves(
        address: String,
        baseTokenId: String,
        assetId: String
    ): Flow<LiquidityData?>

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String, dexId: Int): Boolean

    suspend fun getAvailableSources(tokenId1: String, tokenId2: String, dexId: Int): List<Market>

    fun getPolkaswapDisclaimerVisibility(): Flow<Boolean>

    suspend fun setPolkaswapDisclaimerVisibility(v: Boolean)

    fun observePoolXYKReserves(
        baseTokenId: String,
        tokenId: String
    ): Flow<String>

    fun observePoolTBCReserves(tokenId: String): Flow<String>

    suspend fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Sr25519Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        dexId: Int,
    ): ExtrinsicSubmitStatus

    suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal?

    suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token,
        address: String
    ): BigDecimal?

    suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal?

    suspend fun observeAddLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): ExtrinsicSubmitStatus

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

    suspend fun getRemotePoolReserves(
        address: String,
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean
    ): LiquidityData

    suspend fun observeRemoveLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): ExtrinsicSubmitStatus

    suspend fun poolFavoriteOn(ids: StringPair, account: SoraAccount)

    suspend fun poolFavoriteOff(ids: StringPair, account: SoraAccount)

    suspend fun updatePoolPosition(pools: Map<StringPair, Int>, account: SoraAccount)
}
