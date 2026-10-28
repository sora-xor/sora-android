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
    val poolToken1Symbol: String,
    val poolToken2Symbol: String,
    val poolAmounts: String,
    val fiat: String,
    val fiatChange: String,
    val tokenIds: StringPair,
    val rewardTokenIconsList: List<String?>
)

fun mapPoolsData(
    poolsData: List<CommonUserPoolData>,
    numbersFormatter: NumbersFormatter,
    rewardTokenIconsList: List<List<String>> = emptyList()
): Pair<PoolsListState, Double> {
    val formatted = poolsData.map { it.printFiat() }
    val sum = formatted.map { it?.first ?: 0.0 }.sumOf { it }
    val state = PoolsListState(
        poolsData.mapIndexed { i, poolData ->
            val rewardIcons = if (rewardTokenIconsList.isEmpty()) {
                emptyList()
            } else {
                rewardTokenIconsList[i]
            }

            PoolsListItemState(
                poolToken1Symbol = poolData.basic.baseToken.symbol,
                poolToken2Symbol = poolData.basic.targetToken.symbol,
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
                rewardTokenIconsList = rewardIcons
            )
        }
    )
    return state to sum
}
