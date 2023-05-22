/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun TxDetailsLiquidity(
    modifier: Modifier,
    state: BasicTxDetailsState,
    isAmountGreen: Boolean,
    amount1: String,
    amount2: String,
    icon1: Uri,
    icon2: Uri,
    onCloseClick: () -> Unit,
    onCopyClick: (String) -> Unit,
) {
    BasicTxDetails(
        modifier = modifier,
        state = state,
        imageContent = {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2) = createRefs()
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(icon1).build(),
                    modifier = Modifier
                        .size(size = 48.dp)
                        .constrainAs(token1) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                        },
                    contentDescription = null,
                    imageLoader = LocalContext.current.imageLoader,
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(icon2).build(),
                    modifier = Modifier
                        .size(size = 48.dp)
                        .constrainAs(token2) {
                            top.linkTo(token1.top, margin = 24.dp)
                            start.linkTo(token1.start, margin = 24.dp)
                        },
                    contentDescription = null,
                    imageLoader = LocalContext.current.imageLoader,
                )
            }
        },
        amountContent = {
            Column(
                modifier = Modifier
                    .padding(bottom = Dimens.x3)
                    .align(Alignment.Center)
                    .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = amount1,
                    color = if (isAmountGreen) MaterialTheme.customColors.statusSuccess else MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline3
                )
                Icon(
                    modifier = Modifier
                        .padding(vertical = Dimens.x1_2)
                        .size(Dimens.x3),
                    painter = painterResource(id = R.drawable.ic_neu_plus_24),
                    tint = MaterialTheme.customColors.fgSecondary,
                    contentDescription = null
                )
                Text(
                    text = amount2,
                    color = if (isAmountGreen) MaterialTheme.customColors.statusSuccess else MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.headline3
                )
            }
        },
        onCloseClick = onCloseClick,
        onCopy = onCopyClick,
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewTxDetailsAddLiquidity() {
    TxDetailsLiquidity(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        state = previewBasicTxDetailsItem,
        isAmountGreen = true,
        amount1 = "123.23 XOR",
        amount2 = "87987.23 VAL",
        icon1 = DEFAULT_ICON_URI,
        icon2 = DEFAULT_ICON_URI,
        onCopyClick = {},
        onCloseClick = {},
    )
}
