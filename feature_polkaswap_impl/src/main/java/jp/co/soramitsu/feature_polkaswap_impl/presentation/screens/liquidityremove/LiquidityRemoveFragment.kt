/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
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
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityRemoveConfirmScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.LiquidityRemoveScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap.SwapSlippageScreen
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
    override fun backgroundColorComposable() = colorResource(id = R.color.polkaswap_background_alfa)

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
                                style = MaterialTheme.customTypography.textSBold
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(id = R.string.remove_liquidity_info_text),
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
