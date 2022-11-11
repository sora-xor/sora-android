/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select.model

import jp.co.soramitsu.feature_select_node_api.domain.model.Node

data class SelectNodeState(
    val defaultNodes: List<Node> = emptyList(),
    val customNodes: List<Node> = emptyList(),
    val removeAlertState: RemoveNodeAlertState? = null,
    val switchNodeAlertState: SwitchNodeAlertState? = null,
)

data class RemoveNodeAlertState(
    val node: Node
)

data class SwitchNodeAlertState(
    val node: Node
)
