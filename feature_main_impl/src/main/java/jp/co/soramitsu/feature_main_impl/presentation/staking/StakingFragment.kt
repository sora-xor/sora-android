package jp.co.soramitsu.feature_main_impl.presentation.staking

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
import jp.co.soramitsu.feature_main_impl.databinding.FragmentStakingBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

class StakingFragment : BaseFragment<StakingViewModel>(R.layout.fragment_staking) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentStakingBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as BottomBarController).showBottomBar()
        binding.stakingSubtitleTv.text = Const.VAL_REWARD_LINK

        binding.stakingWrapper.setDebouncedClickListener(debounceClickHandler) {
            showBrowser(Const.VAL_REWARD_LINK)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .stakingComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
