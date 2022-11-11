/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_list

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.drawable.toBitmap
import jp.co.soramitsu.common.presentation.compose.neumorphism.NeuCardPunched
import jp.co.soramitsu.common.presentation.compose.neumorphism.TonalNeumorphButton
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountListScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportAccountData
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun AccountListScreen(
    state: AccountListScreenState,
    viewModel: AccountListViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgPage),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.chooserActivated) {
            EditAccountList(state = state, onSelectOptionsClicked = viewModel::onAccountSelected)
        } else {
            if (state.accountList.isNotEmpty()) {
                AccountList(
                    state,
                    viewModel::onAccountClicked,
                    viewModel::onAccountLongClicked,
                    viewModel::onAccountSelected,
                    viewModel::onAccountOptionsClicked
                )
            }
            AddAccountButton(viewModel::onAddAccountClicked)
        }
    }
}

@Preview
@Composable
private fun PreviewAccountListScreen() {
    SoraAppTheme {
        val state = AccountListScreenState(
            false,
            listOf(
                ExportAccountData(true, false, BitmapDrawable(), "address", "accountName"),
                ExportAccountData(false, false, BitmapDrawable(), "address2", "accountName2"),
            )
        )

        AccountList(state, { }, { }, { }, { })
        AddAccountButton({ })
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
    NeuCardPunched(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
    ) {
        Column {
            state.accountList.forEach { element ->
                val dropDownExpandedState = remember { mutableStateOf(false) }

                element.run {
                    Option(
                        isActivated = isActivated,
                        accountIcon = icon,
                        accountName = accountName,
                        address = address,
                        onAccountClicked = onAccountClicked,
                        onAccountLongClicked = onAccountLongClicked,
                        onSelectOptionsClicked = onSelectOptionsClicked,
                        onAccountOptionsClicked = onAccountOptionsClicked,
                        dropDownExpandedState = dropDownExpandedState,
                        onDotsClick = { dropDownExpandedState.value = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Option(
    isActivated: Boolean,
    accountIcon: Drawable,
    accountName: String,
    address: String,
    onAccountClicked: (address: String) -> Unit,
    onAccountLongClicked: (address: String) -> Unit,
    onSelectOptionsClicked: (address: String) -> Unit,
    onAccountOptionsClicked: (address: String) -> Unit,
    dropDownExpandedState: MutableState<Boolean>,
    onDotsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(Dimens.x2)
            .combinedClickable(
                onClick = { onAccountClicked(address) },
                onLongClick = { onAccountLongClicked(address) },
            )
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isActivated) {
                Icon(
                    modifier = Modifier
                        .size(Dimens.x3),
                    painter = painterResource(id = R.drawable.ic_checkmark_24),
                    tint = MaterialTheme.customColors.accentPrimary,
                    contentDescription = null
                )
            } else {
                Box(modifier = Modifier.size(Dimens.x3))
            }

            Image(
                modifier = Modifier
                    .padding(start = Dimens.x2),
                bitmap = accountIcon.toBitmap().asImageBitmap(),
                contentDescription = null
            )

            Column(
                modifier = Modifier
                    .padding(start = Dimens.x1)
            ) {
                Text(
                    text = accountName,
                    style = MaterialTheme.customTypography.textM
                )

                Text(
                    text = address.truncateUserAddress(),
                    color = MaterialTheme.customColors.fgSecondary,
                    style = MaterialTheme.customTypography.textXS
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(Dimens.x05)
        ) {
            Icon(
                modifier = Modifier
                    .size(Dimens.x3)
                    .clickable { onDotsClick() },
                painter = painterResource(R.drawable.ic_neu_dots),
                tint = MaterialTheme.customColors.fgSecondary,
                contentDescription = null
            )

            AccountMenu(
                onSelectOptionsClicked = onSelectOptionsClicked,
                onAccountOptionsClicked = onAccountOptionsClicked,
                dropDownExpandedState = dropDownExpandedState,
                address = address
            )
        }
    }
}

@Composable
private fun AddAccountButton(onClick: () -> Unit) {
    TonalNeumorphButton(
        modifier = Modifier.padding(Dimens.x2),
        text = stringResource(id = R.string.asset_add),
        onClick = onClick,
        leftIcon = painterResource(R.drawable.ic_plus_24),
    )
}

@Composable
private fun AccountMenu(
    onSelectOptionsClicked: (address: String) -> Unit,
    onAccountOptionsClicked: (address: String) -> Unit,
    address: String,
    dropDownExpandedState: MutableState<Boolean>
) {
    MaterialTheme(shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(Dimens.x3))) {
        DropdownMenu(
            modifier = Modifier.background(MaterialTheme.customColors.bgSurface),
            expanded = dropDownExpandedState.value,
            onDismissRequest = { dropDownExpandedState.value = false }
        ) {
            DropdownMenuItem(onClick = { onSelectOptionsClicked(address) }) {
                Text(stringResource(id = R.string.export_select_account))
            }

            DropdownMenuItem(onClick = { onAccountOptionsClicked(address) }) {
                Text(stringResource(id = R.string.export_account_options))
            }
        }
    }
}

@Composable
private fun EditAccountList(
    state: AccountListScreenState,
    onSelectOptionsClicked: (address: String) -> Unit
) {
    NeuCardPunched(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
    ) {
        Column {
            state.accountList.forEach { element ->
                element.run {
                    ChooseOption(
                        isSelected = isSelected,
                        accountIcon = icon,
                        accountName = accountName,
                        address = address,
                        onSelect = onSelectOptionsClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooseOption(
    isSelected: Boolean,
    accountIcon: Drawable,
    accountName: String,
    address: String,
    onSelect: (address: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(Dimens.x2)
            .clickable { onSelect(address) }
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    modifier = Modifier
                        .size(Dimens.x3),
                    painter = painterResource(id = R.drawable.ic_selected_accent_pin_24),
                    tint = MaterialTheme.customColors.accentPrimary,
                    contentDescription = null
                )
            } else {
                Icon(
                    modifier = Modifier
                        .size(Dimens.x3),
                    painter = painterResource(id = R.drawable.ic_selected_pin_empty_24),
                    contentDescription = null
                )
            }

            Image(
                modifier = Modifier
                    .padding(start = Dimens.x2),
                bitmap = accountIcon.toBitmap().asImageBitmap(),
                contentDescription = null
            )

            Column(
                modifier = Modifier
                    .padding(start = Dimens.x1)
            ) {
                Text(
                    text = accountName,
                    style = MaterialTheme.customTypography.textM
                )

                Text(
                    text = address.truncateUserAddress(),
                    color = MaterialTheme.customColors.fgSecondary,
                    style = MaterialTheme.customTypography.textXS
                )
            }
        }
    }
}
