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

package jp.co.soramitsu.common_wallet.presentation.compose.components

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
import jp.co.soramitsu.ui_core.component.asset.Asset
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AssetItem(
    assetState: AssetItemCardState,
    testTag: String,
    onClick: (String) -> Unit,
) {
    Asset(
        modifier = Modifier
            .testTagAsId(testTag)
            .padding(horizontal = Dimens.x3),
        icon = Uri.parse(assetState.tokenIcon),
        name = assetState.tokenName,
        balance = assetState.assetAmount,
        symbol = frozenSuffix(assetState),
        fiat = assetState.assetFiatAmount,
        change = assetState.fiatChange,
        onClick = { onClick.invoke(assetState.tokenId) },
    )
}

/**
 * The row shows the liquid amount. When the account holds more of the asset that it cannot send
 * now, the same line names it ("0 XOR · 110 XOR frozen"), so that locked funds do not look lost.
 */
@Composable
private fun frozenSuffix(assetState: AssetItemCardState): String =
    assetState.frozenAmount?.let { stringResource(R.string.asset_frozen_amount_suffix, it) }
        .orEmpty()

@Composable
fun AssetItemEnumerated(
    modifier: Modifier = Modifier,
    assetState: AssetItemCardState,
    number: String,
    testTag: String,
    onClick: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .wrapContentHeight()
                .width(Dimens.x4),
            text = number,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary,
        )
        Asset(
            modifier = Modifier
                .testTagAsId(testTag),
            icon = Uri.parse(assetState.tokenIcon),
            name = assetState.tokenName,
            balance = assetState.assetAmount,
            symbol = frozenSuffix(assetState),
            fiat = assetState.assetFiatAmount,
            change = assetState.fiatChange,
            onClick = { onClick.invoke(assetState.tokenId) },
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun PreviewAssetItemEnumerated() {
    AssetItemEnumerated(
        assetState = previewAssetItemCardStateList.first(),
        number = "123",
        testTag = "",
        onClick = {},
    )
}
