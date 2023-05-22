/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.presentation.compose.previewDrawable
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun AccountListScreen(
    state: AccountListScreenState,
    viewModel: AccountListViewModel,
    scrollState: ScrollState,
) {
    ContentCard(
        modifier = Modifier
            .padding(horizontal = Dimens.x2)
            .wrapContentHeight()
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.x2)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isActionMode) {
                EditAccountList(
                    state = state,
                    onSelectOptionsClicked = viewModel::onAccountSelected
                )
            } else {
                AccountList(
                    state,
                    viewModel::onAccountClicked,
                    viewModel::onAccountLongClicked,
                    viewModel::onAccountSelected,
                    viewModel::onAccountOptionsClicked
                )
            }
        }
    }
}

@Composable
private fun AccountList(
    state: AccountListScreenState,
    onAccountClicked: (address: String) -> Unit,
    onAccountLongClicked: (address: String) -> Unit,
    onSelectOptionsClicked: (address: String) -> Unit,
    onAccountOptionsClicked: (address: String) -> Unit
) {
    state.accountList.forEach { element ->

        with(element) {
            AccountListItem(
                isSelected = isSelected,
                accountIcon = icon,
                accountName = account.accountName,
                address = account.substrateAddress,
                onAccountClicked = onAccountClicked,
                onAccountLongClicked = onAccountLongClicked,
                onSelectOptionsClicked = onSelectOptionsClicked,
                onAccountOptionsClicked = onAccountOptionsClicked,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountListItem(
    isSelected: Boolean,
    accountIcon: Drawable,
    accountName: String,
    address: String,
    onAccountClicked: (address: String) -> Unit,
    onAccountLongClicked: (address: String) -> Unit,
    onSelectOptionsClicked: (address: String) -> Unit,
    onAccountOptionsClicked: (address: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .combinedClickable(
                onClick = { onAccountClicked(address) },
                onLongClick = { onAccountLongClicked(address) },
            )
            .fillMaxWidth()
            .padding(vertical = Dimens.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SelectableAccountWithIcon(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f),
            address = address,
            accountName = accountName,
            accountIcon = accountIcon,
            selectableIcon = R.drawable.ic_checkmark_24,
            tint = MaterialTheme.customColors.statusSuccess,
            showSelectableIcon = isSelected,
        )

        Box(
            modifier = Modifier
                .padding(end = Dimens.x2, start = Dimens.x1)
                .wrapContentSize()
        ) {
            val dropDownExpandedState = remember { mutableStateOf(false) }
            Icon(
                modifier = Modifier
                    .testTagAsId("OpenAccountMenuItem")
                    .size(Dimens.x3)
                    .clickable { dropDownExpandedState.value = true },
                painter = painterResource(R.drawable.ic_menu_vertical_dots_24),
                tint = MaterialTheme.customColors.accentTertiary,
                contentDescription = null
            )

            AccountMenu(
                onSelectOptionsClicked = onSelectOptionsClicked,
                onAccountOptionsClicked = onAccountOptionsClicked,
                onDismissMenu = { dropDownExpandedState.value = false },
                address = address,
                expanded = dropDownExpandedState.value,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xffffff)
@Composable
private fun PreviewList() {
    Column {
        AccountList(
            state = AccountListScreenState(
                isActionMode = false,
                accountList = listOf(
                    ExportAccountData(
                        isSelected = false,
                        isSelectedAction = false,
                        icon = previewDrawable,
                        account = SoraAccount("cnFjl....sllkj", "name 1"),
                    ),
                    ExportAccountData(
                        isSelected = true,
                        isSelectedAction = false,
                        icon = previewDrawable,
                        account = SoraAccount("cnFjl....sllkj", "name 2"),
                    ),
                    ExportAccountData(
                        isSelected = false,
                        isSelectedAction = true,
                        icon = previewDrawable,
                        account = SoraAccount("cnFjl....sllkj", "name 3"),
                    ),
                )
            ),
            onAccountClicked = {},
            onAccountLongClicked = {},
            onSelectOptionsClicked = {},
            onAccountOptionsClicked = {},
        )
    }
}
