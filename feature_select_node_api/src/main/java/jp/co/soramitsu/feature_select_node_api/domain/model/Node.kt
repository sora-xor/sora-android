/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_api.domain.model

import androidx.compose.runtime.Stable

@Stable
data class Node(
    val chain: String,
    val name: String,
    val address: String,
    val isSelected: Boolean,
    val isDefault: Boolean
)
