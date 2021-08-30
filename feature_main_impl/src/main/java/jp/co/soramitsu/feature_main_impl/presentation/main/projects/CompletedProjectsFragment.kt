package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentCompletedProjectsBinding
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel

class CompletedProjectsFragment : BaseVotableFragment(R.layout.fragment_completed_projects) {

    companion object {
        fun newInstance(): CompletedProjectsFragment {
            return CompletedProjectsFragment()
        }
    }

    private val binding by viewBinding(FragmentCompletedProjectsBinding::bind)

    override fun provideProjectsLiveData(viewModel: MainViewModel) =
        viewModel.completedProjectsLiveData

    override fun provideProjectsResyncEvent(viewModel: MainViewModel) =
        viewModel.completedProjectsResyncEvent

    override fun updateProjects(viewModel: MainViewModel) {
        viewModel.syncCompletedVotables()
    }

    override fun provideLayoutId() = binding
}
