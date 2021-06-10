/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.subscribeToError
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import java.math.BigDecimal

class MainViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

    val allProjectsLiveData = MutableLiveData<List<Votable>>()
    val allProjectsResyncEvent = SingleLiveEvent<Unit>()
    val votedProjectsLiveData = MutableLiveData<List<Votable>>()
    val votedProjectsResyncEvent = SingleLiveEvent<Unit>()
    val completedProjectsLiveData = MutableLiveData<List<Votable>>()
    val completedProjectsResyncEvent = SingleLiveEvent<Unit>()

    val showVoteForReferendumLiveData = SingleLiveEvent<Int>()
    val showVoteAgainstReferendumLiveData = SingleLiveEvent<Int>()

    private val votesLiveData = MutableLiveData<BigDecimal>()
    private var selectedVotable: Votable? = null

    init {

//        disposables.add(
//            interactor.observeVotes()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    {
//                        votesLiveData.value = it
//                    },
//                    ::onError
//                )
//        )
//
//        disposables.addAll(
//            interactor.observeFinishedVotables()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    {
//                        completedProjectsLiveData.value = it
//                    },
//                    {
//                        it.printStackTrace()
//                    }
//                )
//        )
//
//        disposables.addAll(
//            interactor.observeVotedVotables()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    {
//                        votedProjectsLiveData.value = it
//                    },
//                    {
//                        it.printStackTrace()
//                    }
//                )
//        )
//
//        disposables.addAll(
//            interactor.observeOpenVotables()
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                    {
//                        allProjectsLiveData.value = it
//                    },
//                    {
//                        it.printStackTrace()
//                    }
//                )
//        )

        syncVotes()
    }

    private fun syncVotes() {
        disposables.add(
            interactor.syncVotes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeToError(::onError)
        )
    }

    fun voteOnReferendum(votes: Long, toSupport: Boolean) {
        val selectedReferendum = selectedVotable as? Referendum ?: return

        val action = if (toSupport) {
            interactor.voteForReferendum(selectedReferendum.id, votes)
        } else {
            interactor.voteAgainstReferendum(selectedReferendum.id, votes)
        }

        disposables.add(
            action.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(preloadCompletableCompose())
                .subscribeToError(::onError)
        )
    }

    fun backPressed() {
        router.popBackStack()
    }

    fun votesClick() {
        router.showVotesHistory()
    }

    fun voteOnReferendumClicked(referendum: Referendum, toSupport: Boolean) {
        selectedVotable = referendum

        votesLiveData.value?.let {
            if (hasEnoughVotes(it)) {
                showReferendumVoteDialog(toSupport, it.toInt())
            } else {
                onError(R.string.votes_zero_error_message)
            }
        }
    }

    private fun showReferendumVoteDialog(toSupport: Boolean, userVotes: Int) {
        if (toSupport) {
            showVoteForReferendumLiveData.value = userVotes
        } else {
            showVoteAgainstReferendumLiveData.value = userVotes
        }
    }

    fun referendumClicked(referendum: Referendum) {
        router.showReferendumDetails(referendum.id)
    }

    fun syncOpenedVotables() {
    }

    fun syncVotedVotables() {
    }

    fun syncCompletedVotables() {
    }

    private fun hasEnoughVotes(votes: BigDecimal) = votes >= BigDecimal.ONE

    fun onDeadline(id: String) {
    }
}
