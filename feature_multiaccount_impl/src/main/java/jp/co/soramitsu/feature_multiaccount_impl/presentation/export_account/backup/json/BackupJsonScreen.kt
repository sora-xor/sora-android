/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupJsonScreenState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
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
        modifier = Modifier.padding(horizontal = Dimens.x2),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(Dimens.x3),
        ) {
            Text(
                text = stringResource(R.string.export_json_description),
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
            FilledButton(
                Modifier
                    .fillMaxWidth(),
                enabled = state.buttonEnabledState,
                text = stringResource(id = R.string.export_json_download_json).uppercase(),
                onClick = onDownloadClick,
                order = Order.PRIMARY,
                size = Size.Large,
            )
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
