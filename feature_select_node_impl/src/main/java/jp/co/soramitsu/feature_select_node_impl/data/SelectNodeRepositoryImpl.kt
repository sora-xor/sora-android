/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.data

import androidx.room.withTransaction
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import jp.co.soramitsu.feature_select_node_impl.compareWithUrl
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.xnetworking.sorawallet.envbuilder.SoraEnvBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class SelectNodeRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val soraEnvBuilder: SoraEnvBuilder,
    private val converter: NodeConverter,
    private val calls: SubstrateCalls
) : SelectNodeRepository {

    override suspend fun fetchDefaultNodes(): List<Node> =
        db.withTransaction {
            val selectedNodeAddress = db.nodeDao().getSelectedNode()?.address ?: FlavorOptionsProvider.wsHostUrl
            val customNodes = db.nodeDao().getNodes().filter { !it.isDefault }
            val result = runCatching {
                soraEnvBuilder.getSoraEnv().nodes
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
                .onSuccess {
                    db.nodeDao().clearTable()
                    db.nodeDao().insertNodes(it + customNodes)
                }

            return@withTransaction result.getOrNull()
                ?.map {
                    converter.convert(it)
                }
                ?: emptyList()
        }

    override fun getNodes(): Flow<List<Node>> =
        db.nodeDao().flowNodes()
            .map {
                it.map { nodeLocal -> converter.convert(nodeLocal) }
            }

    override fun getSelectedNode(): Flow<Node?> {
        return db.nodeDao().flowSelectedNode()
            .map { nodeLocal -> nodeLocal?.let { converter.convert(nodeLocal) } }
    }

    override suspend fun selectNode(selectedNode: Node) {
        db.withTransaction {
            if (db.nodeDao().getSelectedNode()?.address == selectedNode.address) {
                return@withTransaction
            }

            val nodes = db.nodeDao().getNodes().map {
                it.copy(isSelected = selectedNode.address == it.address)
            }
            db.nodeDao().clearTable()
            db.nodeDao().insertNodes(nodes)
        }
    }

    override suspend fun addCustomNode(node: Node) {
        db.nodeDao().insertNode(converter.convert(node))
    }

    override suspend fun updateCustomNode(previousAddress: String, node: Node) {
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
