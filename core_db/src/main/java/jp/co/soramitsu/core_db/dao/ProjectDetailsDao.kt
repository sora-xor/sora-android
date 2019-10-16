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
import jp.co.soramitsu.core_db.converters.ProjectStatusConverter
import jp.co.soramitsu.core_db.converters.ProjectUrlConverter
import jp.co.soramitsu.core_db.converters.ProjectVotesConverter
import jp.co.soramitsu.core_db.model.ProjectDetailsLocal
import jp.co.soramitsu.core_db.model.ProjectDetailsWithGalleryLocal

@Dao
@TypeConverters(ProjectStatusConverter::class, ProjectUrlConverter::class, ProjectVotesConverter::class)
abstract class ProjectDetailsDao {

    @Query("DELETE FROM project_details")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(project: ProjectDetailsLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(projects: List<ProjectDetailsLocal>)

    @Query("UPDATE project_details SET isFavorite = 1, favoriteCount = favoriteCount + 1 WHERE id = :projectId")
    abstract fun addProjectToFavorites(projectId: String)

    @Query("UPDATE project_details SET isFavorite = 0, favoriteCount = favoriteCount - 1 WHERE id = :projectId")
    abstract fun removeProjectFromFavorites(projectId: String)

    @Query("UPDATE project_details SET votes = votes + :voteCount, fundingCurrent = fundingCurrent + :voteCount WHERE id = :projectId")
    abstract fun addVotesToProject(projectId: String, voteCount: Long)

    @Query("SELECT * FROM project_details WHERE id = :projectId")
    abstract fun getProjectById(projectId: String): Observable<ProjectDetailsWithGalleryLocal>
}