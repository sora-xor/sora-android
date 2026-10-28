/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.androidfoundation.compose.toTitle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.AlertDialogData
import jp.co.soramitsu.common.base.ConfirmDialogData
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun AlertDialogContent(openAlertDialog: MutableState<AlertDialogData>) {
    if (openAlertDialog.value.title != null) {
        val title = openAlertDialog.value.title.message()
        val message = openAlertDialog.value.message.message()
        if (title != null && message != null) {
            AlertDialog(
                backgroundColor = MaterialTheme.customColors.bgSurfaceVariant,
                title = { Text(color = MaterialTheme.customColors.fgPrimary, text = title) },
                text = { Text(color = MaterialTheme.customColors.fgPrimary, text = message) },
                onDismissRequest = {
                    openAlertDialog.value = AlertDialogData()
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openAlertDialog.value = AlertDialogData()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.common_ok))
                    }
                }
            )
        }
    }
}

@Composable
private fun Any?.message() = this?.toTitle()

/** Two-button question. Nothing happens unless the confirm button is tapped. */
@Composable
fun ConfirmDialogContent(openConfirmDialog: MutableState<ConfirmDialogData?>) {
    val data = openConfirmDialog.value ?: return
    AlertDialog(
        backgroundColor = MaterialTheme.customColors.bgSurfaceVariant,
        title = { Text(color = MaterialTheme.customColors.fgPrimary, text = data.title) },
        text = { Text(color = MaterialTheme.customColors.fgPrimary, text = data.message) },
        onDismissRequest = {
            openConfirmDialog.value = null
            data.onDismiss()
        },
        dismissButton = {
            TextButton(
                onClick = {
                    openConfirmDialog.value = null
                    data.onDismiss()
                }
            ) {
                Text(text = data.dismissText)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    openConfirmDialog.value = null
                    data.onConfirm()
                }
            ) {
                Text(text = data.confirmText)
            }
        },
    )
}
