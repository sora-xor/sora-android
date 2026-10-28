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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.ExperimentalAnimationApi
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
import jp.co.soramitsu.androidfoundation.intent.ShareUtil.shareFile
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.presentation.args.addresses

@AndroidEntryPoint
class BackupJsonFragment : SoraBaseFragment<BackupJsonViewModel>() {

    override val useSafeAurora = true

    @Inject
    lateinit var vmf: BackupJsonViewModel.BackupJsonViewModelFactory

    override val viewModel: BackupJsonViewModel by viewModels {
        CustomViewModelFactory {
            vmf.create(requireArguments().addresses)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.jsonTextLiveData.observe { uri ->
            context?.let { context ->
                shareFile(context, getString(R.string.common_share), uri)
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            viewModel.backupJsonScreenState.observeAsState().value?.let {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    BackupJsonScreen(
                        state = it,
                        onChange = viewModel::passwordInputChanged,
                        onConfirmChange = viewModel::confirmationInputChanged,
                        onDownloadClick = viewModel::downloadJsonClicked,
                    )
                }
            }
        }
    }
}
