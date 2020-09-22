package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import io.reactivex.Maybe
import io.reactivex.Single
import jp.co.soramitsu.core_db.converters.AmountConverter
import jp.co.soramitsu.core_db.converters.DepositTransactionStatusConverter
import jp.co.soramitsu.core_db.model.DepositTransactionLocal

@Dao
@TypeConverters(DepositTransactionStatusConverter::class, AmountConverter::class)
abstract class DepositTransactionDao {

    @Query("DELETE FROM deposit_transactions")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transaction: DepositTransactionLocal): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertWithoutReplacing(transactions: List<DepositTransactionLocal>)

    @Query("UPDATE deposit_transactions SET status = :newStatus WHERE depositTxHash = :depositTxHash")
    abstract fun updateStatus(depositTxHash: String, newStatus: DepositTransactionLocal.Status)

    @Query("SELECT * FROM deposit_transactions WHERE status = 0 LIMIT 1")
    abstract fun getTransactionWaitingToFinish(): Maybe<DepositTransactionLocal>

    @Query("SELECT * FROM deposit_transactions WHERE depositTxHash = :operationId")
    abstract fun getTransaction(operationId: String): Single<DepositTransactionLocal>

    @Query("SELECT * FROM deposit_transactions WHERE depositTxHash IN (:txHashes)")
    abstract fun getTransactionsByDepositHashes(txHashes: List<String>): List<DepositTransactionLocal>
}