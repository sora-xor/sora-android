/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentPoolBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class PoolFragment : BaseFragment<PoolViewModel>(R.layout.fragment_pool) {

    companion object {
        const val ID = 1
        val TITLE_RESOURCE = R.string.polkaswap_pool_title
    }

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentPoolBinding::bind)
    lateinit var poolAdapter: PoolAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        poolAdapter = PoolAdapter(debounceClickHandler)
//        binding.poolRecyclerView.adapter = poolAdapter
//
//        ContextCompat.getDrawable(
//            binding.poolRecyclerView.context,
//            R.drawable.line_ver_divider
//        )?.let {
//            binding.poolRecyclerView.addItemDecoration(
//                DividerItemDecoration(
//                    binding.poolRecyclerView.context,
//                    DividerItemDecoration.VERTICAL
//                ).apply {
//                    setDrawable(it)
//                }
//            )
//        }
//
//        initListeners()
    }
//
//    private fun initListeners() {
//        viewModel.poolModelLiveData.observe {
//            if (it.isEmpty()) {
//                binding.placeholder.show()
//            } else {
//                binding.placeholder.gone()
//            }
//
//            binding.animationView.pauseAnimation()
//            binding.animationView.gone()
//
//            poolAdapter.submitList(it)
//        }
//    }

    override fun onDestroy() {
        if (activity?.isChangingConfigurations == false)
            viewModelStore.clear()
        super.onDestroy()
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(requireContext(), WalletFeatureApi::class.java)
            .polkaswapComponentBuilder()
            .withFragment(this)
            .build()
            .inject(this)
    }
}
