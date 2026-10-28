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

package jp.co.soramitsu.common.view

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.distinctUntilChanged

abstract class LoadMoreListener : RecyclerView.OnScrollListener() {

    abstract fun onLoadMore(elements: Int)

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        val layoutManager = recyclerView.layoutManager
        if (dy > 0 && layoutManager is LinearLayoutManager) {
            val visibleElements = recyclerView.childCount
            val totalElements = layoutManager.itemCount
            val firstVisibleIndex = layoutManager.findFirstVisibleItemPosition()
            if (visibleElements + firstVisibleIndex >= totalElements) {
                onLoadMore(totalElements)
            }
        }
    }
}

@Composable
fun LoadMoreHandler(
    state: LazyListState,
    buffer: Int = 2,
    onLoadMore: () -> Unit,
) {
    val loadMore = remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1

            lastVisibleItemIndex > (totalItemsNumber - buffer)
        }
    }
    LaunchedEffect(loadMore) {
        snapshotFlow { loadMore.value }
            .distinctUntilChanged()
            .collect {
                onLoadMore()
            }
    }
}
