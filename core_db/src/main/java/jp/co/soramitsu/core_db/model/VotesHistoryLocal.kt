package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.BigDecimalConverter
import java.math.BigDecimal

@Entity(tableName = "votes_history")
@TypeConverters(BigDecimalConverter::class)
data class VotesHistoryLocal(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val message: String,
    val timestamp: String,
    val votes: BigDecimal
)