/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_impl.presentation.screen

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.ContentCardEndless
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryScreen
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@AndroidEntryPoint
class ActivitiesFragment : SoraBaseFragment<ActivitiesViewModel>() {

    override val viewModel: ActivitiesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()
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
                Text(
                    modifier = Modifier.padding(
                        top = Dimens.x3,
                    ),
                    text = stringResource(id = R.string.common_activity),
                    style = MaterialTheme.customTypography.headline1,
                    color = MaterialTheme.customColors.fgPrimary,
                )
                val historyState: HistoryState by viewModel.historyState.collectAsStateWithLifecycle()

                ContentCardEndless(
                    modifier = Modifier.padding(top = Dimens.x2).fillMaxSize(),
                    innerPadding = PaddingValues(top = Dimens.x2),
                ) {
                    TxHistoryScreen(
                        historyState = historyState,
                        onRefresh = viewModel::refresh,
                        onHistoryItemClick = viewModel::onTxHistoryItemClick,
                        onMoreHistoryItemRequested = viewModel::onMoreHistoryEventsRequested
                    )
                }
            }
        }
    }
}
