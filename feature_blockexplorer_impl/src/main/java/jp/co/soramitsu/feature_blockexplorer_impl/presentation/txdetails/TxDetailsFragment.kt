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

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txdetails

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.args.txHash
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
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

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val state = viewModel.txDetailsScreenState
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
                        )
                    }
                }
            }
        }
    }
}
