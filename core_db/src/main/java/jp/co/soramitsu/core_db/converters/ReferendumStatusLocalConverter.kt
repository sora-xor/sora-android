package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import jp.co.soramitsu.common.util.ext.inverseMap
import jp.co.soramitsu.core_db.model.ReferendumStatusLocal

private val MAPPER = mapOf(
    ReferendumStatusLocal.CREATED to 0,
    ReferendumStatusLocal.ACCEPTED to 1,
    ReferendumStatusLocal.REJECTED to 2
)

private val INVERSE_MAPPER = MAPPER.inverseMap()

@Suppress("MapGetWithNotNullAssertionOperator")
class ReferendumStatusLocalConverter {
    @TypeConverter
    fun fromType(type: ReferendumStatusLocal): Int {
        return MAPPER[type]!!
    }

    @TypeConverter
    fun toType(state: Int): ReferendumStatusLocal {
        return INVERSE_MAPPER[state]!!
    }
}