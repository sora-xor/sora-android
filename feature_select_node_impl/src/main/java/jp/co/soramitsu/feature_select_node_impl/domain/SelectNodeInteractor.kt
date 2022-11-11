/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.domain

import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class SelectNodeInteractor @Inject constructor(
    private val repository: SelectNodeRepository,
    private val nodeValidator: NodeValidator
) {

    fun subscribeNodes(): Flow<List<Node>> {
        return repository.getNodes()
            .flowOn(Dispatchers.IO)
    }

    fun subscribeSelectedNode(): Flow<Node?> {
        return repository.getSelectedNode()
    }

    fun validateNodeAddress(url: String): ValidationEvent {
        return nodeValidator.validate(url)
    }

    suspend fun addCustomNode(node: Node) {
        repository.addCustomNode(node)
    }

    suspend fun updateCustomNode(previousAddress: String, node: Node) {
        repository.updateCustomNode(previousAddress, node)
    }

    suspend fun removeNode(node: Node) {
        repository.deleteNode(node.address)
    }
}
