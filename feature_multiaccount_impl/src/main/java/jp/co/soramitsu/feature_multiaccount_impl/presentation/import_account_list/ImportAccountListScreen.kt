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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.BackupAccountMetaWithIcon
import jp.co.soramitsu.feature_multiaccount_impl.presentation.ImportAccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password.AccountWithIcon
import jp.co.soramitsu.feature_multiaccount_impl.presentation.import_account_list.import_account_password.readOnlyNote
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun ImportAccountListScreen(
    importAccountScreenState: ImportAccountListScreenState,
    onAccountSelected: (account: BackupAccountMetaWithIcon) -> Unit,
    onCreateNewAccountClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(Dimens.x2)
    ) {
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = Dimens.x2),
        ) {
            Column(modifier = Modifier.padding(Dimens.x1)) {
                importAccountScreenState.accountList.forEach {
                    AccountWithIcon(
                        modifier = Modifier
                            .clickable {
                                onAccountSelected(it)
                            }
                            .padding(Dimens.x2),
                        address = it.backupAccountMeta.address,
                        accountName = it.backupAccountMeta.name,
                        accountIcon = it.icon,
                        note = readOnlyNote(it),
                    )
                }
            }
        }

        FilledButton(
            modifier = Modifier
                .fillMaxWidth(),
            size = Size.Large,
            order = Order.PRIMARY,
            leftIcon = painterResource(id = R.drawable.ic_plus),
            text = stringResource(id = R.string.create_new_account_title),
            onClick = onCreateNewAccountClicked
        )
    }
}

@Composable
@Preview
fun PreviewEnterPassphraseScreen() {
    ImportAccountListScreen(
        ImportAccountListScreenState(),
        {},
        {}
    )
}
