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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityremove

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.PercentContainer
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.ext.getColorFromAttrs
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityRemoveConfirmScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityRemoveScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapSlippageScreen
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class LiquidityRemoveFragment : SoraBaseFragment<LiquidityRemoveViewModel>() {

    companion object {
        private const val ARG_TOKEN_1 = "arg_token_1"
        private const val ARG_TOKEN_2 = "arg_token_2"
        fun createBundle(ids: StringPair) =
            bundleOf(ARG_TOKEN_1 to ids.first, ARG_TOKEN_2 to ids.second)
    }

    @Inject
    lateinit var vmf: LiquidityRemoveViewModel.AssistedLiquidityRemoveViewModelFactory

    override val viewModel: LiquidityRemoveViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                requireArguments().getString(ARG_TOKEN_1).orEmpty(),
                requireArguments().getString(ARG_TOKEN_2).orEmpty(),
            )
        }
    }

    @Composable
    override fun backgroundColorComposable() = Color(
        color = requireContext().getColorFromAttrs(
            R.attr.polkaswapBackground
        ).data
    )

    override fun backgroundColor(): Int = R.attr.polkaswapBackground

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        val onSlippageEntered: (Double) -> Unit = { f ->
            viewModel.slippageChanged(f)
            navController.popBackStack()
        }
        composable(LiquidityRemoveRoutes.slippage) {
            SwapSlippageScreen(
                value = viewModel.removeState.slippage,
                onDone = onSlippageEntered,
            )
        }
        composable(LiquidityRemoveRoutes.confirm) {
            LiquidityRemoveConfirmScreen(
                state = viewModel.removeState,
                onConfirmClick = viewModel::onConfirmClick,
            )
        }
        composable(
            route = LiquidityRemoveRoutes.start,
        ) {
            val percentageVisibility = remember { mutableStateOf(false) }
            PercentContainer(
                modifier = Modifier
                    .fillMaxSize(),
                onSelectPercent = viewModel::onPercentClick,
                barVisible = percentageVisibility.value,
            ) {
                val onFocus: (Boolean) -> Unit = { f ->
                    percentageVisibility.value = f
                }
                if (viewModel.removeState.hintVisible) {
                    AlertDialog(
                        title = {
                            Text(
                                text = stringResource(id = R.string.remove_liquidity_title),
                                color = MaterialTheme.customColors.fgPrimary,
                                style = MaterialTheme.customTypography.textSBold
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(id = R.string.remove_liquidity_info_text),
                                color = MaterialTheme.customColors.fgPrimary,
                                style = MaterialTheme.customTypography.paragraphSBold
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = viewModel::dismissHint
                            ) {
                                Text(
                                    text = stringResource(id = R.string.common_ok),
                                    color = Color.Red,
                                )
                            }
                        },
                        onDismissRequest = viewModel::dismissHint,
                    )
                }
                val onSlippageClick = {
                    navController.navigate(LiquidityRemoveRoutes.slippage)
                }
                val onReview: () -> Unit = {
                    viewModel.onReviewClick()
                    navController.navigate(LiquidityRemoveRoutes.confirm)
                }
                LiquidityRemoveScreen(
                    state = viewModel.removeState,
                    onFocusChange1 = onFocus,
                    onFocusChange2 = onFocus,
                    onAmountChange1 = viewModel::onAmount1Change,
                    onAmountChange2 = viewModel::onAmount2Change,
                    onSlippageClick = onSlippageClick,
                    onReview = onReview,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        super.onViewCreated(view, savedInstanceState)
    }
}
