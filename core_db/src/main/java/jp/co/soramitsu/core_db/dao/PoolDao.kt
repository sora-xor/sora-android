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
import java.math.BigDecimal

@Dao
interface PoolDao {

    @Query("DELETE FROM pools")
    suspend fun clearTable()

    @Query("SELECT * FROM pools")
    fun getPools(): Flow<List<PoolLocal>>

    @Query("SELECT * FROM pools WHERE assetId = :assetId")
    fun getPool(assetId: String): Flow<PoolLocal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pools: List<PoolLocal>)

    @Query(
        "UPDATE pools SET " +
            "reservesFirst = :reservesFirst," +
            "reservesSecond = :reservesSecond," +
            "totalIssuance = :totalIssuance," +
            "strategicBonusApy = :strategicBonusApy," +
            "poolProvidersBalance = :poolProvidersBalance" +
            " WHERE assetId = :assetId"
    )
    suspend fun updatePool(
        assetId: String,
        reservesFirst: BigDecimal,
        reservesSecond: BigDecimal,
        totalIssuance: BigDecimal,
        strategicBonusApy: BigDecimal?,
        poolProvidersBalance: BigDecimal
    )
}
