package jp.co.soramitsu.core_db.converters

import androidx.room.TypeConverter
import java.math.BigDecimal

class AssetBalanceConverter {

    @TypeConverter
    fun fromType(balance: BigDecimal?): String? {
        return balance?.let { it.toString() }
    }

    @TypeConverter
    fun toType(balance: String?): BigDecimal? {
        return balance?.let { BigDecimal(it) }
    }
}