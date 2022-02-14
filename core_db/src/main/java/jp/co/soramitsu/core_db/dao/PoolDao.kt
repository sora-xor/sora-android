/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.PoolLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface PoolDao {

    @Query("DELETE FROM pools")
    suspend fun clearTable()

    @Query("SELECT * FROM pools")
    fun getPools(): Flow<List<PoolLocal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pools: List<PoolLocal>)
}
