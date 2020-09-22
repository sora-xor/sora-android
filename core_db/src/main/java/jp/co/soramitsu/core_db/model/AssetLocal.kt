package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.AssetBalanceConverter
import jp.co.soramitsu.core_db.converters.AssetStateConverter
import java.math.BigDecimal

@Entity(tableName = "assets")
@TypeConverters(AssetBalanceConverter::class, AssetStateConverter::class)
data class AssetLocal(
    @PrimaryKey val id: String,
    val assetFirstName: String,
    val assetLastName: String,
    val displayAsset: Boolean,
    val hidingAllowed: Boolean,
    val position: Int,
    val state: State,
    val roundingPrecision: Int,
    val balance: BigDecimal?
) {
    enum class State {
        NORMAL,
        ASSOCIATING,
        ERROR,
        UNKNOWN
    }
}