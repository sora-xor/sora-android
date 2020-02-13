/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.core_di.holder.FeatureUtils
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.MainProjectsAdapter
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel
import kotlinx.android.synthetic.main.fragment_all_projects.placeholder
import kotlinx.android.synthetic.main.fragment_all_projects.projects_recyclerview
import javax.inject.Inject

class AllProjectsFragment : BaseFragment<MainViewModel>() {

    companion object {

        fun newInstance(): AllProjectsFragment {
            return AllProjectsFragment()
        }
    }

    private var lastState: Parcelable? = null

    @Inject lateinit var numbersFormatter: NumbersFormatter

    @Inject lateinit var debounceClickHandler: DebounceClickHandler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_all_projects, container, false)
    }

    override fun initViews() {
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val totalItemCount = recyclerView.layoutManager?.itemCount
                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (lastVisiblePosition + 1 == totalItemCount) {
                    viewModel.loadMoreAllProjects()
                }
            }
        }

        projects_recyclerview.addOnScrollListener(scrollListener)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .projectsComponentBuilder()
            .withFragment(parentFragment!!)
            .build()
            .inject(this)
    }

    override fun subscribe(viewModel: MainViewModel) {
        observe(viewModel.allProjectsLiveData, Observer {
            if (projects_recyclerview.adapter == null) {
                projects_recyclerview.layoutManager = LinearLayoutManager(activity!!)
                projects_recyclerview.adapter = MainProjectsAdapter(
                    numbersFormatter,
                    debounceClickHandler,
                    { viewModel.voteClicked(it) },
                    { viewModel.projectsFavoriteClicked(it) },
                    { viewModel.projectClick(it) }
                )
                lastState?.let {
                    (projects_recyclerview.layoutManager as LinearLayoutManager).onRestoreInstanceState(it)
                }
            }
            (projects_recyclerview.adapter as MainProjectsAdapter).submitList(it)
            if (it.isEmpty()) {
                placeholder.show()
                projects_recyclerview.gone()
            } else {
                placeholder.gone()
                projects_recyclerview.show()
            }
        })

        viewModel.updateAllProjects()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (projects_recyclerview.layoutManager as LinearLayoutManager?)?.let {
            lastState = it.onSaveInstanceState()
        }
    }
}