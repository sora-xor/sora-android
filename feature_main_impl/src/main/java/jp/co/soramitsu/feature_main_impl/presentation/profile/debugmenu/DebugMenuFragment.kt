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

package jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.compose.neon.NeonGallery
import jp.co.soramitsu.common.util.ext.getOsName
import jp.co.soramitsu.common.util.ext.getSize
import jp.co.soramitsu.ui_core.theme.customColors

@AndroidEntryPoint
class DebugMenuFragment : SoraBaseFragment<DebugMenuViewModel>() {

    override val viewModel: DebugMenuViewModel by viewModels()

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                viewModel.onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            } else {
                viewModel.onSuccessfulGoogleSignIn()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.googleSignInEvent.observe {
            Toast.makeText(requireContext(), "Google Account Changed", Toast.LENGTH_LONG).show()
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(route = theOnlyRoute) {
            val dm = remember {
                activity?.getSize()
            }

            var showNeonGallery by remember { mutableStateOf(false) }
            if (showNeonGallery) {
                NeonGallery(onClose = { showNeonGallery = false })
                return@composable
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Button(onClick = { showNeonGallery = true }) {
                    Text(
                        color = MaterialTheme.customColors.fgPrimary,
                        text = "SORA Neon components"
                    )
                }
                Text(
                    color = MaterialTheme.customColors.fgPrimary,
                    text = "%s %.3f".format("Density", dm?.first ?: 0.0)
                )
                Text(
                    color = MaterialTheme.customColors.fgPrimary,
                    text = "%s %d".format("Width", dm?.second ?: 0.0)
                )
                Text(
                    color = MaterialTheme.customColors.fgPrimary,
                    text = "%s %d".format("Height", dm?.third ?: 0.0)
                )
                Text(
                    color = MaterialTheme.customColors.fgPrimary,
                    text = activity?.getOsName().orEmpty()
                )
                Button(onClick = {
                    viewModel.onChangeGoogleAccount(launcher)
                }) {
                    Text(
                        color = MaterialTheme.customColors.fgPrimary,
                        text = "Change google account"
                    )
                }
                Button(onClick = viewModel::onResetRuntimeClick) {
                    Text(
                        color = MaterialTheme.customColors.fgPrimary,
                        text = "Reset runtime"
                    )
                }
                DebugMenuScreen(state = viewModel.state)
            }
        }
    }
}
