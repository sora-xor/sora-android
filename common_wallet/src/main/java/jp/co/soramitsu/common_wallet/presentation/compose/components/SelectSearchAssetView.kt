/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.presentation.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.ui_core.component.asset.Asset
import jp.co.soramitsu.ui_core.component.searchview.SearchView
import jp.co.soramitsu.ui_core.component.searchview.SearchViewState
import jp.co.soramitsu.ui_core.resources.Dimens

data class SelectSearchAssetState(
    val filter: String,
    val fullList: List<AssetItemCardState>,
)

private fun isFilterMatch(asset: AssetItemCardState, filter: String): Boolean {
    return asset.tokenName.lowercase().contains(filter.lowercase()) ||
        asset.tokenSymbol.lowercase().contains(filter.lowercase()) ||
        asset.tokenId.lowercase().contains(filter.lowercase())
}

private fun filterAssets(list: List<AssetItemCardState>, filter: String): List<AssetItemCardState> {
    return list.filter {
        isFilterMatch(it, filter)
    }
}

@Composable
fun SelectSearchAssetView(
    state: SelectSearchAssetState,
    scrollState: ScrollState,
    onSelect: (String) -> Unit,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = state.filter)) }
    var filtered = filterAssets(
        state.fullList,
        fieldValue.text,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .verticalScroll(scrollState)
    ) {
        SearchView(
            modifier = Modifier
                .testTagAsId("tokenSearchField")
                .fillMaxWidth()
                .wrapContentHeight(),
            state = SearchViewState(value = fieldValue),
            onValueChange = {
                fieldValue = it
                filtered = filterAssets(state.fullList, it.text)
            },
            onAction = {
                fieldValue = TextFieldValue("")
            },
        )
        Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x2))
        filtered.forEachIndexed { index, assetState ->
            Asset(
                modifier = Modifier.testTagAsId("${assetState.tokenSymbol}Element"),
                icon = assetState.tokenIcon,
                name = assetState.tokenName,
                balance = assetState.assetAmount,
                symbol = "",
                fiat = assetState.assetFiatAmount,
                change = assetState.fiatChange,
                onClick = { onSelect.invoke(assetState.tokenId) },
            )
            if (index < filtered.lastIndex) {
                Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x2))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSelectSearchAssetView() {
    SelectSearchAssetView(
        state = SelectSearchAssetState(
            filter = "some",
            fullList = listOf(
                AssetItemCardState(
                    tokenName = "some qwe",
                    tokenId = "id 01",
                    tokenIcon = DEFAULT_ICON_URI,
                    assetAmount = "13.3 XOR",
                    tokenSymbol = "XOR",
                    assetFiatAmount = "$45.9",
                    fiatChange = "+34%"
                ),
                AssetItemCardState(
                    tokenName = "some asd",
                    tokenId = "id 01",
                    tokenIcon = DEFAULT_ICON_URI,
                    assetAmount = "1238.3 VAL",
                    tokenSymbol = "VAL",
                    assetFiatAmount = "$0.09",
                    fiatChange = "-0.12%"
                ),
            )
        ),
        scrollState = rememberScrollState(),
        onSelect = {},
    )
}
