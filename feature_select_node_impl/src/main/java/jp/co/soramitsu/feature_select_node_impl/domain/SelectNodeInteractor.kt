/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.domain

import javax.inject.Inject
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

internal class SelectNodeInteractor @Inject constructor(
    private val repository: SelectNodeRepository,
    private val nodeValidator: NodeValidator,
    private val coroutineManager: CoroutineManager,
) {

    fun subscribeNodes(): Flow<List<ChainNode>> {
        return repository.getNodes()
            .flowOn(coroutineManager.io)
    }

    fun subscribeSelectedNode(): Flow<ChainNode?> {
        return repository.getSelectedNode()
    }

    fun validateNodeAddress(url: String): ValidationEvent {
        return nodeValidator.validate(url)
    }

    suspend fun addCustomNode(node: ChainNode) {
        repository.addCustomNode(node)
    }

    suspend fun updateCustomNode(previousAddress: String, node: ChainNode) {
        repository.updateCustomNode(previousAddress, node)
    }

    suspend fun removeNode(node: ChainNode) {
        repository.deleteNode(node.address)
    }
}
