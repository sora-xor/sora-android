package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.message
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import com.vanpra.composematerialdialogs.title
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class SoraCardDetailsFragment : SoraBaseFragment<SoraCardDetailsViewModel>() {

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
            val materialDialogState = rememberMaterialDialogState()

            viewModel.soraCardLogOutDialogState.observe(viewLifecycleOwner) {
                materialDialogState.show()
            }

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

            MaterialDialog(
                dialogState = materialDialogState,
                buttons = {
                    negativeButton(
                        res = R.string.common_cancel,
                        textStyle = MaterialTheme.customTypography.textSBold,
                        onClick = remember { { materialDialogState.showing = false } }
                    )
                    positiveButton(
                        res = R.string.profile_logout_title,
                        textStyle = MaterialTheme.customTypography.textSBold,
                        onClick = remember { { viewModel.onSoraCardLogOutClick() } }
                    )
                },
                content = {
                    title(
                        center = true,
                        style = MaterialTheme.customTypography.textMBold,
                        color = MaterialTheme.customColors.fgPrimary,
                        res = R.string.sora_card_option_logout,
                    )
                    message(
                        style = MaterialTheme.customTypography.textM,
                        color = MaterialTheme.customColors.fgPrimary,
                        res = R.string.sora_card_option_logout_description,
                    )
                }
            )
        }
    }
}
