/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_project_api.domain.model.Project
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.feature_project_api.domain.model.VotesHistory
import java.math.BigDecimal
import javax.inject.Inject

class MainInteractor @Inject constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val didRepository: DidRepository,
    private val informationRepository: InformationRepository
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
            .zipWith(projectRepository.getLastVotesFromCache(), BiFunction { reputation, lastVotes ->
                Pair(reputation, lastVotes)
            })
    }

    fun updateProject(projectId: String): Completable {
        return projectRepository.updateProject(projectId)
    }

    fun getProjectById(projectId: String): Observable<ProjectDetails> {
        return projectRepository.getProjectById(projectId)
    }

    fun getAllProjects(): Observable<List<Project>> {
        return projectRepository.getAllProjects()
    }

    fun updateAllProjects(): Completable {
        return projectRepository.updateAllProjects()
    }

    fun getVotedProjects(): Observable<List<Project>> {
        return projectRepository.getVotedProjects()
    }

    fun updateVotedProjects(): Completable {
        return projectRepository.updateVotedProjects()
    }

    fun getFavoriteProjects(): Observable<List<Project>> {
        return projectRepository.getFavoriteProjects()
    }

    fun updateFavoriteProjects(): Completable {
        return projectRepository.updateFavoriteProjects()
    }

    fun getCompletedProjects(): Observable<List<Project>> {
        return projectRepository.getFinishedProjects()
    }

    fun updateCompletedProjects(): Completable {
        return projectRepository.updateFinishedProjects()
    }

    fun voteForProject(projectId: String, voteNum: Long): Completable {
        return projectRepository.voteForProject(projectId, voteNum)
    }

    fun getVotes(updateCached: Boolean): Single<BigDecimal> {
        return projectRepository.getVotes(updateCached)
    }

    fun updatePushTokenIfNeeded(): Completable {
        return userRepository.updatePushTokenIfNeeded()
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

    fun getHelpContent(updateCached: Boolean): Single<List<InformationContainer>> {
        return informationRepository.getHelpContent(updateCached)
    }

    fun getReputationContent(updateCached: Boolean): Single<List<InformationContainer>> {
        return informationRepository.getReputationContent(updateCached)
    }

    fun getCurrencies(updateCached: Boolean): Single<List<Currency>> {
        return informationRepository.getCurrencies(updateCached)
    }

    fun getCurrencySelected(): Single<Currency> {
        return informationRepository.getSelectedCurrency()
    }

    fun setCurrencySelected(currency: Currency): Completable {
        return informationRepository.saveSelectedCurrency(currency)
    }

    fun getVotesHistory(updateCached: Boolean, historyOffset: Int, votesPerPage: Int): Single<List<VotesHistory>> {
        return projectRepository.getVotesHistory(votesPerPage, historyOffset, updateCached)
    }

    fun clearUser(): Completable {
        return Completable.fromAction { userRepository.clearUserData() }
    }

    fun getActivityFeed(updateCached: Boolean, activityPerPage: Int, activitiesOffset: Int): Single<List<ActivityFeed>> {
        return userRepository.getActivityFeed(activityPerPage, activitiesOffset, updateCached)
    }

    fun getActivityFeedWithAnnouncement(updateCached: Boolean, activityPerPage: Int, activitiesOffset: Int): Single<List<Any>> {
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
}