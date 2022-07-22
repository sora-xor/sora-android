/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.voteshistory

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentVotesHistoryBinding
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.recycler.VotesHistoryAdapter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import javax.inject.Inject

@AndroidEntryPoint
class VotesHistoryFragment : BaseFragment<VotesHistoryViewModel>(R.layout.fragment_votes_history) {

    @Inject
    lateinit var numbersFormatter: NumbersFormatter
    private val binding by viewBinding(FragmentVotesHistoryBinding::bind)

    private val vm: VotesHistoryViewModel by viewModels()

    override val viewModel: VotesHistoryViewModel
        get() = vm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as BottomBarController).hideBottomBar()

        binding.toolbar.setHomeButtonListener { viewModel.backButtonClick() }

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val totalItemCount = recyclerView.layoutManager?.itemCount
                val lastVisiblePosition =
                    (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (lastVisiblePosition + 1 == totalItemCount) {
                    viewModel.loadMoreHistory()
                }
            }
        }
        binding.votesHistoryRecyclerView.addOnScrollListener(scrollListener)

        binding.swipeContainer.setOnRefreshListener {
            viewModel.loadHistory(true)
        }
        initListeners()
    }

    private fun initListeners() {
        viewModel.votesHistoryLiveData.observe {
            showList(it)
        }
//        viewModel.getPreloadVisibility().observe {
//            if (it) binding.preloaderView.show() else binding.preloaderView.gone()
//        }
        viewModel.showEmptyLiveData.observe {
            if (it) binding.emptyPlaceHolder.show() else binding.emptyPlaceHolder.gone()
        }
    }

    private fun showList(list: List<VotesHistoryItem>) {
        binding.swipeContainer.isRefreshing = false
        binding.votesHistoryRecyclerView.show()

        if (binding.votesHistoryRecyclerView.adapter == null) {
            binding.votesHistoryRecyclerView.adapter = VotesHistoryAdapter(numbersFormatter)
            binding.votesHistoryRecyclerView.itemAnimator = null
        }

        (binding.votesHistoryRecyclerView.adapter as VotesHistoryAdapter).submitList(list)
    }
}
