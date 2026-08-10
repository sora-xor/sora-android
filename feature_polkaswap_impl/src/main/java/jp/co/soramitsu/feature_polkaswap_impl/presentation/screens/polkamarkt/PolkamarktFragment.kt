package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController

@AndroidEntryPoint
class PolkamarktFragment : SoraBaseFragment<PolkamarktViewModel>() {
    override val viewModel: PolkamarktViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController,
    ) {
        composable(theOnlyRoute) {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            PolkamarktScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onSearch = viewModel::search,
                onStatusFilter = viewModel::filterStatus,
                onCategoryFilter = viewModel::filterCategory,
                onMineOnly = viewModel::toggleMineOnly,
                onMarket = viewModel::selectMarket,
                onBackToMarkets = viewModel::clearSelection,
                onSide = viewModel::setSide,
                onOutcome = viewModel::setOutcome,
                onAmount = viewModel::setAmount,
                onSlippage = viewModel::setSlippageBps,
                onQuote = viewModel::requestQuote,
                onConfirm = viewModel::confirmTrade,
                onClaim = viewModel::requestClaim,
                onReviewClaim = viewModel::reviewClaim,
                onSelectedClaim = viewModel::requestSelectedClaim,
                onReviewBatchClaims = viewModel::reviewBatchClaims,
                onBatchClaim = viewModel::requestAllTraderPayouts,
                onConfirmClaim = viewModel::confirmClaim,
                onDismissClaimConfirmation = viewModel::dismissClaimConfirmation,
            )
        }
    }
}
