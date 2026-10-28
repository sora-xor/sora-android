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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun TxDetailsReferralOrTransferScreen(
    modifier: Modifier,
    state: BasicTxDetailsState,
    icon: String,
    isAmountGreen: Boolean = false,
    amount: String,
    onCloseClick: () -> Unit,
    onCopyClick: (String) -> Unit,
    onOpenExplorerClick: (String) -> Unit,
) {
    BasicTxDetails(
        modifier = modifier,
        state = state,
        imageContent = {
            TokenIcon(
                uri = icon,
                size = Dimens.x9,
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
        onOpenExplorer = onOpenExplorerClick,
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
        onOpenExplorerClick = {},
    )
}
