package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
data class AnnouncementLocal(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val message: String,
    val publicationDate: String
)