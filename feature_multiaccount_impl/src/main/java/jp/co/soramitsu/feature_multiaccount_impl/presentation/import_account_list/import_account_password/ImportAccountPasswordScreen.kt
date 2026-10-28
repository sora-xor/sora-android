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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.AccountIcon
import jp.co.soramitsu.common.presentation.compose.neon.InputText
import jp.co.soramitsu.feature_multiaccount_impl.presentation.BackupAccountMetaWithIcon
import jp.co.soramitsu.feature_multiaccount_impl.presentation.ImportAccountPasswordState
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.SafeContentCard as ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta

@Composable
fun ImportAccountPasswordScreen(
    importAccountScreenState: ImportAccountPasswordState,
    onPasswordChanged: (value: TextFieldValue) -> Unit,
    onContinueClicked: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x2),
    ) {
        Column {
            Text(
                modifier = Modifier
                    .padding(
                        start = Dimens.x3,
                        end = Dimens.x2,
                        top = Dimens.x3,
                        bottom = Dimens.x3
                    ),
                text = stringResource(id = R.string.enter_password_description),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM
            )
            importAccountScreenState.selectedAccount?.let {
                AccountWithIcon(
                    modifier = Modifier
                        .background(MaterialTheme.customColors.bgPage)
                        .padding(vertical = Dimens.x2, horizontal = Dimens.x3),
                    address = it.backupAccountMeta.address,
                    accountName = it.backupAccountMeta.name,
                    accountIcon = it.icon,
                    note = readOnlyNote(it),
                )
            }

            InputText(
                modifier = Modifier
                    .padding(Dimens.x3)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                state = importAccountScreenState.passwordInput,
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

            LoaderWrapper(
                modifier = Modifier
                    .padding(start = Dimens.x3, end = Dimens.x3, bottom = Dimens.x3)
                    .fillMaxWidth(),
                loading = importAccountScreenState.isLoading,
                loaderSize = Size.Large,
            ) { modifier, elevation ->
                FilledButton(
                    modifier = modifier
                        .fillMaxWidth(),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = stringResource(id = R.string.common_continue),
                    onClick = onContinueClicked
                )
            }
        }
    }
}

/**
 * The line under a backup whose account is already on this phone but cannot sign here: it says
 * why the backup is offered and what restoring it is for. Null for every other backup.
 */
@Composable
fun readOnlyNote(account: BackupAccountMetaWithIcon): String? =
    if (account.readOnlyOnThisPhone) {
        stringResource(id = R.string.import_account_list_read_only_note)
    } else {
        null
    }

@Composable
fun AccountWithIcon(
    modifier: Modifier = Modifier,
    address: String,
    accountName: String,
    accountIcon: Drawable?,
    note: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountIcon(
            drawable = accountIcon,
            size = 40.dp,
        )

        Column(
            modifier = Modifier
                .padding(start = Dimens.x1)
        ) {
            Text(
                text = accountName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textS,
            )

            Text(
                text = address,
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold,
            )

            if (note != null) {
                Text(
                    modifier = Modifier.padding(top = Dimens.x1_2),
                    text = note,
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.textXS,
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewEnterPassphraseScreen() {
    ImportAccountPasswordScreen(
        ImportAccountPasswordState(
            selectedAccount = BackupAccountMetaWithIcon(
                BackupAccountMeta("aa", "add"), Drawable.createFromPath("")!!
            )
        ),
        {},
        {}
    )
}
