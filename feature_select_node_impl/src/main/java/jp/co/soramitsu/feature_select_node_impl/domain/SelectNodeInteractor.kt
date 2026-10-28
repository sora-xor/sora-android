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

package jp.co.soramitsu.feature_select_node_impl.domain

import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
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
