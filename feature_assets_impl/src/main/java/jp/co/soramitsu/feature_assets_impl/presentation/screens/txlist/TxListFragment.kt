/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.screens.txlist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.args.tokenId
import jp.co.soramitsu.common.presentation.compose.components.ContentCardEndless
import jp.co.soramitsu.core_di.viewmodel.CustomViewModelFactory
import jp.co.soramitsu.feature_blockexplorer_api.domain.HistoryState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryScreen
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class TxListFragment : SoraBaseFragment<TxListViewModel>() {

    @Inject
    lateinit var vmf: TxListViewModel.TxListViewModelFactory

    override val viewModel: TxListViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(requireArguments().tokenId)
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            val historyState: HistoryState by viewModel.state.collectAsStateWithLifecycle()

            ContentCardEndless(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.x2),
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
