/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.voteshistory

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.TimeSectionInteractor
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.mappers.VotesHistoryMapper
import jp.co.soramitsu.feature_main_impl.presentation.voteshistory.model.VotesHistoryItem

class VotesHistoryViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader,
    private val timeSectionInteractor: TimeSectionInteractor
) : BaseViewModel(), WithPreloader by preloader {

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
        if (votesHistoryLiveData.value == null) showPreloader()
        disposables.add(
            interactor.getVotesHistory(updateCached, votesHistoryOffset, VOTES_HISTORY_PER_PAGE)
                .doOnSuccess { votesHistoryOffset += VOTES_HISTORY_PER_PAGE }
                .doFinally { if (!updateCached) loadHistory(true) }
                .map { VotesHistoryMapper.toVmList(it) }
                .map { timeSectionInteractor.insertDateSections(it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    showEmptyLiveData.value = it.isEmpty()
                    hidePreloader()
                    votesHistoryLiveData.value = it
                }, {
                    logException(it)
                }))
    }

    fun loadMoreHistory() {
        if (loading || lastPageLoaded) return
        disposables.add(
            interactor.getVotesHistory(true, votesHistoryOffset, VOTES_HISTORY_PER_PAGE)
                .doOnSuccess {
                    votesHistoryOffset += VOTES_HISTORY_PER_PAGE
                    if (it.size < VOTES_HISTORY_PER_PAGE) lastPageLoaded = true
                }
                .doOnSubscribe { loading = true }
                .map { VotesHistoryMapper.toVmList(it) }
                .map { timeSectionInteractor.insertDateSections(it) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ history ->
                    loading = false
                    votesHistoryLiveData.value?.let {
                        votesHistoryLiveData.value = mutableListOf<VotesHistoryItem>().apply {
                            addAll(it)
                            addAll(history)
                        }
                    }
                }, {
                    logException(it)
                }))
    }

    fun backButtonClick() {
        router.popBackStack()
    }
}