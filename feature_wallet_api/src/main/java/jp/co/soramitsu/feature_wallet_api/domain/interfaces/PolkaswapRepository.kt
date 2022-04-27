/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface PolkaswapRepository {

    suspend fun updateAccountPools(address: String)

    suspend fun updateAccountPool(address: String, tokenId: String)

    suspend fun subscribeToPoolsData(address: String): Flow<String>

    suspend fun getPoolStrategicBonusAPY(tokenId: String): BigDecimal?

    fun getPoolData(address: String, tokenId: String): Flow<PoolData?>

    fun subscribeToPoolsAssets(address: String): Flow<String>

    fun subscribePoolFlow(address: String): Flow<List<PoolData>>

    fun subscribeLocalPoolReserves(address: String, assetId: String): Flow<LiquidityData?>

    suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean

    suspend fun getAvailableSources(tokenId1: String, tokenId2: String): List<Market>

    fun getPolkaswapDisclaimerVisibility(): Flow<Boolean>

    suspend fun setPolkaswapDisclaimerVisibility(v: Boolean)

    fun observePoolXYKReserves(tokenId: String): Flow<String>

    fun observePoolTBCReserves(tokenId: String): Flow<String>

    fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Sr25519Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal

    suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token,
        address: String
    ): BigDecimal

    suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal

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
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun getSwapQuote(
        tokenId1: String,
        tokenId2: String,
        amount: BigDecimal,
        swapVariant: WithDesired,
        markets: List<Market>
    ): SwapQuote?

    suspend fun saveSwap(
        txHash: String,
        status: ExtrinsicStatusResponse,
        fee: BigDecimal,
        eventSuccess: Boolean?,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        market: Market,
        liquidityFee: BigDecimal,
        soraAccount: SoraAccount,
    )

    suspend fun saveAddLiquidity(
        txHash: String,
        status: ExtrinsicStatusResponse,
        eventSuccess: Boolean?,
        networkFee: BigDecimal,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        soraAccount: SoraAccount
    )

    suspend fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String
    ): Flow<Boolean>

    suspend fun isPairPresentedInNetwork(tokenId: String): Flow<Boolean>

    suspend fun getRemotePoolReserves(
        address: String,
        runtime: RuntimeSnapshot,
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean
    ): LiquidityData

    fun observeRemoveLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): Flow<Pair<String, ExtrinsicStatusResponse>>

    suspend fun saveRemoveLiquidity(
        txHash: String,
        status: ExtrinsicStatusResponse,
        fee: BigDecimal,
        eventSuccess: Nothing?,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        soraAccount: SoraAccount
    )
}
