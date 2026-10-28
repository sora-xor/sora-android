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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SwapAmountSquare(
    modifier: Modifier,
    icon: String,
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
            TokenIcon(
                uri = icon,
                size = Size.Small,
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
                color = MaterialTheme.customColors.fgPrimary,
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
