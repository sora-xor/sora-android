/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SwapAmountSquare(
    modifier: Modifier,
    icon: Uri,
    amount: String,
    amountFiat: String,
) {
    ContentCard(
        modifier = modifier,
        innerPadding = PaddingValues(vertical = Dimens.x3),
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(icon).build(),
                modifier = Modifier
                    .size(size = Size.Small),
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
            )
            Text(
                modifier = Modifier
                    .padding(vertical = Dimens.x1)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = Dimens.x1),
                text = amount,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.customTypography.displayS,
            )
            Text(
                modifier = Modifier
                    .wrapContentSize(),
                text = amountFiat,
                maxLines = 1,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    Row {
        SwapAmountSquare(
            modifier = Modifier.weight(1f),
            icon = DEFAULT_ICON_URI,
            amount = "123.144 XOR",
            amountFiat = "$234.34",
        )
        Spacer(modifier = Modifier.size(Dimens.x2))
        SwapAmountSquare(
            modifier = Modifier.weight(1f),
            icon = DEFAULT_ICON_URI,
            amount = "123.144 XOR",
            amountFiat = "$234.34",
        )
    }
}
