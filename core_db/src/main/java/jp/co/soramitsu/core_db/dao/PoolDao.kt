/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.math.BigDecimal
import jp.co.soramitsu.core_db.model.BasePoolWithTokenLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.PoolLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface PoolDao {

    @Query("select * from poolBaseTokens left join tokens on poolBaseTokens.tokenId = tokens.id")
    suspend fun getPoolBaseTokens(): List<BasePoolWithTokenLocal>

    @Query("select * from poolBaseTokens where tokenId = :tokenId")
    suspend fun getPoolBaseToken(tokenId: String): PoolBaseTokenLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoolBaseTokens(tokens: List<PoolBaseTokenLocal>)

    @Query("delete from poolBaseTokens")
    suspend fun clearPoolBaseTokens()

    @Query("DELETE FROM pools where accountAddress = :curAccount")
    suspend fun clearTable(curAccount: String)

    @Query("SELECT * FROM pools where accountAddress = :accountAddress order by pools.sortOrder")
    fun getPools(accountAddress: String): Flow<List<PoolLocal>>

    @Query("SELECT * FROM pools where accountAddress = :accountAddress order by pools.sortOrder")
    suspend fun getPoolsList(accountAddress: String): List<PoolLocal>

    @Query("SELECT * FROM pools WHERE assetId = :assetId and assetIdBase = :baseTokenId and accountAddress = :accountAddress")
    fun getPool(assetId: String, baseTokenId: String, accountAddress: String): Flow<PoolLocal?>

    @Query("SELECT * FROM pools WHERE assetId = :assetId and assetIdBase = :baseTokenId and accountAddress = :accountAddress")
    suspend fun getPoolOf(assetId: String, baseTokenId: String, accountAddress: String): PoolLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pools: List<PoolLocal>)

    @Query(
        """
        update pools set strategicBonusApy = :sbapy where reservesAccount = :reservesAccount and accountAddress = :curAccount
    """
    )
    suspend fun updateSbApyByReservesAccount(sbapy: BigDecimal?, reservesAccount: String, curAccount: String)

    @Query(
        """
        update pools set favorite = 1 where assetIdBase = :baseId and assetId = :secondId and accountAddress = :address
    """
    )
    suspend fun poolFavoriteOn(baseId: String, secondId: String, address: String)

    @Query(
        """
        update pools set favorite = 0 where assetIdBase = :baseId and assetId = :secondId and accountAddress = :address
    """
    )
    suspend fun poolFavoriteOff(baseId: String, secondId: String, address: String)

    @Query("UPDATE pools SET sortOrder = :sortOrder WHERE assetIdBase = :baseAssetId and assetId = :secondAssetId and accountAddress = :address")
    fun updatePoolPosition(baseAssetId: String, secondAssetId: String, sortOrder: Int, address: String)
}
