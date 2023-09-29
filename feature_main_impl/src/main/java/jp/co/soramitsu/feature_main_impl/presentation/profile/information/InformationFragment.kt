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

package jp.co.soramitsu.feature_main_impl.presentation.profile.information

import android.os.Bundle
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.androidfoundation.intent.ShareUtil.sendEmail
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.domain.BottomBarController
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.compose.components.PolkaswapDisclaimer
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.ui_core.resources.Dimens

@AndroidEntryPoint
class InformationFragment : SoraBaseFragment<InformationViewModel>() {

    override val viewModel: InformationViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
    }

    private val askSupport: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.telegramHappinessLink)
    }
    private val announcement: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.telegramAnnouncementsLink)
    }
    private val telegram: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.telegramLink)
    }
    private val wiki: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.wikiLink)
    }
    private val medium: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.mediumLink)
    }
    private val instagram: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.instagramLink)
    }
    private val youtube: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.youtubeLink)
    }
    private val twitter: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.twitterLink)
    }
    private val github: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.sourceLink)
    }
    private val website: () -> Unit = {
        ShareUtil.shareInBrowser(this, OptionsProvider.website)
    }
    private val email: () -> Unit = {
        context?.let { c ->
            sendEmail(
                c,
                OptionsProvider.email,
                getString(R.string.common_select_email_app_title)
            )
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun NavGraphBuilder.content(
        scrollState: ScrollState,
        navController: NavHostController
    ) {
        composable(
            route = Routes.start,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Dimens.x2)
            ) {
                InformationScreen(
                    appVersion = viewModel.state.appVersion,
                    onDisclaimerClick = { navController.navigate(Routes.polkaswapDisclaimer) },
                    onFaqClick = viewModel::faq,
                    onEmailClick = email,
                    onAskForSupportClick = askSupport,
                    onTermsClick = viewModel::terms,
                    onPrivacyClick = viewModel::privacy,
                    onAnnouncements = announcement,
                    onTelegram = telegram,
                    onWiki = wiki,
                    onMedium = medium,
                    onInstagram = instagram,
                    onYoutube = youtube,
                    onTwitter = twitter,
                    onGithub = github,
                    onWebsite = website,
                )
            }
        }
        composable(Routes.polkaswapDisclaimer) {
            Box(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = Dimens.x2)
            ) {
                PolkaswapDisclaimer(
                    onDisclaimerClose = { navController.popBackStack() },
                )
            }
        }
    }
}

internal object Routes {
    const val start = "Information.Start"
    const val polkaswapDisclaimer = "Information.polkaswapDisclaimer"
}
