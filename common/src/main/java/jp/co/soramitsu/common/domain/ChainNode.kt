/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import androidx.compose.runtime.Stable

@Stable
data class ChainNode(
    val chain: String,
    val name: String,
    val address: String,
    val isSelected: Boolean,
    val isDefault: Boolean,
)
