/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.SoraCardInfoLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface SoraCardDao {

    @Query("select * from soraCard where :id = id")
    fun observeSoraCardInfo(id: String): Flow<SoraCardInfoLocal?>

    @Query("select * from soraCard where :id = id")
    suspend fun getSoraCardInfo(id: String): SoraCardInfoLocal?

    @Query("update soraCard set kycStatus=:kycStatus where :id = id")
    suspend fun updateKycStatus(id: String, kycStatus: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(soraCardInfoLocal: SoraCardInfoLocal)

    @Query("delete from soraCard")
    suspend fun clearTable()
}
