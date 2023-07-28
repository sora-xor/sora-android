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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.Option
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AccountName(
    inputTextState: InputTextState,
    onValueChanged: (TextFieldValue) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    InputText(
        modifier = Modifier
            .background(
                color = MaterialTheme.customColors.bgSurface,
                shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
            )
            .fillMaxWidth()
            .wrapContentHeight(),
        maxLines = 1,
        singleLine = true,
        state = inputTextState,
        onValueChange = onValueChanged,
        onFocusChanged = { if (!it.isFocused) focusManager.clearFocus() },
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
                bottomDivider = false,
            )

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
                        textColor = if (isBackupAvailable) MaterialTheme.customColors.statusError else Color.Unspecified,
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

@Preview
@Composable
private fun PreviewBackup() {
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
