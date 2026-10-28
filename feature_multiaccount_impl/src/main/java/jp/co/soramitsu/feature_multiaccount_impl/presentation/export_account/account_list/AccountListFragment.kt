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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.ProgressDialog
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_multiaccount_api.OnboardingNavigator
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class AccountListFragment : SoraBaseFragment<AccountListViewModel>() {

    override val useSafeAurora = true

    override val viewModel: AccountListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.showOnboardingFlowEvent.observe {
            (requireActivity() as OnboardingNavigator).showOnboardingFlow()
        }

        (requireActivity() as BottomBarController).hideBottomBar()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val state = viewModel.accountListScreenState.observeAsState().value
                val isSwitchingAccount = viewModel.isSwitchingAccount.observeAsState(false).value
                if (isSwitchingAccount) {
                    ProgressDialog(message = stringResource(R.string.account_switching))
                }
                state?.let {
                    AccountListScreen(state = it, viewModel = viewModel, scrollState)
                }
                if (state?.isActionMode == false) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = Dimens.x5, end = Dimens.x3)
                            .align(Alignment.BottomEnd)
                            .size(Size.Large)
                    ) {
                        FilledButton(
                            modifier = Modifier.testTagAsId("FloatingButton"),
                            shape = CircleShape,
                            size = Size.Large,
                            order = Order.PRIMARY,
                            leftIcon = painterResource(R.drawable.ic_plus),
                            onClick = viewModel::onAddAccountClicked,
                        )
                    }
                }
            }
        }
    }
}
