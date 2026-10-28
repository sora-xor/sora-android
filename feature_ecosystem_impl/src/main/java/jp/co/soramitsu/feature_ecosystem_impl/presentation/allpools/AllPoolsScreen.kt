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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.allpools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.presentation.compose.components.NothingFoundText
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.BasicPoolListItem
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemPoolsState
import jp.co.soramitsu.common.presentation.compose.neon.ContentCardEndless
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AllPoolsScreen(
    onPoolClicked: (StringPair) -> Unit,
    onAddPoolClicked: () -> Unit,
    viewModel: AllPoolsViewModel = hiltViewModel(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val state = viewModel.poolsState.collectAsStateWithLifecycle().value
        AllPoolsInternal(
            state = state,
            onPoolClicked = onPoolClicked,
            onAddPoolClicked = onAddPoolClicked,
        )
    }
}

@Composable
private fun AllPoolsInternal(
    state: EcoSystemPoolsState,
    onPoolClicked: (StringPair) -> Unit,
    onAddPoolClicked: () -> Unit,
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(Dimens.x6)
                        .padding(Dimens.x1),
                    color = MaterialTheme.customColors.accentPrimary
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = Dimens.x1)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Dimens.x3, end = Dimens.x1),
                    ) {
                        Text(
                            text = stringResource(id = R.string.discovery_polkaswap_pools),
                            style = MaterialTheme.customTypography.headline2,
                            color = MaterialTheme.customColors.fgPrimary,
                            maxLines = 1,
                        )

                        Text(
                            text = stringResource(id = R.string.explore_provide_and_earn),
                            style = MaterialTheme.customTypography.textXSBold,
                            color = MaterialTheme.customColors.fgSecondary,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        modifier = Modifier
                            .size(Dimens.x3)
                            .clickable(onClick = onAddPoolClicked),
                        painter = painterResource(
                            id = jp.co.soramitsu.common.R.drawable.ic_plus,
                        ),
                        contentDescription = stringResource(R.string.explore_create_pool),
                        tint = MaterialTheme.customColors.fgSecondary,
                    )
                }
                if (state.pools.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        NothingFoundText()
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(
                            count = state.pools.size,
                        ) { position ->
                            BasicPoolListItem(
                                modifier = Modifier.padding(vertical = Dimens.x1),
                                state = state.pools[position],
                                onPoolClick = onPoolClicked,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAllPoolsInternal() {
    Column {
        AllPoolsInternal(
            state = EcoSystemPoolsState(
                pools = emptyList(),
            ),
            onPoolClicked = {},
            onAddPoolClicked = {},
        )
    }
}
