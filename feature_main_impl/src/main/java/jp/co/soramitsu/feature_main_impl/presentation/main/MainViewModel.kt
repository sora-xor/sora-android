/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.main

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_project_api.domain.model.Project
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class MainViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader,
    private val numbersFormatter: NumbersFormatter
) : BaseViewModel(), WithPreloader by preloader {

    companion object {
        private const val PROJECTS_PER_PAGE = 50
        private const val PROJECT_CHANGE_FAV_DELAY = 500L
    }

    private var allProjectsOffset = 0
    private var allProjectsLoading = false
    private var allProjectsLastPageLoaded = false

    private var completedProjectsOffset = 0
    private var completedProjectsLoading = false
    private var completedProjectsLastPageLoaded = false

    private var favoriteProjectsOffset = 0
    private var favoriteProjectsLoading = false
    private var favoriteProjectsLastPageLoaded = false

    private var votedProjectsOffset = 0
    private var votedProjectsLoading = false
    private var votedProjectsLastPageLoaded = false

    val votesFormattedLiveData = MediatorLiveData<String>()
    val allProjectsLiveData = MutableLiveData<List<Project>>()
    val favoriteProjectsLiveData = MutableLiveData<List<Project>>()
    val votedProjectsLiveData = MutableLiveData<List<Project>>()
    val completedProjectsLiveData = MutableLiveData<List<Project>>()
    val showVoteProjectLiveData = MutableLiveData<Event<Int>>()
    val showVoteUserLiveData = MutableLiveData<Event<Int>>()

    private val votesLiveData = MutableLiveData<BigDecimal>()
    private val selectedProjectLiveData = MutableLiveData<Project>()

    private val favProjectsHandlingSet = HashSet<String>()

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
                    }, {
                        onError(it)
                    })
            )
        }
    }

    fun projectClick(projectVm: Project) {
        router.showProjectDetails(projectVm.id)
    }

    fun onActivityCreated() {
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

    fun projectsFavoriteClicked(project: Project) {
        if (project.isFavorite) {
            removeProjectFromFavorites(project)
        } else {
            addProjectToFavorites(project)
        }
    }

    private fun addProjectToFavorites(project: Project) {
        if (favProjectsHandlingSet.contains(project.id)) {
            return
        } else {
            favProjectsHandlingSet.add(project.id)
        }
        disposables.add(
            interactor.addProjectToFavorites(project.id)
                .andThen(Completable.timer(PROJECT_CHANGE_FAV_DELAY, TimeUnit.MILLISECONDS))
                .doAfterTerminate { favProjectsHandlingSet.remove(project.id) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    onError(it)
                })
        )
    }

    private fun removeProjectFromFavorites(project: Project) {
        if (favProjectsHandlingSet.contains(project.id)) {
            return
        } else {
            favProjectsHandlingSet.add(project.id)
        }
        disposables.add(
            interactor.removeProjectFromFavorites(project.id)
                .andThen(Completable.timer(PROJECT_CHANGE_FAV_DELAY, TimeUnit.MILLISECONDS))
                .doAfterTerminate { favProjectsHandlingSet.remove(project.id) }
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
        router.showVotesHistory()
    }

    fun voteClicked(project: Project) {
        selectedProjectLiveData.value = project
        votesLiveData.value?.let { userVotes ->
            val votesLeft = (project.fundingTarget - project.fundingCurrent).toInt()
            if (userVotes.toInt() < votesLeft) {
                showVoteUserLiveData.value = Event(userVotes.toInt())
            } else {
                showVoteProjectLiveData.value = Event(votesLeft)
            }
        }
    }

    fun updateAllProjects() {
        allProjectsOffset = 0
        allProjectsLastPageLoaded = false

        disposables.addAll(
            interactor.updateAllProjects(PROJECTS_PER_PAGE)
                .doOnSuccess { allProjectsOffset += 1 }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ elementsCount ->
                    if (elementsCount < PROJECTS_PER_PAGE) {
                        allProjectsLastPageLoaded = true
                    }
                }, {
                    logException(it)
                })
        )
    }

    fun updateFavoriteProjects() {
        favoriteProjectsOffset = 0
        favoriteProjectsLastPageLoaded = false

        disposables.addAll(
            interactor.updateFavoriteProjects(PROJECTS_PER_PAGE)
                .doOnSuccess { favoriteProjectsOffset += 1 }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ elementsCount ->
                    if (elementsCount < PROJECTS_PER_PAGE) {
                        favoriteProjectsLastPageLoaded = true
                    }
                }, {
                    logException(it)
                })
        )
    }

    fun updateVotedProjects() {
        votedProjectsOffset = 0
        votedProjectsLastPageLoaded = false

        disposables.addAll(
            interactor.updateVotedProjects(PROJECTS_PER_PAGE)
                .doOnSuccess { votedProjectsOffset += 1 }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ elementsCount ->
                    if (elementsCount < PROJECTS_PER_PAGE) {
                        votedProjectsLastPageLoaded = true
                    }
                }, {
                    logException(it)
                })
        )
    }

    fun updateCompletedProjects() {
        completedProjectsOffset = 0
        completedProjectsLastPageLoaded = false

        disposables.addAll(
            interactor.updateCompletedProjects(PROJECTS_PER_PAGE)
                .doOnSuccess { completedProjectsOffset += 1 }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (completedProjectsLiveData.value!!.size < PROJECTS_PER_PAGE) {
                        completedProjectsLastPageLoaded = true
                    }
                }, {
                    logException(it)
                })
        )
    }

    fun loadMoreAllProjects() {
        if (allProjectsLoading || allProjectsLastPageLoaded) return

        disposables.addAll(
            interactor.loadMoreAllProjects(PROJECTS_PER_PAGE, allProjectsOffset)
                .doOnSuccess { elementsCount ->
                    allProjectsOffset += 1
                    allProjectsLastPageLoaded = elementsCount < PROJECTS_PER_PAGE
                }
                .doOnSubscribe { allProjectsLoading = true }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    allProjectsLoading = false
                }, {
                    logException(it)
                })
        )
    }

    fun loadMoreCompletedProjects() {
        if (completedProjectsLoading || completedProjectsLastPageLoaded) return

        disposables.addAll(
            interactor.loadMoreCompletedProjects(PROJECTS_PER_PAGE, completedProjectsOffset)
                .doOnSuccess { elementsCount ->
                    completedProjectsOffset += 1
                    completedProjectsLastPageLoaded = elementsCount < PROJECTS_PER_PAGE
                }
                .doOnSubscribe { completedProjectsLoading = true }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    completedProjectsLoading = false
                }, {
                    logException(it)
                })
        )
    }

    fun loadMoreFavoriteProjects() {
        if (favoriteProjectsLoading || favoriteProjectsLastPageLoaded) return

        disposables.addAll(
            interactor.loadMoreFavoriteProjects(PROJECTS_PER_PAGE, favoriteProjectsOffset)
                .doOnSuccess { elementsCount ->
                    favoriteProjectsOffset += 1
                    favoriteProjectsLastPageLoaded = elementsCount < PROJECTS_PER_PAGE
                }
                .doOnSubscribe { favoriteProjectsLoading = true }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    favoriteProjectsLoading = false
                }, {
                    logException(it)
                })
        )
    }

    fun loadMoreVotedProjects() {
        if (votedProjectsLoading || votedProjectsLastPageLoaded) return

        disposables.addAll(
            interactor.loadMoreVotedProjects(PROJECTS_PER_PAGE, votedProjectsOffset)
                .doOnSuccess { elementsCount ->
                    votedProjectsOffset += 1
                    votedProjectsLastPageLoaded = elementsCount < PROJECTS_PER_PAGE
                }
                .doOnSubscribe { votedProjectsLoading = true }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    votedProjectsLoading = false
                }, {
                    logException(it)
                })
        )
    }
}