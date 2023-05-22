/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import jp.co.soramitsu.core_db.model.CardHubLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface CardsHubDao {

    @Query("select * from cardsHub where accountAddress = :address order by sortOrder")
    fun getCardsHub(address: String): Flow<List<CardHubLocal>>

    @Query("select * from cardsHub where accountAddress = :address and visibility = 1 order by sortOrder")
    fun getCardsHubVisible(address: String): Flow<List<CardHubLocal>>

    @Query("UPDATE cardsHub SET collapsed = :collapsed WHERE cardId = :cardId")
    suspend fun updateCardCollapsed(cardId: String, collapsed: Boolean)

    @Insert
    suspend fun insert(cards: List<CardHubLocal>)
}
