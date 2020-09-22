package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.AmountConverter
import jp.co.soramitsu.core_db.converters.GasLimitConverter
import jp.co.soramitsu.core_db.converters.WithdrawTransactionStatusConverter
import java.math.BigDecimal
import java.math.BigInteger

@Entity(tableName = "withdraw_transactions")
@TypeConverters(WithdrawTransactionStatusConverter::class, AmountConverter::class, GasLimitConverter::class)
data class WithdrawTransactionLocal(
    @PrimaryKey val intentTxHash: String,
    val confirmTxHash: String,
    val transferTxHash: String,
    val status: Status,
    val details: String,
    val peerName: String,
    val withdrawAmount: BigDecimal,
    val transferAmount: BigDecimal,
    val timestamp: Long,
    val peerId: String?,
    val transferPeerId: String?,
    val reason: String?,
    val intentFee: BigDecimal,
    val minerFeeInEth: BigDecimal,
    val gasLimit: BigInteger,
    val gasPrice: BigInteger
) {
    enum class Status {
        INTENT_STARTED,
        INTENT_PENDING,
        INTENT_COMPLETED,
        INTENT_FAILED,
        CONFIRM_PENDING,
        CONFIRM_COMPLETED,
        CONFIRM_FAILED,
        TRANSFER_PENDING,
        TRANSFER_FAILED,
        TRANSFER_COMPLETED
    }
}