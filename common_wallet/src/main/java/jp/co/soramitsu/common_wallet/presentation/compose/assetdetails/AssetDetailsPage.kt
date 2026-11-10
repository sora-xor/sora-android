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

package jp.co.soramitsu.common_wallet.presentation.compose.assetdetails

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import jp.co.soramitsu.ui_core.resources.Dimens

/**
 * An asset page's frame: the page margins and pull to refresh. The SORA2 asset page and the XOR
 * pages of the other networks are framed by it.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AssetDetailsPage(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val pullRefresh = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onRefresh,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.x2)
            .pullRefresh(pullRefresh, true)
    ) {
        content()
        PullRefreshIndicator(
            refreshing,
            pullRefresh,
            Modifier.align(Alignment.TopCenter)
        )
    }
}
