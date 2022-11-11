/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_api

interface SelectNodeRouter {

    fun showSelectNode()

    fun showAddCustomNode()

    fun showEditNode(nodeName: String, nodeAddress: String)

    fun returnFromPinCodeCheck()
}
