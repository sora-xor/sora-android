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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SwapMarketSlippageSelector(
    market: String,
    slippage: String,
    isMarketSelectorEnabled: Boolean,
    onMarketClick: () -> Unit,
    onSlippageClick: () -> Unit,
    isSlippageSelectorEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.x2),
    ) {
        MarketSelector(
            modifier = Modifier
                .weight(1f),
            value = market,
            enabled = isMarketSelectorEnabled,
            description = stringResource(id = R.string.polkaswap_market),
            onClick = onMarketClick,
        )
        MarketSelector(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f),
            value = slippage,
            enabled = isSlippageSelectorEnabled,
            description = stringResource(id = R.string.slippage),
            onClick = onSlippageClick,
        )
    }
}

@Composable
internal fun MarketSelector(
    modifier: Modifier = Modifier,
    value: String,
    enabled: Boolean = true,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = description,
            textAlign = TextAlign.Center,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary,
        )
        BleachedButton(
            modifier = Modifier
                .testTagAsId(description)
                .fillMaxWidth(),
            size = 48.dp,
            order = Order.SECONDARY,
            text = value,
            enabled = enabled,
            maxLines = 2,
            onClick = onClick,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 16717215)
@Composable
private fun Preview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SwapMarketSlippageSelector(
            market = "Smart",
            slippage = "0.15%",
            onMarketClick = { },
            onSlippageClick = { },
            isMarketSelectorEnabled = true,
        )
        MarketSelector(
            value = "Value",
            description = "Desc",
            onClick = {},
        )
    }
}
