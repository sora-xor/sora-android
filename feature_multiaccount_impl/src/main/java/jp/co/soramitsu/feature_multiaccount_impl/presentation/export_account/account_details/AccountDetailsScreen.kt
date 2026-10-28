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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.Option
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AccountName(
    inputTextState: InputTextState,
    onValueChanged: (TextFieldValue) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    InputText(
        modifier = Modifier.fillMaxWidth(),
        maxLines = 1,
        singleLine = true,
        state = inputTextState,
        onValueChange = onValueChanged,
        focusRequester = focusRequester,
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        ),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
    )
}

@Composable
internal fun AccountAddress(
    address: String,
    onClick: () -> Unit,
) {
    ContentCard(
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.x3)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Text(
                text = stringResource(R.string.account_address).uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = address,
                style = MaterialTheme.customTypography.paragraphM,
                color = MaterialTheme.customColors.fgPrimary,
            )
        }
    }
}

@Composable
internal fun BackupOptions(
    isMnemonicAvailable: Boolean,
    isBackupAvailable: Boolean?,
    isBackupLoading: Boolean,
    onShowPassphrase: () -> Unit,
    onShowRawSeed: () -> Unit,
    onExportJson: () -> Unit,
    onBackupGoogle: () -> Unit,
    isCloudBackupAvailable: Boolean = true,
) {
    ContentCard {
        Column(
            modifier = Modifier
                .padding(Dimens.x3)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Text(
                text = stringResource(R.string.export_account_details_backup_options).uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.size(Dimens.x1))
            if (isMnemonicAvailable) {
                Option(
                    modifier = Modifier.testTagAsId("ShowPassphrase"),
                    icon = painterResource(R.drawable.ic_passphrase),
                    label = stringResource(R.string.export_account_details_show_passphrase),
                    bottomDivider = true,
                    onClick = onShowPassphrase,
                )
            }
            Option(
                modifier = Modifier.testTagAsId("ShowRawSeed"),
                icon = painterResource(R.drawable.ic_key),
                label = stringResource(R.string.export_account_details_show_raw_seed),
                onClick = onShowRawSeed,
                bottomDivider = true,
            )
            Option(
                modifier = Modifier.testTagAsId("ExportJson"),
                icon = painterResource(R.drawable.ic_arrow_up_rectangle_24),
                label = stringResource(R.string.export_protection_json_title),
                onClick = onExportJson,
                bottomDivider = isBackupAvailable != null,
            )

            if (!isCloudBackupAvailable) {
                Text(stringResource(R.string.wallet_cloud_backup_unavailable), style = MaterialTheme.customTypography.paragraphS,
                    color = MaterialTheme.customColors.fgPrimary, modifier = Modifier.padding(vertical = Dimens.x2))
            }
            isBackupAvailable?.let {
                val text = if (isBackupAvailable) {
                    stringResource(id = R.string.account_options_delete_backup)
                } else {
                    stringResource(id = R.string.account_options_backup_google)
                }

                LoaderWrapper(
                    modifier = Modifier.fillMaxWidth(),
                    loading = isBackupLoading,
                    loaderSize = Size.Small,
                ) { modifier, elevation ->
                    Option(
                        modifier = modifier.testTagAsId("GoogleLogin"),
                        icon = painterResource(R.drawable.ic_arrow_up_rectangle_24),
                        label = text,
                        onClick = onBackupGoogle,
                        textColor = if (isBackupAvailable) MaterialTheme.customColors.statusError else MaterialTheme.customColors.fgPrimary,
                        enabled = !isBackupLoading,
                        bottomDivider = false,
                    )
                }
            }
            Spacer(modifier = Modifier.size(Dimens.x1))
            Text(
                modifier = Modifier.padding(horizontal = Dimens.x3),
                text = stringResource(R.string.export_account_details_backup_description),
                style = MaterialTheme.customTypography.paragraphXS,
                color = MaterialTheme.customColors.fgSecondary,
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewBackup01() {
    SoraAppTheme {
        BackupOptions(
            isMnemonicAvailable = true,
            isBackupAvailable = true,
            isBackupLoading = false,
            onShowPassphrase = { },
            onShowRawSeed = { },
            onExportJson = { },
            onBackupGoogle = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewBackup02() {
    SoraAppTheme {
        BackupOptions(
            isMnemonicAvailable = true,
            isBackupAvailable = true,
            isBackupLoading = false,
            onShowPassphrase = { },
            onShowRawSeed = { },
            onExportJson = { },
            onBackupGoogle = {}
        )
    }
}
