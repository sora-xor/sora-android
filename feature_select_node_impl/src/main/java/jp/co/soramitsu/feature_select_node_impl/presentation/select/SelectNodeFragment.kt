/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.fragment.findNavController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.args.BUNDLE_KEY
import jp.co.soramitsu.feature_select_node_impl.presentation.select.ui.SelectNodeScreen

@AndroidEntryPoint
internal class SelectNodeFragment : SoraBaseFragment<SelectNodeViewModel>() {

    override val viewModel: SelectNodeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Bundle?>(BUNDLE_KEY)?.observe(viewLifecycleOwner) { args ->
                viewModel.onPinCodeChecked()
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
            SelectNodeScreen(scrollState, viewModel)
        }
    }
}
