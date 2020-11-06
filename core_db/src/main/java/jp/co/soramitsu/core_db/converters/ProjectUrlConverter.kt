package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import java.net.URL

class ProjectUrlConverter {

    @TypeConverter
    fun fromType(type: URL): String {
        return type.toString()
    }

    @TypeConverter
    fun toType(state: String): URL {
        return URL(state)
    }
}