package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityFeedLocal(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val type: String,
    val title: String,
    val description: String,
    val votesString: String,
    val issuedAtMillis: Long,
    val iconDrawable: Int,
    val votesRightDrawable: Int
)