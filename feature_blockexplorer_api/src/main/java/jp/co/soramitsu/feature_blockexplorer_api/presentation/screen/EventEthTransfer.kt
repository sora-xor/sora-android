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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.toName
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun EventEthTransfer(
    eventUiModel: EventUiModel.EventTxUiModel.EventEthTransfer,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = Dimens.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConstraintLayout(
            modifier = Modifier.wrapContentSize()
        ) {
            val (token1, typeIcon) = createRefs()
            TokenIcon(
                modifier = Modifier.constrainAs(token1) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                },
                uri = eventUiModel.tokenUri,
                size = Dimens.x4,
            )
            Icon(
                modifier = Modifier
                    .size(Dimens.x2)
                    .clip(CircleShape)
                    .background(MaterialTheme.customColors.bgSurface)
                    .padding(2.dp)
                    .constrainAs(typeIcon) {
                        bottom.linkTo(token1.bottom, (-2).dp)
                        start.linkTo(token1.start, (-2).dp)
                    },
                painter = painterResource(id = R.drawable.ic_refresh_24),
                contentDescription = null,
                tint = Color.Unspecified
            )
        }
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = Dimens.x1)
        ) {
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                ) {
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = stringResource(id = R.string.common_bridged),
                        style = MaterialTheme.customTypography.textM,
                        color = MaterialTheme.customColors.fgPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        text = eventUiModel.amountFormatted,
                        style = MaterialTheme.customTypography.textM,
                        color = MaterialTheme.customColors.fgPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (eventUiModel.status != TransactionStatus.COMMITTED) {
                    val statusIcon = if (eventUiModel.status == TransactionStatus.REJECTED) {
                        R.drawable.ic_error_16
                    } else {
                        R.drawable.ic_pending_16
                    }

                    Icon(
                        modifier = Modifier
                            .padding(start = Dimens.x1_2)
                            .size(Dimens.x2),
                        painter = painterResource(id = statusIcon),
                        // Said aloud by a screen reader: a waiting or failed entry is not
                        // distinguishable from a completed one by its icon alone.
                        contentDescription = stringResource(id = eventUiModel.status.toName()),
                        tint = Color.Unspecified
                    )
                }
            }
            Text(
                modifier = Modifier
                    .wrapContentSize(),
                text = "%s -> %s".format(
                    stringResource(id = R.string.asset_sora_fullname),
                    stringResource(id = R.string.asset_ether_fullname)
                ),
                style = MaterialTheme.customTypography.textXSBold,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewEventEthTransfer() {
    EventEthTransfer(
        eventUiModel = eventPreview,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    )
}

private val eventPreview = EventUiModel.EventTxUiModel.EventEthTransfer(
    hash = "hash",
    timestamp = 1313123132,
    status = TransactionStatus.PENDING,
    tokenUri = DEFAULT_ICON_URI,
    ethTokenUri = DEFAULT_ICON_URI,
    dateTime = "09.03.2001",
    amountFormatted = "12133.2 XOR",
    fiatFormatted = "$123.4",
    requestHash = "0xde0c89ffb5ba12386b25234b98a5154123e7d040157836c40999c8f735678be9",
    sidechainAddress = "0x123b8d3a4cd1a859567b18d5e2d1012372aaa6f5",
)
