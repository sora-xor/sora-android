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
        backgroundColor = MaterialTheme.customColors.bgSurfaceVariant,
        title = {
            Text(
                color = MaterialTheme.customColors.fgPrimary,
                text = stringResource(R.string.remove_node_title)
            )
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
