/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun TxDetailsReferralOrTransferScreen(
    modifier: Modifier,
    state: BasicTxDetailsState,
    icon: Uri,
    isAmountGreen: Boolean = false,
    amount: String,
    onCloseClick: () -> Unit,
    onCopyClick: (String) -> Unit,
) {
    BasicTxDetails(
        modifier = modifier,
        state = state,
        imageContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(icon).build(),
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
            )
        },
        amountContent = {
            Text(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(bottom = Dimens.x3),
                text = amount,
                textAlign = TextAlign.Center,
                color = if (isAmountGreen) MaterialTheme.customColors.statusSuccess else MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.headline3,
            )
        },
        onCloseClick = onCloseClick,
        onCopy = onCopyClick,
    )
}

@Composable
@Preview(showBackground = true)
fun PreviewReferrerTxCard() {
    TxDetailsReferralOrTransferScreen(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        state = previewBasicTxDetailsItem,
        isAmountGreen = false,
        amount = "123.123 XOR",
        icon = DEFAULT_ICON_URI,
        onCloseClick = {},
        onCopyClick = {},
    )
}
