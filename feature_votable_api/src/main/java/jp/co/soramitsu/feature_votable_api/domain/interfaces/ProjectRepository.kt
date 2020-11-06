package jp.co.soramitsu.feature_votable_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_votable_api.domain.model.project.Project
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectDetails
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import java.math.BigDecimal

interface ProjectRepository {

    fun getLastVotesFromCache(): Single<BigDecimal>

    fun getProjectById(projectId: String): Observable<ProjectDetails>

    fun updateProject(projectId: String): Completable

    fun observeOpenedProjects(): Observable<List<Project>>

    fun syncOpenedProjects(refreshing: Boolean): Single<Int>

    fun observeVotedProjects(): Observable<List<Project>>

    fun syncVotedProjects(refreshing: Boolean): Single<Int>

    fun observeFavouriteProjects(): Observable<List<Project>>

    fun syncFavoriteProjects(refreshing: Boolean): Single<Int>

    fun observeFinishedProjects(): Observable<List<Project>>

    fun syncFinishedProjects(refreshing: Boolean): Single<Int>

    fun getVotesHistory(count: Int, offset: Int, updateCached: Boolean): Single<List<VotesHistory>>

    fun voteForProject(projectId: String, voteCount: Long): Completable

    fun addProjectToFavorites(projectId: String): Completable

    fun removeProjectFromFavorites(projectId: String): Completable
}