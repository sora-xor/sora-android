/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import jp.co.soramitsu.core_db.model.BasePoolWithTokenLocal
import jp.co.soramitsu.core_db.model.BasicPoolLocal
import jp.co.soramitsu.core_db.model.BasicPoolWithTokenFiatLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocal
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocalNullable
import jp.co.soramitsu.core_db.model.UserPoolLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface PoolDao {

    companion object {
        private const val userPoolJoinBasic = """
            SELECT * FROM userpools left join allpools on userpools.userTokenIdBase=allpools.tokenIdBase and userpools.userTokenIdTarget=allpools.tokenIdTarget
        """
    }

    @Transaction
    @Query("select * from allpools")
    fun subscribeBasicPoolsWithToken(): Flow<List<BasicPoolWithTokenFiatLocal>>

    @Query(
        """
        select * from allpools where tokenIdBase=:base and tokenIdTarget=:target
    """
    )
    suspend fun getBasicPool(base: String, target: String): BasicPoolLocal?

    @Query("select * from allpools")
    suspend fun getBasicPools(): List<BasicPoolLocal>

    @Query("select * from poolBaseTokens left join tokens on poolBaseTokens.tokenId = tokens.id")
    suspend fun getPoolBaseTokens(): List<BasePoolWithTokenLocal>

    @Query("select * from poolBaseTokens where tokenId = :tokenId")
    suspend fun getPoolBaseToken(tokenId: String): PoolBaseTokenLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoolBaseTokens(tokens: List<PoolBaseTokenLocal>)

    @Query("delete from poolBaseTokens")
    suspend fun clearPoolBaseTokens()

    @Query("DELETE FROM userpools where accountAddress = :curAccount")
    suspend fun clearTable(curAccount: String)

    @Query("DELETE FROM allpools")
    suspend fun clearBasicTable()

    @Query(
        """
        $userPoolJoinBasic where userpools.accountAddress = :accountAddress order by userpools.sortOrder
    """
    )
    fun subscribePoolsList(accountAddress: String): Flow<List<UserPoolJoinedLocal>>

    @Query(
        """
        $userPoolJoinBasic where userpools.accountAddress = :accountAddress order by userpools.sortOrder 
    """
    )
    suspend fun getPoolsList(accountAddress: String): List<UserPoolJoinedLocal>

    @Query(
        """
        select * from allpools left join userpools on 
        allpools.tokenIdBase=userpools.userTokenIdBase and allpools.tokenIdTarget=userpools.userTokenIdTarget 
        where allpools.tokenIdBase=:baseTokenId and allpools.tokenIdTarget=:assetId
    """
    )
    fun subscribePool(assetId: String, baseTokenId: String): Flow<List<UserPoolJoinedLocalNullable>>

    @Delete
    suspend fun deleteBasicPools(p: List<BasicPoolLocal>)

    @Upsert()
    suspend fun insertBasicPools(pools: List<BasicPoolLocal>)

    @Upsert()
    suspend fun insertUserPools(pools: List<UserPoolLocal>)

    @Query(
        """
        update userpools set favorite = 1 where userTokenIdBase = :baseId and userTokenIdTarget = :secondId and accountAddress = :address
    """
    )
    suspend fun poolFavoriteOn(baseId: String, secondId: String, address: String)

    @Query(
        """
        update userpools set favorite = 0 where userTokenIdBase = :baseId and userTokenIdTarget = :secondId and accountAddress = :address
    """
    )
    suspend fun poolFavoriteOff(baseId: String, secondId: String, address: String)

    @Query("UPDATE userpools SET sortOrder = :sortOrder WHERE userTokenIdBase = :baseAssetId and userTokenIdTarget = :secondAssetId and accountAddress = :address")
    fun updatePoolPosition(baseAssetId: String, secondAssetId: String, sortOrder: Int, address: String)
}
