package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.core_db.model.ProjectStatusLocal

class ProjectStatusConverter {

    @TypeConverter
    fun fromType(type: ProjectStatusLocal): Int {
        return when (type) {
            ProjectStatusLocal.OPEN -> 0
            ProjectStatusLocal.FAILED -> 1
            ProjectStatusLocal.COMPLETED -> 2
        }
    }

    @TypeConverter
    fun toType(state: Int): ProjectStatusLocal {
        return when (state) {
            0 -> ProjectStatusLocal.OPEN
            1 -> ProjectStatusLocal.FAILED
            else -> ProjectStatusLocal.COMPLETED
        }
    }
}