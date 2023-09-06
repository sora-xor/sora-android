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

package jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.ContentCardEndless
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItem
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
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
@Preview
private fun PreviewSelectSearchTokenCard2() {
    SelectSearchTokenCard(
        scrollState = rememberScrollState(),
        state = SelectSearchAssetState(
            list = emptyList(),
        ),
        onAssetSelect = {},
    )
}
