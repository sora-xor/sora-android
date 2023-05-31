/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_referral_impl.presentation

import android.os.Bundle
import android.view.View
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.presentation.compose.components.animatedComposable
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.feature_referral_impl.presentation.screens.ReferralWelcomePageScreen
import jp.co.soramitsu.feature_referral_impl.presentation.screens.WelcomeProgress
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class ReferralFragment : SoraBaseFragment<ReferralViewModel>() {

    override val viewModel: ReferralViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        viewModel.shareLinkEvent.observe { link ->
            context?.let { c ->
                ShareUtil.shareText(c, getString(R.string.common_share), link)
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
            FooWrapper(scrollState) {
                WelcomeProgress()
            }
        }
        animatedComposable(
            route = ReferralFeatureRoutes.WELCOME_PAGE
        ) {
            FooWrapper(scrollState) {
                ReferralWelcomePageScreen(
                    state = viewModel.referralScreenState,
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
            FooWrapper(scrollState) {
                ReferralProgramPage(
                    state = viewModel.referralScreenState,
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
            FooWrapper(scrollState) {
                ReferralBondXor(
                    common = viewModel.referralScreenState.common,
                    state = viewModel.referralScreenState.bondState,
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
            FooWrapper(scrollState) {
                ReferralUnbondXor(
                    common = viewModel.referralScreenState.common,
                    state = viewModel.referralScreenState.bondState,
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
            FooWrapper(scrollState) {
                ReferrerInput(
                    common = viewModel.referralScreenState.common,
                    state = viewModel.referralScreenState.referrerInputState,
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
            FooWrapper(scrollState) {
                ReferrerFilled(
                    state = viewModel.referralScreenState.common,
                    onCloseClicked = {
                        if (viewModel.referralScreenState.isInitialized()) {
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
    private fun FooWrapper(
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
            content()
        }
    }
}
