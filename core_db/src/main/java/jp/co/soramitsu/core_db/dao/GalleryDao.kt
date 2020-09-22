package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.GalleryItemTypeConverter
import jp.co.soramitsu.core_db.model.GalleryItemLocal

@Dao
@TypeConverters(GalleryItemTypeConverter::class)
abstract class GalleryDao {

    @Query("DELETE FROM project_gallery")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(gallery: GalleryItemLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(gallery: List<GalleryItemLocal>)

    @Query("DELETE FROM project_gallery WHERE projectId = :projectId")
    abstract fun removeByProjectId(projectId: String)
}