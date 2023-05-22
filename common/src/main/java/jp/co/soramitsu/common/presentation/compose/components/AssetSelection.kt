/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.createAssetBalance
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AssetSelection(
    asset: Asset,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .wrapContentSize()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(asset.token.iconFile).build(),
            modifier = Modifier.size(size = Dimens.x3),
            contentDescription = null,
            imageLoader = LocalContext.current.imageLoader,
        )
        Text(
            modifier = Modifier
                .padding(horizontal = Dimens.x1_2)
                .wrapContentSize(),
            text = asset.token.symbol,
            style = MaterialTheme.customTypography.displayS,
            color = MaterialTheme.customColors.fgPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Icon(
            modifier = Modifier
                .size(size = Dimens.x2),
            painter = painterResource(id = R.drawable.ic_chevron_down_rounded_16),
            tint = MaterialTheme.customColors.fgSecondary,
            contentDescription = null
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    AssetSelection(
        asset = previewAsset,
        onClick = {},
    )
}

val previewToken = Token(
    id = "",
    name = "XOR token",
    symbol = "XOR",
    precision = 18,
    isHidable = false,
    iconFile = DEFAULT_ICON_URI,
    fiatPrice = 0.0,
    fiatPriceChange = 0.0,
    fiatSymbol = "USD",
)

val previewAsset = Asset(
    token = previewToken,
    favorite = false,
    position = 1,
    balance = createAssetBalance(),
    visibility = false,
)
