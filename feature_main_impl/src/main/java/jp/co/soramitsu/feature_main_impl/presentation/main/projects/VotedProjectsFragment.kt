/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel

class VotedProjectsFragment : BaseVotableFragment() {

    companion object {

        fun newInstance(): VotedProjectsFragment {
            return VotedProjectsFragment()
        }
    }

    override fun provideProjectsLiveData(viewModel: MainViewModel) = viewModel.votedProjectsLiveData
    override fun provideProjectsResyncEvent(viewModel: MainViewModel) = viewModel.votedProjectsResyncEvent

    override fun updateProjects(viewModel: MainViewModel) {
        viewModel.syncVotedVotables()
    }

    override fun provideLayoutId() = R.layout.fragment_voted_projects
}