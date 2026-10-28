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

package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.feature_multiaccount_api.OnboardingNavigator
@AndroidEntryPoint
class LoginSecurityFragment : SoraBaseFragment<LoginSecurityViewModel>() {

    override val viewModel: LoginSecurityViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewModel.importAccountEvent.observe {
            // Import account: entering a wallet's own recovery phrase adopts the keys that are
            // saved for it on this phone.
            (requireActivity() as OnboardingNavigator).showOnboardingFlow()
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
            val state = viewModel.state
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                LoginSecurityScreen(
                    bioAvailable = state.bioAvailable,
                    bioEnabled = state.bioEnabled,
                    onChangePinClick = viewModel::changePin,
                    onToggle = viewModel::toggleBio,
                    retained = RetainedWalletsUi(
                        addresses = state.retainedWallets,
                        restoring = state.restoring,
                        restored = state.restored,
                        notRestorable = state.notRestorable,
                        needRecoveryPhrase = state.needRecoveryPhrase,
                    ),
                    onRestoreRetained = viewModel::restoreRetainedWallet,
                    onImportRecoveryPhrase = viewModel::importRecoveryPhrase,
                )
            }
        }
    }
}
