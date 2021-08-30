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
        binding.websiteSubtitleTv.text = BuildConfig.WEBSITE.substring(8)
        binding.sourceSubtitleTv.text = BuildConfig.SOURCE_LINK.substring(8)
        binding.telegramSubtitleTv.text = BuildConfig.TELEGRAM_LINK.substring(8)
        binding.contactSubtitleTv.text = BuildConfig.EMAIL

        binding.icHome.setDebouncedClickListener(debounceClickHandler) {
            viewModel.backPressed()
        }

        binding.sourceWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.openSourceClicked()
        }

        binding.tgWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.telegramClicked()
        }

        binding.websiteWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.websiteClicked()
        }

        binding.termsWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.termsClicked()
        }

        binding.privacyWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.privacyClicked()
        }

        binding.contactsWrapper.setDebouncedClickListener(debounceClickHandler) {
            viewModel.contactsClicked()
        }

        initListeners()
        viewModel.getAppVersion()
    }

    private fun initListeners() {
        viewModel.sourceTitleLiveData.observe {
            binding.sourceTv.text = it
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
