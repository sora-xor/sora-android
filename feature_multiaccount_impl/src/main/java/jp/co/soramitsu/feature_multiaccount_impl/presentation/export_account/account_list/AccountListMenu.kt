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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun AccountMenu(
    onSelectOptionsClicked: (address: String) -> Unit,
    onAccountOptionsClicked: (address: String) -> Unit,
    onDismissMenu: () -> Unit,
    address: String,
    expanded: Boolean,
) {
    DropdownMenu(
        modifier = Modifier.background(MaterialTheme.customColors.bgSurface),
        expanded = expanded,
        onDismissRequest = onDismissMenu,
    ) {
        DropdownMenuItem(
            modifier = Modifier.testTagAsId("SelectAccountForBatchExport"),
            onClick = { onSelectOptionsClicked(address) }
        ) {
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textM,
                text = stringResource(id = R.string.export_select_account),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenuItem(
            modifier = Modifier.testTagAsId("OpenAccountOptions"),
            onClick = { onAccountOptionsClicked(address) }
        ) {
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textM,
                text = stringResource(id = R.string.export_account_options),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
