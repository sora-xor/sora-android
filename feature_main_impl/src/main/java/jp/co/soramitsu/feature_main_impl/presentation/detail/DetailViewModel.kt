/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail

import android.app.Activity
import android.view.View
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.DeciminalFormatter
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.detail.gallery.GalleryActivity
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItemType
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import java.net.URL

class DetailViewModel(
    private val interactor: MainInteractor,
    private val preloader: WithPreloader,
    private val router: MainRouter,
    private val projectId: String
) : BaseViewModel(), WithPreloader by preloader {

    val projectDetailsLiveData = MutableLiveData<ProjectDetails>()
    val votesLiveData = MutableLiveData<Pair<Int, String>>()
    val playVideoLiveData = MutableLiveData<Event<String>>()
    val sendEmailEvent = MutableLiveData<Event<String>>()
    val showVoteProjectLiveData = MutableLiveData<Event<Int>>()
    val showVoteUserLiveData = MutableLiveData<Event<Int>>()

    init {
        disposables.add(
            interactor.getProjectById(projectId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showPreloader() }
                .subscribe({
                    hidePreloader()
                    projectDetailsLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    fun updateProject() {
        disposables.add(
            interactor.updateProject(projectId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    logException(it)
                })
        )
    }

    fun onActivityCreated() {
        router.hideBottomView()
    }

    fun backPressed() {
        router.popBackStackFragment()
    }

    fun getVotes(updateCached: Boolean) {
        disposables.add(
            interactor.getVotes(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ votes ->
                    votesLiveData.value = Pair(votes.toInt(), DeciminalFormatter.formatInteger(votes))
                }, {
                    logException(it)
                })
        )
    }

    private fun removeProjectFromFavorites() {
        disposables.add(
            interactor.removeProjectFromFavorites(projectId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateProject()
                }, {
                    onError(it)
                })
        )
    }

    private fun addProjectToFavorites() {
        disposables.add(
            interactor.addProjectToFavorites(projectId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateProject()
                }, {
                    onError(it)
                })
        )
    }

    fun voteForProject(votes: Long) {
        disposables.add(
            interactor.voteForProject(projectId, votes)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateProject()
                    getVotes(true)
                }, {
                    onError(it)
                })
        )
    }

    fun websiteClicked(projectLink: URL?) {
        if (projectLink != null) {
            router.showBrowser(projectLink)
        }
    }

    fun emailClicked() {
        projectDetailsLiveData.value?.email?.let {
            if (it.isNotEmpty()) {
                sendEmailEvent.value = Event(it)
            }
        }
    }

    fun galleryClicked(activity: Activity, sharedView: View, galleryItemVm: GalleryItem, index: Int) {
        if (GalleryItemType.VIDEO == galleryItemVm.type) {
            playVideoLiveData.value = Event(galleryItemVm.url)
        } else {
            projectDetailsLiveData.value?.let { GalleryActivity.start(activity, it.gallery, index) }
        }
    }

    fun voteClicked() {
        projectDetailsLiveData.value?.let { project ->
            votesLiveData.value?.let { pair ->
                val votesLeft = (project.fundingTarget - project.fundingCurrent).toInt()
                if (pair.first.toLong() < votesLeft) {
                    showVoteUserLiveData.value = Event(pair.first)
                } else {
                    showVoteProjectLiveData.value = Event(votesLeft)
                }
            }
        }
    }

    fun favoriteClicked() {
        projectDetailsLiveData.value?.let { project ->
            if (project.isFavorite) {
                removeProjectFromFavorites()
            } else {
                addProjectToFavorites()
            }
        }
    }

    fun votesClicked() {
        router.showVotesScreen()
    }
}