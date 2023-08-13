/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.depositLiquidity
import jp.co.soramitsu.sora.substrate.substrate.initializePool
import jp.co.soramitsu.sora.substrate.substrate.register
import jp.co.soramitsu.sora.substrate.substrate.removeLiquidity
import jp.co.soramitsu.sora.substrate.substrate.swap

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
            mapBalance(it, tokenId1.precision)
        }
    }

    override suspend fun observeRemoveLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): ExtrinsicSubmitStatus = extrinsicManager.submitAndWatchExtrinsic(
        from = address,
        keypair = keypair,
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
        slippageTolerance: Double
    ): ExtrinsicSubmitStatus {
        val amountFromMin = PolkaswapFormulas.calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = PolkaswapFormulas.calculateMinAmount(tokenToAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(tokenFrom.id)
        return extrinsicManager.submitAndWatchExtrinsic(
            from = address,
            keypair = keypair,
            useBatchAll = !pairPresented,
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
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        val amountFromMin = PolkaswapFormulas.calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = PolkaswapFormulas.calculateMinAmount(tokenToAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(tokenFrom.id)
        val fee = extrinsicManager.calcFee(
            from = address,
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
        return fee?.let {
            mapBalance(it, tokenFrom.precision)
        }
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token,
        address: String,
    ): BigDecimal? {
        val fee = extrinsicManager.calcFee(
            from = address
        ) {
            removeLiquidity(
                dexId = getPoolBaseTokenDexId(tokenId1.id),
                outputAssetIdA = tokenId1.id,
                outputAssetIdB = tokenId2.id,
                markerAssetDesired = mapBalance(BigDecimal.ONE, tokenId1.precision),
                outputAMin = mapBalance(BigDecimal.ONE, tokenId1.precision),
                outputBMin = mapBalance(BigDecimal.ONE, tokenId1.precision)
            )
        }
        return fee?.let {
            mapBalance(it, tokenId1.precision)
        }
    }
}
