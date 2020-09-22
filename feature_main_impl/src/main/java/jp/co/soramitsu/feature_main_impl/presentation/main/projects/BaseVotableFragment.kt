/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel
import jp.co.soramitsu.feature_main_impl.presentation.main.VotablesAdapter
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import jp.co.soramitsu.feature_votable_api.domain.model.project.Project
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import kotlinx.android.synthetic.main.fragment_all_projects.placeholder
import kotlinx.android.synthetic.main.fragment_all_projects.projects_recyclerview
import javax.inject.Inject

abstract class BaseVotableFragment : BaseFragment<MainViewModel>(), VotablesAdapter.ProjectHandler,
    VotablesAdapter.ReferendumHandler {

    private val RECYCLERVIEW_UPDATE_DELAY = 300L

    @Inject
    lateinit var numbersFormatter: NumbersFormatter

    @Inject
    lateinit var dateTimeFormatter: DateTimeFormatter

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private var lastState: Parcelable? = null

    private lateinit var adapter: VotablesAdapter

    abstract fun provideProjectsLiveData(viewModel: MainViewModel): LiveData<List<Votable>>
    abstract fun provideProjectsResyncEvent(viewModel: MainViewModel): LiveData<Event<Unit>>

    abstract fun updateProjects(viewModel: MainViewModel)

    abstract fun provideLayoutId(): Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(provideLayoutId(), container, false)
    }

    override fun initViews() {
        adapter = VotablesAdapter(numbersFormatter, debounceClickHandler, this, this, dateTimeFormatter)

        projects_recyclerview.layoutManager = LinearLayoutManager(requireActivity())
        projects_recyclerview.adapter = adapter

        lastState?.let { restoreListPosition(it) }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .projectsComponentBuilder()
            .withFragment(parentFragment!!)
            .build()
            .inject(this)
    }

    override fun subscribe(viewModel: MainViewModel) {
        observe(provideProjectsLiveData(viewModel), Observer {
            adapter.submitList(it)

            showPlaceholderIfNeeded(it)
        })

        observe(provideProjectsResyncEvent(viewModel), EventObserver {
            Handler().postDelayed({
                projects_recyclerview?.scrollToPosition(0)
            }, RECYCLERVIEW_UPDATE_DELAY)
        })

        updateProjects(viewModel)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        (projects_recyclerview.layoutManager as LinearLayoutManager?)?.let {
            lastState = it.onSaveInstanceState()
        }
    }

    private fun restoreListPosition(it: Parcelable) {
        (projects_recyclerview.layoutManager as LinearLayoutManager).onRestoreInstanceState(it)
    }

    private fun showPlaceholderIfNeeded(it: List<Votable>) {
        if (it.isEmpty()) {
            placeholder.show()
            projects_recyclerview.gone()
        } else {
            placeholder.gone()
            projects_recyclerview.show()
        }
    }

    override fun projectVoteClicked(project: Project) {
        viewModel.voteForProjectClicked(project)
    }

    override fun projectFavouriteClicked(project: Project) {
        viewModel.projectsFavoriteClicked(project)
    }

    override fun projectClicked(project: Project) {
        viewModel.projectClicked(project)
    }

    override fun referendumClicked(referendum: Referendum) {
        viewModel.referendumClicked(referendum)
    }

    override fun referendumVoteForClicked(referendum: Referendum) {
        viewModel.voteOnReferendumClicked(referendum, toSupport = true)
    }

    override fun referendumVoteAgainstClicked(referendum: Referendum) {
        viewModel.voteOnReferendumClicked(referendum, toSupport = false)
    }

    override fun onDeadline(id: String) {
        viewModel.onDeadline(id)
    }
}