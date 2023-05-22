/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
                selectableIcon = if (isSelectedAction) R.drawable.ic_selected_accent_pin_24 else R.drawable.ic_selected_pin_empty_24,
                tint = MaterialTheme.customColors.accentPrimary,
                showSelectableIcon = true,
            )
        }
    }
}
