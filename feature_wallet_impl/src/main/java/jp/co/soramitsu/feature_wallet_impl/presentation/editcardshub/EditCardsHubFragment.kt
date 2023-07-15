package jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController

@AndroidEntryPoint
class EditCardsHubFragment : SoraBaseFragment<EditCardsHubViewModel>() {

    override val viewModel: EditCardsHubViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(theOnlyRoute) {
            val state = viewModel.state.collectAsState()
            EditCardsHubScreen(
                state = state.value,
                onCloseScreen = viewModel::onNavIcon,
                onCardEnabled = viewModel::onEnabledCardItemClick,
                onCardDisabled = viewModel::onDisabledCardItemClick
            )
        }
    }
}
