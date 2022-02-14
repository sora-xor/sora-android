/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.databinding.FragmentMyMnemonicBinding
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ScreenshotBlockHelper
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import javax.inject.Inject
import kotlin.math.ceil

class MnemonicFragment : BaseFragment<MnemonicViewModel>(R.layout.fragment_my_mnemonic) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var screenshotBlockHelper: ScreenshotBlockHelper
    private val binding by viewBinding(FragmentMyMnemonicBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setHomeButtonListener { requireActivity().onBackPressed() }
        binding.toolbar.setRightActionClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.common_info)
                .setMessage(R.string.mnemonic_alert_text)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        binding.ibMnemonicShare.setDebouncedClickListener(debounceClickHandler) {
            viewModel.onShareClick()
        }

        binding.nextBtn.show()

        binding.nextBtn.setDebouncedClickListener(debounceClickHandler) {
            viewModel.btnNextClicked()
        }

        screenshotBlockHelper = ScreenshotBlockHelper(requireActivity())

        viewModel.mnemonicShare.observe { mnemonic ->
            (requireActivity() as? AppCompatActivity)?.let {
                ShareUtil.openShareDialog(it, getString(R.string.common_share), mnemonic)
            }
        }
        viewModel.mnemonicWords.observe { words: List<String> ->
            val rowCount = ceil(words.size.toFloat() / 2.0).toInt()
            binding.tvPassphraseNumber1.text =
                List(rowCount) { "${it + 1}" }.joinToString(separator = "\n")
            binding.tvPassphraseNumber2.text =
                List(words.size - rowCount) { "${it + rowCount + 1}" }.joinToString(separator = "\n")
            binding.tvPassphraseWords1.text =
                List(rowCount) { words[it] }.joinToString(separator = "\n")
            binding.tvPassphraseWords2.text =
                List(words.size - rowCount) { words[it + rowCount] }.joinToString(separator = "\n")
        }
        viewModel.getPassphrase()
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(requireContext(), OnboardingFeatureApi::class.java)
            .mnemonicComponentBuilder()
            .withFragment(this)
            .withRouter(activity as OnboardingRouter)
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
