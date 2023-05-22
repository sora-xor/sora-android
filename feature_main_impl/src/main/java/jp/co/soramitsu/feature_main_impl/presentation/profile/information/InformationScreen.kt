/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.information

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.compose.components.Option
import jp.co.soramitsu.common.util.ext.removeWebPrefix
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun InformationScreen(
    appVersion: String,
    onDisclaimerClick: () -> Unit,
    onFaqClick: () -> Unit,
    onEmailClick: () -> Unit,
    onAskForSupportClick: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onAnnouncements: () -> Unit,
    onTelegram: () -> Unit,
    onWiki: () -> Unit,
    onMedium: () -> Unit,
    onInstagram: () -> Unit,
    onYoutube: () -> Unit,
    onTwitter: () -> Unit,
    onGithub: () -> Unit,
    onWebsite: () -> Unit,
) {
    Spacer(modifier = Modifier.size(Dimens.x2))
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(horizontal = Dimens.x3),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Option(
                icon = painterResource(id = R.drawable.ic_neu_question),
                label = stringResource(id = R.string.faq_title),
                bottomDivider = true,
                onClick = onFaqClick,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_neu_heart),
                label = stringResource(id = R.string.about_ask_support),
                bottomDivider = false,
                description = OptionsProvider.telegramHappinessLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onAskForSupportClick,
            )
        }
    }
    Spacer(modifier = Modifier.size(Dimens.x2))
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(horizontal = Dimens.x3),
    ) {
        Column {
            Option(
                icon = painterResource(id = R.drawable.ic_polkaswap),
                label = stringResource(id = R.string.polkaswap_info_title_2),
                bottomDivider = true,
                onClick = onDisclaimerClick,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_document_text_24),
                label = stringResource(id = R.string.common_terms_title),
                bottomDivider = true,
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onTermsClick,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_security_shield_chekmark_24),
                label = stringResource(id = R.string.about_privacy),
                bottomDivider = false,
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onPrivacyClick,
            )
        }
    }
    Spacer(modifier = Modifier.size(Dimens.x2))
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        innerPadding = PaddingValues(horizontal = Dimens.x3),
    ) {
        Column {
            Option(
                icon = painterResource(id = R.drawable.ic_0x0200000000000000000000000000000000000000000000000000000000000000),
                label = stringResource(id = R.string.about_website),
                bottomDivider = true,
                description = OptionsProvider.website.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                tint = false,
                onClick = onWebsite,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_github_24),
                label = stringResource(id = R.string.about_source_code),
                bottomDivider = true,
                description = appVersion,
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onGithub,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_twitter),
                label = stringResource(id = R.string.about_twitter),
                bottomDivider = true,
                description = OptionsProvider.twitterLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onTwitter,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_youtube),
                label = stringResource(id = R.string.about_youtube),
                bottomDivider = true,
                description = OptionsProvider.youtubeLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onYoutube,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_neu_instagram),
                label = stringResource(id = R.string.about_instagram),
                bottomDivider = true,
                description = OptionsProvider.instagramLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onInstagram,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_neu_medium),
                label = stringResource(id = R.string.about_medium),
                bottomDivider = true,
                description = OptionsProvider.mediumLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onMedium,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_neu_question),
                label = stringResource(id = R.string.about_wiki),
                bottomDivider = true,
                description = OptionsProvider.wikiLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onWiki,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_tg_24),
                label = stringResource(id = R.string.about_telegram),
                bottomDivider = true,
                description = OptionsProvider.telegramLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onTelegram,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_neu_wifi),
                label = stringResource(id = R.string.about_announcements),
                bottomDivider = true,
                description = OptionsProvider.telegramAnnouncementsLink.removeWebPrefix(),
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onAnnouncements,
            )
            Option(
                icon = painterResource(id = R.drawable.ic_email_24),
                label = stringResource(id = R.string.about_contact_us),
                bottomDivider = false,
                description = OptionsProvider.email,
                iconEnd = R.drawable.ic_arrow_top_right_24,
                onClick = onEmailClick,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewInformation() {
    Column(modifier = Modifier.fillMaxSize()) {
        InformationScreen(
            appVersion = "App version 2.3.4.5",
            onDisclaimerClick = { },
            onFaqClick = { },
            onEmailClick = {},
            onAskForSupportClick = {},
            onTermsClick = {},
            onPrivacyClick = {},
            onAnnouncements = {},
            onTelegram = {},
            onWiki = {},
            onMedium = {},
            onInstagram = {},
            onYoutube = {},
            onTwitter = {},
            onGithub = {},
            onWebsite = {},
        )
    }
}
