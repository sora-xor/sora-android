/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.details

import jp.co.soramitsu.ui_core.component.input.InputTextState

data class NodeDetailsState(
    val nameState: InputTextState,
    val addressState: InputTextState,
    val submitButtonEnabled: Boolean = false,
    val nodeAddressErrorLabel: String? = null,
    val howToRunNodeInfo: Boolean = false,
    val loading: Boolean = false,
)
