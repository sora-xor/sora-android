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
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
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
            AssetItem(
                assetState = assetState,
                testTag = "${assetState.tokenSymbol}Element",
                onClick = onSelect,
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
            fullList = previewAssetItemCardStateList,
        ),
        scrollState = rememberScrollState(),
        onSelect = {},
    )
}
