/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.voteshistory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.recycler.VotesHistoryAdapter
import kotlinx.android.synthetic.main.fragment_votes_history.emptyPlaceHolder
import kotlinx.android.synthetic.main.fragment_votes_history.preloaderView
import kotlinx.android.synthetic.main.fragment_votes_history.swipeContainer
import kotlinx.android.synthetic.main.fragment_votes_history.toolbar
import kotlinx.android.synthetic.main.fragment_votes_history.votesHistoryRecyclerView

class VotesHistoryFragment : BaseFragment<VotesHistoryViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_votes_history, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .votesHistoryComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        toolbar.setTitle(getString(R.string.votesHistoryScreenTitle))
        toolbar.setHomeButtonListener { viewModel.backButtonClick() }
        (activity as MainActivity).hideBottomView()

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val totalItemCount = recyclerView.layoutManager?.itemCount
                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (lastVisiblePosition + 1 == totalItemCount) {
                    viewModel.loadMoreHistory()
                }
            }
        }
        votesHistoryRecyclerView.addOnScrollListener(scrollListener)

        swipeContainer.setOnRefreshListener {
            viewModel.loadHistory(true)
        }
    }

    override fun subscribe(viewModel: VotesHistoryViewModel) {
        observe(viewModel.votesHistoryLiveData, Observer {
            showList(it)
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) preloaderView.show() else preloaderView.gone()
        })

        observe(viewModel.showEmptyLiveData, Observer {
            if (it) emptyPlaceHolder.show() else emptyPlaceHolder.gone()
        })

        viewModel.loadHistory(false)
    }

    private fun showList(list: List<VotesHistoryItem>) {
        swipeContainer.isRefreshing = false
        votesHistoryRecyclerView.show()

        if (votesHistoryRecyclerView.adapter == null) {
            votesHistoryRecyclerView.adapter = VotesHistoryAdapter()
            votesHistoryRecyclerView.itemAnimator = null
        }

        (votesHistoryRecyclerView.adapter as VotesHistoryAdapter).submitList(list)
    }
}