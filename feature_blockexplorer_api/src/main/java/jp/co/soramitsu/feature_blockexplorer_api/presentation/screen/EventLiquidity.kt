/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
internal fun EventLiquidity(
    eventUiModel: EventUiModel.EventTxUiModel.EventLiquidityAddUiModel,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = Dimens.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConstraintLayout(
            modifier = Modifier.wrapContentSize()
        ) {
            val (token1, token2, typeIcon) = createRefs()
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(eventUiModel.icon1).build(),
                modifier = Modifier
                    .size(size = 28.dp)
                    .constrainAs(token1) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    },
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
            )
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(eventUiModel.icon2).build(),
                modifier = Modifier
                    .size(size = 28.dp)
                    .constrainAs(token2) {
                        top.linkTo(token1.top, margin = 12.dp)
                        start.linkTo(token1.start, margin = 12.dp)
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
                        bottom.linkTo(token2.bottom)
                        start.linkTo(token2.start, margin = (-16).dp)
                    },
                painter = painterResource(id = if (eventUiModel.add) R.drawable.ic_send_wrapped else R.drawable.ic_receive_wrapped),
                contentDescription = null,
                tint = Color.Unspecified
            )
        }
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = Dimens.x1)
        ) {
            Text(
                modifier = Modifier.wrapContentSize(),
                text = eventUiModel.type,
                style = MaterialTheme.customTypography.textM,
                color = MaterialTheme.customColors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier
                    .wrapContentSize(),
                text = eventUiModel.tickers,
                style = MaterialTheme.customTypography.textXSBold,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier.wrapContentSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = eventUiModel.amounts,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.customTypography.textM,
                    color = if (eventUiModel.add) MaterialTheme.customColors.fgPrimary else MaterialTheme.customColors.statusSuccess,
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
            Text(
                modifier = Modifier.wrapContentSize(),
                text = eventUiModel.fiat,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewEventLiquidity() {
    EventLiquidity(
        eventUiModel = eventPreview,
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
    )
}

private val eventPreview = EventUiModel.EventTxUiModel.EventLiquidityAddUiModel(
    hash = "hash",
    timestamp = 12345678,
    status = TransactionStatus.COMMITTED,
    dateTime = "12.03.1998",
    icon1 = DEFAULT_ICON_URI,
    icon2 = DEFAULT_ICON_URI,
    amounts = "199994857.134536 XOR / 12332453.2345345 DAI",
    type = "Pool add",
    tickers = "XOR / DAI",
    fiat = "$125235235235236",
    add = false,
)
