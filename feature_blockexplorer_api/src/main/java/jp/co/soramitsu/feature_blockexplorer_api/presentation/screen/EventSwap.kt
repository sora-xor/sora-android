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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
internal fun EventSwap(
    eventUiModel: EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel,
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
                    modifier = Modifier.constrainAs(token1) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    },
                    uri = eventUiModel.iconFrom,
                    size = 28.dp,
                )
                TokenIcon(
                    modifier = Modifier.constrainAs(token2) {
                        top.linkTo(token1.top, margin = 12.dp)
                        start.linkTo(token1.start, margin = 12.dp)
                    },
                    uri = eventUiModel.iconTo,
                    size = 28.dp,
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
                    painter = painterResource(id = R.drawable.ic_polkaswap_40),
                    contentDescription = null,
                    tint = Color.Unspecified
                )
            }
        },
        name = {
            Text(
                text = stringResource(id = R.string.polkaswap_swapped),
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
            )
        },
        below = {
            TextWithDelimiter(
                text1 = eventUiModel.tickerFrom,
                text2 = eventUiModel.tickerTo,
                delimiter = " -> ",
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold
            )
        },
        figures = {
            Column(horizontalAlignment = Alignment.End) {
                val fromColor = MaterialTheme.customColors.fgPrimary
                val toColor = MaterialTheme.customColors.statusSuccess
                EventAmountWithStatus(
                    amount = buildAnnotatedString {
                        withStyle(SpanStyle(color = fromColor)) { append(eventUiModel.amountFrom) }
                        withStyle(SpanStyle(color = toColor)) { append(eventUiModel.amountTo) }
                    },
                    color = fromColor,
                    status = eventUiModel.status,
                )
                Text(
                    text = eventUiModel.fiatTo,
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
private fun PreviewEventSwap() {
    EventSwap(
        eventUiModel = eventPreview,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    )
}

private val eventPreview = EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel(
    hash = "hash",
    iconFrom = DEFAULT_ICON_URI,
    iconTo = DEFAULT_ICON_URI,
    amountFrom = "12133123 XOR -> ",
    amountTo = "34879.987 DAI",
    tickerFrom = "XOR ",
    tickerTo = "DAI",
    fiatTo = "$123.4",
    dateTime = "09.03.2001",
    timestamp = 1313123132,
    status = TransactionStatus.COMMITTED,
)
