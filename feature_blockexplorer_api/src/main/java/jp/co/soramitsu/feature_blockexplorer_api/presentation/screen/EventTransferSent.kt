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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
internal fun EventTransferSent(
    eventUiModel: EventUiModel.EventTxUiModel.EventTransferOutUiModel,
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
                uri = eventUiModel.tokenIcon,
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
                painter = painterResource(id = R.drawable.ic_send_wrapped),
                contentDescription = null,
                tint = Color.Unspecified
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.x2)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = stringResource(id = R.string.common_sent),
                    style = MaterialTheme.customTypography.textM,
                    color = MaterialTheme.customColors.fgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = eventUiModel.amountFormatted,
                        style = MaterialTheme.customTypography.textM,
                        color = MaterialTheme.customColors.fgPrimary,
                        maxLines = 1,
                    )
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
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    }
                }
            }
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
                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = "",
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewEventTransferSent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        EventTransferSent(
            eventUiModel = eventPreview,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
        EventTransferSent(
            eventUiModel = EventUiModel.EventTxUiModel.EventTransferOutUiModel(
                hash = "hash",
                tokenIcon = DEFAULT_ICON_URI,
                peerAddress = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
                dateTime = "12.11.2003",
                timestamp = 121515132,
                amountFormatted = "1123141.1235 XOR",
                fiatFormatted = "$12313.1",
                status = TransactionStatus.PENDING,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
        EventTransferSent(
            eventUiModel = EventUiModel.EventTxUiModel.EventTransferOutUiModel(
                hash = "hash",
                tokenIcon = DEFAULT_ICON_URI,
                peerAddress = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
                dateTime = "12.11.2003",
                timestamp = 121515132,
                amountFormatted = "1123141.1235 XOR",
                fiatFormatted = "$12313.1",
                status = TransactionStatus.REJECTED,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
    }
}

private val eventPreview = EventUiModel.EventTxUiModel.EventTransferOutUiModel(
    hash = "hash",
    tokenIcon = DEFAULT_ICON_URI,
    peerAddress = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
    dateTime = "12.11.2003",
    timestamp = 121515132,
    amountFormatted = "1123141.1235 XOR",
    fiatFormatted = "$12313.1",
    status = TransactionStatus.COMMITTED,
)
