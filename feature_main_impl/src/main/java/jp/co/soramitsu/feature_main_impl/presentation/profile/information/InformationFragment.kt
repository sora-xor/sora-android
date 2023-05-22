/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import com.google.accompanist.navigation.animation.composable
import dagger.hilt.android.AndroidEntryPoint
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
            ShareUtil.sendEmail(
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
