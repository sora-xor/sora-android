/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.contacts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun ModalQrSelectionDialog(
    onFromGallery: () -> Unit,
    onFromCamera: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.x3),
        buttons = {
            Column(
                modifier = Modifier
                    .padding(Dimens.x2)
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                TonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = Size.Small,
                    order = Order.TERTIARY,
                    text = stringResource(id = R.string.qr_upload),
                ) {
                    onFromGallery.invoke()
                }
                Divider(thickness = Dimens.x2, color = Color.Transparent)
                TonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = Size.Small,
                    order = Order.TERTIARY,
                    text = stringResource(id = R.string.common_scan),
                ) {
                    onFromCamera.invoke()
                }
            }
        },
        title = {
            Text(
                text = stringResource(id = R.string.qr_code),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewModalQrSelectionDialog() {
    Box(modifier = Modifier.fillMaxSize()) {
        ModalQrSelectionDialog({}, {}, {})
    }
}
