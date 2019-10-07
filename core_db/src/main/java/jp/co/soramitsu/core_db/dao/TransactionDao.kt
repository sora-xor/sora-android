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
import io.reactivex.Single
import jp.co.soramitsu.core_db.converters.TransactionStatusConverter
import jp.co.soramitsu.core_db.converters.TransactionTypeConverter
import jp.co.soramitsu.core_db.model.TransactionLocal

@Dao
@TypeConverters(TransactionStatusConverter::class, TransactionTypeConverter::class)
abstract class TransactionDao {

    @Query("DELETE FROM transactions")
    abstract fun clearTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transaction: TransactionLocal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transactions: List<TransactionLocal>)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    abstract fun getTransactions(): Single<List<TransactionLocal>>
}