/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_project_api.domain.model.Project
import java.math.BigDecimal

class MainViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader,
    private val numbersFormatter: NumbersFormatter
) : BaseViewModel(), WithPreloader by preloader {

    val votesFormattedLiveData = MediatorLiveData<String>()
    val hideVoteDialogLiveData = MutableLiveData<Event<Unit>>()
    val showVoteDialogLiveData = MutableLiveData<Event<Int>>()
    val allProjectsLiveData = MutableLiveData<List<Project>>()
    val favoriteProjectsLiveData = MutableLiveData<List<Project>>()
    val votedProjectsLiveData = MutableLiveData<List<Project>>()
    val completedProjectsLiveData = MutableLiveData<List<Project>>()

    private val votesLiveData = MutableLiveData<BigDecimal>()
    private val selectedProjectLiveData = MutableLiveData<Project>()

    init {
        votesFormattedLiveData.addSource(votesLiveData) {
            votesFormattedLiveData.value = numbersFormatter.formatInteger(it)
        }

        disposables.add(
            interactor.getFavoriteProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    favoriteProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.addAll(
            interactor.getCompletedProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    completedProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.addAll(
            interactor.getVotedProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    votedProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.addAll(
            interactor.getAllProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    allProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun loadVotes(updateCached: Boolean) {
        disposables.add(
            interactor.getVotes(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    votesLiveData.value = it
                    if (!updateCached) loadVotes(true)
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun voteForProject(votesNumber: Long) {
        selectedProjectLiveData.value?.let {
            disposables.add(
                interactor.voteForProject(it.id, votesNumber)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(preloadCompletableCompose())
                    .subscribe({
                        votesLiveData.value?.let {
                            val currentVotes = it.toLong() - votesNumber
                            votesLiveData.value = currentVotes.toBigDecimal()
                        }
                        hideVoteDialogLiveData.value = Event(Unit)
                    }, {
                        onError(it)
                    })
            )
        }
    }

    fun projectClick(projectVm: Project) {
        router.showProjectDetailed(projectVm.id)
    }

    fun onActivityCreated() {
        router.showBottomView()
        disposables.add(
            interactor.updatePushTokenIfNeeded()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    logException(it)
                })
        )
    }

    fun addProjectToFavorites(project: Project) {
        disposables.add(
            interactor.addProjectToFavorites(project.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    onError(it)
                })
        )
    }

    fun removeProjectFromFavorites(project: Project) {
        disposables.add(
            interactor.removeProjectFromFavorites(project.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    onError(it)
                })
        )
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun votesClick() {
        router.showVotesScreen()
    }

    fun voteClicked(project: Project) {
        selectedProjectLiveData.value = project
        votesLiveData.value?.let {
            val minimal = Math.min(it.toLong(), project.fundingTarget)
            showVoteDialogLiveData.value = Event(minimal.toInt())
        }
    }

    fun updateAllProjects() {
        disposables.addAll(
            interactor.updateAllProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    logException(it)
                })
        )
    }

    fun updateFavoriteProjects() {
        disposables.addAll(
            interactor.updateFavoriteProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    logException(it)
                })
        )
    }

    fun updateVotedProjects() {
        disposables.addAll(
            interactor.updateVotedProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    logException(it)
                })
        )
    }

    fun updateCompletedProjects() {
        disposables.addAll(
            interactor.updateCompletedProjects()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    logException(it)
                })
        )
    }
}