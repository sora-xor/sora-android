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

package jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.LoadingRows
import jp.co.soramitsu.common.presentation.compose.components.NothingFoundText
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItem
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
import jp.co.soramitsu.common.presentation.compose.neon.ContentCardEndless
import jp.co.soramitsu.ui_core.resources.Dimens

data class SelectSearchAssetState(
    val list: List<AssetItemCardState>,
    val loading: Boolean = false,
)

data class SearchTokenFilter(
    val filter: String,
    val tokenIds: List<String>,
    val restrictToTokenIds: Boolean = false,
)

val emptySearchTokenFilter = SearchTokenFilter("", emptyList())

@Composable
fun SelectSearchTokenScreen(
    scrollState: ScrollState,
    searchTokenFilter: SearchTokenFilter,
    viewModel: SelectSearchTokenViewModel = hiltViewModel(),
    onAssetSelect: (String) -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(searchTokenFilter) {
        viewModel.onFilterChange(searchTokenFilter)
    }
    SelectSearchTokenCard(
        scrollState = scrollState,
        state = state,
        searching = searchTokenFilter.filter.isNotBlank(),
        onAssetSelect = onAssetSelect,
    )
}

@Composable
private fun SelectSearchTokenCard(
    scrollState: ScrollState,
    state: SelectSearchAssetState,
    onAssetSelect: (String) -> Unit,
    searching: Boolean = false,
) {
    ContentCardEndless(
        modifier = Modifier
            .padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x2)
            .fillMaxSize(),
        innerPadding = PaddingValues(top = Dimens.x3),
    ) {
        if (state.loading) {
            LoadingRows(description = stringResource(R.string.common_loading))
        } else if (state.list.isEmpty()) {
            NothingFoundText(stringResource(if (searching) R.string.search_empty_state else R.string.asset_picker_empty))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                state.list.forEachIndexed { index, assetState ->
                    AssetItem(
                        assetState = assetState,
                        testTag = "${assetState.tokenSymbol}Element",
                        onClick = onAssetSelect,
                    )
                    if (index < state.list.lastIndex) {
                        Divider(color = Color.Transparent, modifier = Modifier.height(Dimens.x1))
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun PreviewSelectSearchTokenCard1() {
    SelectSearchTokenCard(
        scrollState = rememberScrollState(),
        state = SelectSearchAssetState(
            list = previewAssetItemCardStateList,
        ),
        onAssetSelect = {},
    )
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun PreviewSelectSearchTokenCard2() {
    SelectSearchTokenCard(
        scrollState = rememberScrollState(),
        state = SelectSearchAssetState(
            list = emptyList(),
        ),
        onAssetSelect = {},
    )
}
