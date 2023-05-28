package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import com.google.accompanist.navigation.animation.composable
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController

@AndroidEntryPoint
class SoraCardDetailsFragment: SoraBaseFragment<SoraCardDetailsViewModel>() {

    override val viewModel: SoraCardDetailsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        super.onViewCreated(view, savedInstanceState)
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(theOnlyRoute) {
            SoraCardDetailsScreen(
                soraCardDetailsScreenState = viewModel.soraCardDetailsScreenState,
                onShowSoraCardDetailsClick = viewModel::onShowSoraCardDetailsClick,
                onSoraCardMenuActionClick = viewModel::onSoraCardMenuActionClick,
                onReferralBannerClick = viewModel::onReferralBannerClick,
                onCloseReferralBannerClick = viewModel::onCloseReferralBannerClick,
                onShowMoreRecentActivitiesClick = viewModel::onShowMoreRecentActivitiesClick,
                onRecentActivityClick = viewModel::onRecentActivityClick,
                onIbanCardActionClick = viewModel::onIbanCardActionClick,
                onSettingsOptionClick = viewModel::onSettingsOptionClick
            )
        }
    }

}