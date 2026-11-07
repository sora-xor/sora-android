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

package jp.co.soramitsu.common_wallet.presentation.compose.assetdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.common.presentation.compose.neon.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * An identifier to copy: the asset ID of a SORA2 asset, or the account address of another
 * network's XOR. Tapping the card copies it. The parameters after [onClick] default to the SORA2
 * asset page.
 *
 * @param title what the identifier is.
 * @param actionText a link under the identifier (for example the block explorer), when not null.
 */
@Composable
fun AssetIdCard(
    id: String,
    onClick: () -> Unit,
    title: String = stringResource(id = R.string.asset_details_asset_id),
    actionText: String? = null,
    onActionClick: () -> Unit = {},
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Text(
                text = title,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary
            )
            Text(
                text = id,
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphXS
            )
            if (actionText != null) {
                TextButton(
                    modifier = Modifier
                        .padding(top = Dimens.x1)
                        .wrapContentSize(),
                    text = actionText,
                    size = Size.ExtraSmall,
                    order = Order.PRIMARY,
                    onClick = onActionClick,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewCard() {
    AssetIdCard(
        id = "0x0200050000000000000000000000000000000000000000000000000000000000",
        onClick = {}
    )
}
