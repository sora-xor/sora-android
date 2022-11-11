/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentPolkaSwapBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.marketchooserdialog.MarketChooserDialog
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.swap.SwapFragment
import javax.inject.Inject

@AndroidEntryPoint
class PolkaSwapFragment : BaseFragment<PolkaSwapViewModel>(R.layout.fragment_polka_swap) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentPolkaSwapBinding::bind)

    private val vm: PolkaSwapViewModel by viewModels()
    override val viewModel: PolkaSwapViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).showBottomBar()

        binding.viewpager.adapter = ViewPagerAdapter(this)
        binding.viewpager.isUserInputEnabled = false

        binding.settingsButton.setDebouncedClickListener(debounceClickHandler) {
            viewModel.marketSettingsClicked()
        }

        TabLayoutMediator(binding.tabsLayout, binding.viewpager) { tab, position ->
            tab.setText((binding.viewpager.adapter as ViewPagerAdapter).getTitle(position))
        }.attach()

        arguments?.let { args ->
            (binding.viewpager.adapter as ViewPagerAdapter).getFragmentAtPosition(
                args.getInt(SwapFragment.ARG_ID)
            ).arguments = arguments
        }

        binding.viewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.grPolkaswapMarkets.isVisible =
                    binding.viewpager.adapter?.safeCast<ViewPagerAdapter>()
                        ?.isVisibleMarketsTitle(position) ?: false
            }
        })

        initListeners()
    }

    private fun initListeners() {
        viewModel.showMarketDialogLiveData.observe { pair ->
            MarketChooserDialog(
                requireActivity(),
                viewModel::marketClicked,
                pair.second,
                pair.first
            ).show()
        }

        viewModel.selectedMarketLiveData.observe {
            binding.marketValueText.setText(it.titleResource)
        }
    }
}
