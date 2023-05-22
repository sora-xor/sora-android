/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
