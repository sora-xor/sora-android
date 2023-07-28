/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("select * from allpools")
    fun subscribeBasicPoolsWithToken(): Flow<List<BasicPoolWithTokenFiatLocal>>

    @Query(
        """
        select * from allpools where tokenIdBase=:base and tokenIdTarget=:target
    """
    )
    fun getBasicPool(base: String, target: String): BasicPoolLocal?

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
