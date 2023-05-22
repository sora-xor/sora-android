/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl

import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.text.input.TextFieldValue
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.util.Const

object TestData {

    val DEFAULT_NODES = listOf(
        ChainNode(
            chain = "SORA",
            name = "Test name",
            address = "Test address 0",
            isSelected = true,
            isDefault = true
        ),
        ChainNode(
            chain = "SORA",
            name = "Test name",
            address = "Test address 1",
            isSelected = false,
            isDefault = true
        ),
        ChainNode(
            chain = "SORA",
            name = "Test name",
            address = "Test address 2",
            isSelected = false,
            isDefault = true
        ),
    )

    val SELECTED_NODE = ChainNode(
        chain = "SORA",
        name = "Test name",
        address = "Test address 0",
        isSelected = true,
        isDefault = true
    )

    val CUSTOM_NODES = listOf(
        ChainNode(
            chain = "SORA",
            name = "Test name",
            address = "Test custom address 0",
            isSelected = true,
            isDefault = false
        ),
        ChainNode(
            chain = "SORA",
            name = "Test name",
            address = "Test custom address 1",
            isSelected = false,
            isDefault = false
        ),
        ChainNode(
            chain = "SORA",
            name = "Test name",
            address = "Test custom address 2",
            isSelected = false,
            isDefault = false
        ),
    )

    val NODE_LIST = DEFAULT_NODES + CUSTOM_NODES

    val FOCUS_STATE = object : FocusState {
        override val hasFocus: Boolean
            get() = true
        override val isCaptured: Boolean
            get() = true
        override val isFocused: Boolean
            get() = true
    }

    val NODE_DETAILS_NAME: TextFieldValue = TextFieldValue("name")
    val NODE_DETAILS_ADDRESS: TextFieldValue = TextFieldValue("address")

    val NODE_DETAIL_NODE = ChainNode(
        chain = Const.SORA,
        name = NODE_DETAILS_NAME.text,
        address = NODE_DETAILS_ADDRESS.text,
        isSelected = false,
        isDefault = false
    )
}