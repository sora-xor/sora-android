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

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.ShareUtil
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.neon.NeonBackground

@AndroidEntryPoint
class ClaimFragment : SoraBaseFragment<ClaimViewModel>() {

    /**
     * The claim signs with the selected account. When that account cannot sign on this phone, the
     * screen says so above the form and offers the repair (`SoraBaseFragment`).
     */
    override val explainsReadOnlyAccount: Boolean = true

    override val viewModel: ClaimViewModel by viewModels()

    @OptIn(ExperimentalUnitApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = theOnlyRoute,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                NeonBackground()
                viewModel.claimScreenState.observeAsState().value?.let {
                    ClaimScreen(
                        claimState = it,
                        onSubmitClicked = viewModel::nextButtonClicked,
                        onContactUsClicked = viewModel::contactsUsClicked,
                    )
                }
            }
        }
    }

    override fun onBack() {
        requireActivity().finish()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        viewModel.openSendEmailEvent.observe {
            context?.let { c ->
                ShareUtil.sendEmail(c, it, getString(R.string.common_select_email_app_title))
            }
        }
    }

    // SoraBaseFragment tells the view model when this screen comes to the front and when it
    // leaves it. Nothing is followed, and the screen is not closed, while it is not in front.
}
