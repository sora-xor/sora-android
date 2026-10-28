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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.presentation.compose.components.TextWithDelimiter
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun PoolsList(
    cardState: PoolsListState,
    onPoolClick: ((StringPair) -> Unit)? = null,
) {
    cardState.pools.forEach { poolState ->
        Row(
            modifier = Modifier
                .padding(vertical = Dimens.x1)
                .height(Size.Small)
                .fillMaxWidth()
                .padding(horizontal = Dimens.x3)
                .clickable { onPoolClick?.invoke(poolState.tokenIds) }
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

                Text(
                    color = MaterialTheme.customColors.fgSecondary,
                    style = MaterialTheme.customTypography.textXSBold,
                    text = poolState.poolAmounts,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier
                    .wrapContentSize(),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.textM,
                    text = poolState.fiat,
                    maxLines = 1,
                )
                Row {
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
