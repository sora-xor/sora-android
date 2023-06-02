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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsBalanceCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsPooledCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsRecentActivityCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsTokenPriceCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetIdCard
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

    @OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class)
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
                if (stateData.xorBalance != null) {
                    XorBalancesDialog(
                        state = stateData.xorBalance,
                        onClick = viewModel::onBalanceClick
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    AssetDetailsTokenPriceCard(
                        tokenName = stateData.tokenName,
                        tokenSymbol = stateData.tokenSymbol,
                        tokenPrice = stateData.price,
                        tokenPriceChange = stateData.priceChange,
                        iconUri = stateData.tokenIcon,
                    )
                    Spacer(modifier = Modifier.size(Dimens.x2))
                    AssetDetailsBalanceCard(
                        amount = stateData.transferableBalance,
                        amountFiat = stateData.transferableBalanceFiat,
                        frozenAmount = stateData.frozenBalance,
                        frozenAmountFiat = stateData.frozenBalanceFiat,
                        buyCryptoAvailable = stateData.buyCryptoAvailable,
                        isTransferableAmountAvailable = stateData.isTransferableBalanceAvailable,
                        hasHistory = stateData.events.isNotEmpty(),
                        onSendClick = viewModel::sendClicked,
                        onReceiveClick = viewModel::receiveClicked,
                        onSwapClick = viewModel::swapClicked,
                        onBalanceClick = viewModel::onBalanceClick,
                        onBuyCryptoClick = viewModel::onBuyCrypto
                    )
                    if (stateData.poolsState.pools.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(Dimens.x2))
                        AssetDetailsPooledCard(
                            title = stateData.poolsCardTitle,
                            state = stateData.poolsState,
                            onPoolClick = viewModel::onPoolClick
                        )
                    }
                    if (stateData.events.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(Dimens.x2))
                        AssetDetailsRecentActivityCard(
                            events = stateData.events,
                            onShowMoreActivity = viewModel::onRecentClick,
                            onHistoryItemClick = viewModel::onHistoryItemClick
                        )
                    }
                    Spacer(modifier = Modifier.size(Dimens.x2))
                    AssetIdCard(
                        id = stateData.tokenId,
                        onClick = viewModel::onAssetIdClick
                    )
                    Spacer(modifier = Modifier.size(Dimens.x2))
                }
                PullRefreshIndicator(assetState.loading, pullRefresh, Modifier.align(Alignment.TopCenter))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        viewModel.copyEvent.observe {
            Toast.makeText(requireActivity(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
