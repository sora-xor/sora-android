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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.CustomViewModelFactory
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class AssetDetailsFragment : SoraBaseFragment<AssetDetailsViewModel>() {

    companion object {
        private const val KEY_ASSET_ID = "assetId"

        fun createBundle(
            assetId: String
        ): Bundle {
            return Bundle().apply {
                putString(KEY_ASSET_ID, assetId)
            }
        }
    }

    @Inject
    lateinit var vmf: AssetDetailsViewModel.AssetDetailsViewModelFactory

    override val viewModel: AssetDetailsViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(requireArguments().getString(KEY_ASSET_ID, ""))
        }
    }

    @OptIn(ExperimentalMaterialApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val assetState = viewModel.state
            val stateData = assetState.state
            val pullRefresh = rememberPullRefreshState(
                refreshing = assetState.loading,
                onRefresh = viewModel::onPullToRefresh
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2)
                    .pullRefresh(pullRefresh, true)
            ) {
                AssetDetailsScreen(
                    stateData = stateData,
                    scrollState = scrollState,
                    onBalanceClick = viewModel::onBalanceClick,
                    onSendClick = viewModel::sendClicked,
                    onReceiveClick = viewModel::receiveClicked,
                    onSwapClick = viewModel::swapClicked,
                    onPoolClick = viewModel::onPoolClick,
                    onRecentClick = viewModel::onRecentClick,
                    onHistoryItemClick = viewModel::onHistoryItemClick,
                    onAssetIdClick = viewModel::onAssetIdClick,
                )
                PullRefreshIndicator(
                    assetState.loading,
                    pullRefresh,
                    Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
    }
}
