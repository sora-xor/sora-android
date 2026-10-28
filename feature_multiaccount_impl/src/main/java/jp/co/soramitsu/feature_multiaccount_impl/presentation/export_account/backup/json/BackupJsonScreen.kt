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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup.json

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupJsonScreenState
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.SafeContentCard as ContentCard
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

private const val MASK_CHAR = '*'

@Composable
internal fun BackupJsonScreen(
    state: BackupJsonScreenState,
    onChange: (TextFieldValue) -> Unit,
    onConfirmChange: (TextFieldValue) -> Unit,
    onDownloadClick: () -> Unit,
) {
    ContentCard(
        modifier = Modifier.padding(horizontal = Dimens.x2).fillMaxWidth().wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(Dimens.x3),
        ) {
            Text(
                text = stringResource(R.string.export_json_description),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )
            Spacer(modifier = Modifier.size(Dimens.x3))
            InputText(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.customColors.bgSurface,
                        shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
                    )
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = state.state,
                visualTransformation = PasswordVisualTransformation(MASK_CHAR),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password
                ),
                onValueChange = onChange,
                maxLines = 1,
                singleLine = true,
            )
            Spacer(modifier = Modifier.size(Dimens.x2))
            InputText(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.customColors.bgSurface,
                        shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
                    )
                    .fillMaxWidth()
                    .wrapContentHeight(),
                visualTransformation = PasswordVisualTransformation(MASK_CHAR),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password
                ),
                state = state.confirmationState,
                onValueChange = onConfirmChange,
                maxLines = 1,
                singleLine = true,
            )
            Spacer(modifier = Modifier.size(Dimens.x3))
            // Writing the file encrypts it (scrypt), which takes a moment and runs off the
            // main thread: the button shows that the app is working.
            LoaderWrapper(
                modifier = Modifier.fillMaxWidth(),
                loading = state.loading,
                loaderSize = Size.Large,
            ) { modifier, _ ->
                FilledButton(
                    modifier.fillMaxWidth(),
                    enabled = state.buttonEnabledState,
                    text = stringResource(id = R.string.export_json_download_json).uppercase(),
                    onClick = onDownloadClick,
                    order = Order.PRIMARY,
                    size = Size.Large,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewJson() {
    BackupJsonScreen(
        state = BackupJsonScreenState(
            state = InputTextState(
                value = TextFieldValue(""),
                label = "set password",
            ),
            confirmationState = InputTextState(
                value = TextFieldValue(""),
                label = "confirm password",
            ),
            buttonEnabledState = true,
        ),
        onChange = {},
        onConfirmChange = {},
        onDownloadClick = {},
    )
}
