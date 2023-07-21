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

package jp.co.soramitsu.feature_ecosystem_impl.domain

import jp.co.soramitsu.common.util.ext.compareNullDesc
import jp.co.soramitsu.common.util.ext.multiplyNullable
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface EcoSystemTokensInteractor {
    fun subscribeTokens(): Flow<EcoSystemTokens>
}

internal class EcoSystemTokensInteractorImpl(
    private val assetsRepository: AssetsRepository,
    private val blockExplorerManager: BlockExplorerManager,
) : EcoSystemTokensInteractor {
    override fun subscribeTokens(): Flow<EcoSystemTokens> {
        return assetsRepository.subscribeTokensList().map { list ->
            val liquidity = blockExplorerManager.getTokensLiquidity(list.map { it.id })
            val mapped = list.map { token ->
                token to liquidity.firstOrNull { it.first == token.id }?.second?.let { bi ->
                    mapBalance(bi, token.precision)
                }
            }
            val marketCap = mapped.map { tokenLiquidity ->
                val sum =
                    tokenLiquidity.second?.multiplyNullable(tokenLiquidity.first.fiatPrice?.toBigDecimal())
                EcoSystemToken(tokenLiquidity.first, sum, tokenLiquidity.second)
            }
            val sorted = marketCap.sortedWith { o1, o2 ->
                compareNullDesc(o1.liquidityFiat, o2.liquidityFiat)
            }
            EcoSystemTokens(
                tokens = sorted,
            )
        }
    }
}
