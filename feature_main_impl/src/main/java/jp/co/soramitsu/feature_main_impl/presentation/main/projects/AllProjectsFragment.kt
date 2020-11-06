package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel

class AllProjectsFragment : BaseVotableFragment() {

    companion object {

        fun newInstance(): AllProjectsFragment {
            return AllProjectsFragment()
        }
    }

    override fun provideProjectsLiveData(viewModel: MainViewModel) = viewModel.allProjectsLiveData
    override fun provideProjectsResyncEvent(viewModel: MainViewModel) = viewModel.allProjectsResyncEvent

    override fun updateProjects(viewModel: MainViewModel) {
        viewModel.syncOpenedVotables()
    }

    override fun provideLayoutId() = R.layout.fragment_all_projects
}