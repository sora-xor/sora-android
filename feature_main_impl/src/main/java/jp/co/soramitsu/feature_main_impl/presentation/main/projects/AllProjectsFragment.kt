package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import by.kirich1409.viewbindingdelegate.viewBinding
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.databinding.FragmentAllProjectsBinding
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel

class AllProjectsFragment : BaseVotableFragment(R.layout.fragment_all_projects) {

    companion object {

        fun newInstance(): AllProjectsFragment {
            return AllProjectsFragment()
        }
    }
    private val binding by viewBinding(FragmentAllProjectsBinding::bind)

    override fun provideProjectsLiveData(viewModel: MainViewModel) = viewModel.allProjectsLiveData
    override fun provideProjectsResyncEvent(viewModel: MainViewModel) = viewModel.allProjectsResyncEvent

    override fun updateProjects(viewModel: MainViewModel) {
        viewModel.syncOpenedVotables()
    }

    override fun provideLayoutId() = binding
}
