/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.animateLoader
import jp.co.soramitsu.common.util.ext.slideUpOrDown
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentPoolBinding
import javax.inject.Inject

@AndroidEntryPoint
class PoolFragment : BaseFragment<PoolViewModel>(R.layout.fragment_pool) {

    companion object {
        const val ID = 1
        val TITLE_RESOURCE = R.string.polkaswap_pool_title
    }

    private val vm: PoolViewModel by viewModels()
    override val viewModel: PoolViewModel
        get() = vm

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler
    private val binding by viewBinding(FragmentPoolBinding::bind)
    lateinit var poolAdapter: PoolAdapter

    @Inject
    lateinit var numbersFormatter: NumbersFormatter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        poolAdapter = PoolAdapter(
            debounceClickHandler,
            { binding.fabButton.slideUpOrDown(true) },
            onAddLiquidity = ::onAddLiquidity,
            onRemoveLiquidity = ::onRemoveLiquidity,
            numbersFormatter
        )
        binding.poolRecyclerView.adapter = poolAdapter

        ContextCompat.getDrawable(
            binding.poolRecyclerView.context,
            R.drawable.line_ver_divider
        )?.let {
            binding.poolRecyclerView.addItemDecoration(
                DividerItemDecoration(
                    binding.poolRecyclerView.context,
                    DividerItemDecoration.VERTICAL
                ).apply {
                    setDrawable(it)
                }
            )
        }

        initListeners()
    }

    private fun onAddLiquidity(tokenFrom: Token, tokenTo: Token) {
        viewModel.onAddLiquidity(tokenFrom, tokenTo)
    }

    private fun onRemoveLiquidity(tokenFrom: Token, tokenTo: Token) {
        viewModel.onRemoveLiquidity(tokenFrom, tokenTo)
    }

    private fun initListeners() {
        viewModel.poolModelLiveData.observe {
            when (it) {
                PoolViewModel.PoolsState.Empty -> {
                    poolAdapter.submitList(emptyList())
                    binding.ivNoPools.isVisible = true
                    binding.tvNoPools.isVisible = true
                    binding.ivPoolsListLoader.animateLoader(false)
                }
                PoolViewModel.PoolsState.Error -> {
                    poolAdapter.submitList(emptyList())
                    binding.ivNoPools.isVisible = false
                    binding.tvNoPools.isVisible = false
                    binding.ivPoolsListLoader.animateLoader(false)
                }
                is PoolViewModel.PoolsState.PoolsList -> {
                    poolAdapter.submitList(it.pools)
                    binding.ivNoPools.isVisible = false
                    binding.tvNoPools.isVisible = false
                    binding.ivPoolsListLoader.animateLoader(it.loading)
                }
            }
        }

        binding.fabButton.setOnClickListener {
            viewModel.onAddNewLiquidity()
        }
    }

    override fun onDestroy() {
        if (activity?.isChangingConfigurations == false)
            viewModelStore.clear()
        super.onDestroy()
    }
}
