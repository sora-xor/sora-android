/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_api

sealed class NodeManagerEvent {

    class Connected(val url: String) : NodeManagerEvent()
    class ConnectionFailed(val url: String) : NodeManagerEvent()
    class GenesisValidated(val result: Boolean) : NodeManagerEvent()
    class NodeExisting(val existedNodeName: String, val currentNodeUrl: String) : NodeManagerEvent()
    object AllNodesUnavailable : NodeManagerEvent()
    object NoConnection : NodeManagerEvent()
}
