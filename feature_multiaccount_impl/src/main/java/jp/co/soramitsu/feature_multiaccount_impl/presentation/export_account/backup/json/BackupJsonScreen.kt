/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
