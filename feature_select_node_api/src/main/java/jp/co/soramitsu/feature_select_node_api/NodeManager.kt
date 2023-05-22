/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_api

import jp.co.soramitsu.common.domain.ChainNode
import kotlinx.coroutines.flow.Flow

interface NodeManager {

    val events: Flow<NodeManagerEvent>

    fun tryToConnect(node: ChainNode)

    fun checkGenesisHash(url: String)

    val connectionState: Flow<Boolean>
}
