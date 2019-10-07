/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_project_api.domain.model.Project
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.feature_project_api.domain.model.VotesHistory
import java.math.BigDecimal

interface ProjectRepository {

    fun getLastVotesFromCache(): Single<BigDecimal>

    fun getProjectById(projectId: String): Observable<ProjectDetails>

    fun updateProject(projectId: String): Completable

    fun getAllProjects(): Observable<List<Project>>

    fun updateAllProjects(): Completable

    fun getVotedProjects(): Observable<List<Project>>

    fun updateVotedProjects(): Completable

    fun getFavoriteProjects(): Observable<List<Project>>

    fun updateFavoriteProjects(): Completable

    fun getFinishedProjects(): Observable<List<Project>>

    fun updateFinishedProjects(): Completable

    fun getVotesHistory(count: Int, offset: Int, updateCached: Boolean): Single<List<VotesHistory>>

    fun getVotes(updateCached: Boolean): Single<BigDecimal>

    fun voteForProject(projectId: String, voteCount: Long): Completable

    fun addProjectToFavorites(projectId: String): Completable

    fun removeProjectFromFavorites(projectId: String): Completable
}