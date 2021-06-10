package jp.co.soramitsu.feature_main_impl.presentation.polkaswap

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.common.util.ext.showBrowser
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentPolkaSwapBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import javax.inject.Inject

class PolkaSwapFragment : BaseFragment<PolkaSwapViewModel>(R.layout.fragment_polka_swap) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentPolkaSwapBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.polkaswapWrapper.setDebouncedClickListener(debounceClickHandler) {
            showBrowser(Const.POLKASWAP_WEBSITE)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .polkaswapComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
