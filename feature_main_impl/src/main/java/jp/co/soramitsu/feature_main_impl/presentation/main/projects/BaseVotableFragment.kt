package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.annotation.LayoutRes
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.gone
import jp.co.soramitsu.common.util.ext.show
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_impl.databinding.FragmentAllProjectsBinding
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel
import jp.co.soramitsu.feature_main_impl.presentation.main.VotablesAdapter
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import javax.inject.Inject

abstract class BaseVotableFragment(@LayoutRes layoutRes: Int) :
    BaseFragment<MainViewModel>(layoutRes),
    VotablesAdapter.ReferendumHandler {

    @Inject
    lateinit var numbersFormatter: NumbersFormatter

    @Inject
    lateinit var dateTimeFormatter: DateTimeFormatter

    @Inject
    lateinit var debounceClickHandler: DebounceClickHandler

    private var lastState: Parcelable? = null

    private lateinit var adapter: VotablesAdapter

    abstract fun provideProjectsLiveData(viewModel: MainViewModel): LiveData<List<Votable>>
    abstract fun provideProjectsResyncEvent(viewModel: MainViewModel): LiveData<Unit>

    abstract fun updateProjects(viewModel: MainViewModel)

    abstract fun provideLayoutId(): ViewBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = VotablesAdapter(numbersFormatter, debounceClickHandler, this, dateTimeFormatter)
        (provideLayoutId() as? FragmentAllProjectsBinding)?.projectsRecyclerview?.let {
            it.layoutManager = LinearLayoutManager(requireActivity())
            it.adapter = adapter
        }
        lastState?.let { restoreListPosition(it) }
        initListeners()
        updateProjects(viewModel)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(requireContext(), MainFeatureApi::class.java)
            .projectsComponentBuilder()
            .withFragment(requireParentFragment())
            .build()
            .inject(this)
    }

    private fun initListeners() {
        provideProjectsLiveData(viewModel).observe {
            adapter.submitList(it)
            showPlaceholderIfNeeded(it)
        }
        provideProjectsResyncEvent(viewModel).observe {
        }
    }

    override fun onDestroyView() {
        ((provideLayoutId() as? FragmentAllProjectsBinding)?.projectsRecyclerview?.layoutManager as? LinearLayoutManager?)?.let {
            lastState = it.onSaveInstanceState()
        }
        super.onDestroyView()
    }

    private fun restoreListPosition(it: Parcelable) {
        ((provideLayoutId() as? FragmentAllProjectsBinding)?.projectsRecyclerview?.layoutManager as? LinearLayoutManager)?.onRestoreInstanceState(
            it
        )
    }

    private fun showPlaceholderIfNeeded(it: List<Votable>) {
        if (it.isEmpty()) {
            (provideLayoutId() as? FragmentAllProjectsBinding)?.placeholder?.show()
            (provideLayoutId() as? FragmentAllProjectsBinding)?.projectsRecyclerview?.gone()
        } else {
            (provideLayoutId() as? FragmentAllProjectsBinding)?.placeholder?.gone()
            (provideLayoutId() as? FragmentAllProjectsBinding)?.projectsRecyclerview?.show()
        }
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
