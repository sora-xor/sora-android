/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.common.util.ScreenshotBlockHelper
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.showOrGone
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentMyMnemonicBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class PassphraseFragment : BaseFragment<PassphraseViewModel>(R.layout.fragment_my_mnemonic) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var screenshotBlockHelper: ScreenshotBlockHelper
    private val binding by viewBinding(FragmentMyMnemonicBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { requireActivity().onBackPressed() }

        binding.btnShare.setOnClickListener(
            DebounceClickListener(debounceClickHandler) {
                ShareUtil.openShareDialog(
                    (activity as AppCompatActivity?)!!,
                    getString(R.string.common_passphrase_save_mnemonic_title),
                    binding.passphraseTv.text.toString()
                )
            }
        )

        screenshotBlockHelper = ScreenshotBlockHelper(requireActivity())

        viewModel.passphraseLiveData.observe {
            if (it.isNotEmpty()) binding.passphraseTv.text = it
        }
        viewModel.getPreloadVisibility().observe {
            binding.preloaderView.showOrGone(it)
        }
        viewModel.getPassphrase()
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .passphraseComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }

    override fun onResume() {
        super.onResume()
        screenshotBlockHelper.disableScreenshoting()
    }

    override fun onPause() {
        super.onPause()
        screenshotBlockHelper.enableScreenshoting()
    }
}
