/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
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
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarketSelector(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.x1_2),
            value = market,
            enabled = isMarketSelectorEnabled,
            description = stringResource(id = R.string.polkaswap_market),
            onClick = onMarketClick,
        )
        MarketSelector(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
                .padding(horizontal = Dimens.x1_2),
            value = slippage,
            description = stringResource(id = R.string.slippage),
            onClick = onSlippageClick,
        )
    }
}

@Composable
internal fun MarketSelector(
    modifier: Modifier = Modifier.wrapContentSize(),
    value: String,
    enabled: Boolean = true,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            modifier = Modifier.weight(1f, false).wrapContentHeight(),
            text = description,
            textAlign = TextAlign.Center,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.size(Dimens.x1))
        BleachedButton(
            modifier = Modifier
                .testTagAsId(description)
                .wrapContentSize(),
            size = Size.ExtraSmall,
            order = Order.SECONDARY,
            text = value,
            enabled = enabled,
            maxLines = 1,
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
