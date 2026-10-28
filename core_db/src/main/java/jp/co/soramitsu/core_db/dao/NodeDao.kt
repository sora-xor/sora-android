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
import jp.co.soramitsu.core_db.model.NodeLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Query("DELETE FROM nodes")
    suspend fun clearTable()

    @Query("SELECT * FROM nodes")
    fun flowNodes(): Flow<List<NodeLocal>>

    @Query("SELECT * FROM nodes")
    fun getNodes(): List<NodeLocal>

    @Query("SELECT * FROM nodes WHERE isSelected = 1")
    fun getSelectedNode(): NodeLocal?

    @Query("SELECT * FROM nodes WHERE isSelected = 1")
    fun flowSelectedNode(): Flow<NodeLocal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NodeLocal)

    @Query("update nodes set isSelected = 0")
    suspend fun resetSelected()

    @Query("update nodes set isSelected = 1 where address = :address")
    suspend fun selectNode(address: String)

    @Query("UPDATE nodes SET name = :newName, address = :newAddress WHERE address = :oldAddress")
    suspend fun updateNode(oldAddress: String, newName: String, newAddress: String)

    @Query("DELETE FROM nodes WHERE address = :address")
    suspend fun deleteNode(address: String)
}
