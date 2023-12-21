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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.co.soramitsu.common.presentation.compose.components.NothingFoundText
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.BasicPoolListItem
import jp.co.soramitsu.common_wallet.presentation.compose.previewBasicPoolListItemState
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.EcoSystemPoolsState
import jp.co.soramitsu.ui_core.component.card.ContentCardEndless
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
                        contentDescription = "",
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
