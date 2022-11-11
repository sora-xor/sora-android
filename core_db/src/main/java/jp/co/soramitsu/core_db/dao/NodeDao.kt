/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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

    @Query("UPDATE nodes SET name = :newName, address = :newAddress WHERE address = :oldAddress")
    suspend fun updateNode(oldAddress: String, newName: String, newAddress: String)

    @Query("DELETE FROM nodes WHERE address = :address")
    suspend fun deleteNode(address: String)
}
