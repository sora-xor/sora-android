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

    @Query("DELETE FROM extrinsics where localPending == 0 and accountAddress = :accountAddress")
    suspend fun clearNotLocal(accountAddress: String)

    @Query("SELECT COUNT(txHash) from extrinsics")
    suspend fun countAll(): Long

    @Query("SELECT COUNT(txHash) from extrinsics where localPending == 0")
    suspend fun countExtrinsicNotLocal(): Long

    @Query("SELECT * FROM extrinsics where accountAddress = :accountAddress ORDER BY timestamp DESC")
    fun getExtrinsicPaging(accountAddress: String): PagingSource<Int, ExtrinsicLocal>

    @Query(
        """
        SELECT * FROM extrinsics inner join extrinsic_params 
        on extrinsics.txHash = extrinsic_params.extrinsicId 
        where extrinsics.accountAddress = :accountAddress and 
        (extrinsics.type = 0 and extrinsic_params.paramValue = :assetId and (extrinsic_params.paramName = 'tokenId'  or extrinsic_params.paramName = 'token2Id')) or 
        (extrinsics.type = 1 and extrinsic_params.paramValue = :assetId and extrinsic_params.paramName = 'tokenId') or 
        (extrinsics.type = 2 and extrinsic_params.paramValue = :assetId and (extrinsic_params.paramName = 'tokenId' or extrinsic_params.paramName = 'token2Id'))
        """
    )
    fun getExtrinsicPaging(
        accountAddress: String,
        assetId: String
    ): PagingSource<Int, ExtrinsicLocal>

    @Query("SELECT * FROM extrinsics WHERE txHash == :txHash")
    suspend fun getExtrinsic(txHash: String): ExtrinsicLocal

    @Query("SELECT * from extrinsic_params where extrinsicId == :txHash")
    suspend fun getParamsOfExtrinsic(txHash: String): List<ExtrinsicParamLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transactions: List<ExtrinsicLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParams(transactions: List<ExtrinsicParamLocal>)
}
