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

package jp.co.soramitsu.feature_ecosystem_impl.domain

import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface EcoSystemTokensInteractor {
    fun subscribeTokens(): Flow<EcoSystemTokens>

    suspend fun getTokensWithTvl(): EcoSystemTokens
}

internal class EcoSystemTokensInteractorImpl(
    private val assetsRepository: AssetsRepository,
    private val blockExplorerManager: BlockExplorerManager,
) : EcoSystemTokensInteractor {
    override fun subscribeTokens(): Flow<EcoSystemTokens> {
        return assetsRepository.subscribeTokensList().combine(flowTokensLiquidity) { f1, f2 ->
            f1 to f2
        }.map { (list, liquidity) ->
            val marketCap = list.map { token ->
                EcoSystemToken(token, liquidity.firstOrNull { it.first == token.id }?.second)
            }
            val sorted = marketCap.sortedWith { o1, o2 ->
                compareNullDesc(o1.liquidityFiat, o2.liquidityFiat)
            }
            EcoSystemTokens(
                tokens = sorted,
            )
        }
    }

    override suspend fun getTokensWithTvl(): EcoSystemTokens {
        val tokens = assetsRepository.tokensList()
        val tvls = blockExplorerManager.getTokensLiquidity(tokens.map { it.id })

        val marketCap = tokens.map { token ->
            EcoSystemToken(token, tvls.firstOrNull { it.first == token.id }?.second)
        }
        val sorted = marketCap.sortedWith { o1, o2 ->
            compareNullDesc(o1.liquidityFiat, o2.liquidityFiat)
        }
        return EcoSystemTokens(tokens = sorted)
    }

    private val flowTokensLiquidity: Flow<List<Pair<String, Double>>> = flow {
        emit(blockExplorerManager.getTokensLiquidity(assetsRepository.tokensList().map { it.id }))
    }.onStart {
        emit(emptyList())
    }
}
