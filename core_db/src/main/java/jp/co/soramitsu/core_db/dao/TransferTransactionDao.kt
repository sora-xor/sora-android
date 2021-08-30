/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import jp.co.soramitsu.core_db.converters.ExtrinsicStatusConverter
import jp.co.soramitsu.core_db.converters.ExtrinsicTypeConverter
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal

@Dao
@TypeConverters(ExtrinsicTypeConverter::class, ExtrinsicStatusConverter::class)
interface TransferTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: ExtrinsicLocal)

    @Query("UPDATE extrinsics SET eventSuccess = :txSuccess WHERE txHash = :txHash")
    suspend fun updateSuccess(txHash: String, txSuccess: Boolean)

    @Query(
        """
            SELECT DISTINCT paramValue FROM extrinsics inner join extrinsic_params 
            WHERE extrinsics.txHash == extrinsic_params.extrinsicId and type == 1 and extrinsic_params.paramName == 'peerId' and (paramValue LIKE '%' || :query || '%')
        """
    )
    suspend fun getContacts(query: String): List<String>

    @Query("DELETE FROM extrinsics")
    suspend fun clearTable()

    @Query("DELETE FROM extrinsics where localPending == 0")
    suspend fun clearNotLocal()

    @Query("SELECT COUNT(txHash) from extrinsics")
    suspend fun countAll(): Long

    @Query("SELECT COUNT(txHash) from extrinsics where type == 1 and localPending == 0 and eventSuccess == 1")
    suspend fun countTransferNotLocalSuccess(): Long

    @Query("SELECT COUNT(txHash) from extrinsics where type == 1 and localPending == 0 and eventSuccess == 0")
    suspend fun countTransferNotLocalError(): Long

    @Query("SELECT COUNT(txHash) from extrinsics where type == 0 and localPending == 0")
    suspend fun countSwapNotLocal(): Long

    @Query("SELECT * FROM extrinsics ORDER BY timestamp DESC")
    fun getExtrinsicPaging(): PagingSource<Int, ExtrinsicLocal>

    @Query("SELECT * FROM extrinsics WHERE txHash == :txHash")
    suspend fun getExtrinsic(txHash: String): ExtrinsicLocal

    @Query("SELECT * from extrinsic_params where extrinsicId == :txHash")
    suspend fun getParamsOfExtrinsic(txHash: String): List<ExtrinsicParamLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transactions: List<ExtrinsicLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParams(transactions: List<ExtrinsicParamLocal>)
}
