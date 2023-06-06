/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.CheckboxButton
import jp.co.soramitsu.feature_multiaccount_impl.presentation.CreateBackupPasswordState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
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
                text = "Setting a password will encrypt your Google backup. You’ll need to enter this when restoring your wallet.",
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
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
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
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            )

            CheckboxButton(
                modifier = Modifier.padding(top = Dimens.x2),
                isSelected = state.warningIsSelected,
                itemClicked = onWarningToggle,
                text = "I understand that if I forget my password there is no way to retrieve it."
            )

            FilledButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x3),
                size = Size.Large,
                order = Order.PRIMARY,
                text = "Set backup password",
                onClick = onSetBackupPasswordClicked,
                enabled = state.setPasswordButtonIsEnabled,
            )
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
