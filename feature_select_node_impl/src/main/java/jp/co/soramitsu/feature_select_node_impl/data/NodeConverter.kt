/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.data

import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import javax.inject.Inject

class NodeConverter @Inject constructor() {

    fun convert(nodeLocal: NodeLocal): Node = Node(
        chain = nodeLocal.chain,
        address = nodeLocal.address,
        name = nodeLocal.name,
        isSelected = nodeLocal.isSelected,
        isDefault = nodeLocal.isDefault
    )

    fun convert(node: Node): NodeLocal = NodeLocal(
        chain = node.chain,
        address = node.address,
        name = node.name,
        isSelected = node.isSelected,
        isDefault = node.isDefault
    )
}
