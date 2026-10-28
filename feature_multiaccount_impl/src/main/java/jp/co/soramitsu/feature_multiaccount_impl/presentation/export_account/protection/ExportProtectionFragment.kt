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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.CustomViewModelFactory
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.args.address
import jp.co.soramitsu.common.presentation.args.addresses
import jp.co.soramitsu.feature_multiaccount_impl.util.type

@AndroidEntryPoint
class ExportProtectionFragment : SoraBaseFragment<ExportProtectionViewModel>() {

    override val useSafeAurora = true

    @Inject
    lateinit var vmf: ExportProtectionViewModel.ExportProtectionViewModelFactory

    override val viewModel: ExportProtectionViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(
                requireArguments().type,
                requireArguments().address,
                requireArguments().addresses
            )
        }
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            viewModel.exportProtectionScreenState.observeAsState().value?.let {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ExportProtection(
                        state = it,
                        onItemClicked = viewModel::onItemClicked,
                        continueClicked = viewModel::continueClicked,
                    )
                }
            }
        }
    }
}
