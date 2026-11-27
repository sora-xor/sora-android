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

package jp.co.soramitsu.common_wallet.presentation.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.presentation.compose.components.TextWithDelimiter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.component.text.FittedText
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * The pools the account is in. On each row the pooled amounts ("2 XOR - 5 VAL") are the figure, at the end in the
 * larger type, breaking only between the two amounts when the row is short; the value and the reward tokens are the
 * small line under them.
 */
@Composable
fun PoolsList(
    cardState: PoolsListState,
    onPoolClick: ((StringPair) -> Unit)? = null,
) {
    cardState.pools.forEach { poolState ->
        Row(
            modifier = Modifier
                .padding(vertical = Dimens.x1)
                .heightIn(min = Size.Small)
                .fillMaxWidth()
                .padding(horizontal = Dimens.x3)
                .clickable { onPoolClick?.invoke(poolState.tokenIds) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2) = createRefs()
                TokenIcon(
                    uri = poolState.token1Icon, size = Size.Small,
                    modifier = Modifier
                        .constrainAs(token1) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                        }
                )
                TokenIcon(
                    uri = poolState.token2Icon, size = Size.Small,
                    modifier = Modifier
                        .constrainAs(token2) {
                            top.linkTo(parent.top)
                            start.linkTo(token1.start, margin = 24.dp)
                        }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.x1, end = Dimens.x1)
            ) {
                TextWithDelimiter(
                    text1 = poolState.poolToken1Symbol,
                    text2 = poolState.poolToken2Symbol,
                    delimiter = " - ",
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.textM
                )
            }
            // The amounts may take up to two thirds of the row's room; the pair keeps the rest. They are set smaller,
            // then wrap, and are never cut.
            Column(
                modifier = Modifier.weight(2f, fill = false),
                horizontalAlignment = Alignment.End,
            ) {
                FittedText(
                    text = AnnotatedString(pooledAmountsText(poolState.poolAmounts)),
                    style = MaterialTheme.customTypography.textM.copy(
                        color = MaterialTheme.customColors.fgPrimary,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = Int.MAX_VALUE,
                    smallest = POOLED_AMOUNTS_SMALLEST,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        color = MaterialTheme.customColors.fgSecondary,
                        style = MaterialTheme.customTypography.textXSBold,
                        text = poolState.fiat,
                    )
                    poolState.rewardTokenIconsList.forEach {
                        TokenIcon(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            uri = it,
                            size = Dimens.x2
                        )
                    }
                }
            }
        }
    }
}

/** "2 XOR - 5 VAL" with each amount kept whole, so a short row breaks the line only between the two amounts. */
internal fun pooledAmountsText(amounts: String): String =
    amounts.split(POOLED_AMOUNTS_DELIMITER).joinToString(POOLED_AMOUNTS_DELIMITER) { it.replace(' ', '\u00A0') }

private const val POOLED_AMOUNTS_DELIMITER = " - "

/** A step over the 11 sp of the value under the amounts: they stay the larger figures however they are fitted. */
private val POOLED_AMOUNTS_SMALLEST = 12.sp

@Composable
@Preview
private fun PreviewPoolsList() {
    Column(Modifier.background(Color.White)) {
        PoolsList(
            onPoolClick = {},
            cardState = PoolsListState(
                pools = listOf(
                    PoolsListItemState(
                        token1Icon = DEFAULT_ICON_URI,
                        token2Icon = DEFAULT_ICON_URI,
                        poolAmounts = "123.456",
                        poolToken1Symbol = "XOR",
                        poolToken2Symbol = "VAL",
                        fiat = "$7908",
                        fiatChange = "+23.1 %",
                        tokenIds = "" to "",
                        rewardTokenIconsList = emptyList()
                    ),
                    PoolsListItemState(
                        token1Icon = DEFAULT_ICON_URI,
                        token2Icon = DEFAULT_ICON_URI,
                        poolAmounts = "98.76",
                        poolToken1Symbol = "DAI",
                        poolToken2Symbol = "PSWAP",
                        fiat = "$ 0.00123",
                        fiatChange = "-9.88 %",
                        tokenIds = "" to "",
                        rewardTokenIconsList = emptyList()
                    ),
                ),
            ),
        )
    }
}
