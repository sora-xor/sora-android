/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail.referendum

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
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

    private val _showVoteSheet = SingleLiveEvent<ShowVoteSheetPayload>()
    val showVoteSheet: LiveData<ShowVoteSheetPayload> = _showVoteSheet

    fun voteOnReferendumClicked(toSupport: Boolean) {
        votesLiveData.value?.let {
            _showVoteSheet.value = ShowVoteSheetPayload(toSupport, it.toInt())
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
    }
}
