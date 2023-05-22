/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalCardsHubDao {

    @Query("SELECT * FROM globalCardsHub WHERE visibility = 1 order by sortOrder")
    fun getGlobalCardsHubVisible(): Flow<List<GlobalCardHubLocal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cards: List<GlobalCardHubLocal>)

    @Query("select count(*) from globalCardsHub")
    suspend fun count(): Int

    @Query("UPDATE globalCardsHub SET visibility = :visibility WHERE cardId = :cardId")
    suspend fun updateCardVisibility(cardId: String, visibility: Boolean)

    @Query("DELETE FROM globalCardsHub")
    suspend fun clearTable()
}
