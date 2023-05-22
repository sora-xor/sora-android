/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.txdetails

import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
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

        viewModel.copyEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
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
