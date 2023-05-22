/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose

data class SnackBarState(
    val title: String,
    val actionText: String? = null,
    val onActionHandler: () -> Unit = {},
    val onCancelHandler: () -> Unit = {}
)
