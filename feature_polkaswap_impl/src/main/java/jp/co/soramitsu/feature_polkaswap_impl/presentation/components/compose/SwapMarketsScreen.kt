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

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.AlertDialog
import androidx.compose.material.IconButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.neon.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SwapMarketsScreen(
    selected: Market,
    markets: List<Market>,
    onMarketSelect: (Market) -> Unit,
    emptyText: Int = jp.co.soramitsu.common.R.string.swap_market_empty,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2)
    ) {
        ContentCard(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth(),
            innerPadding = PaddingValues(vertical = Dimens.x2, horizontal = Dimens.x3),
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                if (markets.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painterResource(jp.co.soramitsu.common.R.drawable.ic_polkaswap_40), null,
                            modifier = Modifier.size(36.dp), tint = MaterialTheme.neon.cyan,
                        )
                        Text(
                            stringResource(emptyText), modifier = Modifier.padding(Dimens.x2),
                            textAlign = TextAlign.Center, style = MaterialTheme.customTypography.textM,
                            color = MaterialTheme.customColors.fgPrimary,
                        )
                        if (onBack != null) {
                            TonalButton(
                                modifier = Modifier.fillMaxWidth(), size = Size.Large, order = Order.SECONDARY,
                                text = stringResource(jp.co.soramitsu.common.R.string.common_back), onClick = onBack,
                            )
                        }
                    }
                }
                markets.forEach { market ->
                    MarketScreenItem(
                        selected = market.name == selected.name,
                        market = market,
                        onClick = { onMarketSelect.invoke(market) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketScreenItem(
    selected: Boolean,
    market: Market,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(min = 48.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var hintVisible by rememberSaveable { mutableStateOf(false) }
        if (hintVisible) {
            AlertDialog(
                backgroundColor = MaterialTheme.customColors.bgPage,
                title = {
                    Text(
                        text = stringResource(id = market.titleResource),
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.textSBold
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = market.descriptionResource),
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.paragraphSBold
                    )
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTagAsId("MarketAlertOkButton"),
                        onClick = { hintVisible = false }
                    ) {
                        Text(
                            text = stringResource(id = jp.co.soramitsu.common.R.string.common_ok),
                            color = MaterialTheme.neon.cyan,
                        )
                    }
                },
                onDismissRequest = {
                    hintVisible = false
                },
            )
        }
        if (selected) {
            Icon(
                modifier = Modifier.size(Dimens.x3),
                painter = painterResource(R.drawable.ic_check_rounded),
                tint = MaterialTheme.neon.cyan,
                contentDescription = null
            )
        } else {
            Spacer(modifier = Modifier.size(Dimens.x3))
        }
        Text(
            modifier = Modifier
                .testTagAsId("MarketTitle")
                .padding(horizontal = Dimens.x1)
                .weight(1f)
                .wrapContentHeight(),
            textAlign = TextAlign.Start,
            text = stringResource(id = market.titleResource),
            style = MaterialTheme.customTypography.textM,
            color = MaterialTheme.customColors.fgPrimary,
        )
        IconButton(
            modifier = Modifier.testTagAsId("MarketHintIcon").size(48.dp),
            onClick = { hintVisible = true },
        ) {
            Icon(
                painter = painterResource(jp.co.soramitsu.common.R.drawable.ic_neu_exclamation),
                tint = MaterialTheme.customColors.fgSecondary,
                contentDescription = stringResource(jp.co.soramitsu.common.R.string.swap_about_market, stringResource(market.titleResource)),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSwapMarketsScreen() {
    SwapMarketsScreen(
        onMarketSelect = {},
        selected = Market.SMART,
        markets = listOf(Market.SMART, Market.TBC, Market.XYK),
    )
}
