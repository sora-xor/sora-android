/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0
 */

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import jp.co.soramitsu.common.R

@Composable
fun PinCodeNodeConnection(
    onSwitchClicked: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Text(
                text = stringResource(id = R.string.node_offline),
            )
        },
        text = {
            Text(
                text = stringResource(id = R.string.node_connection_issue),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onSwitchClicked,
            ) {
                Text(
                    text = stringResource(id = R.string.switch_node),
                )
            }
        },
    )
}

@Composable
@Preview(showBackground = true)
private fun PinCodeNodeConnectionPreview() {
    PinCodeNodeConnection({})
}
