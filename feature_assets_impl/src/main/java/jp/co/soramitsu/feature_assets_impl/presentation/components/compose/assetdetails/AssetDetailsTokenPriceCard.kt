/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import jp.co.soramitsu.ui_core.component.asset.changePriceColor
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AssetDetailsTokenPriceCard(
    tokenName: String,
    tokenSymbol: String,
    tokenPrice: String,
    tokenPriceChange: String,
    iconUri: Uri,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = 36.dp),
            innerPadding = PaddingValues(
                top = 46.dp,
                bottom = Dimens.x3,
                start = Dimens.x3,
                end = Dimens.x3
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tokenSymbol,
                    style = MaterialTheme.customTypography.textXSBold,
                    color = MaterialTheme.customColors.fgSecondary,
                )
                Spacer(modifier = Modifier.height(Dimens.x1))
                Text(
                    modifier = Modifier.wrapContentSize(),
                    style = MaterialTheme.customTypography.headline1,
                    textAlign = TextAlign.Center,
                    text = tokenName,
                )
                Spacer(modifier = Modifier.height(Dimens.x1))
                Row(
                    modifier = Modifier.wrapContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tokenPrice,
                        style = MaterialTheme.customTypography.headline3,
                        color = MaterialTheme.customColors.fgSecondary
                    )
                    Spacer(modifier = Modifier.width(Dimens.x1))
                    Text(
                        text = tokenPriceChange,
                        style = MaterialTheme.customTypography.textXSBold,
                        color = tokenPriceChange.changePriceColor()
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(Dimens.x9)
                .align(Alignment.TopCenter)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(iconUri).build(),
                modifier = Modifier.size(size = Dimens.x9),
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAssetDetailsTokenPriceCard() {
    AssetDetailsTokenPriceCard(
        tokenName = "SORA",
        tokenSymbol = "XOR",
        tokenPrice = "$12.34",
        tokenPriceChange = "+4.5%",
        iconUri = Uri.EMPTY,
    )
}
