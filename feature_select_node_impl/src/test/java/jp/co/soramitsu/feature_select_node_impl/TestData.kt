/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
