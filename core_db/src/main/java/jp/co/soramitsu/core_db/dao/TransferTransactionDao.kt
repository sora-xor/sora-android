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
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.converters.TransactionStatusConverter
import jp.co.soramitsu.core_db.converters.TransactionTypeConverter
import jp.co.soramitsu.core_db.model.TransferTransactionLocal

@Dao
@TypeConverters(TransactionStatusConverter::class, TransactionTypeConverter::class)
abstract class TransferTransactionDao {

    @Query("DELETE FROM transfer_transactions")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transaction: TransferTransactionLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transactions: List<TransferTransactionLocal>)

    @Query("SELECT * FROM transfer_transactions ORDER BY timestamp DESC")
    abstract fun getTransactions(): Observable<List<TransferTransactionLocal>>

    @Query("SELECT * FROM transfer_transactions WHERE txHash == :txHash")
    abstract fun getTransactionByHash(txHash: String): TransferTransactionLocal

    @Query("SELECT * FROM transfer_transactions WHERE assetId IS 'xor_erc20#sora' AND status == 0")
    abstract fun getPendingEthereumTransactions(): Single<List<TransferTransactionLocal>>

    @Query("UPDATE transfer_transactions SET status = :newStatus WHERE txHash = :txHash")
    abstract fun updateStatus(txHash: String, newStatus: TransferTransactionLocal.Status)
}