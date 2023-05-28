/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.swap

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.presentation.args.tokenFromId
import jp.co.soramitsu.common.presentation.args.tokenToId
import jp.co.soramitsu.common.presentation.compose.components.PercentContainer
import jp.co.soramitsu.common.presentation.compose.components.PolkaswapDisclaimer
import jp.co.soramitsu.common_wallet.presentation.compose.components.SwapSelectTokenScreen
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapConfirmScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapMainScreen
import jp.co.soramitsu.feature_polkaswap_impl.presentation.components.compose.SwapMarketsScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@ExperimentalCoroutinesApi
@FlowPreview
@AndroidEntryPoint
class SwapFragment : SoraBaseFragment<SwapViewModel>() {

    @Inject
    lateinit var vmf: SwapViewModel.AssistedSwapViewModelFactory

    override val viewModel: SwapViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(requireArguments().tokenFromId, requireArguments().tokenToId)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        super.onViewCreated(view, savedInstanceState)
    }

    @Composable
    override fun backgroundColorComposable() = colorResource(id = R.color.polkaswap_background_alfa)

    override fun backgroundColor(): Int = R.attr.polkaswapBackground

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        viewModel.navigationDisclaimerEvent.observe {
            navController.navigate(SwapRoutes.disclaimer)
        }
        composable(SwapRoutes.confirm) {
            SwapConfirmScreen(
                state = viewModel.swapMainState,
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
            val state = viewModel.swapMainState.selectSearchAssetState
            if (state != null) {
                SwapSelectTokenScreen(
                    state = state,
                    scrollState = scrollState,
                    onAssetSelect = { id -> onTokenSelected.invoke(id, type) },
                )
            }
        }
        composable(SwapRoutes.markets) {
            val onMarketSelected: (Market) -> Unit = {
                viewModel.onMarketSelected(it)
                navController.popBackStack()
            }
            val state = viewModel.swapMainState.selectMarketState
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
                value = state.slippage,
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
                    state = swapState,
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
                )
            }
        }
    }
}