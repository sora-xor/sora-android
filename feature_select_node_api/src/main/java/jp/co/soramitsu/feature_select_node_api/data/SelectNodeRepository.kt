/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_api.data

import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import kotlinx.coroutines.flow.Flow

interface SelectNodeRepository {

    suspend fun fetchDefaultNodes(): List<Node>

    fun getNodes(): Flow<List<Node>>

    fun getSelectedNode(): Flow<Node?>

    suspend fun selectNode(selectedNode: Node)

    suspend fun addCustomNode(node: Node)

    suspend fun updateCustomNode(previousAddress: String, node: Node)

    suspend fun getBlockHash(): String

    suspend fun deleteNode(url: String)
}
