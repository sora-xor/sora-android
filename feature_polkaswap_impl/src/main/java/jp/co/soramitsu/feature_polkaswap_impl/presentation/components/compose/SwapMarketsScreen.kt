/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.R
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun SwapMarketsScreen(
    selected: Market,
    markets: List<Market>,
    onMarketSelect: (Market) -> Unit,
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
            .padding(vertical = Dimens.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var hintVisible by remember { mutableStateOf(false) }
        if (hintVisible) {
            AlertDialog(
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
                            color = Color.Red,
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
                tint = MaterialTheme.customColors.statusSuccess,
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
                .wrapContentHeight()
                .clickable(onClick = onClick),
            textAlign = TextAlign.Start,
            text = stringResource(id = market.titleResource),
            style = MaterialTheme.customTypography.textM,
            color = MaterialTheme.customColors.fgPrimary,
        )
        Icon(
            modifier = Modifier
                .testTagAsId("MarketHintIcon")
                .size(Dimens.x3)
                .clickable {
                    hintVisible = true
                },
            painter = painterResource(jp.co.soramitsu.common.R.drawable.ic_neu_exclamation),
            tint = MaterialTheme.customColors.fgSecondary,
            contentDescription = null
        )
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
