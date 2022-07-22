/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.voteshistory

import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.TimeSectionInteractor
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem
import javax.inject.Inject

@HiltViewModel
class VotesHistoryViewModel @Inject constructor(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val timeSectionInteractor: TimeSectionInteractor
) : BaseViewModel() {

    companion object {
        private const val VOTES_HISTORY_PER_PAGE = 50
    }

    val votesHistoryLiveData = MutableLiveData<List<VotesHistoryItem>>()
    val showEmptyLiveData = MutableLiveData<Boolean>()

    private var votesHistoryOffset = 0
    private var loading = false
    private var lastPageLoaded = false

    fun loadHistory(updateCached: Boolean) {
        votesHistoryOffset = 0
        lastPageLoaded = false
        // if (votesHistoryLiveData.value == null) showPreloader()
    }

    fun loadMoreHistory() {
        if (loading || lastPageLoaded) return
    }

    fun backButtonClick() {
        router.popBackStack()
    }
}
