/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentAboutBinding
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : BaseFragment<AboutViewModel>(R.layout.fragment_about) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentAboutBinding::bind)

    private val vm: AboutViewModel by viewModels()

    override val viewModel: AboutViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()
        binding.acContactEmail.setDescription(OptionsProvider.email)
        binding.acTwitter.setDescription(OptionsProvider.twitterLink)
        binding.acYoutube.setDescription(OptionsProvider.youtubeLink)
        binding.acInstagram.setDescription(OptionsProvider.instagramLink)
        binding.acMedium.setDescription(OptionsProvider.mediumLink)
        binding.acWiki.setDescription(OptionsProvider.wikiLink)
        binding.acTelegramAnnouncements.setDescription(OptionsProvider.telegramAnnouncementsLink)
        binding.acTelegramHappiness.setDescription(OptionsProvider.telegramHappinessLink)

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
            context?.let { c ->
                ShareUtil.sendEmail(c, it, getString(R.string.common_select_email_app_title))
            }
        }
        viewModel.showBrowserLiveData.observe {
            ShareUtil.shareInBrowser(this, it)
        }
    }
}
