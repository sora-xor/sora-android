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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.presentation.compose.components.BasicSearchBar
import jp.co.soramitsu.common.presentation.compose.components.ContentCardEndless
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItemEnumerated
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemTokensState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
internal fun AllCurrenciesScreen(
    onTokenClicked: (String) -> Unit,
    onNavClicked: () -> Unit,
    viewModel: AllCurrenciesViewModel = hiltViewModel(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val state = viewModel.state.collectAsStateWithLifecycle().value
        AllCurrenciesInternal(
            state = state,
            onNavIconClicked = onNavClicked,
            onClearClicked = viewModel::onClearSearch,
            onSearch = viewModel::onSearch,
            onTokenClicked = onTokenClicked,
        )
    }
}

@Composable
private fun ColumnScope.AllCurrenciesInternal(
    state: EcoSystemTokensState,
    onNavIconClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onSearch: (String) -> Unit,
    onTokenClicked: (String) -> Unit,
) {
    BasicSearchBar(
        backgroundColor = MaterialTheme.customColors.bgPage,
        placeholder = "",
        action = null,
        onNavigate = onNavIconClicked,
        onClear = onClearClicked,
        onAction = null,
        onSearch = onSearch,
    )
    ContentCardEndless(
        modifier = Modifier
            .padding(start = Dimens.x2, end = Dimens.x2, top = Dimens.x2)
            .fillMaxWidth()
            .weight(1f),
        innerPadding = PaddingValues(all = Dimens.x2),
    ) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(
                count = state.topTokens.size,
            ) { position ->
                AssetItemEnumerated(
                    modifier = Modifier.padding(vertical = Dimens.x1),
                    assetState = state.topTokens[position].second,
                    number = state.topTokens[position].first,
                    testTag = "",
                    onClick = onTokenClicked,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun PreviewAllCurrenciesInternal() {
    Column(modifier = Modifier.fillMaxSize()) {
        AllCurrenciesInternal(
            state = EcoSystemTokensState(
                previewAssetItemCardStateList.mapIndexed { q, w ->
                    q.toString() to w
                }
            ),
            {}, {}, {}, {},
        )
    }
}
