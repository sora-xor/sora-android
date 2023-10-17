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

package jp.co.soramitsu.common_wallet.presentation.compose.states

import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiat
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData

class PoolsListState(
    val pools: List<PoolsListItemState>
)

data class PoolsListItemState(
    val token1Icon: String,
    val token2Icon: String,
    val poolName: String,
    val poolAmounts: String,
    val fiat: String,
    val fiatChange: String,
    val tokenIds: StringPair,
)

fun mapPoolsData(
    poolsData: List<CommonUserPoolData>,
    numbersFormatter: NumbersFormatter
): Pair<PoolsListState, Double> {
    val formatted = poolsData.map { it.printFiat() }
    val sum = formatted.map { it?.first ?: 0.0 }.sumOf { it }
    val state = PoolsListState(
        poolsData.mapIndexed { i, poolData ->
            PoolsListItemState(
                poolName = String.format(
                    "%s - %s",
                    poolData.basic.baseToken.symbol,
                    poolData.basic.targetToken.symbol,
                ),
                poolAmounts = String.format(
                    "%s - %s",
                    poolData.basic.baseToken.printBalance(
                        poolData.user.basePooled,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    ),
                    poolData.basic.targetToken.printBalance(
                        poolData.user.targetPooled,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    )
                ),
                token1Icon = poolData.basic.baseToken.iconUri(),
                token2Icon = poolData.basic.targetToken.iconUri(),
                fiat = formatted[i]?.first?.let { poolData.basic.baseToken.formatFiat(it, numbersFormatter) }
                    .orEmpty(),
                fiatChange = "",
//                fiatChange = formatted[i]?.second?.let {
//                    formatFiatChange(
//                        it.isNanZero(),
//                        numbersFormatter
//                    )
//                }.orEmpty(),
                tokenIds = poolData.basic.baseToken.id to poolData.basic.targetToken.id,
            )
        }
    )
    return state to sum
}
