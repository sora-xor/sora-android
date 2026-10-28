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

package jp.co.soramitsu.feature_referral_impl.presentation

import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.ShareUtil.shareText
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.animatedComposable
import jp.co.soramitsu.feature_referral_impl.presentation.screens.ReferralWelcomePageScreen
import jp.co.soramitsu.feature_referral_impl.presentation.screens.WelcomeProgress
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class ReferralFragment : SoraBaseFragment<ReferralViewModel>() {

    /**
     * Bonding, unbonding and setting a referrer signs with the selected account. When that account cannot sign on this phone, the
     * screen says so above the form and offers the repair (`SoraBaseFragment`).
     */
    override val explainsReadOnlyAccount: Boolean = true

    override val viewModel: ReferralViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewModel.shareLinkEvent.observe { link ->
            context?.let { c ->
                shareText(c, getString(R.string.common_share), link)
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        animatedComposable(
            route = ReferralFeatureRoutes.WELCOME_PROGRESS
        ) {
            ReferralWrapper(scrollState) {
                WelcomeProgress()
            }
        }
        animatedComposable(
            route = ReferralFeatureRoutes.WELCOME_PAGE
        ) {
            ReferralWrapper(scrollState) {
                ReferralWelcomePageScreen(
                    state = viewModel.referralScreenState.collectAsStateWithLifecycle().value,
                    onStartInviting = {
                        viewModel.openBond()
                        navController.navigate(ReferralFeatureRoutes.BOND_XOR)
                    },
                    onEnterLink = {
                        viewModel.openReferrerInput()
                        navController.navigate(ReferralFeatureRoutes.REFERRER_INPUT)
                    }
                )
            }
        }

        animatedComposable(
            route = ReferralFeatureRoutes.REFERRAL_PROGRAM
        ) {
            ReferralWrapper(scrollState) {
                ReferralProgramPage(
                    state = viewModel.referralScreenState.collectAsStateWithLifecycle().value,
                    onGetMoreInvitations = {
                        viewModel.openBond()
                        navController.navigate(ReferralFeatureRoutes.BOND_XOR)
                    },
                    onEnterLink = {
                        viewModel.openReferrerInput()
                        navController.navigate(ReferralFeatureRoutes.REFERRER_INPUT)
                    },
                    onReferralsCardHeadClick = {
                        viewModel.toggleReferralsCard()
                    },
                    onUnboundXor = {
                        viewModel.openUnbond()
                        navController.navigate(ReferralFeatureRoutes.UNBOND_XOR)
                    },
                    onShareClick = viewModel::onShareLink
                )
            }
        }

        animatedComposable(
            route = ReferralFeatureRoutes.BOND_XOR
        ) {
            ReferralWrapper(scrollState) {
                val state = viewModel.referralScreenState.collectAsStateWithLifecycle().value
                ReferralBondXor(
                    common = state.common,
                    state = state.bondState,
                    onBondInvitationsCountChange = { viewModel.onBondValueChange(it) },
                    onBondMinus = { viewModel.onBondMinus() },
                    onBondPlus = { viewModel.onBondPlus() },
                    onBondClick = { viewModel.onBondButtonClick() }
                )
            }
        }

        animatedComposable(
            route = ReferralFeatureRoutes.UNBOND_XOR
        ) {
            ReferralWrapper(scrollState) {
                val state = viewModel.referralScreenState.collectAsStateWithLifecycle().value
                ReferralUnbondXor(
                    common = state.common,
                    state = state.bondState,
                    onUnbondInvitationsCountChange = { viewModel.onUnbondValueChange(it) },
                    onUnbondMinus = { viewModel.onUnbondMinus() },
                    onUnbondPlus = { viewModel.onUnbondPlus() },
                    onUnbondClick = { viewModel.onUnbondButtonClick() }
                )
            }
        }

        animatedComposable(
            route = ReferralFeatureRoutes.REFERRER_INPUT
        ) {
            ReferralWrapper(scrollState) {
                ReferrerInput(
                    common = viewModel.referralScreenState.collectAsStateWithLifecycle().value.common,
                    state = viewModel.referralScreenState.collectAsStateWithLifecycle().value.referrerInputState,
                    onActivateReferrerClicked = {
                        viewModel.onActivateLinkClick()
                    },
                    onReferrerValueChanged = { viewModel.onReferrerInputChange(it) }
                )
            }
        }

        animatedComposable(
            route = ReferralFeatureRoutes.REFERRER_FILLED
        ) {
            ReferralWrapper(scrollState) {
                val state = viewModel.referralScreenState.collectAsStateWithLifecycle().value
                ReferrerFilled(
                    state = state.common,
                    onCloseClicked = {
                        if (state.isInitialized()) {
                            navController.popBackStack(
                                ReferralFeatureRoutes.REFERRAL_PROGRAM,
                                false
                            )
                        } else {
                            navController.popBackStack(
                                ReferralFeatureRoutes.WELCOME_PAGE,
                                false
                            )
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun ReferralWrapper(
        scrollState: ScrollState,
        content: @Composable BoxScope.() -> Unit
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = Dimens.x2, vertical = 0.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            contentAlignment = Alignment.TopCenter,
        ) {
            BackHandler {
                viewModel.onBackPressed()
            }
            content()
        }
    }
}
