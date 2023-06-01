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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.send

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.PercentContainer
import jp.co.soramitsu.common.presentation.view.ToastDialog
import jp.co.soramitsu.common_wallet.presentation.compose.components.SwapSelectTokenScreen
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendConfirmScreen
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.send.SendScreen
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class TransferAmountFragment : SoraBaseFragment<TransferAmountViewModel>() {

    @Inject
    lateinit var vmf: TransferAmountViewModel.TransferAmountViewModelFactory

    override val viewModel: TransferAmountViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                requireArguments().getString(ARG_RECIPIENT_ID, ""),
                requireArguments().getString(ARG_TOKEN_ID, "")
            )
        }
    }

    companion object {
        private const val ARG_TOKEN_ID = "arg_asset_id"
        private const val ARG_RECIPIENT_ID = "arg_recipient_id"

        fun createBundle(
            recipientId: String,
            assetId: String,
        ): Bundle {
            return Bundle().apply {
                putString(ARG_RECIPIENT_ID, recipientId)
                putString(ARG_TOKEN_ID, assetId)
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        val onTokenChange: (String) -> Unit = {
            navController.popBackStack()
            viewModel.onTokenChange(it)
        }
        composable(SendRoutes.confirm) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2)
            ) {
                val state = viewModel.sendState
                SendConfirmScreen(
                    address = state.address,
                    icon = state.icon,
                    onAddressClick = viewModel::copyAddress,
                    onAddressLongClick = { },
                    inputState = state.input,
                    onConfirmClick = viewModel::onConfirmClick,
                    inProgress = state.inProgress,
                    feeFiat = state.feeFiat,
                    feeAmount = state.fee,
                    reviewEnabled = state.reviewEnabled,
                )
            }
        }
        composable(SendRoutes.selectToken) {
            SwapSelectTokenScreen(
                state = viewModel.sendState.selectSearchAssetState,
                scrollState = scrollState,
                onAssetSelect = onTokenChange,
            )
        }
        composable(
            route = SendRoutes.start,
        ) {
            val percentageVisibility = remember { mutableStateOf(false) }
            val onFocus: (Boolean) -> Unit = {
                percentageVisibility.value = it
            }
            val onReviewClick: () -> Unit = {
                viewModel.onReviewClick()
                navController.navigate(SendRoutes.confirm)
            }
            val onSelectTokenClick: () -> Unit = {
                navController.navigate(SendRoutes.selectToken)
            }
            PercentContainer(
                modifier = Modifier.fillMaxSize(),
                onSelectPercent = viewModel::optionSelected,
                barVisible = percentageVisibility.value,
            ) {
                val focusRequester = remember { FocusRequester() }
                Column(
                    modifier = Modifier
                        .padding(horizontal = Dimens.x2)
                        .padding(top = Dimens.x1)
                        .fillMaxSize()
                ) {
                    val state = viewModel.sendState
                    SendScreen(
                        address = state.address,
                        icon = state.icon,
                        onAddressClick = viewModel::onBackPressed,
                        onAddressLongClick = viewModel::copyAddress,
                        inputState = state.input,
                        onAmountChange = viewModel::amountChanged,
                        onSelectToken = onSelectTokenClick,
                        onFocusChange = onFocus,
                        onReviewClick = onReviewClick,
                        focusRequester = focusRequester,
                        feeLoading = state.feeLoading,
                        feeFiat = state.feeFiat,
                        feeAmount = state.fee,
                        reviewEnabled = state.reviewEnabled,
                    )
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewModel.copiedAddressEvent.observe {
            Toast.makeText(requireContext(), R.string.common_copied, Toast.LENGTH_SHORT).show()
        }
        viewModel.transactionSuccessEvent.observe {
            activity?.let {
                ToastDialog(
                    R.drawable.ic_green_pin,
                    R.string.wallet_transaction_submitted_1,
                    1000,
                    it
                ).show()
            }
        }
    }
}
