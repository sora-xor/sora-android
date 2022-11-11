/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.staking

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import androidx.core.text.underline
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentStakingBinding
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class StakingFragment : BaseFragment<StakingViewModel>(R.layout.fragment_staking) {

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentStakingBinding::bind)

    private val vm: StakingViewModel by viewModels()
    override val viewModel: StakingViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as BottomBarController).showBottomBar()
        binding.tvStakingMore.text =
            SpannableStringBuilder().underline { append(getString(R.string.common_learn_more)) }
        binding.tvStakingMore.setDebouncedClickListener(debounceClickHandler) {
            ShareUtil.shareInBrowser(this, Const.STAKING_LEARN_MORE_LINK)
        }
    }
}
