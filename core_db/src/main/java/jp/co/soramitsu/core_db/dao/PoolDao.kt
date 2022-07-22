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

    @Query("SELECT * FROM pools where accountAddress = :accountAddress")
    fun getPools(accountAddress: String): Flow<List<PoolLocal>>

    @Query("SELECT * FROM pools WHERE assetId = :assetId and accountAddress = :accountAddress")
    fun getPool(assetId: String, accountAddress: String): Flow<PoolLocal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pools: List<PoolLocal>)

    @Query(
        "UPDATE pools SET " +
            "reservesFirst = :reservesFirst," +
            "reservesSecond = :reservesSecond," +
            "totalIssuance = :totalIssuance," +
            "strategicBonusApy = :strategicBonusApy," +
            "poolProvidersBalance = :poolProvidersBalance" +
            " WHERE assetId = :assetId and accountAddress = :accountAddress"
    )
    suspend fun updatePool(
        assetId: String,
        accountAddress: String,
        reservesFirst: BigDecimal,
        reservesSecond: BigDecimal,
        totalIssuance: BigDecimal,
        strategicBonusApy: BigDecimal?,
        poolProvidersBalance: BigDecimal
    )
}
