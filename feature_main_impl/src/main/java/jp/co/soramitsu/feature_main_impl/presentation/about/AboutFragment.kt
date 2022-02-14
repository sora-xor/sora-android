/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.createSendEmailIntent
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showBrowser
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentAboutBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class AboutFragment : BaseFragment<AboutViewModel>(R.layout.fragment_about) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentAboutBinding::bind)

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .aboutComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        binding.acContactEmail.setDescription(BuildConfig.EMAIL)
        binding.acTwitter.setDescription(BuildConfig.TWITTER_LINK)
        binding.acYoutube.setDescription(BuildConfig.YOUTUBE_LINK)
        binding.acInstagram.setDescription(BuildConfig.INSTAGRAM_LINK)
        binding.acMedium.setDescription(BuildConfig.MEDIUM_LINK)
        binding.acWiki.setDescription(BuildConfig.WIKI_LINK)
        binding.acTelegramAnnouncements.setDescription(BuildConfig.TELEGRAM_ANNOUNCEMENTS_LINK)
        binding.acTelegramHappiness.setDescription(BuildConfig.TELEGRAM_HAPPINESS_LINK)

        binding.tbAbout.setHomeButtonListener { viewModel.backPressed() }

        binding.acGithubSource.setDebouncedClickListener(debounceClickHandler) {
            viewModel.openSourceClicked()
        }

        binding.acTelegram.setDebouncedClickListener(debounceClickHandler) {
            viewModel.telegramClicked()
        }

        binding.acTelegramAnnouncements.setDebouncedClickListener(debounceClickHandler) {
            viewModel.telegramAnnouncementsClicked()
        }

        binding.acTelegramHappiness.setDebouncedClickListener(debounceClickHandler) {
            viewModel.telegramAskSupportClicked()
        }

        binding.acOfficialWebsite.setDebouncedClickListener(debounceClickHandler) {
            viewModel.websiteClicked()
        }

        binding.acTermsAndConditions.setDebouncedClickListener(debounceClickHandler) {
            viewModel.termsClicked()
        }

        binding.acPrivacyPolicy.setDebouncedClickListener(debounceClickHandler) {
            viewModel.privacyClicked()
        }

        binding.acContactEmail.setDebouncedClickListener(debounceClickHandler) {
            viewModel.contactsClicked()
        }

        binding.acTwitter.setDebouncedClickListener(debounceClickHandler) {
            viewModel.twitterClicked()
        }

        binding.acYoutube.setDebouncedClickListener(debounceClickHandler) {
            viewModel.youtubeClicked()
        }

        binding.acInstagram.setDebouncedClickListener(debounceClickHandler) {
            viewModel.instagramClicked()
        }

        binding.acMedium.setDebouncedClickListener(debounceClickHandler) {
            viewModel.mediumClicked()
        }

        binding.acWiki.setDebouncedClickListener(debounceClickHandler) {
            viewModel.wikiClicked()
        }

        initListeners()
        viewModel.getAppVersion()
    }

    private fun initListeners() {
        viewModel.sourceTitleLiveData.observe {
            binding.acGithubSource.setDescription(it)
        }
        viewModel.openSendEmailEvent.observe {
            requireActivity().createSendEmailIntent(
                it,
                getString(R.string.common_select_email_app_title)
            )
        }
        viewModel.showBrowserLiveData.observe {
            showBrowser(it)
        }
    }
}
