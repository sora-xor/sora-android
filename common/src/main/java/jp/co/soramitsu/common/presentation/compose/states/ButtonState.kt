/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.states

data class ButtonState(
    val text: String = "",
    val enabled: Boolean = false,
    val loading: Boolean = false,
)
