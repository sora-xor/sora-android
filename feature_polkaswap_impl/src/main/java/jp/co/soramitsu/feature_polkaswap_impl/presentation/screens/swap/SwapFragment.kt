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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.CustomViewModelFactory
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.presentation.args.tokenFromId
import jp.co.soramitsu.common.presentation.args.tokenToId
import jp.co.soramitsu.common.presentation.compose.components.PercentContainer
import jp.co.soramitsu.common.presentation.compose.components.PolkaswapDisclaimer
import jp.co.soramitsu.common.util.ext.getColorFromAttrs
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SelectSearchTokenScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapConfirmScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapMainScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapMarketsScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapSlippageScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@ExperimentalCoroutinesApi
@FlowPreview
@AndroidEntryPoint
class SwapFragment : SoraBaseFragment<SwapViewModel>() {

    /**
     * Swap signs with the selected account. When that account cannot sign on this phone, the
     * screen says so above the form and offers the repair (`SoraBaseFragment`).
     */
    override val explainsReadOnlyAccount: Boolean = true

    @Inject
    lateinit var vmf: SwapViewModel.AssistedSwapViewModelFactory

    override val viewModel: SwapViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                idFrom = requireArguments().tokenFromId,
                idTo = requireArguments().tokenToId,
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        super.onViewCreated(view, savedInstanceState)
    }

    @Composable
    override fun backgroundColorComposable() = Color(
        color = requireContext().getColorFromAttrs(
            R.attr.polkaswapBackground
        ).data
    )

    override fun backgroundColor(): Int = R.attr.polkaswapBackground

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        viewModel.navigationDisclaimerEvent.observe {
            navController.navigate(SwapRoutes.disclaimer)
        }
        composable(SwapRoutes.confirm) {
            SwapConfirmScreen(
                state = viewModel.swapMainState.collectAsStateWithLifecycle().value,
                onConfirmClick = viewModel::onConfirmClicked,
            )
        }
        composable(SwapRoutes.disclaimer) {
            val onDisclaimerClose: () -> Unit = {
                viewModel.onDisclaimerClose()
                navController.popBackStack()
            }
            Box(modifier = Modifier.verticalScroll(scrollState)) {
                PolkaswapDisclaimer(
                    onDisclaimerClose = onDisclaimerClose,
                )
            }
        }
        composable(SwapRoutes.selectToken) {
            val onTokenSelected: (String, String) -> Unit = { id, type ->
                debounceClickHandler.debounceClick {
                    navController.popBackStack()

                    if (type == SwapRoutes.SelectTokenParam.FROM.path) {
                        viewModel.fromAssetSelected(id)
                    } else if (type == SwapRoutes.SelectTokenParam.TO.path) {
                        viewModel.toAssetSelected(id)
                    }
                }
            }
            val type = requireNotNull(it.arguments?.getString(SwapRoutes.selectTokenParamName))
            val state = viewModel.swapTokensFilter.collectAsStateWithLifecycle()
            SelectSearchTokenScreen(
                scrollState = scrollState,
                onAssetSelect = { id -> onTokenSelected.invoke(id, type) },
                searchTokenFilter = state.value,
            )
        }
        composable(SwapRoutes.markets) {
            val onMarketSelected: (Market) -> Unit = {
                viewModel.onMarketSelected(it)
                navController.popBackStack()
            }
            val state =
                viewModel.swapMainState.collectAsStateWithLifecycle().value.selectMarketState
            if (state != null) {
                SwapMarketsScreen(
                    selected = state.first,
                    markets = state.second,
                    onMarketSelect = onMarketSelected,
                )
            }
        }
        composable(SwapRoutes.slippage) {
            val onSlippageEntered: (Double) -> Unit = {
                viewModel.slippageChanged(it)
                navController.popBackStack()
            }
            val state = viewModel.swapMainState
            SwapSlippageScreen(
                value = state.collectAsStateWithLifecycle().value.slippage,
                onDone = { debounceClickHandler.debounceClick { onSlippageEntered(it) } },
            )
        }
        composable(
            route = SwapRoutes.start,
        ) {
            val percentageVisibility = remember { mutableStateOf(false) }
            val onSlippageClick = {
                viewModel.onSlippageClick()
                navController.navigate(SwapRoutes.slippage)
            }
            val onFromAssetClick = {
                viewModel.fromCardClicked()
                navController.navigate(SwapRoutes.buildSelectTokenRoute(SwapRoutes.SelectTokenParam.FROM))
            }
            val onToAssetClick = {
                viewModel.toCardClicked()
                navController.navigate(SwapRoutes.buildSelectTokenRoute(SwapRoutes.SelectTokenParam.TO))
            }
            val onMarketClick: () -> Unit = {
                viewModel.onMarketClick()
                navController.navigate(SwapRoutes.markets)
            }
            val onFocusChangeFromToken: (Boolean) -> Unit = {
                if (it) viewModel.fromAmountFocused()
                percentageVisibility.value = it
            }
            val onFocusChangeToToken: (Boolean) -> Unit = {
                if (it) viewModel.toAmountFocused()
            }
            val onSwapReview: () -> Unit = {
                viewModel.swapClicked()
                navController.navigate(SwapRoutes.confirm)
            }
            PercentContainer(
                modifier = Modifier.fillMaxSize(),
                onSelectPercent = viewModel::fromInputPercentClicked,
                barVisible = percentageVisibility.value,
            ) {
                val swapState = viewModel.swapMainState
                SwapMainScreen(
                    state = swapState.collectAsStateWithLifecycle().value,
                    onSlippageClick = onSlippageClick,
                    onSelectFrom = onFromAssetClick,
                    onSelectTo = onToAssetClick,
                    onMarketClick = onMarketClick,
                    onFocusChangeFrom = onFocusChangeFromToken,
                    onFocusChangeTo = onFocusChangeToToken,
                    onAmountChangeFrom = viewModel::onFromAmountChange,
                    onAmountChangeTo = viewModel::onToAmountChange,
                    onTokenSwapClick = viewModel::onTokensSwapClick,
                    onSwapClick = onSwapReview,
                    onRetryFee = viewModel::onRetryFee,
                )
            }
        }
    }
}
