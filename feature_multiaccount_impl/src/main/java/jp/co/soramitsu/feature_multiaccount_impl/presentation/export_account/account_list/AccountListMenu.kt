/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
internal fun AccountMenu(
    onSelectOptionsClicked: (address: String) -> Unit,
    onAccountOptionsClicked: (address: String) -> Unit,
    onDismissMenu: () -> Unit,
    address: String,
    expanded: Boolean,
) {
    MaterialTheme(shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(Dimens.x3))) {
        DropdownMenu(
            modifier = Modifier.background(MaterialTheme.customColors.bgSurface),
            expanded = expanded,
            onDismissRequest = onDismissMenu,
        ) {
            DropdownMenuItem(
                modifier = Modifier.testTagAsId("SelectAccountForBatchExport"),
                onClick = { onSelectOptionsClicked(address) }
            ) {
                Text(stringResource(id = R.string.export_select_account))
            }

            DropdownMenuItem(
                modifier = Modifier.testTagAsId("OpenAccountOptions"),
                onClick = { onAccountOptionsClicked(address) }
            ) {
                Text(stringResource(id = R.string.export_account_options))
            }
        }
    }
}
