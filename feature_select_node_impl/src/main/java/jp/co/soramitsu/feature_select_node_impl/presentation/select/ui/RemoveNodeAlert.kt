/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select.ui

import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.feature_select_node_impl.R
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun RemoveNodeAlertDialog(
    onRemoveCanceled: () -> Unit,
    onRemoveConfirmed: () -> Unit
) {
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.remove_node_title))
        },
        confirmButton = {
            TextButton(
                onClick = onRemoveConfirmed
            ) {
                Text(
                    text = stringResource(id = R.string.common_remove),
                    color = Color.Red
                )
            }
        },
        onDismissRequest = onRemoveCanceled,
        dismissButton = {
            TextButton(
                onClick = onRemoveCanceled
            ) {
                Text(
                    text = stringResource(id = R.string.common_cancel),
                    color = MaterialTheme.customColors.fgPrimary

                )
            }
        }
    )
}
