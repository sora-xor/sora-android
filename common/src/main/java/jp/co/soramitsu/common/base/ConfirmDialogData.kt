/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.common.base

/**
 * A question with two answers. Only [confirmText] runs [onConfirm]. Dismissing the dialog (back,
 * tap outside, [dismissText]) runs [onDismiss], which by default does nothing.
 */
class ConfirmDialogData(
    val title: String,
    val message: String,
    val confirmText: String,
    val dismissText: String,
    val onDismiss: () -> Unit = {},
    val onConfirm: () -> Unit,
)
