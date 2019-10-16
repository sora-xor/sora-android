/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ProjectStatusLocal
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectDatasource
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_project_api.domain.model.Project
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.feature_project_api.domain.model.VotesHistory
import jp.co.soramitsu.feature_project_impl.data.mappers.mapGalleryToGalleryLocal
import jp.co.soramitsu.feature_project_impl.data.mappers.mapProjectDetailsLocalToProjectDetails
import jp.co.soramitsu.feature_project_impl.data.mappers.mapProjectDetailsRemoteToProject
import jp.co.soramitsu.feature_project_impl.data.mappers.mapProjectDetailsToProjectDetailsLocal
import jp.co.soramitsu.feature_project_impl.data.mappers.mapProjectLocalToProject
import jp.co.soramitsu.feature_project_impl.data.mappers.mapProjectRemoteToProject
import jp.co.soramitsu.feature_project_impl.data.mappers.mapProjectToProjectLocal
import jp.co.soramitsu.feature_project_impl.data.mappers.mapVotesHistoryLocalToVotesHistory
import jp.co.soramitsu.feature_project_impl.data.mappers.mapVotesHistoryRemoteToVotesHistory
import jp.co.soramitsu.feature_project_impl.data.mappers.mapVotesHistoryToVotesHistoryLocal
import jp.co.soramitsu.feature_project_impl.data.network.ProjectNetworkApi
import java.math.BigDecimal
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val api: ProjectNetworkApi,
    private val datasource: ProjectDatasource,
    private val db: AppDatabase
) : ProjectRepository {

    override fun getVotesHistory(count: Int, offset: Int, updateCached: Boolean): Single<List<VotesHistory>> {
        return if (updateCached) {
            getVotesHistoryRemote(count, offset)
        } else {
            db.votesHistoryDao().getVotesHistory()
                .flatMap {
                    if (it.isEmpty()) {
                        getVotesHistoryRemote(count, offset)
                    } else {
                        Single.just(it)
                            .map { it.map { mapVotesHistoryLocalToVotesHistory(it) } }
                    }
                }
        }
    }

    private fun getVotesHistoryRemote(count: Int, offset: Int): Single<List<VotesHistory>> {
        return api.getVotesHistory(count, offset)
            .map { it.votesHistoryRemote.map { mapVotesHistoryRemoteToVotesHistory(it) } }
            .doOnSuccess {
                if (offset == 0) {
                    db.votesHistoryDao().clearTable()
                    db.votesHistoryDao().insert(it.map { mapVotesHistoryToVotesHistoryLocal(it) })
                }
            }
    }

    override fun getAllProjects(): Observable<List<Project>> {
        return db.projectDao().getProjectsByStatus(ProjectStatusLocal.OPEN)
            .map { it.map { mapProjectLocalToProject(it) }.sortedByDescending { it.statusUpdateTime } }
    }

    override fun updateAllProjects(): Completable {
        return api.getAllProjects(50, 0)
            .map { it.projects.map { mapProjectRemoteToProject(it) } }
            .doOnSuccess {
                db.runInTransaction {
                    db.projectDao().clearProjectsByStatus(ProjectStatusLocal.OPEN)
                    db.runInTransaction { it.forEach { db.projectDao().insert(mapProjectToProjectLocal(it)) } }
                }
            }
            .ignoreElement()
    }

    override fun getVotedProjects(): Observable<List<Project>> {
        return db.projectDao().getVotedProjects()
            .map { it.map { mapProjectLocalToProject(it) }.sortedByDescending { it.statusUpdateTime } }
    }

    override fun updateVotedProjects(): Completable {
        return api.getVotedProjects(50, 0)
            .map { it.projects.map { mapProjectRemoteToProject(it) } }
            .doOnSuccess {
                db.runInTransaction {
                    db.projectDao().clearVotedProjects()
                    db.runInTransaction { it.forEach { db.projectDao().insert(mapProjectToProjectLocal(it)) } }
                }
            }
            .ignoreElement()
    }

    override fun getFavoriteProjects(): Observable<List<Project>> {
        return db.projectDao().getFavoriteProjects()
            .map { it.map { mapProjectLocalToProject(it) }.sortedByDescending { it.statusUpdateTime } }
    }

    override fun updateFavoriteProjects(): Completable {
        return api.getFavoriteProjects(50, 0)
            .map { it.projects.map { mapProjectRemoteToProject(it) } }
            .doOnSuccess {
                db.runInTransaction {
                    db.projectDao().clearFavoritesProjects()
                    db.runInTransaction { it.forEach { db.projectDao().insert(mapProjectToProjectLocal(it)) } }
                }
            }
            .ignoreElement()
    }

    override fun getFinishedProjects(): Observable<List<Project>> {
        return db.projectDao().getProjectsByStatuses(ProjectStatusLocal.COMPLETED, ProjectStatusLocal.FAILED)
            .map { it.map { mapProjectLocalToProject(it) }.sortedByDescending { it.statusUpdateTime } }
    }

    override fun updateFinishedProjects(): Completable {
        return api.getFinishedProjects(50, 0)
            .map { it.projects.map { mapProjectRemoteToProject(it) } }
            .doOnSuccess {
                db.runInTransaction {
                    db.projectDao().clearProjectsByStatuses(ProjectStatusLocal.COMPLETED, ProjectStatusLocal.FAILED)
                    db.runInTransaction { it.forEach { db.projectDao().insert(mapProjectToProjectLocal(it)) } }
                }
            }
            .ignoreElement()
    }

    override fun getProjectById(projectId: String): Observable<ProjectDetails> {
        return db.projectDetailsDao().getProjectById(projectId)
            .map { mapProjectDetailsLocalToProjectDetails(it) }
    }

    override fun updateProject(projectId: String): Completable {
        return api.getProjectDetails(projectId)
            .map { mapProjectDetailsRemoteToProject(it.project) }
            .doOnSuccess { saveProjectDetails(it) }
            .ignoreElement()
    }

    override fun getVotes(updateCached: Boolean): Single<BigDecimal> {
        return if (updateCached) {
            getVotesRemote()
        } else {
            val votesStr = datasource.retrieveVotes()
            if (votesStr.isEmpty()) {
                getVotesRemote()
            } else {
                Single.just(BigDecimal(votesStr))
            }
        }
    }

    private fun getVotesRemote(): Single<BigDecimal> {
        return api.getVotes()
            .doOnSuccess {
                datasource.saveVotes(it.votes.toString())
                it.lastReceivedVotes.let { datasource.saveLastReceivedVotes(it.toString()) }
            }
            .map { it.votes }
    }

    override fun getLastVotesFromCache(): Single<BigDecimal> {
        return Single.fromCallable {
            val lastReceivedVotes = datasource.retrieveLastReceivedVotes()
            BigDecimal(if (lastReceivedVotes.isEmpty()) "-1" else lastReceivedVotes)
        }
    }

    override fun voteForProject(projectId: String, voteCount: Long): Completable {
        return api.voteForProject(projectId, voteCount)
            .doOnSuccess {
                val currentVotes = BigDecimal(datasource.retrieveVotes()).toLong() - voteCount
                datasource.saveVotes(currentVotes.toString())
                db.projectDao().addVotesToProject(projectId, voteCount)
                db.projectDetailsDao().addVotesToProject(projectId, voteCount)
            }
            .doOnSuccess {
                val project = db.projectDao().getProjectById(projectId)
                if (project.fundingCurrent >= project.fundingTarget) {
                    db.projectDao().updateProjectStatus(projectId, ProjectStatusLocal.COMPLETED)
                }
            }
            .ignoreElement()
    }

    override fun addProjectToFavorites(projectId: String): Completable {
        return api.toggleFavoriteProject(projectId)
            .doOnSuccess {
                db.projectDao().addProjectToFavorites(projectId)
                db.projectDetailsDao().addProjectToFavorites(projectId)
            }
            .ignoreElement()
    }

    override fun removeProjectFromFavorites(projectId: String): Completable {
        return api.toggleFavoriteProject(projectId)
            .doOnSuccess {
                db.projectDao().removeProjectFromFavorites(projectId)
                db.projectDetailsDao().removeProjectFromFavorites(projectId)
            }
            .ignoreElement()
    }

    private fun saveProjectDetails(projectDetails: ProjectDetails) {
        db.runInTransaction {
            db.projectDetailsDao().insert(mapProjectDetailsToProjectDetailsLocal(projectDetails))
            db.galleryDao().removeByProjectId(projectDetails.id)
            db.galleryDao().insert(projectDetails.gallery.map { mapGalleryToGalleryLocal(it, projectDetails.id) })
        }
    }

    private fun saveProjects(projects: List<Project>) {
        db.runInTransaction { projects.forEach { db.projectDao().insert(mapProjectToProjectLocal(it)) } }
    }
}