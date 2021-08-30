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
import androidx.core.content.ContextCompat
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.Const
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

        val text = getString(R.string.polkaswap_info_text).highlightWords(
            listOf(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.brand_soramitsu_red
                )
            ),
            listOf { showBrowser(Const.POLKASWAP_FAQ) }
        )

        binding.text.setText(
            text,
            TextView.BufferType.SPANNABLE
        )

        binding.text.movementMethod = LinkMovementMethod.getInstance()
        binding.text.highlightColor = Color.TRANSPARENT

        binding.toolbar.setHomeButtonListener {
            viewModel.backPressed()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(requireContext(), WalletFeatureApi::class.java)
            .polkaswapComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
