/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun EventTransferReceived(
    eventUiModel: EventUiModel.EventTxUiModel.EventTransferInUiModel,
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(eventUiModel.tokenIcon).build(),
                modifier = Modifier
                    .size(size = Dimens.x4)
                    .constrainAs(token1) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    },
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = stringResource(id = R.string.common_received),
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
                        color = MaterialTheme.customColors.statusSuccess,
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
private fun PreviewEventTransferReceived() {
    EventTransferReceived(
        eventUiModel = eventPreview,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    )
}

private val eventPreview = EventUiModel.EventTxUiModel.EventTransferInUiModel(
    hash = "hash",
    tokenIcon = DEFAULT_ICON_URI,
    peerAddress = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm",
    dateTime = "12.12.2002",
    timestamp = 1231231312,
    amountFormatted = "+ 12313132.12145 XOR",
    fiatFormatted = "$123.12",
    status = TransactionStatus.COMMITTED,
)
