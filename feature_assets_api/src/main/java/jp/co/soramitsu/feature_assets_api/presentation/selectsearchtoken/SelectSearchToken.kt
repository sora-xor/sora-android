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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItem
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
import jp.co.soramitsu.common.presentation.compose.neon.ContentCardEndless
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

data class SelectSearchAssetState(
    val list: List<AssetItemCardState>,
)

data class SearchTokenFilter(
    val filter: String,
    val tokenIds: List<String>,
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
        onAssetSelect = onAssetSelect,
    )
}

@Composable
private fun SelectSearchTokenCard(
    scrollState: ScrollState,
    state: SelectSearchAssetState,
    onAssetSelect: (String) -> Unit,
) {
    ContentCardEndless(
        modifier = Modifier
            .padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x2)
            .fillMaxSize(),
        innerPadding = PaddingValues(top = Dimens.x3),
    ) {
        if (state.list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = stringResource(id = R.string.search_empty_state),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.customTypography.paragraphM,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            }
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
