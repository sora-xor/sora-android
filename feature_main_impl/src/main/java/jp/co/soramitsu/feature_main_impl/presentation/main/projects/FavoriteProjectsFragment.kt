/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main.projects

import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.main.MainViewModel

class FavoriteProjectsFragment : BaseVotableFragment() {

    companion object {

        fun newInstance(): FavoriteProjectsFragment {
            return FavoriteProjectsFragment()
        }
    }

    override fun provideProjectsLiveData(viewModel: MainViewModel) = viewModel.favoriteProjectsLiveData
    override fun provideProjectsResyncEvent(viewModel: MainViewModel) = viewModel.favoriteProjectsResyncEvent

    override fun updateProjects(viewModel: MainViewModel) {
        viewModel.syncFavoriteVotables()
    }

    override fun provideLayoutId() = R.layout.fragment_favorite_projects
}