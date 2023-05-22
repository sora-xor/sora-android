/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetslist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.feature_assets_impl.presentation.states.FullAssetListState
import jp.co.soramitsu.ui_core.component.asset.Asset
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun ColumnScope.CommonAssetsList(
    state: FullAssetListState,
    onAssetClick: (String) -> Unit,
) {
    state.topList.forEachIndexed { index, assetState ->
        Asset(
            icon = assetState.tokenIcon,
            name = assetState.tokenName,
            balance = assetState.assetAmount,
            symbol = "",
            fiat = assetState.assetFiatAmount,
            change = assetState.fiatChange,
            onClick = { onAssetClick.invoke(assetState.tokenId) }
        )
        if (index < state.topList.lastIndex) {
            Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x2))
        }
    }
    if (state.bottomList.isNotEmpty()) {
        val (collapseState, collapseMethod) = remember { mutableStateOf(state.topList.isEmpty()) }
        TonalButton(
            modifier = Modifier
                .padding(vertical = Dimens.x3)
                .align(Alignment.CenterHorizontally),
            size = Size.ExtraSmall,
            order = Order.SECONDARY,
            text = stringResource(id = if (collapseState) R.string.hide_zero_balances else R.string.show_zero_balances),
            rightIcon = painterResource(id = if (collapseState) R.drawable.ic_chevron_up_rounded_16 else R.drawable.ic_chevron_down_rounded_16)
        ) {
            collapseMethod.invoke(!collapseState)
        }
        AnimatedVisibility(visible = collapseState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                state.bottomList.forEachIndexed { index, assetState ->
                    Asset(
                        icon = assetState.tokenIcon,
                        name = assetState.tokenName,
                        balance = assetState.assetAmount,
                        symbol = "",
                        fiat = assetState.assetFiatAmount,
                        change = assetState.fiatChange,
                        onClick = { onAssetClick.invoke(assetState.tokenId) }
                    )
                    if (index < state.bottomList.lastIndex) {
                        Divider(
                            color = Color.Transparent,
                            modifier = Modifier.height(Dimens.x2)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCommonAssetsList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        CommonAssetsList(
            onAssetClick = {},
            state = FullAssetListState(
                searchMode = false,
                topList = listOf(
                    AssetItemCardState(
                        tokenName = "qwe",
                        tokenId = "id 01",
                        tokenIcon = DEFAULT_ICON_URI,
                        assetAmount = "13.3",
                        tokenSymbol = "XOR",
                        assetFiatAmount = "$45.9",
                        fiatChange = "+34%"
                    ),
                    AssetItemCardState(
                        tokenName = "qwe",
                        tokenId = "id 02",
                        tokenIcon = DEFAULT_ICON_URI,
                        assetAmount = "13.3",
                        tokenSymbol = "XOR",
                        assetFiatAmount = "$45.9",
                        fiatChange = "+34%"
                    ),
                    AssetItemCardState(
                        tokenName = "qwe",
                        tokenId = "id 03",
                        tokenIcon = DEFAULT_ICON_URI,
                        assetAmount = "13.3",
                        tokenSymbol = "XOR",
                        assetFiatAmount = "$45.9",
                        fiatChange = "+34%"
                    ),
                ),
                bottomList = listOf(
                    AssetItemCardState(
                        tokenName = "qwe",
                        tokenId = "id 01",
                        tokenIcon = DEFAULT_ICON_URI,
                        assetAmount = "13.3",
                        tokenSymbol = "XOR",
                        assetFiatAmount = "$45.9",
                        fiatChange = "+34%"
                    ),
                    AssetItemCardState(
                        tokenName = "qwe",
                        tokenId = "id 02",
                        tokenIcon = DEFAULT_ICON_URI,
                        assetAmount = "13.3",
                        tokenSymbol = "XOR",
                        assetFiatAmount = "$45.9",
                        fiatChange = "+34%"
                    ),
                    AssetItemCardState(
                        tokenName = "qwe",
                        tokenId = "id 03",
                        tokenIcon = DEFAULT_ICON_URI,
                        assetAmount = "13.3",
                        tokenSymbol = "XOR",
                        assetFiatAmount = "$45.9",
                        fiatChange = "+34%"
                    ),
                ),
                fiatSum = "$12 123.44"
            ),
        )
    }
}
