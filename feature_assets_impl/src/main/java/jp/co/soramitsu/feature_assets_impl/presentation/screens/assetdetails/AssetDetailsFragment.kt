/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2)
            ) {
                val state = viewModel.state.state
                if (state.xorBalance != null) {
                    XorBalancesDialog(
                        state = state.xorBalance,
                        onClick = viewModel::onBalanceClick
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    AssetDetailsTokenPriceCard(
                        tokenName = state.tokenName,
                        tokenSymbol = state.tokenSymbol,
                        tokenPrice = state.price,
                        tokenPriceChange = state.priceChange,
                        iconUri = state.tokenIcon,
                    )
                    Spacer(modifier = Modifier.size(Dimens.x2))
                    AssetDetailsBalanceCard(
                        amount = state.transferableBalance,
                        amountFiat = state.transferableBalanceFiat,
                        frozenAmount = state.frozenBalance,
                        frozenAmountFiat = state.frozenBalanceFiat,
                        buyCryptoAvailable = state.buyCryptoAvailable,
                        isTransferableAmountAvailable = state.isTransferableBalanceAvailable,
                        hasHistory = state.events.isNotEmpty(),
                        onSendClick = viewModel::sendClicked,
                        onReceiveClick = viewModel::receiveClicked,
                        onSwapClick = viewModel::swapClicked,
                        onBalanceClick = viewModel::onBalanceClick,
                        onBuyCryptoClick = viewModel::onBuyCrypto
                    )
                    if (state.poolsState.pools.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(Dimens.x2))
                        AssetDetailsPooledCard(
                            title = state.poolsCardTitle,
                            state = state.poolsState,
                            onPoolClick = viewModel::onPoolClick
                        )
                    }
                    if (state.events.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(Dimens.x2))
                        AssetDetailsRecentActivityCard(
                            events = state.events,
                            onShowMoreActivity = viewModel::onRecentClick,
                            onHistoryItemClick = viewModel::onHistoryItemClick
                        )
                    }
                    Spacer(modifier = Modifier.size(Dimens.x2))
                    AssetIdCard(
                        id = state.tokenId,
                        onClick = viewModel::onAssetIdClick
                    )
                    Spacer(modifier = Modifier.size(Dimens.x2))
                }
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
