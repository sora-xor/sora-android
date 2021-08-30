/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.passphrase

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ScreenshotBlockHelper
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentMyMnemonicBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject
import kotlin.math.ceil

class PassphraseFragment : BaseFragment<PassphraseViewModel>(R.layout.fragment_my_mnemonic) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private lateinit var screenshotBlockHelper: ScreenshotBlockHelper
    private val binding by viewBinding(FragmentMyMnemonicBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

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

        screenshotBlockHelper = ScreenshotBlockHelper(requireActivity())

        viewModel.mnemonicShare.observe { mnemonic ->
            (requireActivity() as? AppCompatActivity)?.let {
                ShareUtil.openShareDialog(it, getString(R.string.common_share), mnemonic)
            }
        }
        viewModel.mnemonicWords.observe { words: List<String> ->
            val rowCount = ceil(words.size.toFloat() / 2.0).toInt()
            for (row in 1..rowCount) {
                val viewWords = layoutInflater.inflate(R.layout.item_mnemonic_words, binding.llPassphrase, false)
                viewWords.findViewById<TextView>(R.id.tvLeftPosition).also {
                    it.text = "$row"
                }
                viewWords.findViewById<TextView>(R.id.tvLeftWord).also {
                    it.text = words[row - 1]
                }
                val rightWord = words.getOrElse(row - 1 + rowCount) { "" }
                viewWords.findViewById<TextView>(R.id.tvRightPosition).also {
                    it.text = if (rightWord.isEmpty()) "" else "${row + rowCount}"
                }
                viewWords.findViewById<TextView>(R.id.tvRightWord).also {
                    it.text = rightWord
                }
                binding.llPassphrase.addView(viewWords)
            }
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
