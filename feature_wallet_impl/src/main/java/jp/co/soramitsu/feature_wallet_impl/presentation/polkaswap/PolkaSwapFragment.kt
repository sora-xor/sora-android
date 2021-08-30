package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.tabs.TabLayoutMediator
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserBottomSheet
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserItem
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentPolkaSwapBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class PolkaSwapFragment : BaseFragment<PolkaSwapViewModel>(R.layout.fragment_polka_swap) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentPolkaSwapBinding::bind)

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

        initListeners()
    }

    private fun initListeners() {
        viewModel.marketListLiveData.observe { pair ->
            val chooserItems = pair.first.map {
                ChooserItem(
                    it.titleResource,
                    selected = it.titleResource == pair.second.titleResource
                ) { viewModel.marketClicked(it) }
            }

            ChooserBottomSheet(
                requireActivity(),
                R.string.polkaswap_market_title,
                chooserItems,
                R.string.polkaswap_market_info
            ).show()
        }

        viewModel.selectedMarketLiveData.observe {
            binding.marketValueText.setText(it.titleResource)
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
