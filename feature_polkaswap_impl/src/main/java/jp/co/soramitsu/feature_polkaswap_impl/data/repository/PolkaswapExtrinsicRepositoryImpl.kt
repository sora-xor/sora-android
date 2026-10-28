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

package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TokenPrecision
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.depositLiquidity
import jp.co.soramitsu.sora.substrate.substrate.initializePool
import jp.co.soramitsu.sora.substrate.substrate.register
import jp.co.soramitsu.sora.substrate.substrate.removeLiquidity
import jp.co.soramitsu.sora.substrate.substrate.swap
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

class PolkaswapExtrinsicRepositoryImpl @Inject constructor(
    private val extrinsicManager: ExtrinsicManager,
    private val marketMapper: SwapMarketMapper,
    db: AppDatabase,
    blockExplorerManager: BlockExplorerManager,
) : PolkaswapExtrinsicRepository,
    PolkaswapBasicRepositoryImpl(db, blockExplorerManager) {

    override suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal? {
        val amount2 = mapBalance(
            BigDecimal.ONE,
            tokenId1.precision
        )
        val markets = emptyList<Market>()
        val fee = extrinsicManager.calcFee(
            from = address,
        ) {
            swap(
                dexId = 0,
                inputAssetId = tokenId1.id,
                outputAssetId = tokenId1.id,
                amount = amount2,
                limit = amount2,
                filter = marketMapper.mapMarketsToFilter(markets),
                markets = marketMapper.mapMarketsToStrings(markets),
                desired = WithDesired.INPUT,
            )
        }
        return fee?.let {
            mapBalance(it, OptionsProvider.defaultScale)
        }
    }

    override suspend fun observeRemoveLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        activity: Sora2SubmissionActivity?,
    ): ExtrinsicSubmitStatus = extrinsicManager.submitAndWatchExtrinsic(
        from = address,
        keypair = keypair,
        activity = activity,
    ) {
        removeLiquidity(
            dexId = getPoolBaseTokenDexId(token1.id),
            outputAssetIdA = token1.id,
            outputAssetIdB = token2.id,
            markerAssetDesired = mapBalance(markerAssetDesired, token1.precision),
            outputAMin = mapBalance(firstAmountMin, token1.precision),
            outputBMin = mapBalance(secondAmountMin, token2.precision)
        )
    }

    override suspend fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Sr25519Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        dexId: Int,
        activity: Sora2SubmissionActivity?,
    ): ExtrinsicSubmitStatus {
        val (amount2, limit2) = if (swapVariant == WithDesired.INPUT) mapBalance(
            amount,
            tokenId1.precision
        ) to mapBalance(limit, tokenId2.precision) else mapBalance(
            amount,
            tokenId2.precision
        ) to mapBalance(limit, tokenId1.precision)
        return extrinsicManager.submitAndWatchExtrinsic(
            from = address,
            keypair = keypair,
            activity = activity,
        ) {
            swap(
                dexId = dexId,
                inputAssetId = tokenId1.id,
                outputAssetId = tokenId2.id,
                amount = amount2,
                limit = limit2,
                filter = marketMapper.mapMarketsToFilter(markets),
                markets = marketMapper.mapMarketsToStrings(markets),
                desired = swapVariant,
            )
        }
    }

    override suspend fun observeAddLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double,
        activity: Sora2SubmissionActivity?,
    ): ExtrinsicSubmitStatus {
        val amountFromMin = PolkaswapFormulas.calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = PolkaswapFormulas.calculateMinAmount(tokenToAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(tokenFrom.id)
        return extrinsicManager.submitAndWatchExtrinsic(
            from = address,
            keypair = keypair,
            useBatchAll = !pairPresented,
            activity = activity,
        ) {
            if (!pairPresented) {
                if (!pairEnabled) {
                    register(
                        dexId = dexId,
                        tokenFrom.id, tokenTo.id
                    )
                }
                initializePool(
                    dexId = dexId,
                    tokenFrom.id, tokenTo.id
                )
            }

            depositLiquidity(
                dexId = dexId,
                tokenFrom.id,
                tokenTo.id,
                mapBalance(tokenFromAmount, tokenFrom.precision),
                mapBalance(tokenToAmount, tokenTo.precision),
                mapBalance(amountFromMin, tokenFrom.precision),
                mapBalance(amountToMin, tokenTo.precision)
            )
        }
    }

    override suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: TokenPrecision,
        tokenTo: TokenPrecision,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        val amountFromMin = PolkaswapFormulas.calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = PolkaswapFormulas.calculateMinAmount(tokenToAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(tokenFrom.first)
        val fee = extrinsicManager.calcFee(
            from = address,
            useBatchAll = !pairPresented,
        ) {
            if (!pairPresented) {
                if (!pairEnabled) {
                    register(
                        dexId = dexId,
                        tokenFrom.first, tokenTo.first
                    )
                }
                initializePool(
                    dexId = dexId,
                    tokenFrom.first, tokenTo.first
                )
            }

            depositLiquidity(
                dexId = dexId,
                tokenFrom.first,
                tokenTo.first,
                mapBalance(tokenFromAmount, tokenFrom.second),
                mapBalance(tokenToAmount, tokenTo.second),
                mapBalance(amountFromMin, tokenFrom.second),
                mapBalance(amountToMin, tokenTo.second)
            )
        }
        return fee?.let {
            mapBalance(it, OptionsProvider.defaultScale)
        }
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: TokenPrecision,
        tokenId2: TokenPrecision,
        address: String,
    ): BigDecimal? {
        val fee = extrinsicManager.calcFee(
            from = address
        ) {
            removeLiquidity(
                dexId = getPoolBaseTokenDexId(tokenId1.first),
                outputAssetIdA = tokenId1.first,
                outputAssetIdB = tokenId2.first,
                markerAssetDesired = mapBalance(BigDecimal.ONE, tokenId1.second),
                outputAMin = mapBalance(BigDecimal.ONE, tokenId1.second),
                outputBMin = mapBalance(BigDecimal.ONE, tokenId2.second)
            )
        }
        return fee?.let {
            mapBalance(it, OptionsProvider.defaultScale)
        }
    }
}
