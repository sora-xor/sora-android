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

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txdetails

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
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
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.args.txHash
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsDemeterStake
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsLiquidity
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsReferralOrTransferScreen
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxDetailsSwap
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails.TxType

@AndroidEntryPoint
class TxDetailsFragment : SoraBaseFragment<TxDetailsViewModel>() {

    @Inject
    lateinit var vmf: TxDetailsViewModel.TxDetailsViewModelFactory

    override val viewModel: TxDetailsViewModel by viewModels {
        CustomViewModelFactory { vmf.create(requireArguments().txHash) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                val state = viewModel.txDetailsScreenState.collectAsStateWithLifecycle().value
                when (state.txType) {
                    TxType.LIQUIDITY -> {
                        TxDetailsLiquidity(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            state = state.basicTxDetailsState,
                            amount1 = state.amount1,
                            amount2 = state.amount2 ?: "",
                            isAmountGreen = state.isAmountGreen,
                            icon1 = state.icon1,
                            icon2 = state.icon2 ?: DEFAULT_ICON_URI,
                            onCloseClick = ::onBack,
                            onCopyClick = viewModel::onCopyClicked,
                            onOpenExplorerClick = ::onOpenExplorerClicked,
                        )
                    }
                    TxType.DEMETER -> {
                        TxDetailsDemeterStake(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            state = state.basicTxDetailsState,
                            amount1 = state.amount1,
                            amount2 = state.amount2 ?: "",
                            isAmountGreen = state.isAmountGreen,
                            icon1 = state.icon1,
                            icon2 = state.icon2 ?: DEFAULT_ICON_URI,
                            icon3 = state.icon3 ?: DEFAULT_ICON_URI,
                            onCloseClick = ::onBack,
                            onCopyClick = viewModel::onCopyClicked,
                            onOpenExplorerClick = ::onOpenExplorerClicked,
                        )
                    }
                    TxType.REFERRAL_TRANSFER -> {
                        TxDetailsReferralOrTransferScreen(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            state = state.basicTxDetailsState,
                            amount = state.amount1,
                            icon = state.icon1,
                            isAmountGreen = state.isAmountGreen,
                            onCloseClick = ::onBack,
                            onCopyClick = viewModel::onCopyClicked,
                            onOpenExplorerClick = ::onOpenExplorerClicked,
                        )
                    }
                    TxType.SWAP -> {
                        TxDetailsSwap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            state = state.basicTxDetailsState,
                            amount1 = state.amount1,
                            amount2 = state.amount2 ?: "",
                            icon1 = state.icon1,
                            icon2 = state.icon2 ?: DEFAULT_ICON_URI,
                            onCloseClick = ::onBack,
                            onCopyClick = viewModel::onCopyClicked,
                            onOpenExplorerClick = ::onOpenExplorerClicked,
                        )
                    }
                }
            }
        }
    }

    private fun onOpenExplorerClicked(url: String) {
        ShareUtil.shareInBrowser(requireContext(), url)
    }
}
