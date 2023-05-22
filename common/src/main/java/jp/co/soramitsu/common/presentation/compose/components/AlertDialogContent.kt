/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.AlertDialogData
import jp.co.soramitsu.common.util.ext.safeCast

@Composable
fun AlertDialogContent(openAlertDialog: MutableState<AlertDialogData>) {
    if (openAlertDialog.value.title != null) {
        val title = openAlertDialog.value.title.message()
        val message = openAlertDialog.value.message.message()
        if (title != null && message != null) {
            AlertDialog(
                title = { Text(text = title) },
                text = { Text(text = message) },
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
private fun Any?.message() = this.safeCast<Int>()?.let {
    stringResource(id = it)
} ?: this.safeCast<String>()
