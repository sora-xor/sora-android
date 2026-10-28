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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.ui_core.component.asset.changePriceColor
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AssetDetailsTokenPriceCard(
    tokenName: String,
    tokenSymbol: String,
    tokenPrice: String,
    tokenPriceChange: String,
    iconUri: String,
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
                    color = MaterialTheme.customColors.fgPrimary,
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
            TokenIcon(uri = iconUri, size = Dimens.x9)
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
        iconUri = DEFAULT_ICON_URI,
    )
}
