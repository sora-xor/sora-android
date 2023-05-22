/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select.model

import jp.co.soramitsu.common.domain.ChainNode

data class SelectNodeState(
    val defaultNodes: List<ChainNode> = emptyList(),
    val customNodes: List<ChainNode> = emptyList(),
    val removeAlertState: RemoveNodeAlertState? = null,
    val switchNodeAlertState: SwitchNodeAlertState? = null,
)

data class RemoveNodeAlertState(
    val node: ChainNode
)

data class SwitchNodeAlertState(
    val node: ChainNode
)
