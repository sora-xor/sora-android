/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.webview

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute

@AndroidEntryPoint
class WebViewFragment : SoraBaseFragment<WebViewViewModel>() {

    @Inject
    lateinit var vmf: WebViewViewModel.WebViewViewModelFactory

    override val viewModel: WebViewViewModel by viewModels {
        WebViewViewModel.provideFactory(
            vmf,
            requireArguments().title,
            requireArguments().url
        )
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            WebView(
                viewModel.state,
                viewModel::onPageFinished,
            )
        }
    }
}
