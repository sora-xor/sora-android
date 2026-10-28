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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.CustomViewModelFactory
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.args.tokenId
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryScreenContainer
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class TxListFragment : SoraBaseFragment<TxListViewModel>() {

    @Inject
    lateinit var vmf: TxListViewModel.TxListViewModelFactory

    override val viewModel: TxListViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(requireArguments().tokenId)
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val historyState: HistoryState by viewModel.state.collectAsStateWithLifecycle()
            TxHistoryScreenContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2),
                historyState = historyState,
                onRefreshClick = viewModel::refresh,
                onHistoryItemClick = viewModel::onTxHistoryItemClick,
                onMoreHistoryItemRequested = viewModel::onMoreHistoryEventsRequested,
            )
        }
    }
}
