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

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card.details

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.ShareUtil.shareText
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.ui_core.component.button.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class SoraCardDetailsFragment : SoraBaseFragment<SoraCardDetailsViewModel>() {

    override val viewModel: SoraCardDetailsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as BottomBarController).hideBottomBar()
        super.onViewCreated(view, savedInstanceState)
        viewModel.shareLinkEvent.observe { share ->
            context?.let { c ->
                shareText(c, getString(R.string.common_share), share)
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(theOnlyRoute) {
            val state = viewModel.soraCardDetailsScreenState.collectAsStateWithLifecycle()
            SoraCardDetailsScreen(
                soraCardDetailsScreenState = state.value,
                onShowSoraCardDetailsClick = viewModel::onShowSoraCardDetailsClick,
                onSoraCardMenuActionClick = viewModel::onSoraCardMenuActionClick,
                onReferralBannerClick = viewModel::onReferralBannerClick,
                onCloseReferralBannerClick = viewModel::onCloseReferralBannerClick,
                onShowMoreRecentActivitiesClick = viewModel::onShowMoreRecentActivitiesClick,
                onRecentActivityClick = viewModel::onRecentActivityClick,
                onIbanCardShareClick = viewModel::onIbanCardShareClick,
                onIbanCardClick = viewModel::onIbanCardClick,
                onSettingsOptionClick = viewModel::onSettingsOptionClick
            )
            if (state.value.logoutDialog) {
                AlertDialog(
                    onDismissRequest = viewModel::onLogoutDismiss,
                    buttons = {
                        Row(
                            Modifier.fillMaxWidth().wrapContentHeight().padding(Dimens.x1),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.x2),
                        ) {
                            TextButton(
                                modifier = Modifier.weight(1f),
                                size = Size.Small,
                                order = Order.TERTIARY,
                                text = stringResource(id = R.string.common_cancel),
                                onClick = viewModel::onLogoutDismiss,
                            )
                            TextButton(
                                modifier = Modifier.weight(1f),
                                size = Size.Small,
                                order = Order.PRIMARY,
                                text = stringResource(id = R.string.profile_logout_title),
                                onClick = viewModel::onSoraCardLogOutClick,
                            )
                        }
                    },
                    title = { Text(text = stringResource(id = R.string.sora_card_option_logout)) },
                    text = { Text(text = stringResource(id = R.string.sora_card_option_logout_description)) },
                )
            }
        }
    }
}
