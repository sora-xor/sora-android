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

package jp.co.soramitsu.feature_select_node_impl.data

import androidx.room.withTransaction
import javax.inject.Inject
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.common.network.SoraNodeEndpointPolicy
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.feature_select_node_impl.compareWithUrl
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SelectNodeRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val converter: NodeConverter,
    private val calls: SubstrateCalls,
    private val soraConfigManager: SoraConfigManager,
) : SelectNodeRepository {

    override suspend fun fetchDefaultNodes(): List<ChainNode> {
        // Remote configuration can consume its full transport deadline. Keep that wait outside
        // Room, then re-read the current selection and custom nodes in the publishing transaction.
        val remoteNodes = runCatching {
            soraConfigManager.getNodes()
        }.getOrNull() ?: return emptyList()

        return db.withTransaction {
            val selectedNodeAddress =
                db.nodeDao().getSelectedNode()?.address?.takeIf(SoraNodeEndpointPolicy::isAllowed)
                    ?: FlavorOptionsProvider.wsHostUrl
            val customNodes = db.nodeDao().getNodes().filter { !it.isDefault }.map {
                if (SoraNodeEndpointPolicy.isAllowed(it.address)) it else it.copy(isSelected = false)
            }
            val defaultNodes = remoteNodes.filter { SoraNodeEndpointPolicy.isAllowed(it.address) }.map { nodeInfo ->
                NodeLocal(
                    address = "${nodeInfo.address.trimEnd('/')}/",
                    chain = nodeInfo.chain,
                    name = nodeInfo.name,
                    isDefault = true,
                    isSelected = selectedNodeAddress.compareWithUrl(nodeInfo.address)
                )
            }
            val eligibleDefaults = defaultNodes.ifEmpty {
                listOf(NodeLocal(
                    address = FlavorOptionsProvider.wsHostUrl, chain = "SORA", name = "SORA",
                    isDefault = true, isSelected = selectedNodeAddress.compareWithUrl(FlavorOptionsProvider.wsHostUrl),
                ))
            }
            val nodes = eligibleDefaults + customNodes
            val noneSelected = nodes.none { it.isSelected }
            db.nodeDao().clearTable()
            db.nodeDao().insertNodes(nodes)
            if (noneSelected) {
                nodes.getOrNull(0)?.address?.let { db.nodeDao().selectNode(it) }
            }

            eligibleDefaults.map {
                converter.convert(it)
            }
        }
    }

    override fun getNodes(): Flow<List<ChainNode>> =
        db.nodeDao().flowNodes()
            .map {
                it.filter { nodeLocal -> SoraNodeEndpointPolicy.isAllowed(nodeLocal.address) }
                    .map { nodeLocal -> converter.convert(nodeLocal) }
            }

    override fun getSelectedNode(): Flow<ChainNode?> {
        return db.nodeDao().flowSelectedNode()
            .map { nodeLocal -> nodeLocal?.takeIf { SoraNodeEndpointPolicy.isAllowed(it.address) }?.let { converter.convert(it) } }
    }

    override suspend fun selectNode(selectedNode: ChainNode) {
        require(SoraNodeEndpointPolicy.isAllowed(selectedNode.address)) { "SORA_NODE_ENDPOINT_BLOCKED" }
        db.withTransaction {
            if (db.nodeDao().getSelectedNode()?.address == selectedNode.address) {
                return@withTransaction
            }
            db.nodeDao().resetSelected()
            db.nodeDao().selectNode(selectedNode.address)
        }
    }

    override suspend fun addCustomNode(node: ChainNode) {
        require(SoraNodeEndpointPolicy.isAllowed(node.address)) { "SORA_NODE_ENDPOINT_BLOCKED" }
        db.nodeDao().insertNode(converter.convert(node))
    }

    override suspend fun updateCustomNode(previousAddress: String, node: ChainNode) {
        require(SoraNodeEndpointPolicy.isAllowed(node.address)) { "SORA_NODE_ENDPOINT_BLOCKED" }
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
