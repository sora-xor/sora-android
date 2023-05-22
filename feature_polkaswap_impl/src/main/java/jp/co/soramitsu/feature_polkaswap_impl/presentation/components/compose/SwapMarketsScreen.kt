/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
                        style = MaterialTheme.customTypography.textSBold
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = market.descriptionResource),
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
