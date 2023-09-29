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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.view.LoadMoreHandler
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCardEndless
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TxHistoryScreenContainer(
    modifier: Modifier,
    historyState: HistoryState,
    onRefreshClick: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onMoreHistoryItemRequested: () -> Unit,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = historyState.safeCast<HistoryState.History>()?.pullToRefresh ?: false,
        onRefresh = onRefreshClick
    )
    Box(modifier = modifier.pullRefresh(pullRefreshState, historyState is HistoryState.History)) {
        ContentCardEndless(
            innerPadding = PaddingValues(top = Dimens.x2),
            modifier = Modifier.fillMaxSize(),
        ) {
            TxHistoryScreen(
                historyState = historyState,
                onRefresh = onRefreshClick,
                onHistoryItemClick = onHistoryItemClick,
                onMoreHistoryItemRequested = onMoreHistoryItemRequested,
            )
        }
        PullRefreshIndicator(
            historyState.safeCast<HistoryState.History>()?.pullToRefresh ?: false,
            pullRefreshState,
            Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun TxHistoryScreen(
    historyState: HistoryState,
    onRefresh: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onMoreHistoryItemRequested: () -> Unit
) {
    when (historyState) {
        HistoryState.Error -> {
            TxHistoryErrorScreen(onRefresh)
        }

        is HistoryState.History -> {
            val list =
                if (historyState.endReached || historyState.hasErrorLoadingNew) historyState.events else buildList {
                    addAll(historyState.events)
                    add(EventUiModel.EventUiLoading)
                }

            Column(
                modifier = Modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (historyState.hasErrorLoadingNew) {
                    Text(
                        modifier = Modifier.padding(top = Dimens.x2),
                        textAlign = TextAlign.Center,
                        text = stringResource(id = R.string.activity_data_not_up_to_date_title),
                        style = MaterialTheme.customTypography.paragraphM,
                        color = MaterialTheme.customColors.fgSecondary
                    )

                    FilledButton(
                        modifier = Modifier
                            .padding(top = Dimens.x2),
                        text = stringResource(id = R.string.common_refresh),
                        onClick = onRefresh,
                        size = Size.ExtraSmall,
                        order = Order.SECONDARY,
                    )
                }
                TxHistoryList(
                    listItems = list,
                    onItemClick = onHistoryItemClick,
                    onLoadMore = onMoreHistoryItemRequested
                )
            }
        }

        HistoryState.Loading -> {
            Box(
                modifier = Modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.customColors.accentPrimary
                )
            }
        }

        HistoryState.NoData -> {
            Box(
                modifier = Modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x6),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    textAlign = TextAlign.Center,
                    text = stringResource(id = R.string.activity_empty_content_title),
                    style = MaterialTheme.customTypography.paragraphM,
                    color = MaterialTheme.customColors.fgSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TxHistoryList(
    listItems: List<EventUiModel>,
    onLoadMore: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .background(color = MaterialTheme.customColors.bgSurface)
            .fillMaxSize(),
    ) {
        listItems.forEach { event ->
            if (event is EventUiModel.EventTimeSeparatorUiModel) {
                stickyHeader(key = event.title) {
                    EventUiModelItem(event = event, onItemClick = onItemClick, modifier = Modifier)
                }
            } else {
                item(key = if (event is EventUiModel.EventTxUiModel) event.txHash else 0) {
                    EventUiModelItem(
                        event = event,
                        onItemClick = onItemClick,
                        modifier = Modifier.padding(horizontal = Dimens.x3)
                    )
                }
            }
        }
    }

    LoadMoreHandler(state = listState) {
        onLoadMore()
    }
}

@Composable
private fun EventUiModelItem(
    modifier: Modifier,
    event: EventUiModel,
    onItemClick: (String) -> Unit,
) {
    Spacer(
        modifier = Modifier
            .background(MaterialTheme.customColors.bgSurface)
            .fillMaxWidth()
            .height(Dimens.x1)
    )
    TxHistoryListItem(
        modifier = modifier,
        onTxHistoryItemClick = onItemClick,
        eventUiModel = event,
    )
}
