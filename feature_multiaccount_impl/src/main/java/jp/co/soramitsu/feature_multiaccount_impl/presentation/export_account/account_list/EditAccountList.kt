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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.co.soramitsu.common.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
internal fun EditAccountList(
    state: AccountListScreenState,
    onSelectOptionsClicked: (address: String) -> Unit
) {
    state.accountList.forEach { element ->
        with(element) {
            SelectableAccountWithIcon(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .clickable { onSelectOptionsClicked(account.substrateAddress) }
                    .padding(vertical = Dimens.x2),
                address = account.substrateAddress,
                accountName = account.accountName,
                accountIcon = icon,
                readOnly = readOnly,
                selectableIcon = if (isSelectedAction) R.drawable.ic_selected_accent_pin_24 else R.drawable.ic_selected_pin_empty_24,
                tint = MaterialTheme.customColors.accentPrimary,
                showSelectableIcon = true,
            )
        }
    }
}
