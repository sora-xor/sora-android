/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import io.reactivex.Maybe
import io.reactivex.Single
import jp.co.soramitsu.core_db.converters.WithdrawTransactionStatusConverter
import jp.co.soramitsu.core_db.model.WithdrawTransactionLocal

@Dao
@TypeConverters(WithdrawTransactionStatusConverter::class)
abstract class WithdrawTransactionDao {

    @Query("DELETE FROM withdraw_transactions")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transaction: WithdrawTransactionLocal): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertWithoutReplacing(transactions: List<WithdrawTransactionLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transactions: List<WithdrawTransactionLocal>)

    @Query("UPDATE withdraw_transactions SET status = :newStatus WHERE intentTxHash = :txHash")
    abstract fun updateStatus(txHash: String, newStatus: WithdrawTransactionLocal.Status)

    @Query("UPDATE withdraw_transactions SET confirmTxHash = :confirmTxHash WHERE intentTxHash = :txHash")
    abstract fun updateConfirmTxHash(txHash: String, confirmTxHash: String)

    @Query("SELECT * FROM withdraw_transactions WHERE intentTxHash == :txHash")
    abstract fun getTransactionByIntentHash(txHash: String): WithdrawTransactionLocal?

    @Query("SELECT * FROM withdraw_transactions WHERE status == :newStatus")
    abstract fun getTransactionsByStatus(newStatus: WithdrawTransactionLocal.Status): Single<List<WithdrawTransactionLocal>>

    @Query("SELECT * FROM withdraw_transactions WHERE intentTxHash IN (:txHashes)")
    abstract fun getTransactionsByIntentHashes(txHashes: List<String>): List<WithdrawTransactionLocal>

    @Query("SELECT * FROM withdraw_transactions WHERE status = 6 AND transferPeerId != '' LIMIT 1")
    abstract fun getLastTransactionWaitingToFinish(): Maybe<WithdrawTransactionLocal>

    @Query("UPDATE withdraw_transactions SET transferTxHash = :transferTxHash WHERE intentTxHash = :intentTxHash")
    abstract fun updateTransferHash(intentTxHash: String, transferTxHash: String)
}