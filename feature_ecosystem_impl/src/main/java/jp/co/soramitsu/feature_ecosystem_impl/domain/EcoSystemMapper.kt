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

import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.format.formatFiatSuffix
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.presentation.compose.BasicPoolListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.states.mapTokensToCardState

class EcoSystemMapper @Inject constructor(
    private val numbersFormatter: NumbersFormatter,
) {
    fun mapEcoSystemTokens(tokens: EcoSystemTokens) =
        mapTokensToCardState(
            tokens.tokens.map {
                it.token to it.liquidityFiat?.toBigDecimal()
            },
            numbersFormatter,
        )
            .mapIndexed { index, assetItemCardState ->
                (index + 1).toString() to assetItemCardState
            }

    fun mapEcoSystemPools(pools: EcoSystemPools) =
        pools.pools.mapIndexed { index, pool ->
            BasicPoolListItemState(
                ids = pool.pool.baseToken.id to pool.pool.targetToken.id,
                number = (index + 1).toString(),
                token1Icon = pool.pool.baseToken.iconUri(),
                token2Icon = pool.pool.targetToken.iconUri(),
                text1 = "%s-%s".format(pool.pool.baseToken.symbol, pool.pool.targetToken.symbol),
                text2 = pool.pool.baseToken.printFiat(pool.tvl?.formatFiatSuffix()).orEmpty(),
                text3 = pool.pool.sbapy?.let {
                    "%s%%".format(numbersFormatter.format(it, 2))
                }.orEmpty(),
            )
        }
}
