/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.presentation.compose.webview

import androidx.compose.foundation.ScrollState
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
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
