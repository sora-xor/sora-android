/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_api.domain.model.AddInvitationCase
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ReferendumRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_votable_api.domain.model.Votable
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectDetails
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import java.math.BigDecimal
import javax.inject.Inject

class MainInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val referendumRepository: ReferendumRepository,
    private val votesDataSource: VotesDataSource,
    private val didRepository: DidRepository,
    private val informationRepository: InformationRepository,
    private val notificationRepository: NotificationRepository
) {

    fun getMnemonic(): Single<String> {
        return didRepository.retrieveMnemonic()
            .flatMap {
                if (it.isNotEmpty()) {
                    Single.just(it)
                } else {
                    throw SoraException.businessError(ResponseCode.GENERAL_ERROR)
                }
            }
    }

    fun getUserInfo(updateCached: Boolean): Single<User> {
        return userRepository.getUser(updateCached)
    }

    fun getUserReputation(updateCached: Boolean): Single<Reputation> {
        return userRepository.getUserReputation(updateCached)
    }

    fun getReputationWithLastVotes(updateCached: Boolean): Single<Pair<Reputation, BigDecimal>> {
        return userRepository.getUserReputation(updateCached)
            .zipWith(
                projectRepository.getLastVotesFromCache(),
                BiFunction { reputation, lastVotes ->
                    Pair(reputation, lastVotes)
                })
    }

    fun syncProject(projectId: String): Completable {
        return projectRepository.updateProject(projectId)
    }

    fun syncReferendum(referendumId: String): Completable {
        return referendumRepository.syncReferendum(referendumId)
    }

    fun observeProject(projectId: String): Observable<ProjectDetails> {
        return projectRepository.getProjectById(projectId)
    }

    fun observeReferendum(referendumId: String): Observable<Referendum> {
        return referendumRepository.observeReferendum(referendumId)
    }

    fun observeOpenVotables(): Observable<List<Votable>> {
        return mergeVotables(
            projectRepository.observeOpenedProjects(),
            referendumRepository.observeOpenedReferendums()
        ).map {
            it.sortedBy { it.deadline }
        }
    }

    fun syncOpenVotables(): Completable {
        return Completable.mergeArray(
            referendumRepository.syncOpenedReferendums(),
            projectRepository.syncOpenedProjects(true).ignoreElement()
        )
    }

    fun observeVotedVotables(): Observable<List<Votable>> {
        return mergeVotables(
            projectRepository.observeVotedProjects(),
            referendumRepository.observeVotedReferendums()
        )
    }

    fun syncVotedVotables(): Completable {
        return Completable.mergeArray(
            referendumRepository.syncVotedReferendums(),
            projectRepository.syncVotedProjects(true).ignoreElement()
        )
    }

    fun observeFavoriteVotables(): Observable<List<Votable>> {
        return projectRepository.observeFavouriteProjects().map { it as List<Votable> }
    }

    fun syncFavoriteVotables(): Completable {
        return projectRepository.syncFavoriteProjects(true)
            .ignoreElement()
    }

    fun observeFinishedVotables(): Observable<List<Votable>> {
        return mergeVotables(
            referendumRepository.observeFinishedReferendums(),
            projectRepository.observeFinishedProjects()
        ).map {
            it.sortedByDescending { it.statusUpdateTime }
        }
    }

    fun syncCompletedVotables(): Completable {
        return Completable.mergeArray(
            referendumRepository.syncFinishedReferendums(),
            projectRepository.syncFinishedProjects(true).ignoreElement()
        )
    }

    fun voteForProject(projectId: String, voteNum: Long): Completable {
        return projectRepository.voteForProject(projectId, voteNum)
    }

    fun voteForReferendum(referendumId: String, votes: Long): Completable {
        return referendumRepository.voteForReferendum(referendumId, votes)
    }

    fun voteAgainstReferendum(referendumId: String, votes: Long): Completable {
        return referendumRepository.voteAgainstReferendum(referendumId, votes)
    }

    fun syncVotes(): Completable {
        return votesDataSource.syncVotes()
    }

    fun updatePushTokenIfNeeded(): Completable {
        return notificationRepository.updatePushTokenIfNeeded()
    }

    fun addProjectToFavorites(projectId: String): Completable {
        return projectRepository.addProjectToFavorites(projectId)
    }

    fun removeProjectFromFavorites(projectId: String): Completable {
        return projectRepository.removeProjectFromFavorites(projectId)
    }

    fun saveUserInfo(firstName: String, lastName: String): Completable {
        return userRepository.saveUserInfo(firstName, lastName)
    }

    fun getReputationContent(updateCached: Boolean): Single<List<InformationContainer>> {
        return informationRepository.getReputationContent(updateCached)
    }

    fun getVotesHistory(
        updateCached: Boolean,
        historyOffset: Int,
        votesPerPage: Int
    ): Single<List<VotesHistory>> {
        return projectRepository.getVotesHistory(votesPerPage, historyOffset, updateCached)
    }

    fun getActivityFeed(
        updateCached: Boolean,
        activityPerPage: Int,
        activitiesOffset: Int
    ): Single<List<ActivityFeed>> {
        return userRepository.getActivityFeed(activityPerPage, activitiesOffset, updateCached)
    }

    fun getActivityFeedWithAnnouncement(
        updateCached: Boolean,
        activityPerPage: Int,
        activitiesOffset: Int
    ): Single<List<Any>> {
        return userRepository.getActivityFeed(activityPerPage, activitiesOffset, updateCached)
            .zipWith(userRepository.getAnnouncements(updateCached),
                BiFunction { activityFeed, announcement ->
                    val list = mutableListOf<Any>()
                    if (announcement.isNotEmpty()) list.add(announcement.first())
                    list.addAll(activityFeed)
                    list
                })
    }

    fun getAppVersion(): Single<String> {
        return userRepository.getAppVersion()
    }

    fun checkAddInviteCodeIsPossible(dateInMillis: Long): Single<AddInvitationCase> {
        return userRepository.getUser(true)
            .map {
                if (it.parentId.isEmpty()) {
                    if (it.inviteAcceptExpirationMomentMillis < dateInMillis) {
                        AddInvitationCase.TIME_IS_UP
                    } else {
                        AddInvitationCase.AVAILABLE
                    }
                } else {
                    AddInvitationCase.ALREADY_APPLIED
                }
            }
    }

    fun applyInvitationCode(): Completable {
        return userRepository.applyInvitationCode()
    }

    fun getInviteCode(): Single<String> {
        return userRepository.getParentInviteCode()
            .subscribeOn(Schedulers.io())
    }

    fun getAvailableLanguagesWithSelected(): Single<Pair<List<Language>, String>> {
        return userRepository.getAvailableLanguages()
            .subscribeOn(Schedulers.io())
    }

    fun changeLanguage(language: String): Single<String> {
        return userRepository.changeLanguage(language)
            .subscribeOn(Schedulers.io())
    }

    fun getSelectedLanguage(): Single<Language> {
        return userRepository.getSelectedLanguage()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeVotables(vararg sources: Observable<out List<Votable>>): Observable<List<Votable>> {
        return Observable.combineLatest(sources) { combined ->
            combined.map { it as List<Votable> }
                .flatten()
        }
    }

    fun observeVotes(): Observable<BigDecimal> = votesDataSource.observeVotes()
        .map(::BigDecimal)
}