package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.GalleryItemTypeConverter

@Entity(tableName = "project_gallery")
@TypeConverters(GalleryItemTypeConverter::class)
data class GalleryItemLocal(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val projectId: String,
    val type: GalleryItemTypeLocal,
    val url: String,
    val preview: String,
    val duration: Int
)