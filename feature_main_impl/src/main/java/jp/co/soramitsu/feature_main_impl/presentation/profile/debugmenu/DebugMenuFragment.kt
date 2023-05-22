/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.util.ext.getOsName
import jp.co.soramitsu.common.util.ext.getSize

@AndroidEntryPoint
class DebugMenuFragment : SoraBaseFragment<DebugMenuViewModel>() {

    override val viewModel: DebugMenuViewModel by viewModels()

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(route = theOnlyRoute) {
            val dm = remember {
                activity?.getSize()
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Text(text = "%s %.3f".format("Density", dm?.first ?: 0.0))
                Text(text = "%s %d".format("Width", dm?.second ?: 0.0))
                Text(text = "%s %d".format("Height", dm?.third ?: 0.0))
                Text(text = activity?.getOsName().orEmpty())
                Button(onClick = viewModel::onResetRuntimeClick) {
                    Text(text = "Reset runtime")
                }
                DebugMenuScreen(state = viewModel.state)
            }
        }
    }
}
