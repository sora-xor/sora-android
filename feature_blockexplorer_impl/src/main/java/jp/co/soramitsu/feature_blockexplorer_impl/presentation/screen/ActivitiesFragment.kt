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

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class ActivitiesFragment : SoraBaseFragment<ActivitiesViewModel>() {

    override val viewModel: ActivitiesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2)
            ) {
                Text(
                    modifier = Modifier.padding(
                        top = Dimens.x3,
                    ),
                    text = stringResource(id = R.string.common_activity),
                    style = MaterialTheme.customTypography.headline1,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                val historyState: HistoryState by viewModel.historyState.collectAsStateWithLifecycle()
                val networkActivity by viewModel.networkActivity.collectAsStateWithLifecycle()
                AccountActivityScreen(
                    modifier = Modifier.padding(top = Dimens.x2).fillMaxSize(),
                    historyState = historyState,
                    networkActivity = networkActivity,
                    onRefreshClick = viewModel::refresh,
                    onHistoryItemClick = viewModel::onTxHistoryItemClick,
                    onMoreHistoryItemRequested = viewModel::onMoreHistoryEventsRequested,
                    onMoreNetworkHistoryRequested = viewModel::loadMoreNetworkHistory,
                )
            }
        }
    }
}
