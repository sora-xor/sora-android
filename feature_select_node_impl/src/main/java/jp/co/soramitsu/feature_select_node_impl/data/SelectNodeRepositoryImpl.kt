/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.data

import androidx.room.withTransaction
import javax.inject.Inject
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.feature_select_node_impl.compareWithUrl
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SelectNodeRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val converter: NodeConverter,
    private val calls: SubstrateCalls,
    private val soraConfigManager: SoraConfigManager,
) : SelectNodeRepository {

    override suspend fun fetchDefaultNodes(): List<ChainNode> =
        db.withTransaction {
            val selectedNodeAddress =
                db.nodeDao().getSelectedNode()?.address ?: FlavorOptionsProvider.wsHostUrl
            val customNodes = db.nodeDao().getNodes().filter { !it.isDefault }
            val result = runCatching {
                soraConfigManager.getNodes()
                    .map { nodeInfo ->
                        NodeLocal(
                            address = "${nodeInfo.address}/",
                            chain = nodeInfo.chain,
                            name = nodeInfo.name,
                            isDefault = true,
                            isSelected = selectedNodeAddress.compareWithUrl(nodeInfo.address)
                        )
                    }
            }
                .onSuccess { list ->
                    val nodes = list + customNodes
                    val noneSelected = nodes.none { it.isSelected }
                    db.nodeDao().clearTable()
                    db.nodeDao().insertNodes(nodes)
                    if (noneSelected) {
                        nodes.getOrNull(0)?.address?.let { db.nodeDao().selectNode(it) }
                    }
                }

            return@withTransaction result.getOrNull()
                ?.map {
                    converter.convert(it)
                }
                ?: emptyList()
        }

    override fun getNodes(): Flow<List<ChainNode>> =
        db.nodeDao().flowNodes()
            .map {
                it.map { nodeLocal -> converter.convert(nodeLocal) }
            }

    override fun getSelectedNode(): Flow<ChainNode?> {
        return db.nodeDao().flowSelectedNode()
            .map { nodeLocal -> nodeLocal?.let { converter.convert(nodeLocal) } }
    }

    override suspend fun selectNode(selectedNode: ChainNode) {
        db.withTransaction {
            if (db.nodeDao().getSelectedNode()?.address == selectedNode.address) {
                return@withTransaction
            }
            db.nodeDao().resetSelected()
            db.nodeDao().selectNode(selectedNode.address)
        }
    }

    override suspend fun addCustomNode(node: ChainNode) {
        db.nodeDao().insertNode(converter.convert(node))
    }

    override suspend fun updateCustomNode(previousAddress: String, node: ChainNode) {
        db.nodeDao().updateNode(
            oldAddress = previousAddress,
            newName = node.name,
            newAddress = node.address
        )
    }

    override suspend fun getBlockHash(): String {
        return calls.getBlockHash()
    }

    override suspend fun deleteNode(url: String) {
        db.nodeDao().deleteNode(url)
    }
}
