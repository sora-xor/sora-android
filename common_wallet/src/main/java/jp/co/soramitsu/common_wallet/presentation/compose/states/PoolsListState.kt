/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.presentation.compose.states

import android.net.Uri
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.formatFiat
import jp.co.soramitsu.common.domain.formatFiatChange
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.PoolData

class PoolsListState(
    val pools: List<PoolsListItemState>
)

data class PoolsListItemState(
    val token1Icon: Uri,
    val token2Icon: Uri,
    val poolName: String,
    val poolAmounts: String,
    val fiat: String,
    val fiatChange: String,
    val tokenIds: StringPair,
)

fun mapPoolsData(
    poolsData: List<PoolData>,
    numbersFormatter: NumbersFormatter
): Pair<PoolsListState, Double> {
    val formatted = poolsData.map { it.printFiat() }
    val sum = formatted.map { it?.first ?: 0.0 }.sumOf { it }
    val state = PoolsListState(
        poolsData.mapIndexed { i, poolData ->
            PoolsListItemState(
                poolName = String.format(
                    "%s - %s",
                    poolData.baseToken.symbol,
                    poolData.token.symbol
                ),
                poolAmounts = String.format(
                    "%s - %s",
                    poolData.baseToken.printBalance(
                        poolData.basePooled,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    ),
                    poolData.token.printBalance(
                        poolData.secondPooled,
                        numbersFormatter,
                        AssetHolder.ROUNDING
                    )
                ),
                token1Icon = poolData.baseToken.iconUri(),
                token2Icon = poolData.token.iconUri(),
                fiat = formatted[i]?.first?.let { poolData.baseToken.formatFiat(it, numbersFormatter) }
                    .orEmpty(),
                fiatChange = formatted[i]?.second?.let {
                    formatFiatChange(
                        it,
                        numbersFormatter
                    )
                }.orEmpty(),
                tokenIds = poolData.baseToken.id to poolData.token.id,
            )
        }
    )
    return state to sum
}
