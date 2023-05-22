/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.LoadMoreHandler
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun TxHistoryScreen(
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
            val list = if (historyState.endReached || historyState.hasErrorLoadingNew) historyState.events else buildList {
                addAll(historyState.events)
                add(EventUiModel.EventUiLoading)
            }

            Column(
                modifier = Modifier
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
            .fillMaxWidth()
            .height(Dimens.x1)
            .background(MaterialTheme.customColors.bgSurface)
    )
    TxHistoryListItem(
        modifier = modifier,
        onTxHistoryItemClick = onItemClick,
        eventUiModel = event,
    )
}
