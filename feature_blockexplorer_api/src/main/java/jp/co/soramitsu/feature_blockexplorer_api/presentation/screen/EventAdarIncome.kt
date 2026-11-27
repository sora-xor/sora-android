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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun EventAdarIncome(
    eventUiModel: EventUiModel.EventTxUiModel.EventAdarIncomeUiModel,
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
                uri = eventUiModel.tokenUri, size = Dimens.x4,
                modifier = Modifier
                    .constrainAs(token1) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    }
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
                painter = painterResource(id = R.drawable.ic_receive_wrapped),
                contentDescription = null,
                tint = Color.Unspecified
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.x2)
        ) {
            EventLabelAndAmount(
                label = stringResource(id = R.string.received_from_adar),
                amount = AnnotatedString(eventUiModel.amountFormatted),
                color = MaterialTheme.customColors.statusSuccess,
                status = eventUiModel.status,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentSize(),
                    text = eventUiModel.peerAddress,
                    style = MaterialTheme.customTypography.textXSBold,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.customColors.fgSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewEventTransferReceived() {
    EventAdarIncome(
        eventUiModel = eventPreview,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    )
}

private val eventPreview = EventUiModel.EventTxUiModel.EventAdarIncomeUiModel(
    hash = "hash",
    tokenUri = DEFAULT_ICON_URI,
    peerAddress = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
    timestamp = 1231231312,
    amountFormatted = "+ 12313132.12145 XOR",
    status = TransactionStatus.COMMITTED,
)
