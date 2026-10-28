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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.GlobalCardHubLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalCardsHubDao {

    @Query("SELECT * FROM globalCardsHub order by sortOrder")
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
