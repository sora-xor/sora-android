/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.activity

import android.annotation.SuppressLint
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
import kotlinx.android.synthetic.main.fragment_activity.activityRecycler
import kotlinx.android.synthetic.main.fragment_activity.emptyPlaceHolder
import kotlinx.android.synthetic.main.fragment_activity.projectsRecyclerviewPlaceholder
import kotlinx.android.synthetic.main.fragment_activity.swipeLayout
import kotlinx.android.synthetic.main.fragment_activity.toolbarView

@SuppressLint("CheckResult")
class ActivityFragment : BaseFragment<ActivityFeedViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_activity, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .activityFeedComponentBuilder()
            .withFragment(this)
            .withRouter(activity as MainRouter)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as MainActivity).showBottomView()

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val totalItemCount = recyclerView.layoutManager?.itemCount
                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (lastVisiblePosition + 1 == totalItemCount) {
                    viewModel.loadMoreActivity()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                viewModel.onScrolled(dy)
            }
        }
        activityRecycler.addOnScrollListener(scrollListener)

        swipeLayout.setOnRefreshListener { viewModel.refreshData(false, true) }
    }

    override fun subscribe(viewModel: ActivityFeedViewModel) {
        observe(viewModel.activityFeedLiveData, Observer {
            showActivityFeed(it)
        })

        observe(viewModel.getPreloadVisibility(), Observer {
            if (it) projectsRecyclerviewPlaceholder.show() else projectsRecyclerviewPlaceholder.gone()
        })

        observe(viewModel.showToolbarLiveData, Observer {
            if (it) {
                toolbarView.animate()
                    .withStartAction { toolbarView.show() }
                    .alpha(1.0f)
                    .start()
            } else {
                toolbarView.animate()
                    .alpha(0.0f)
                    .start()
            }
        })

        observe(viewModel.showEmptyLiveData, Observer {
            if (it) emptyPlaceHolder.show() else emptyPlaceHolder.gone()
        })

        viewModel.refreshData(true, false)
    }

    private fun showActivityFeed(activities: List<Any>) {
        swipeLayout.isRefreshing = false

        activityRecycler.show()

        if (activityRecycler.adapter == null) {
            activityRecycler.run {
                adapter = ActivityRecyclerAdapter { viewModel.btnHelpClicked() }
                setHasFixedSize(true)
                activityRecycler.itemAnimator = null
            }
        }

        (activityRecycler.adapter as ActivityRecyclerAdapter).submitList(activities)
    }
}