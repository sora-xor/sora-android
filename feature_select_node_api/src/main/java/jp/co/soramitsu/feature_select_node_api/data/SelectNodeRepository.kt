/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_api.data

import jp.co.soramitsu.common.domain.ChainNode
import kotlinx.coroutines.flow.Flow

interface SelectNodeRepository {

    suspend fun fetchDefaultNodes(): List<ChainNode>

    fun getNodes(): Flow<List<ChainNode>>

    fun getSelectedNode(): Flow<ChainNode?>

    suspend fun selectNode(selectedNode: ChainNode)

    suspend fun addCustomNode(node: ChainNode)

    suspend fun updateCustomNode(previousAddress: String, node: ChainNode)

    suspend fun getBlockHash(): String

    suspend fun deleteNode(url: String)
}
