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
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.BottomBarController
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? BottomBarController)?.hideBottomBar()
        viewModel.googleSignInEvent.observe {
            Toast.makeText(requireContext(), R.string.debug_google_changed, Toast.LENGTH_LONG).show()
        }
    }

    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(route = theOnlyRoute) {
            val metrics = LocalContext.current.resources.displayMetrics
            DebugMenuScreen(
                state = viewModel.state,
                display = stringResource(R.string.debug_display_size, metrics.widthPixels, metrics.heightPixels, metrics.density),
                onChangeAccount = { viewModel.onChangeGoogleAccount(launcher) },
                onResetRuntime = viewModel::onResetRuntimeClick,
            )
        }
    }
}
