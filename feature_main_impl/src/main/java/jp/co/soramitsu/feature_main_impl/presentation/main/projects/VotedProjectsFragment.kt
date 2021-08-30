package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentVotedProjectsBinding
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel

class VotedProjectsFragment : BaseVotableFragment(R.layout.fragment_voted_projects) {

    companion object {

        fun newInstance(): VotedProjectsFragment {
            return VotedProjectsFragment()
        }
    }

    private val binding by viewBinding(FragmentVotedProjectsBinding::bind)

    override fun provideProjectsLiveData(viewModel: MainViewModel) = viewModel.votedProjectsLiveData
    override fun provideProjectsResyncEvent(viewModel: MainViewModel) =
        viewModel.votedProjectsResyncEvent

    override fun updateProjects(viewModel: MainViewModel) {
        viewModel.syncVotedVotables()
    }

    override fun provideLayoutId() = binding
}
