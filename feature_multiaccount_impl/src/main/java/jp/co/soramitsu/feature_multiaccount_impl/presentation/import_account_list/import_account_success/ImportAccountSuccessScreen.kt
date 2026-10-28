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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_success

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.BackupAccountMetaWithIcon
import jp.co.soramitsu.feature_multiaccount_impl.presentation.ImportAccountPasswordState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password.AccountWithIcon
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.LoaderWrapper
import jp.co.soramitsu.common.presentation.compose.neon.OutlinedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.xbackup.domain.models.BackupAccountMeta

@Composable
fun ImportAccountSuccessScreen(
    importAccountScreenState: ImportAccountPasswordState,
    onContinueClicked: () -> Unit,
    onImportMoreClicked: () -> Unit
) {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = Dimens.x2, end = Dimens.x2, bottom = Dimens.x2),
    ) {
        Column {
            Text(
                modifier = Modifier
                    .padding(Dimens.x3),
                text = stringResource(id = R.string.succesfully_imported_account_title),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.headline2,
                textAlign = TextAlign.Center
            )
            importAccountScreenState.selectedAccount?.let {
                AccountWithIcon(
                    modifier = Modifier
                        .background(MaterialTheme.customColors.bgPage)
                        .padding(vertical = Dimens.x2, horizontal = Dimens.x3),
                    address = it.backupAccountMeta.address,
                    accountName = it.backupAccountMeta.name,
                    accountIcon = it.icon
                )
            }

            val bottomPadding = if (importAccountScreenState.isImportMoreAvailable) {
                Dimens.x2
            } else {
                Dimens.x3
            }

            FilledButton(
                modifier = Modifier
                    .padding(
                        start = Dimens.x3,
                        end = Dimens.x3,
                        bottom = bottomPadding,
                        top = Dimens.x4
                    )
                    .fillMaxWidth(),
                size = Size.Large,
                order = Order.PRIMARY,
                text = stringResource(id = R.string.common_continue),
                onClick = onContinueClicked
            )

            if (importAccountScreenState.isImportMoreAvailable) {
                LoaderWrapper(
                    modifier = Modifier
                        .padding(start = Dimens.x3, end = Dimens.x3, bottom = Dimens.x3)
                        .fillMaxWidth(),
                    loading = importAccountScreenState.isLoading,
                    loaderSize = Size.Large,
                ) { modifier, elevation ->
                    OutlinedButton(
                        modifier = modifier
                            .fillMaxWidth(),
                        size = Size.Large,
                        order = Order.PRIMARY,
                        text = stringResource(id = R.string.import_more_title),
                        onClick = onImportMoreClicked
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun PreviewEnterPassphraseScreen() {
    ImportAccountSuccessScreen(
        ImportAccountPasswordState(
            selectedAccount = BackupAccountMetaWithIcon(
                BackupAccountMeta("aa", "add"), ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
        ),
        {},
        {}
    )
}
