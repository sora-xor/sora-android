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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.presentation.compose.components.TextWithDelimiter
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.ui_core.lit.NameAndFigures
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun EventLiquidity(
    eventUiModel: EventUiModel.EventTxUiModel.EventLiquidityAddUiModel,
    modifier: Modifier,
) {
    // The label (and the tickers under it) beside the amounts where both are whole, otherwise the amounts on a line of
    // their own under it, at the end, wrapping rather than cut: each block at its own width, in either direction.
    NameAndFigures(
        modifier = modifier.padding(vertical = Dimens.x1),
        leadingGap = Dimens.x1,
        leading = {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2, typeIcon) = createRefs()
                TokenIcon(
                    uri = eventUiModel.icon1, size = 28.dp,
                    modifier = Modifier
                        .size(size = 28.dp)
                        .constrainAs(token1) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                        }
                )
                TokenIcon(
                    uri = eventUiModel.icon2, size = 28.dp,
                    modifier = Modifier
                        .size(size = 28.dp)
                        .constrainAs(token2) {
                            top.linkTo(token1.top, margin = 12.dp)
                            start.linkTo(token1.start, margin = 12.dp)
                        }
                )
                Icon(
                    modifier = Modifier
                        .size(Dimens.x2)
                        .clip(CircleShape)
                        .background(MaterialTheme.customColors.bgSurface)
                        .padding(2.dp)
                        .constrainAs(typeIcon) {
                            bottom.linkTo(token2.bottom)
                            start.linkTo(token2.start, margin = (-16).dp)
                        },
                    painter = painterResource(id = if (eventUiModel.add) R.drawable.ic_send_wrapped else R.drawable.ic_receive_wrapped),
                    contentDescription = null,
                    tint = Color.Unspecified
                )
            }
        },
        name = {
            Text(
                text = eventUiModel.type,
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
            )
        },
        below = {
            TextWithDelimiter(
                text1 = eventUiModel.ticker1,
                text2 = eventUiModel.ticker2,
                delimiter = " / ",
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold
            )
        },
        figures = {
            Column(horizontalAlignment = Alignment.End) {
                EventAmountWithStatus(
                    amount = AnnotatedString(eventUiModel.amount1 + " / " + eventUiModel.amount2),
                    color = if (eventUiModel.add) MaterialTheme.customColors.fgPrimary else MaterialTheme.customColors.statusSuccess,
                    status = eventUiModel.status,
                )
                Text(
                    text = eventUiModel.fiat,
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary,
                    textAlign = TextAlign.End,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewEventLiquidity() {
    EventLiquidity(
        eventUiModel = eventPreview,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    )
}

private val eventPreview = EventUiModel.EventTxUiModel.EventLiquidityAddUiModel(
    hash = "hash",
    timestamp = 12345678,
    status = TransactionStatus.COMMITTED,
    dateTime = "12.03.1998",
    icon1 = DEFAULT_ICON_URI,
    icon2 = DEFAULT_ICON_URI,
    amount1 = "199994857.134536 XOR",
    amount2 = "12332453.2345345 DAI",
    type = "Pool add",
    ticker1 = "XOR",
    ticker2 = "XOR",
    fiat = "$125235235235236",
    add = false,
)
