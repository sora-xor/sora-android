/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.ncorti.slidetoact.SlideToActView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ext.attrColor
import jp.co.soramitsu.common.util.ext.highlightWords
import jp.co.soramitsu.common.util.ext.showBrowser
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentPolkaSwapInfoBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class PolkaSwapInfoFragment : BaseFragment<PolkaSwapViewModel>(R.layout.fragment_polka_swap_info) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentPolkaSwapInfoBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        val color = requireContext().attrColor(R.attr.polkaswapPrimary)
        val text = getString(R.string.polkaswap_info_text_1).highlightWords(
            listOf(
                color,
                color,
                color,
            ),
            listOf(
                { showBrowser(Const.POLKASWAP_FAQ) },
                { showBrowser(Const.POLKASWAP_MEMORANDUM) },
                { showBrowser(Const.POLKASWAP_PRIVACY_POLICY) }
            ),
            true,
        )

        binding.tvPolkaswapText1.setText(
            text,
            TextView.BufferType.SPANNABLE
        )
        binding.tvPolkaswapText1.movementMethod = LinkMovementMethod.getInstance()

        getString(R.string.polkaswap_info_text_6).highlightWords(
            listOf(
                color,
                color,
                color,
            ),
            listOf(
                { showBrowser(Const.POLKASWAP_FAQ) },
                { showBrowser(Const.POLKASWAP_MEMORANDUM) },
                { showBrowser(Const.POLKASWAP_PRIVACY_POLICY) }
            ),
            true,
        ).also {
            binding.tvPolkaswapText6.setText(
                it,
                TextView.BufferType.SPANNABLE
            )
            binding.tvPolkaswapText6.movementMethod = LinkMovementMethod.getInstance()
        }

        binding.tvPolkaswapText1.highlightColor = Color.TRANSPARENT
        binding.tvPolkaswapText1.movementMethod = LinkMovementMethod.getInstance()
        binding.tvPolkaswapText6.highlightColor = Color.TRANSPARENT
        binding.tvPolkaswapText6.movementMethod = LinkMovementMethod.getInstance()

        binding.tbPolkaswapDisclaimer.setHomeButtonListener {
            viewModel.backPressed()
        }

        binding.sbtnPolkaswapInfo.onSlideCompleteListener =
            object : SlideToActView.OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {
                    viewModel.onDisclaimerSwipe()
                }
            }

        viewModel.disclaimerLiveData.observe {
            binding.tvPolkaswapText7.text =
                getString(if (it) R.string.polkaswap_info_text_7 else R.string.polkaswap_info_text_9)
            binding.sbtnPolkaswapInfo.text =
                getString(if (it) R.string.common_hide else R.string.common_show)
            binding.sbtnPolkaswapInfo.resetSlider()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .polkaswapComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
