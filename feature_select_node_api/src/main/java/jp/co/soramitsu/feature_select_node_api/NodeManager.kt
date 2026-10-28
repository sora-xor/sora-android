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

package jp.co.soramitsu.feature_select_node_api

import jp.co.soramitsu.common.domain.ChainNode
import kotlinx.coroutines.flow.Flow

interface NodeManager {

    val events: Flow<NodeManagerEvent>

    /**
     * Connects to the selected node once the wallet database may be opened (the upgrade gate is
     * clear). Safe to call any number of times; returns true once the node connection has been
     * started. While the gate is still closed it does nothing and returns false, so the caller
     * can try again after recovery (Try again, Continue read-only) clears it in this process.
     */
    fun ensureStarted(): Boolean

    fun tryToConnect(node: ChainNode)

    fun checkGenesisHash(url: String)

    val connectionState: Flow<Boolean>
}
