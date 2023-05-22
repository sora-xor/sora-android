/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.Option
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class AppSettingsFragment : SoraBaseFragment<AppSettingsViewModel>() {

    override val viewModel: AppSettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2)
            ) {
                ContentCard(
                    innerPadding = PaddingValues(horizontal = Dimens.x3)
                ) {
                    Option(
                        icon = painterResource(id = R.drawable.ic_neu_world),
                        label = stringResource(id = R.string.change_language),
                        bottomDivider = false,
                        onClick = viewModel::onLanguageClick,
                    )
                }
                Spacer(modifier = Modifier.size(Dimens.x2))
                val state = viewModel.state
                AppSettingsScreen(
                    checkedSystem = state.systemAppearanceChecked,
                    checkedDark = state.darkModeChecked,
                    onSystemToggle = viewModel::toggleSystemAppearance,
                    onDarkToggle = viewModel::toggleDarkMode,
                )
            }
        }
    }
}
