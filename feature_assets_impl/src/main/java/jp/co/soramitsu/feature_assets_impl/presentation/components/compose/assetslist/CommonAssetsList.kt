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
