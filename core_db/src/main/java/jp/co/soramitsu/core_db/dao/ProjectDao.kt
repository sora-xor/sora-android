/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import io.reactivex.Observable
import jp.co.soramitsu.core_db.converters.BigDecimalConverter
import jp.co.soramitsu.core_db.converters.ProjectStatusConverter
import jp.co.soramitsu.core_db.converters.ProjectUrlConverter
import jp.co.soramitsu.core_db.model.ProjectLocal
import jp.co.soramitsu.core_db.model.ProjectStatusLocal

@Dao
@TypeConverters(
    ProjectStatusConverter::class,
    ProjectUrlConverter::class,
    BigDecimalConverter::class
)
abstract class ProjectDao {

    @Query("DELETE FROM projects")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(project: ProjectLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(projects: List<ProjectLocal>)

    @Query("SELECT * FROM projects WHERE id = :projectId")
    abstract fun getProjectById(projectId: String): ProjectLocal

    @Query("SELECT * FROM projects WHERE isFavorite = 1")
    abstract fun getFavoriteProjects(): Observable<List<ProjectLocal>>

    @Query("SELECT * FROM projects WHERE status = :status")
    abstract fun getProjectsByStatus(status: ProjectStatusLocal): Observable<List<ProjectLocal>>

    @Query("SELECT * FROM projects WHERE status = :status OR status = :secondStatus")
    abstract fun getProjectsByStatuses(
        status: ProjectStatusLocal,
        secondStatus: ProjectStatusLocal
    ): Observable<List<ProjectLocal>>

    @Query("SELECT * FROM projects WHERE votes > 0")
    abstract fun getVotedProjects(): Observable<List<ProjectLocal>>

    @Query("UPDATE projects SET isFavorite = 1, favoriteCount = favoriteCount + 1 WHERE id = :projectId")
    abstract fun addProjectToFavorites(projectId: String)

    @Query("UPDATE projects SET isFavorite = 0, favoriteCount = favoriteCount - 1 WHERE id = :projectId")
    abstract fun removeProjectFromFavorites(projectId: String)

    @Query("UPDATE projects SET votes = votes + :voteCount, fundingCurrent = fundingCurrent + :voteCount WHERE id = :projectId")
    abstract fun addVotesToProject(projectId: String, voteCount: Long)

    @Query("DELETE FROM projects WHERE status = :status")
    abstract fun clearProjectsByStatus(status: ProjectStatusLocal)

    @Query("DELETE FROM projects WHERE votes > 0")
    abstract fun clearVotedProjects()

    @Query("DELETE FROM projects WHERE isFavorite = 1")
    abstract fun clearFavoritesProjects()

    @Query("DELETE FROM projects WHERE status = :status OR status = :secondStatus")
    abstract fun clearProjectsByStatuses(
        status: ProjectStatusLocal,
        secondStatus: ProjectStatusLocal
    )

    @Query("UPDATE projects SET status = :projectStatusLocal WHERE id = :projectId")
    abstract fun updateProjectStatus(projectId: String, projectStatusLocal: ProjectStatusLocal)
}