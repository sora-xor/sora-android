/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.referendum

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.plusAssign
import jp.co.soramitsu.common.util.ext.subscribeToError
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.detail.BaseDetailViewModel
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum

class ShowVoteSheetPayload(val toSupport: Boolean, val maxAllowedVotes: Int)

class DetailReferendumViewModel(
    private val interactor: MainInteractor,
    private val referendumId: String,
    router: MainRouter,
    numbersFormatter: NumbersFormatter,
    resourceManager: ResourceManager
) : BaseDetailViewModel(interactor, router, numbersFormatter, resourceManager) {
    private val _referendumLiveData = MutableLiveData<Referendum>()
    val referendumLiveData: LiveData<Referendum> = _referendumLiveData

    private val _showVoteSheet = MutableLiveData<Event<ShowVoteSheetPayload>>()
    val showVoteSheet: LiveData<Event<ShowVoteSheetPayload>> = _showVoteSheet

    init {
        startObservingReferendum()
    }

    private fun startObservingReferendum() {
        disposables += interactor.observeReferendum(referendumId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWithDefaultError(_referendumLiveData::setValue)
    }

    fun voteOnReferendumClicked(toSupport: Boolean) {
        votesLiveData.value?.let {
            _showVoteSheet.value = Event(ShowVoteSheetPayload(toSupport, it.toInt()))
        }
    }

    fun voteOnReferendum(votes: Long, toSupport: Boolean) {
        val action = if (toSupport) {
            interactor.voteForReferendum(referendumId, votes)
        } else {
            interactor.voteAgainstReferendum(referendumId, votes)
        }

        disposables.add(
            action.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeToError(::onError)
        )
    }

    fun onDeadline(id: String) {
        disposables.add(
            interactor.syncReferendum(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
        )
    }
}