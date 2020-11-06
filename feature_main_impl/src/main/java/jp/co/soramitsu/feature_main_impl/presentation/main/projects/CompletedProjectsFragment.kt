package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel

class CompletedProjectsFragment : BaseVotableFragment() {

    companion object {
        fun newInstance(): CompletedProjectsFragment {
            return CompletedProjectsFragment()
        }
    }

    override fun provideProjectsLiveData(viewModel: MainViewModel) = viewModel.completedProjectsLiveData
    override fun provideProjectsResyncEvent(viewModel: MainViewModel) = viewModel.completedProjectsResyncEvent

    override fun updateProjects(viewModel: MainViewModel) {
        viewModel.syncCompletedVotables()
    }

    override fun provideLayoutId() = R.layout.fragment_completed_projects
}