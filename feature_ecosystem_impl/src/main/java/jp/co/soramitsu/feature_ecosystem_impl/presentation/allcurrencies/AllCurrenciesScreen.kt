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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.allcurrencies

import jp.co.soramitsu.common.presentation.compose.components.LoadingRows
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.presentation.compose.components.NothingFoundText
import jp.co.soramitsu.common_wallet.presentation.compose.components.AssetItemEnumerated
import jp.co.soramitsu.common_wallet.presentation.compose.states.previewAssetItemCardStateList
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemTokensState
import jp.co.soramitsu.common.presentation.compose.neon.ContentCardEndless
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun AllCurrenciesScreen(
    onTokenClicked: (String) -> Unit,
    viewModel: AllCurrenciesViewModel = hiltViewModel(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val state = viewModel.state.collectAsStateWithLifecycle().value
        AllCurrenciesInternal(
            state = state,
            onTokenClicked = onTokenClicked,
        )
    }
}

@Composable
private fun AllCurrenciesInternal(
    state: EcoSystemTokensState,
    onTokenClicked: (String) -> Unit,
) {
    ContentCardEndless(
        modifier = Modifier
            .padding(horizontal = Dimens.x2)
            .fillMaxSize(),
        innerPadding = PaddingValues(
            start = Dimens.x1_2,
            end = Dimens.x3,
            top = Dimens.x3
        ),
    ) {
        if (state.loading) {
            LoadingRows(stringResource(R.string.common_loading))
        } else {
            Column {
                val listState = rememberLazyListState()
                if (state.topTokens.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        NothingFoundText()
                    }
                } else {
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
                },
            ),
        ) {}
    }
}
