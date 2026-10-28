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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.enter_passphrase

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.ProtectWalletSecrets
import jp.co.soramitsu.common.presentation.compose.neon.NeonSegments
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.RecoveryType
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.common.presentation.compose.neon.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.SafeContentCard as ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun EnterPassphraseScreen(
    recoveryState: RecoveryState,
    onRecoveryInputChanged: (TextFieldValue) -> Unit,
    onPasswordChanged: (TextFieldValue) -> Unit = {},
    onChooseFile: () -> Unit = {},
    onSourceChanged: ((RecoveryType) -> Unit)? = null,
    onNextClicked: () -> Unit,
) {
    ProtectWalletSecrets()
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
                    .fillMaxWidth()
                    .padding(bottom = Dimens.x3),
                text = stringResource(id = recoveryState.title),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )

            onSourceChanged?.let { select ->
                NeonSegments(
                    labels = listOf(stringResource(R.string.common_passphrase_title),
                        stringResource(R.string.common_raw_seed), "JSON"),
                    selected = recoveryState.recoveryType.ordinal,
                    onSelect = { index -> if (index != recoveryState.recoveryType.ordinal) select(RecoveryType.values()[index]) },
                    modifier = Modifier.padding(bottom = Dimens.x2),
                )
            }

            if (recoveryState.recoveryType == RecoveryType.JSON) {
                // An encrypted backup file: choose the file (only its name is shown; its text
                // is not put into the field below) or paste the text, then the password it was
                // exported with.
                recoveryState.chosenFileName?.let { fileName ->
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.x1)
                            .testTag("RecoveryChosenFile"),
                        text = stringResource(id = R.string.recovery_json_file_chosen, fileName),
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.textXS,
                    )
                }
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x1)
                        .testTag("RecoveryChooseFile"),
                    size = Size.Small,
                    order = Order.TERTIARY,
                    text = stringResource(id = R.string.recovery_json_choose_file),
                    onClick = onChooseFile,
                )
            }

            InputText(
                modifier = Modifier
                    .background(MaterialTheme.customColors.bgSurface)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = recoveryState.recoveryInputState,
                onValueChange = onRecoveryInputChanged,
                // Pasted backup text can be long: the field stays a few lines high so the
                // password field and the button stay on the screen.
                maxLines = if (recoveryState.recoveryType == RecoveryType.JSON) {
                    JSON_FIELD_MAX_LINES
                } else {
                    Int.MAX_VALUE
                },
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    },
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password
                ),
            )

            if (recoveryState.recoveryType == RecoveryType.JSON) {
                InputText(
                    modifier = Modifier
                        .background(MaterialTheme.customColors.bgSurface)
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(top = Dimens.x1)
                        .testTag("RecoveryFilePassword"),
                    state = recoveryState.passwordInputState,
                    onValueChange = onPasswordChanged,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        },
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Password
                    ),
                )
                Text(
                    modifier = Modifier.padding(top = Dimens.x1),
                    text = stringResource(R.string.recovery_json_password_help),
                    color = MaterialTheme.customColors.fgSecondary,
                    style = MaterialTheme.customTypography.textXS,
                )
            }

            FilledButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x5),
                size = Size.Large,
                enabled = recoveryState.isButtonEnabled,
                order = Order.PRIMARY,
                text = stringResource(id = R.string.common_continue),
                onClick = onNextClicked
            )
        }
    }
}

/** Lines of pasted backup text shown at once. */
private const val JSON_FIELD_MAX_LINES = 4

@Composable
@Preview
fun PreviewEnterPassphraseScreen() {
    EnterPassphraseScreen(
        recoveryState = RecoveryState(recoveryType = RecoveryType.PASSPHRASE, title = R.string.common_passphrase_title),
        onRecoveryInputChanged = {},
        onNextClicked = {},
    )
}
