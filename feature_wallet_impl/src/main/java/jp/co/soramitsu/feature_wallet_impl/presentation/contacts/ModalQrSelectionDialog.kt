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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun ModalQrSelectionDialog(
    onFromGallery: () -> Unit,
    onFromCamera: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        backgroundColor = MaterialTheme.customColors.bgPage,
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
