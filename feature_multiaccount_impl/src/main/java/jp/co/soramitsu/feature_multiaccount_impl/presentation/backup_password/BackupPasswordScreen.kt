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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.backup_password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.CheckboxButton
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.feature_multiaccount_impl.presentation.CreateBackupPasswordState
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.SafeContentCard as ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BackupPasswordScreen(
    state: CreateBackupPasswordState,
    onPasswordChanged: (TextFieldValue) -> Unit,
    onPasswordConfirmationChanged: (TextFieldValue) -> Unit,
    onWarningToggle: () -> Unit,
    onSetBackupPasswordClicked: () -> Unit,
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x2, top = Dimens.x1),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.x3)
        ) {
            val focusManager = LocalFocusManager.current

            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = stringResource(id = R.string.backup_password_title_2),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )

            InputText(
                modifier = Modifier
                    .background(MaterialTheme.customColors.bgSurface)
                    .padding(top = Dimens.x2)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = state.password,
                visualTransformation = PasswordVisualTransformation('*'),
                onValueChange = onPasswordChanged,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
            )

            InputText(
                modifier = Modifier
                    .background(MaterialTheme.customColors.bgSurface)
                    .padding(top = Dimens.x2)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = state.passwordConfirmation,
                visualTransformation = PasswordVisualTransformation('*'),
                onValueChange = onPasswordConfirmationChanged,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
            )

            CheckboxButton(
                modifier = Modifier.padding(top = Dimens.x2),
                isSelected = state.warningIsSelected,
                itemClicked = onWarningToggle,
                text = stringResource(id = R.string.create_backup_password_warning_text)
            )

            LoaderWrapper(
                modifier = Modifier
                    .padding(top = Dimens.x3)
                    .fillMaxWidth(),
                loading = state.isLoading,
                loaderSize = Size.Large,
            ) { modifier, _ ->
                FilledButton(
                    modifier = modifier
                        .fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.create_backup_password_button_text),
                    onClick = onSetBackupPasswordClicked,
                    enabled = state.setPasswordButtonIsEnabled,
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewCreateAccountScreen() {
    BackupPasswordScreen(
        state = CreateBackupPasswordState(),
        {},
        {},
        {},
        {}
    )
}
