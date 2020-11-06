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
import jp.co.soramitsu.common.util.ext.setValueIfEmpty
import jp.co.soramitsu.common.util.ext.subscribeToError
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import jp.co.soramitsu.feature_votable_api.domain.model.project.Project
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class MainViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val preloader: WithPreloader,
    private val numbersFormatter: NumbersFormatter
) : BaseViewModel(), WithPreloader by preloader {

    companion object {
        private const val PROJECT_CHANGE_FAV_DELAY = 500L
    }

    val votesFormattedLiveData = MediatorLiveData<String>()

    val allProjectsLiveData = MutableLiveData<List<Votable>>()
    val allProjectsResyncEvent = MutableLiveData<Event<Unit>>()
    val favoriteProjectsLiveData = MutableLiveData<List<Votable>>()
    val favoriteProjectsResyncEvent = MutableLiveData<Event<Unit>>()
    val votedProjectsLiveData = MutableLiveData<List<Votable>>()
    val votedProjectsResyncEvent = MutableLiveData<Event<Unit>>()
    val completedProjectsLiveData = MutableLiveData<List<Votable>>()
    val completedProjectsResyncEvent = MutableLiveData<Event<Unit>>()

    val showVoteProjectLiveData = MutableLiveData<Event<Int>>()
    val showVoteUserLiveData = MutableLiveData<Event<Int>>()

    val showVoteForReferendumLiveData = MutableLiveData<Event<Int>>()
    val showVoteAgainstReferendumLiveData = MutableLiveData<Event<Int>>()

    private val votesLiveData = MutableLiveData<BigDecimal>()
    private var selectedVotable: Votable? = null

    private val favProjectsHandlingSet = HashSet<String>()

    init {
        votesFormattedLiveData.addSource(votesLiveData) {
            votesFormattedLiveData.value = numbersFormatter.formatInteger(it)
        }

        disposables.add(
            interactor.observeVotes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    votesLiveData.value = it
                }, ::onError)
        )

        disposables.add(
            interactor.observeFavoriteVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    favoriteProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.addAll(
            interactor.observeFinishedVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    completedProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.addAll(
            interactor.observeVotedVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    votedProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.addAll(
            interactor.observeOpenVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    allProjectsLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

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

    fun voteForProject(votesNumber: Long) {
        val selectedProject = selectedVotable as? Project ?: return

        disposables.add(
            interactor.voteForProject(selectedProject.id, votesNumber)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(preloadCompletableCompose())
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

    fun projectClicked(projectVm: Project) {
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

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun votesClick() {
        router.showVotesHistory()
    }

    fun voteForProjectClicked(project: Project) {
        selectedVotable = project

        votesLiveData.value?.let { userVotes ->
            if (hasEnoughVotes(userVotes)) {
                showProjectVoteDialog(project, userVotes)
            } else {
                onError(R.string.votes_zero_error_message)
            }
        }
    }

    private fun showProjectVoteDialog(project: Project, userVotes: BigDecimal) {
        val votesLeft = project.getVotesLeft()
        if (userVotes.toInt() < votesLeft) {
            showVoteUserLiveData.value = Event(userVotes.toInt())
        } else {
            showVoteProjectLiveData.value = Event(votesLeft)
        }
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
            showVoteForReferendumLiveData.value = Event(userVotes)
        } else {
            showVoteAgainstReferendumLiveData.value = Event(userVotes)
        }
    }

    fun referendumClicked(referendum: Referendum) {
        router.showReferendumDetails(referendum.id)
    }

    fun syncOpenedVotables() {
        disposables.addAll(
            interactor.syncOpenVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    allProjectsResyncEvent.setValueIfEmpty(Event(Unit))
                }, {
                    logException(it)
                })
        )
    }

    fun syncFavoriteVotables() {
        disposables.addAll(
            interactor.syncFavoriteVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    favoriteProjectsResyncEvent.setValueIfEmpty(Event(Unit))
                }, {
                    logException(it)
                })
        )
    }

    fun syncVotedVotables() {
        disposables.addAll(
            interactor.syncVotedVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    votedProjectsResyncEvent.setValueIfEmpty(Event(Unit))
                }, {
                    logException(it)
                })
        )
    }

    fun syncCompletedVotables() {
        disposables.addAll(
            interactor.syncCompletedVotables()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    completedProjectsResyncEvent.setValueIfEmpty(Event(Unit))
                }, {
                    logException(it)
                })
        )
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

    private fun hasEnoughVotes(votes: BigDecimal) = votes >= BigDecimal.ONE

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

    fun onDeadline(id: String) {
        disposables.add(
            interactor.syncReferendum(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
        )
    }
}